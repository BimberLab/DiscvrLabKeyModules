package org.labkey.singlecell.analysis;

import org.apache.commons.lang3.StringUtils;
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
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.singlecell.SingleCellModule;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ProcessSingleCellHandler implements SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>, SequenceOutputHandler.HasActionNames
{
    private static FileType LOUPE_TYPE = new FileType("cloupe", false);

    //TODO:
    //private ProcessSingleCellHandler.Resumer _resumer;

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
        return DetailsURL.fromString("/singlecell/scProcessing.view?outputFileIds=" + StringUtils.join(outputFileIds, ";"), c).getActionURL();
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
            List<PipelineStepCtx<SingleCellStep>> steps = SequencePipelineService.get().getSteps(ctx.getJob(), SingleCellStep.class);
            for (PipelineStepCtx<SingleCellStep> stepCtx : steps)
            {
                SingleCellStep step = stepCtx.getProvider().create(ctx);
                step.init(ctx, inputFiles);
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

            SingleCellStep.SeuratContext sc = new SingleCellStep.SeuratContext()
            {
                @Override
                public int hashCode()
                {
                    return super.hashCode();
                }
            };

            for (PipelineStepCtx<SingleCellStep> stepCtx : steps)
            {
                SingleCellStep step = stepCtx.getProvider().create(ctx);
                //step.execute(inputFiles, sc);

            }
        }
    }
}
