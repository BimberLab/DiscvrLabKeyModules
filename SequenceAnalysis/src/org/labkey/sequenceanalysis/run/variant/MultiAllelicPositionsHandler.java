package org.labkey.sequenceanalysis.run.variant;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.old.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.Compress;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.util.MultiAllelicPositionWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 4/24/2017.
 */
public class MultiAllelicPositionsHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public MultiAllelicPositionsHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Multi-allelic Positions", "This will run a custom tool to identify any positions on the selected BAMs with more than 2 alleles in the raw reads.  Any positions where a third allele is present above the supplied threshold will be flagged.  These positions often indicate misalignment; however, since variants are usually not called this information is not available for filtering based on VCFs alone.", null, Arrays.asList(
                ToolParameterDescriptor.create("basename", "File Basename", "This will be used as the basename for the output table", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, null),
                ToolParameterDescriptor.create("outputDescription", "Description", "This will be used as the description for output files", "textarea", new JSONObject(){{
                    put("allowBlank", false);
                    put("height", 150);
                }}, null),
                ToolParameterDescriptor.create("minDepth", "Min Depth", "The minimum depth required for a position to be considered", "ldk-integerfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, 20),
                ToolParameterDescriptor.create("callThreshold", "Call Threshold", "If provided, only sites detected in at least this many samples will be reported.", "ldk-integerfield", null, null),
                ToolParameterDescriptor.create("doSplitJobs", "Split Jobs", "If checked, this will run once per BAM, instead of processing all as one job.  This can be useful for processing large batches", "checkbox", null, false)
            ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getFile().exists() && SequenceUtil.FILETYPE.bam.getFileType().isType(o.getFile());
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
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());

            List<File> inputBams = new ArrayList<>();
            Set<Integer> libraryIds = new HashSet<>();
            for (SequenceOutputFile so : inputFiles)
            {
                inputBams.add(so.getFile());
                action.addInput(so.getFile(), "Input BAM");
                libraryIds.add(so.getLibrary_id());
            }

            if (libraryIds.size() != 1)
            {
                throw new PipelineJobException("Not all files use the same reference library");
            }
            ReferenceGenome rg = ctx.getSequenceSupport().getCachedGenome(libraryIds.iterator().next());
            action.addInput(rg.getSourceFastaFile(), "Reference Genome");

            String basename = ctx.getParams().getString("basename");

            File outputFile = new File(ctx.getOutputDir(), basename + ".bed");

            List<String> options = new ArrayList<>();
            Integer minDepth = ctx.getParams().optInt("minDepth", 0);
            if (minDepth > 0)
            {
                options.add("-minDepth");
                options.add(minDepth.toString());
            }

            MultiAllelicPositionWrapper wrapper = new MultiAllelicPositionWrapper(ctx.getLogger());
            wrapper.run(inputBams, outputFile, rg.getWorkingFastaFile(), options);
            if (!outputFile.exists())
            {
                throw new PipelineJobException("Unable to find expected file:" + outputFile.getPath());
            }

            Integer callThreshold = ctx.getParams().optInt("callThreshold", 0);
            if (callThreshold > 0)
            {
                ctx.getLogger().info("will only report sites present in at least " + callThreshold + " samples");
                File tmp = new File(ctx.getOutputDir(), "filteredSites.bed");
                int totalLines = 0;
                int linesFiltered = 0;
                try
                {
                    try (CSVReader reader = new CSVReader(Readers.getReader(outputFile), '\t'); CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(tmp), '\t', CSVWriter.NO_QUOTE_CHARACTER))
                    {
                        String[] line;
                        while ((line = reader.readNext()) != null)
                        {
                            totalLines++;
                            Integer val = Integer.parseInt(line[4]);
                            if (val < callThreshold)
                            {
                                linesFiltered++;
                                continue;
                            }

                            writer.writeNext(line);
                        }
                    }

                    ctx.getLogger().info("total lines inspected: " + totalLines);
                    ctx.getLogger().info("total lines filtered: " + linesFiltered);

                    outputFile.delete();
                    FileUtils.moveFile(tmp, outputFile);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            ctx.getLogger().info("compressing BED file");
            File compressedOutput = Compress.compressGzip(outputFile);
            if (outputFile.exists())
            {
                outputFile.delete();
            }

            action.addOutput(compressedOutput, "Multi-Allelic Sites Table", false);

            SequenceOutputFile so = new SequenceOutputFile();
            so.setName(compressedOutput.getName());
            so.setCategory("Multi-Allelic Sites Table");
            so.setFile(compressedOutput);
            String outputDescription = StringUtils.trimToNull(ctx.getParams().optString("outputDescription"));
            so.setDescription(outputDescription);
            if (inputFiles.size() == 1)
            {
                so.setReadset(inputFiles.get(0).getReadset());
                so.setAnalysis_id(inputFiles.get(0).getAnalysis_id());
            }

            ctx.getFileManager().addSequenceOutput(so);
        }
    }
}
