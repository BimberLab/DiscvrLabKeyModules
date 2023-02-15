package org.labkey.sequenceanalysis.analysis;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.SamRecordIntervalIteratorFactory;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.util.BuildBamIndexWrapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Created by bimber on 9/8/2014.
 */
public class AlignmentMetricsHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _bamFileType = new FileType("bam", false);

    public AlignmentMetricsHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Alignment Metrics", "This will iterate all alignments and create one or more feature tracks summarizing the alignment.  The original purpose was to create information used for QC purposes or to better understand how data performed against the reference.", null, Arrays.asList(
                ToolParameterDescriptor.create("windowSize", "Window Size", "Metrics will be gathered by iterating the genome with a window of this size", "ldk-integerfield", null, 500)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _bamFileType.isType(o.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            PipelineJob job = ctx.getJob();
            JSONObject params = ctx.getParams();

            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            Integer windowSize = params.has("windowSize") && params.get("windowSize") != null ? Integer.valueOf(params.getInt("windowSize")) : null;
            if (windowSize == null)
            {
                job.error("No window size provided, aborting");
                return;
            }

            List<File> bams = new ArrayList<>();
            bams.addAll(job.getJobSupport(FileAnalysisJobSupport.class).getInputFiles());
            if (bams.isEmpty())
            {
                job.error("No BAMS found, aborting");
                return;
            }

            List<SAMSequenceRecord> distinctReferences = new ArrayList<>();
            for (File bam : bams)
            {
                action.addInput(bam, "BAM File");

                File bai = new File(bam.getPath() + ".bai");
                if (!bai.exists())
                {
                    new BuildBamIndexWrapper(job.getLogger()).executeCommand(bam);
                }

                SamReaderFactory fact = SamReaderFactory.makeDefault();
                try (SamReader reader = fact.open(bam))
                {
                    SAMSequenceDictionary dict = reader.getFileHeader().getSequenceDictionary();
                    for (SAMSequenceRecord s : dict.getSequences())
                    {
                        distinctReferences.add(s);
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            File totalAlignmentsFile = new File(ctx.getOutputDir(), "totalAlignments.bed");
            action.addOutput(totalAlignmentsFile, "Total Alignments BED", false, true);
            File totalReadsFile = new File(ctx.getOutputDir(), "totalReads.bed");
            action.addOutput(totalReadsFile, "Total Reads BED", false, true);
            File duplicateReadsFile = new File(ctx.getOutputDir(), "duplicateReads.bed");
            action.addOutput(duplicateReadsFile, "Duplicate Reads BED", false, true);
            File notPrimaryAlignmentsFile = new File(ctx.getOutputDir(), "notPrimaryAlignments.bed");
            action.addOutput(notPrimaryAlignmentsFile, "Not Primary Alignments BED", false, true);
            File avgMappingQualFile = new File(ctx.getOutputDir(), "avgMappingQual.bed");
            action.addOutput(avgMappingQualFile, "Avg Mapping Quality BED", false, true);

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

                SamReaderFactory bamFact = SamReaderFactory.makeDefault();
                bamFact.validationStringency(ValidationStringency.SILENT);

                for (SAMSequenceRecord sr : distinctReferences)
                {
                    job.getLogger().info("starting reference sequence " + sr.getSequenceName());

                    List<File> bamsWithReference = new ArrayList<>();
                    for (File bam : bams)
                    {
                        try (SamReader reader = bamFact.open(bam))
                        {
                            SAMSequenceDictionary dict = reader.getFileHeader().getSequenceDictionary();
                            if (dict == null || dict.getSequence(sr.getSequenceName()) == null)
                            {
                                job.getLogger().info("sequence " + sr.getSequenceName() + " not found in BAM, skipping: " + bam.getName());
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
                        job.getLogger().info("window: " + windowStart + "/" + windowEnd);

                        int totalNotPrimaryAlignments = 0;
                        double totalMappingQuality = 0.0;
                        int totalAlignments = 0;
                        int totalReads = 0;
                        int totalDuplicateReads = 0;

                        for (File bam : bamsWithReference)
                        {
                            try (SamReader reader = bamFact.open(bam))
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

                action.setEndTime(new Date());
                ctx.addActions(action);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }
}
