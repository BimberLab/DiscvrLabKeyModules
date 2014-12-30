package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.SamRecordIntervalIteratorFactory;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;
import org.labkey.sequenceanalysis.api.pipeline.AbstractAnalysisStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.AnalysisStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceGenome;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by bimber on 9/8/2014.
 */
public class AlignmentMetricsAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public AlignmentMetricsAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractAnalysisStepProvider<AlignmentMetricsAnalysis>
    {
        public Provider()
        {
            super("AlignmentMetricsAnalysis", "Alignment Metrics", null, "This step will iterate all alignments and create one or more feature tracks summarizing the alignment.  The original purpose was to create information used for QC purposes or to better understand how data performed against the reference.", Arrays.asList(
                    ToolParameterDescriptor.create("windowSize", "Window Size", "Metrics will be gathered by iterating the genome with a window of this size", "ldk-integerfield", null, 500)
//                    ToolParameterDescriptor.create("multipleMappedReads", "Multiple Mapped Reads", "This will count the total number of reads/window which mapped against more than 1 location.  Note: not all aligners retain this information", "checkbox", new JSONObject()
//                    {{
//                        put("checked", true);
//                    }}, true),
//                    ToolParameterDescriptor.create("avgMappingQual", "Average Mapping Quality", "This will calculate the average mapping quality of all reads spanning the window.", "checkbox", new JSONObject()
//                    {{
//                            put("checked", true);
//                    }}, true)
            ), null, null);
        }

        @Override
        public AlignmentMetricsAnalysis create(PipelineContext ctx)
        {
            return new AlignmentMetricsAnalysis(this, ctx);
        }

    }

    @Override
    public void init(List<AnalysisModel> models) throws PipelineJobException
    {

    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta) throws PipelineJobException
    {
        return null;
    }

    @Override
    public Output performAnalysisPerSampleRemote(ReadsetModel rs, File inputBam, ReferenceGenome referenceGenome) throws PipelineJobException
    {
        return null;
    }

    @Override
    public void performAnalysisOnAll(List<AnalysisModel> analysisModels) throws PipelineJobException
    {
        Integer windowSize = getProvider().getParameterByName("windowSize").extractValue(getPipelineCtx().getJob(), getProvider(), Integer.class);
        if (windowSize == null)
        {
            getPipelineCtx().getJob().error("No window size provided, aborting");
            return;
        }

        List<File> bams  = new ArrayList<>();
        for (File f : getPipelineCtx().getJob().getJobSupport(FileAnalysisJobSupport.class).getInputFiles())
        {
            String basename = SequenceTaskHelper.getMinimalBaseName(f);
            File expectedBam = new File(getPipelineCtx().getSourceDirectory(), basename + "/Alignment/" + basename + ".bam");
            if (expectedBam.exists())
            {
                bams.add(expectedBam);
            }
        }

        if (bams.isEmpty())
        {
            getPipelineCtx().getJob().error("No BAMS found, aborting");
            return;
        }

        List<SAMSequenceRecord> distinctReferences = new ArrayList<>();
        for (File bam : bams)
        {
            File bai = new File(bam.getPath() + ".bai");
            try (SAMFileReader reader = new SAMFileReader(bam, bai))
            {
                SAMSequenceDictionary dict = reader.getFileHeader().getSequenceDictionary();
                for (SAMSequenceRecord s : dict.getSequences())
                {
                    distinctReferences.add(s);
                }
            }
        }

        File totalAlignmentsFile = new File(getPipelineCtx().getSourceDirectory(), "totalAlignments.bed");
        File totalReadsFile = new File(getPipelineCtx().getSourceDirectory(), "totalReads.bed");
        File duplicateReadsFile = new File(getPipelineCtx().getSourceDirectory(), "duplicateReads.bed");
        File notPrimaryAlignmentsFile = new File(getPipelineCtx().getSourceDirectory(), "notPrimaryAlignments.bed");
        File avgMappingQualFile = new File(getPipelineCtx().getSourceDirectory(), "avgMappingQual.bed");

        try (
            BufferedWriter totalAlignmentsWriter = new BufferedWriter(new FileWriter(totalAlignmentsFile, true));
            BufferedWriter totalReadsWriter = new BufferedWriter(new FileWriter(totalReadsFile, true));
            BufferedWriter duplicateReadsWriter = new BufferedWriter(new FileWriter(duplicateReadsFile, true));
            BufferedWriter notPrimaryAlignmentsWriter = new BufferedWriter(new FileWriter(notPrimaryAlignmentsFile, true));
            BufferedWriter avgMappingQualWriter = new BufferedWriter(new FileWriter(avgMappingQualFile, true))
        )
        {
            //add headers
            totalAlignmentsWriter.write("track name=\"TotalAlignments\" description=\"Contains the total # of alignments that span this region, which may be larger than the total # of distinct reads if there are multiple mappings\" useScore=1" + System.getProperty("line.separator"));
            totalReadsWriter.write("track name=\"TotalReads\" description=\"Contains the total # of reads that span this region\" useScore=1" + System.getProperty("line.separator"));
            duplicateReadsWriter.write("track name=\"DuplicateReads\" description=\"Contains the total # of reads that are marked as duplicates\" useScore=1" + System.getProperty("line.separator"));
            notPrimaryAlignmentsWriter.write("track name=\"NotPrimaryAlignments\" description=\"Contains the total # of reads that are tagged not being the primary alignment, which would indicate there is ambiguity in this region\" useScore=1" + System.getProperty("line.separator"));
            avgMappingQualWriter.write("track name=\"AvgMappingQuality\" description=\"Contains the average mapping quality of all reads spanning this region\" useScore=1" + System.getProperty("line.separator"));

            for (SAMSequenceRecord sr : distinctReferences)
            {
                getPipelineCtx().getLogger().info("starting reference sequence " + sr.getSequenceName());

                List<File> bamsWithReference = new ArrayList<>();
                for (File bam : bams)
                {
                    File bai = new File(bam.getPath() + ".bai");
                    try (SAMFileReader reader = new SAMFileReader(bam, bai))
                    {
                        SAMSequenceDictionary dict = reader.getFileHeader().getSequenceDictionary();
                        if (dict == null || dict.getSequence(sr.getSequenceName()) == null)
                        {
                            getPipelineCtx().getLogger().info("sequence " + sr.getSequenceName() + " not found in BAM, skipping: " + bam.getName());
                            continue;
                        }

                        bamsWithReference.add(bam);
                    }
                }

                int refLength = sr.getSequenceLength();
                //1-based
                int windowStart = 1;
                int windowEnd;

                while (windowStart <= refLength)
                {
                    //track as 1-based
                    windowEnd = Math.min(windowStart + windowSize, refLength);
                    getPipelineCtx().getLogger().info("window: " + windowStart + "/" + windowEnd);

                    int totalNotPrimaryAlignments = 0;
                    double totalMappingQuality = 0.0;
                    int totalAlignments = 0;
                    int totalReads = 0;
                    int totalDuplicateReads = 0;

                    for (File bam : bamsWithReference)
                    {
                        File bai = new File(bam.getPath() + ".bai");
                        try (SAMFileReader reader = new SAMFileReader(bam, bai))
                        {
                            SamRecordIntervalIteratorFactory fact = new SamRecordIntervalIteratorFactory();
                            try (CloseableIterator<SAMRecord> it = fact.makeSamRecordIntervalIterator(reader, Arrays.asList(new Interval(sr.getSequenceName(), windowStart, windowEnd)), true))
                            {
                                while (it.hasNext())
                                {
                                    totalAlignments++;

                                    SAMRecord rec = it.next();
                                    if (rec.isSecondaryOrSupplementary())
                                    {
                                        totalNotPrimaryAlignments++;
                                    }
                                    else
                                    {
                                        totalReads++;

                                        totalMappingQuality += rec.getMappingQuality();
                                        if (rec.getDuplicateReadFlag())
                                        {
                                            totalDuplicateReads++;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    //0-based start, open end coordinate
                    if (totalAlignments > 0)
                        totalAlignmentsWriter.write(StringUtils.join(new String[]{sr.getSequenceName(), Integer.toString(windowStart - 1), Integer.toString(windowEnd), Integer.toString(totalAlignments)}, '\t') + System.getProperty("line.separator"));
                    if (totalReads > 0)
                        totalReadsWriter.write(StringUtils.join(new String[]{sr.getSequenceName(), Integer.toString(windowStart - 1), Integer.toString(windowEnd), Integer.toString(totalReads)}, '\t') + System.getProperty("line.separator"));
                    if (totalDuplicateReads > 0)
                        duplicateReadsWriter.write(StringUtils.join(new String[]{sr.getSequenceName(), Integer.toString(windowStart - 1), Integer.toString(windowEnd), Integer.toString(totalDuplicateReads)}, '\t') + System.getProperty("line.separator"));
                    if (totalNotPrimaryAlignments > 0)
                        notPrimaryAlignmentsWriter.write(StringUtils.join(new String[]{sr.getSequenceName(), Integer.toString(windowStart - 1), Integer.toString(windowEnd), Integer.toString(totalNotPrimaryAlignments)}, '\t') + System.getProperty("line.separator"));
                    if (totalMappingQuality > 0)
                        avgMappingQualWriter.write(StringUtils.join(new String[]{sr.getSequenceName(), Integer.toString(windowStart - 1), Integer.toString(windowEnd), Double.toString(totalMappingQuality / totalReads)}, '\t') + System.getProperty("line.separator"));

                    windowStart += windowSize;
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
