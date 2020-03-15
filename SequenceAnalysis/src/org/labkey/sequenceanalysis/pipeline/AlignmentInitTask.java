package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceLibraryStep;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 12/15/12
 * Time: 8:34 PM
 *
 * This task is designed to create the reference FASTA, which requires the DB.  this task will run
 * on the webserver
 */
public class AlignmentInitTask extends WorkDirectoryTask<AlignmentInitTask.Factory>
{
    private static final String ACTIONNAME = "Preparing Run";
    public static final String ID_KEY_FILE = "Reference Id Key";

    private SequenceTaskHelper _taskHelper;

    protected AlignmentInitTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(AlignmentInitTask.class);
            setJoin(true);
        }

        @Override
        public boolean isParticipant(PipelineJob job)
        {
            //note: this must be included because this is now how we cache readsets
            //consider moving this to sequence job?
            return true;
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return ACTIONNAME.toUpperCase();
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(ACTIONNAME);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            AlignmentInitTask task = new AlignmentInitTask(this, job);

            return task;
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private SequenceTaskHelper getHelper()
    {
        return _taskHelper;
    }

    private SequenceAlignmentJob getPipelineJob()
    {
        return (SequenceAlignmentJob)getJob();
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        _taskHelper = new SequenceTaskHelper(getPipelineJob(), _wd);

        getJob().getLogger().info("Starting to process readset: " + getPipelineJob().getReadset().getName() + " (" + getPipelineJob().getReadset().getRowId() + ")");

        if (getPipelineJob().getReadset().hasArchivedData())
        {
            throw new PipelineJobException("The input readset has archived read data and cannot be used for new alignments");
        }

        getHelper().cacheExpDatasForParams();

        //build reference if needed
        if (SequenceTaskHelper.isAlignmentUsed(getJob()))
        {
            RecordedAction action = new RecordedAction(ACTIONNAME);
            List<PipelineStepCtx<ReferenceLibraryStep>> steps = SequencePipelineService.get().getSteps(getJob(), ReferenceLibraryStep.class);
            if (steps.isEmpty())
            {
                throw new PipelineJobException("No reference library type was supplied");
            }
            else if (steps.size() > 1)
            {
                throw new PipelineJobException("More than 1 reference library type was supplied");
            }
            else
            {
                getHelper().getFileManager().addInput(action, "Job Parameters", getHelper().getJob().getParametersFile());
                getJob().getLogger().info("Creating Reference Library FASTA");

                ReferenceLibraryStep step = steps.get(0).getProvider().create(getHelper());

                //ensure the FASTA exists
                File sharedDirectory = new File(getHelper().getJob().getAnalysisDirectory(), SequenceTaskHelper.SHARED_SUBFOLDER_NAME);
                if (!sharedDirectory.exists())
                {
                    sharedDirectory.mkdirs();
                }

                ReferenceLibraryStep.Output output = step.createReferenceFasta(sharedDirectory);
                File refFasta = output.getReferenceGenome().getSourceFastaFile();
                if (!refFasta.exists())
                {
                    throw new PipelineJobException("Reference file does not exist: " + refFasta.getPath());
                }

                getPipelineJob().getSequenceSupport().cacheGenome(output.getReferenceGenome());

                getHelper().getFileManager().addStepOutputs(action, output);
                getHelper().getFileManager().cleanup(Collections.singleton(action));

                List<PipelineStepCtx<AlignmentStep>> alignmentSteps = SequencePipelineService.get().getSteps(getJob(), AlignmentStep.class);
                if (!alignmentSteps.isEmpty())
                {
                    getJob().getLogger().info("Preparing for alignment");
                    SequenceTaskHelper taskHelper = new SequenceTaskHelper(getPipelineJob(), _wd);
                    for (PipelineStepCtx<AlignmentStep> stepCtx : alignmentSteps)
                    {
                        AlignmentStep aStep = stepCtx.getProvider().create(taskHelper);
                        aStep.init(taskHelper.getSequenceSupport());
                    }
                }

                List<PipelineStepCtx<AnalysisStep>> analysisSteps = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
                if (!analysisSteps.isEmpty())
                {
                    getJob().getLogger().info("Preparing for analysis");
                    SequenceTaskHelper taskHelper = new SequenceTaskHelper(getPipelineJob(), _wd);
                    for (PipelineStepCtx<AnalysisStep> stepCtx : analysisSteps)
                    {
                        AnalysisStep aStep = stepCtx.getProvider().create(taskHelper);
                        aStep.init(taskHelper.getSequenceSupport());
                    }
                }
            }

            return new RecordedActionSet(action);
        }

        return new RecordedActionSet();
    }
}
