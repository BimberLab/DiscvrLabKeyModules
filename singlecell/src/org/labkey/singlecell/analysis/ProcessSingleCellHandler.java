package org.labkey.singlecell.analysis;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.singlecell.CellHashingServiceImpl;
import org.labkey.singlecell.SingleCellModule;
import org.labkey.singlecell.pipeline.singlecell.PrepareRawCounts;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ProcessSingleCellHandler implements SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>, SequenceOutputHandler.HasActionNames
{
    private static FileType LOUPE_TYPE = new FileType("cloupe", false);

    public ProcessSingleCellHandler()
    {

    }

    @Override
    public String getName()
    {
        return "Single Cell Processing";
    }

    @Override
    public String getDescription()
    {
        return "Run one or more tools to process 10x scRNA-seq Data";
    }

    @Override
    public String getButtonJSHandler()
    {
        return null;
    }

    @Override
    public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return DetailsURL.fromString("/singlecell/singleCellProcessing.view?outputFileIds=" + StringUtils.join(outputFileIds, ";"), c).getActionURL();
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(SingleCellModule.class);
    }

    @Override
    public LinkedHashSet<String> getClientDependencies()
    {
        return null;
    }

    @Override
    public boolean useWorkbooks()
    {
        return true;
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && LOUPE_TYPE.isType(f.getFile());
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
    public Collection<String> getAllowableActionNames()
    {
        Set<String> allowableNames = new HashSet<>();
        for (PipelineStepProvider provider: SequencePipelineService.get().getProviders(SingleCellStep.class))
        {
            allowableNames.add(provider.getLabel());
        }

        return allowableNames;
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            boolean requiresHashingOrCite = false;
            List<PipelineStepCtx<SingleCellStep>> steps = SequencePipelineService.get().getSteps(ctx.getJob(), SingleCellStep.class);
            for (PipelineStepCtx<SingleCellStep> stepCtx : steps)
            {
                SingleCellStep step = stepCtx.getProvider().create(ctx);
                step.init(ctx, inputFiles);

                if (step.requiresHashingOrCiteSeq())
                {
                    requiresHashingOrCite = true;
                }

                for (ToolParameterDescriptor pd : stepCtx.getProvider().getParameters())
                {
                    if (pd instanceof ToolParameterDescriptor.CachableParam)
                    {
                        ctx.getLogger().debug("caching params for : " + pd.getName());
                        Object val = pd.extractValue(ctx.getJob(), stepCtx.getProvider(), stepCtx.getStepIdx(), Object.class);
                        ((ToolParameterDescriptor.CachableParam)pd).doCache(ctx.getJob(), val, ctx.getSequenceSupport());
                    }
                }
            }

            if (requiresHashingOrCite)
            {
                CellHashingService.get().prepareHashingAndCiteSeqFilesIfNeeded(ctx.getSourceDirectory(), ctx.getJob(), ctx.getSequenceSupport(), "readsetId", false, false, false);
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            List<PipelineStepCtx<SingleCellStep>> steps = SequencePipelineService.get().getSteps(ctx.getJob(), SingleCellStep.class);

            String basename;
            if (inputFiles.size() == 1 && inputFiles.get(0).getReadset() != null)
            {
                Integer readsetId = inputFiles.get(0).getReadset();
                Readset rs = ctx.getSequenceSupport().getCachedReadset(readsetId);
                basename = FileUtil.makeLegalName(rs.getName());
            }
            else
            {
                basename = "SingleCellProcessing";
            }


            List<File> markdowns = new ArrayList<>();

            // Step 1: read raw data
            PrepareRawCounts prepareRawCounts = new PrepareRawCounts.Provider().create(ctx);
            ctx.getLogger().info("Starting to run: " + prepareRawCounts.getProvider().getLabel());
            List<SingleCellStep.SeuratObjectWrapper> inputMatrices = new ArrayList<>();
            inputFiles.forEach(x -> {
                String datasetName = ctx.getSequenceSupport().getCachedReadset(x.getReadset()).getName();
                File source = new File(x.getFile().getParentFile(), "raw_feature_bc_matrix");
                if (!source.exists())
                {
                    throw new IllegalArgumentException("Unable to find file: " + source.getPath());
                }

                try
                {
                    File localCopy = new File(ctx.getOutputDir(), x.getRowid() + "_RawData");
                    if (localCopy.exists())
                    {
                        ctx.getLogger().debug("Deleting directory: " + localCopy.getPath());
                        FileUtils.deleteDirectory(localCopy);
                    }

                    ctx.getLogger().debug("Copying raw data directory: " + source.getPath());
                    ctx.getLogger().debug("To: " + localCopy.getPath());

                    FileUtils.copyDirectory(source, localCopy);

                    inputMatrices.add(new SingleCellStep.SeuratObjectWrapper(String.valueOf(x.getRowid()), datasetName, localCopy));
                }
                catch (IOException e)
                {
                    throw new IllegalArgumentException(e);
                }
            });

            SingleCellStep.Output output0 = prepareRawCounts.execute(ctx, inputMatrices, basename + ".rawCounts");
            List<SingleCellStep.SeuratObjectWrapper> currentFiles =  output0.getSeuratObjects();
            markdowns.add(output0.getMarkdownFile());

            // Step 2: iterate seurat processing:
            String outputPrefix = basename;
            for (PipelineStepCtx<SingleCellStep> stepCtx : steps)
            {
                ctx.getLogger().info("Starting to run: " + stepCtx.getProvider().getLabel());

                ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Running: " + stepCtx.getProvider().getLabel());

                RecordedAction action = new RecordedAction(stepCtx.getProvider().getLabel());
                Date start = new Date();
                action.setStartTime(start);
                currentFiles.forEach(currentFile -> action.addInput(currentFile.getFile(), "Input Seurat Object"));
                ctx.getFileManager().addIntermediateFiles(currentFiles.stream().map(SingleCellStep.SeuratObjectWrapper::getFile).collect(Collectors.toList()));

                SingleCellStep step = stepCtx.getProvider().create(ctx);
                step.setStepIdx(stepCtx.getStepIdx());

                outputPrefix = outputPrefix + "." + step.getProvider().getName() + (step.getStepIdx() == 0 ? "" : "-" + step.getStepIdx());
                SingleCellStep.Output output = step.execute(ctx, currentFiles, outputPrefix);
                ctx.getFileManager().addStepOutputs(action, output);

                if (output.getSeuratObjects() != null)
                {
                    currentFiles = new ArrayList<>(output.getSeuratObjects());
                }
                else if (step.createsSeuratObjects())
                {
                    throw new PipelineJobException("Expected step to create seurat objects but none reported");
                }
                else
                {
                    ctx.getLogger().info("No seurat objects were produced");
                }

                markdowns.add(output.getMarkdownFile());

                Date end = new Date();
                action.setEndTime(end);
                ctx.getJob().getLogger().info(stepCtx.getProvider().getLabel() + " Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));
            }

            for (SingleCellStep.SeuratObjectWrapper seurat : currentFiles)
            {
                if (seurat.getFile().exists())
                {
                    ctx.getFileManager().removeIntermediateFile(seurat.getFile());
                }
            }

            if (markdowns.isEmpty())
            {
                throw new PipelineJobException("No markdown files produced!");
            }

            //TODO: process with pandoc
        }
    }
}
