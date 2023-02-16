package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.reference.FastaSequenceIndex;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalList;
import htsjdk.samtools.util.SamLocusIterator;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisServiceImpl;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: bimber
 * Date: 7/3/2014
 * Time: 11:29 AM
 */
public class SnpCountAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public SnpCountAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractAnalysisStepProvider<SnpCountAnalysis>
    {
        public Provider()
        {
            super("SnpCountAnalysis", "SNP Count", null, "This will calculate the count per base and avg quality at each position over the intervals selected.  There are many uses of this information, which could include quantifying a mixed sequence population or calculating a consensus base.  Note: this will ignore indels.", List.of(
                    ToolParameterDescriptor.create("intervals", "Interval(s)", "Provide a list of intervals to scan.  These should be in the form 'ReferenceName:Start-Stop' (ie. chr01:2039-2504)", "textarea", new JSONObject()
                    {{
                        put("width", 600);
                        put("height", 150);
                        put("allowBlank", false);
                    }}, null)
            ), null, null);
        }

        @Override
        public SnpCountAnalysis create(PipelineContext ctx)
        {
            return new SnpCountAnalysis(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        return null;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();
        
        String intervalList = StringUtils.trimToNull(getProvider().getParameterByName("intervals").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()));
        if (intervalList == null)
        {
            throw new PipelineJobException("Must provide a list of intervals to query");
        }

        File indexFile = new File(inputBam.getPath() + ".bai");
        if (!indexFile.exists())
        {
            getPipelineCtx().getLogger().error("BAM index does not exist, expected: " + indexFile.getPath());
            indexFile = null;
        }

        File fastaIndexFile = new File(referenceFasta.getPath() + ".fai");
        if (!fastaIndexFile.exists())
        {
            getPipelineCtx().getLogger().error("FASTA index does not exist, expected: " + fastaIndexFile.getPath());
            fastaIndexFile = null;
        }

        File outputFile = new File(getPipelineCtx().getJob().getJobSupport(FileAnalysisJobSupport.class).getAnalysisDirectory(), FileUtil.getBaseName(inputBam) + ".snps.txt");
        SequenceReadsetImpl rs = SequenceAnalysisServiceImpl.get().getReadset(model.getReadset(), getPipelineCtx().getJob().getUser());

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, true));IndexedFastaSequenceFile indexedFastaSequenceFile = new IndexedFastaSequenceFile(referenceFasta, new FastaSequenceIndex(fastaIndexFile)))
        {
            writer.write(StringUtils.join(new String[]{"AlignmentFile", "ReadsetName", "RefName", "Pos", "RefBase", "TotalDepth", "TotalA", "TotalT", "TotalG", "TotalC", "TotalN", "AvgQualA", "AvgQualT", "AvgQualG", "AvgQualC", "AvgQualN"}, "\t") + System.getProperty("line.separator"));

            String[] intervals = intervalList.split("\\r?\\n");
            Pattern intervalRe = Pattern.compile("^(.+):([0-9]+)-([0-9]+)$");
            for (String interval : intervals)
            {
                Matcher m = intervalRe.matcher(interval);
                if (!m.find())
                {
                    getPipelineCtx().getLogger().error("Invalid interval, skipping: " + interval);
                    continue;
                }

                String refName = m.group(1);
                Integer start = Integer.parseInt(m.group(2));
                Integer stop = Integer.parseInt(m.group(3));

                getPipelineCtx().getLogger().info("calculating bases over " + refName + ": " + start + "-" + stop);
                calculateForInterval(writer, inputBam, indexFile, indexedFastaSequenceFile, inputBam.getName(), rs.getName(), refName, start, stop);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        output.addSequenceOutput(outputFile, "SNP Count: " + rs.getName(), "SNP Count Output", model.getReadset(), model.getAnalysisId(), model.getLibraryId(), null);

        return output;
    }

    private void calculateForInterval(BufferedWriter writer, File inputBam, File bamIndex, IndexedFastaSequenceFile indexedFastaSequenceFile, String alignmentFileName, String readsetName, String refName, int start, int stop) throws IOException
    {
        SamReaderFactory bamFact = SamReaderFactory.makeDefault();
        bamFact.validationStringency(ValidationStringency.SILENT);
        try (SamReader sam = bamFact.open(inputBam))
        {
            IntervalList il = new IntervalList(sam.getFileHeader());
            il.add(new Interval(refName, start, stop));

            //NOTE: when useIndex=true, something in htsjdk seems to be holding onto the BAM, giving errors in the junit tests
            try (SamLocusIterator sli = new SamLocusIterator(sam, il, false))
            {
                sli.setEmitUncoveredLoci(false);

                ReferenceSequence refSeq = indexedFastaSequenceFile.getSequence(refName);

                Iterator<SamLocusIterator.LocusInfo> it = sli.iterator();
                int idx = 0;
                while (it.hasNext())
                {
                    SamLocusIterator.LocusInfo locus = it.next();
                    idx++;

                    if (idx % 2500 == 0)
                    {
                        getPipelineCtx().getLogger().info("processed " + idx + " loci in SNP Count Analysis");
                    }

                    Map<String, Integer> totalByBase = new HashMap<>();
                    Map<String, Integer> totalQualByBase = new HashMap<>();
                    int depth = 0;
                    for (SamLocusIterator.RecordAndOffset r : locus.getRecordAndPositions())
                    {
                        depth++;
                        String base = Character.toString((char) r.getReadBase());
                        if (!totalByBase.containsKey(base))
                        {
                            totalByBase.put(base, 1);
                            totalQualByBase.put(base, (int) r.getBaseQuality());

                        }
                        else
                        {
                            totalByBase.put(base, totalByBase.get(base) + 1);
                            totalQualByBase.put(base, totalQualByBase.get(base) + r.getBaseQuality());
                        }
                    }

                    String refBase = String.valueOf((char) refSeq.getBases()[locus.getPosition() - 1]);

                    Map<String, Double> avgQualMap = new HashMap<>();
                    for (String b : totalQualByBase.keySet())
                    {
                        double avg = totalQualByBase.get(b) / totalByBase.get(b).doubleValue();
                        avgQualMap.put(b, avg);
                    }

                    writer.write(StringUtils.join(new String[]{
                            alignmentFileName,
                            readsetName,
                            refName,
                            String.valueOf(locus.getPosition()),
                            refBase,
                            String.valueOf(depth),
                            (totalByBase.containsKey("A") ? totalByBase.get("A").toString() : "0"),
                            (totalByBase.containsKey("T") ? totalByBase.get("T").toString() : "0"),
                            (totalByBase.containsKey("G") ? totalByBase.get("G").toString() : "0"),
                            (totalByBase.containsKey("C") ? totalByBase.get("C").toString() : "0"),
                            (totalByBase.containsKey("N") ? totalByBase.get("N").toString() : "0"),
                            //TODO: indel

                            (avgQualMap.containsKey("A") ? avgQualMap.get("A").toString() : "0"),
                            (avgQualMap.containsKey("T") ? avgQualMap.get("T").toString() : "0"),
                            (avgQualMap.containsKey("G") ? avgQualMap.get("G").toString() : "0"),
                            (avgQualMap.containsKey("C") ? avgQualMap.get("C").toString() : "0"),
                            (avgQualMap.containsKey("N") ? avgQualMap.get("N").toString() : "0"),
                    }, "\t") + System.getProperty("line.separator"));
                }
            }
        }
    }
}
