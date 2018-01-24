package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.fastq.FastqWriterFactory;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalList;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
                    }}, null)
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

        AnalysisOutputImpl output = new AnalysisOutputImpl();

        File fq1 = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + ".overlapping-R1.fastq.gz");
        File fq2 = new File(inputBam.getParentFile(), FileUtil.getBaseName(inputBam) + ".overlapping-R2.fastq.gz");
        FastqWriterFactory fact = new FastqWriterFactory();
        fact.setUseAsyncIo(true);
        try (FastqWriter writer1 = fact.newWriter(fq1); FastqWriter writer2 = fact.newWriter(fq2))
        {
            String[] intervals = intervalList.split("\\r?\\n");
            Pattern intervalRe = Pattern.compile("^(.+):([0-9]+)-([0-9]+)$");
            List<String> comments = new ArrayList<>();
            int totalAdded = 0;
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
                int added = calculateForInterval(writer1, writer2, inputBam, refName, start, stop);
                totalAdded += added;
                if (added > 0)
                {
                    comments.add(refName + ":" + start + "-" + stop + ": " + added);
                }
                getPipelineCtx().getLogger().info("total read pairs: " + added);
            }

            String description = "Total pairs: " + totalAdded + "\n" + StringUtils.join(comments, '\n');
            output.addSequenceOutput(fq1, "Overlapping Reads - Forward", "Overlapping Reads", rs.getRowId(), null, referenceGenome.getGenomeId(), description);
            output.addSequenceOutput(fq2, "Overlapping Reads - Reverse", "Overlapping Reads", rs.getRowId(), null, referenceGenome.getGenomeId(), description);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }

    private int calculateForInterval(FastqWriter writer1, FastqWriter writer2, File inputBam, String refName, int start, int stop) throws IOException
    {
        int totalAdded = 0;
        SamReaderFactory bamFact = SamReaderFactory.makeDefault();
        bamFact.validationStringency(ValidationStringency.SILENT);
        try (SamReader sam = bamFact.open(inputBam);SamReader mateReader = bamFact.open(inputBam))
        {
            IntervalList il = new IntervalList(sam.getFileHeader());
            il.add(new Interval(refName, start, stop));

            try (SAMRecordIterator it = sam.queryOverlapping(refName, start, stop))
            {
                while (it.hasNext())
                {
                    SAMRecord r = it.next();
                    if (r.isSecondaryOrSupplementary())
                    {
                        continue;
                    }

                    if (!r.getReadPairedFlag())
                    {
                        throw new IOException("This tool only supports paired-end reads");
                    }

                    SAMRecord mate = mateReader.queryMate(r);
                    if (mate == null)
                    {
                        throw new IOException("Unable to find mate for read: " + r.getReadName());
                    }

                    writer1.write(new FastqRecord(r.getReadName(), r.getReadBases(), null, r.getBaseQualities()));
                    writer2.write(new FastqRecord(mate.getReadName(), mate.getReadBases(), null, mate.getBaseQualities()));
                    totalAdded++;
                }
            }
        }

        return totalAdded;
    }
}
