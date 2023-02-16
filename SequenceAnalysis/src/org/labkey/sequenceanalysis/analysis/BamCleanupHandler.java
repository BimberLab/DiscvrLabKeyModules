package org.labkey.sequenceanalysis.analysis;

import htsjdk.samtools.SAMFileHeader;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SamSorter;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.util.AddOrReplaceReadGroupsWrapper;
import org.labkey.sequenceanalysis.run.util.BuildBamIndexWrapper;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 2/3/2016.
 */
public class BamCleanupHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private final FileType _bamFileType = new FileType("bam", false);

    public BamCleanupHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Cleanup BAM File", "This gives the option to run picard tools AddReadGroups and/or SortSam.  These two steps are often required for entry into downstream tools like GATK HaplotypeCaller.", null, Arrays.asList(
                ToolParameterDescriptor.create("addReadGroups", "Add ReadGroups", "This will run picard tools Add ReadGroups, based on the readset information", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true),
                ToolParameterDescriptor.create("sortBam", "Sort BAM", "This will sort the BAM by coordinate", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true)
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

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile so : inputFiles)
            {
                if (so.getReadset() != null)
                {
                    ctx.getSequenceSupport().cacheReadset(so.getReadset(), ctx.getJob().getUser());
                }
                else
                {
                    ctx.getJob().getLogger().error("Output file lacks a readset and will be skipped: " + so.getRowid());
                }
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            PipelineJob job = ctx.getJob();
            JSONObject params = ctx.getParams();

            if (inputFiles.isEmpty())
            {
                job.getLogger().warn("no input files");
            }

            for (SequenceOutputFile so : inputFiles)
            {
                RecordedAction action = new RecordedAction(getName());
                action.setStartTime(new Date());
                action.addInput(so.getFile(), "Input BAM");

                File input = so.getFile();
                File output = null;
                Set<File> toDelete = new HashSet<>();

                String addReadGroups = params.optString("addReadGroups", null);
                if (params.optBoolean(addReadGroups, false))
                {
                    if (so.getReadset() == null)
                    {
                        job.getLogger().error("Output file lacks a readset and will be skipped: " + so.getRowid());
                        continue;
                    }

                    Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                    if (rs == null)
                    {
                        job.getLogger().error("Unable to find readset for output file: " + so.getRowid());
                        continue;
                    }

                    output = new File(ctx.getOutputDir(), FileUtil.getBaseName(input) + ".readgroups.bam");

                    AddOrReplaceReadGroupsWrapper wrapper = new AddOrReplaceReadGroupsWrapper(job.getLogger());
                    wrapper.executeCommand(input, output, rs.getReadsetId().toString(), rs.getPlatform(), rs.getReadsetId().toString(), rs.getName().replaceAll(" ", "_"));

                    if (!output.exists())
                    {
                        throw new PipelineJobException("Expected output not found: " + output.getPath());
                    }

                    toDelete.add(output);
                    input = output;
                }

                String sortBam = params.optString("sortBam", null);
                if (params.optBoolean(sortBam, false))
                {
                    output = new File(ctx.getOutputDir(), FileUtil.getBaseName(input) + ".sorted.bam");
                    new SamSorter(job.getLogger()).execute(input, output, SAMFileHeader.SortOrder.coordinate);
                    if (!output.exists())
                    {
                        throw new PipelineJobException("Expected output not found: " + output.getPath());
                    }
                }

                if (output == null)
                {
                    job.getLogger().error("No options chosen, nothing to do");
                    return;
                }

                File finalOutput = new File(ctx.getOutputDir(), FileUtil.getBaseName(so.getFile()) + ".cleaned.bam");
                try
                {
                    FileUtils.moveFile(output, finalOutput);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                File outputIdx = new BuildBamIndexWrapper(job.getLogger()).executeCommand(finalOutput);

                action.addOutput(finalOutput, "Cleaned BAM", false);
                action.addOutput(outputIdx, "Cleaned BAM Index", false);
                ctx.addActions(action);

                SequenceOutputFile o = new SequenceOutputFile();
                o.setName(finalOutput.getName());
                o.setFile(finalOutput);
                o.setLibrary_id(so.getLibrary_id());
                o.setCategory("Alignment");
                o.setReadset(so.getReadset());

                ctx.addSequenceOutput(o);

                for (File f : toDelete)
                {
                    if (f.exists())
                    {
                        job.getLogger().debug("deleting intermediate file: " + f.getName());
                        f.delete();
                    }
                }
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }
}