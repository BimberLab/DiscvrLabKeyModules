package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.fastq.FastqReader;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.fastq.FastqWriterFactory;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.ReadData;
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
import org.labkey.sequenceanalysis.run.assembly.TrinityRunner;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: bimber
 * Date: 7/3/2014
 * Time: 11:29 AM
 */
public class ExportOverlappingReadsAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public ExportOverlappingReadsAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractAnalysisStepProvider<ExportOverlappingReadsAnalysis>
    {
        public Provider()
        {
            super("ExportOverlappingReads", "Export Overlapping Reads", null, "This will export any read pairs overlapping the provided intervals.", Arrays.asList(
                    ToolParameterDescriptor.create("intervals", "Interval(s)", "Provide a list of intervals to scan.  These should be in the form 'ReferenceName:Start-Stop' (ie. chr01:2039-2504)", "textarea", new JSONObject()
                    {{
                        put("width", 600);
                        put("height", 150);
                        put("allowBlank", false);
                    }}, null),
                    ToolParameterDescriptor.create("discardFastq", "Discard Raw FASTQ", "If checked, the FASTQs of overlapping reads will be discarded, leaving only theassembled contigs", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true)
            ), null, null);
        }

        @Override
        public ExportOverlappingReadsAnalysis create(PipelineContext ctx)
        {
            return new ExportOverlappingReadsAnalysis(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        String intervalList = StringUtils.trimToNull(getProvider().getParameterByName("intervals").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()));
        boolean discardFastq = getProvider().getParameterByName("discardFastq").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);

        if (intervalList == null)
        {
            throw new PipelineJobException("Must provide a list of intervals to query");
        }

        File indexFile = new File(inputBam.getPath() + ".bai");
        if (!indexFile.exists())
        {
            getPipelineCtx().getLogger().error("BAM index does not exist, expected: " + indexFile.getPath());
        }

        AnalysisOutputImpl output = new AnalysisOutputImpl();

        List<String> segmentSummary = new ArrayList<>();
        int totalAdded = 0;

        try
        {
            String[] intervals = intervalList.split("\\r?\\n");
            Pattern intervalRe = Pattern.compile("^(.+):([0-9]+)-([0-9]+)$");
            Set<String> distinctReadNames = new HashSet<>(10000);
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
                int added = calculateForInterval(inputBam, refName, start, stop, distinctReadNames);
                totalAdded += added;
                if (added > 0)
                {
                    segmentSummary.add(refName + ":" + start + "-" + stop + ": " + added);
                }
                getPipelineCtx().getLogger().info("total read pairs added: " + added);
            }

            if (distinctReadNames.isEmpty())
            {
                getPipelineCtx().getLogger().info("no overlapping reads found");
                return output;
            }

            File fq1 = new File(outputDir, FileUtil.getBaseName(inputBam) + ".overlapping-R1.fastq.gz");
            File fq2 = new File(outputDir, FileUtil.getBaseName(inputBam) + ".overlapping-R2.fastq.gz");
            FastqWriterFactory fact = new FastqWriterFactory();
            fact.setUseAsyncIo(true);
            try (FastqWriter writer1 = fact.newWriter(fq1); FastqWriter writer2 = fact.newWriter(fq2))
            {
                getPipelineCtx().getLogger().debug("total file pairs: " + rs.getReadData().size());
                for (ReadData rd : rs.getReadData())
                {
                    try (FastqReader reader1 = new FastqReader(rd.getFile1()); FastqReader reader2 = new FastqReader(rd.getFile2()))
                    {
                        getPipelineCtx().getLogger().debug("inspecting fastq: " + rd.getFile1().getPath());
                        while (reader1.hasNext())
                        {
                            FastqRecord rec1 = reader1.next();
                            FastqRecord rec2 = reader2.next();

                            String name = rec1.getReadName().split(" ")[0];
                            if (distinctReadNames.contains(name))
                            {
                                writer1.write(rec1);
                                writer2.write(rec2);

                                totalAdded++;
                            }
                        }
                    }
                }
            }

            getPipelineCtx().getLogger().info("total alignments found: " + distinctReadNames.size());
            getPipelineCtx().getLogger().info("total reads written: " + totalAdded);

            if (totalAdded > 0)
            {
                //now run Trinity:
                TrinityRunner tr = new TrinityRunner(getPipelineCtx().getLogger());
                String commentLine = segmentSummary.size() > 5 ? "" : ". \n" + StringUtils.join(segmentSummary, ". \n");
                tr.setThrowNonZeroExits(false); //this will occur if no contigs are found
                File trinityFasta = tr.performAssembly(fq1, fq2, outputDir, FileUtil.getBaseName(inputBam), Arrays.asList("--max_memory", "8G"), true, true);
                if (trinityFasta != null && trinityFasta.exists())
                {
                    long lineCount = SequenceUtil.getLineCount(trinityFasta) / 2;
                    String description = "Total contigs: " + lineCount + ". \n" + "Total pairs: " + totalAdded + commentLine;
                    output.addSequenceOutput(trinityFasta, "Assembled Overlapping Reads: " + rs.getName(), "Overlapping Contigs", rs.getRowId(), null, referenceGenome.getGenomeId(), description);
                }
            }
            else
            {
                getPipelineCtx().getLogger().error("no matching reads found, despite matching alignments");
            }
            if (discardFastq)
            {
                output.addIntermediateFile(fq1);
                output.addIntermediateFile(fq2);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        //output.addSequenceOutput(fq1, "Overlapping Reads - Forward", "Overlapping Reads", rs.getRowId(), null, referenceGenome.getGenomeId(), description);
        //output.addSequenceOutput(fq2, "Overlapping Reads - Reverse", "Overlapping Reads", rs.getRowId(), null, referenceGenome.getGenomeId(), description);

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }

    private int calculateForInterval(File inputBam, String refName, int start, int stop, Set<String> distinctReadNames) throws IOException
    {
        int totalAdded = 0;
        int totalPreviouslyEncountered = 0;
        SamReaderFactory bamFact = SamReaderFactory.makeDefault();
        bamFact.validationStringency(ValidationStringency.SILENT);
        try (SamReader sam = bamFact.open(inputBam))
        {
            try (SAMRecordIterator it = sam.queryOverlapping(refName, start, stop))
            {
                int i = 0;
                while (it.hasNext())
                {
                    i++;
                    if (i % 10000 == 0)
                    {
                        getPipelineCtx().getLogger().info("processed " + i + " reads");
                    }

                    SAMRecord r = it.next();
                    if (r.isSecondaryOrSupplementary())
                    {
                        continue;
                    }

                    if (!r.getReadPairedFlag())
                    {
                        throw new IOException("This tool only supports paired-end reads");
                    }

                    if (distinctReadNames.contains(r.getReadName()))
                    {
                        totalPreviouslyEncountered++;
                    }
                    else
                    {
                        distinctReadNames.add(r.getReadName());
                    }
                }
            }
        }

        getPipelineCtx().getLogger().info("total reads previously encountered (in multiple intervals): " + totalPreviouslyEncountered);

        return totalAdded;
    }
}
