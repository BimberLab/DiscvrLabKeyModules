package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.run.FastaIndexer;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 12/15/12
 * Time: 8:34 PM
 *
 * This task is designed to create the reference FASTA, which requires the DB.  this task will run
 * on the webserver
 */
public class PrepareAlignerIndexesTask extends WorkDirectoryTask<PrepareAlignerIndexesTask.Factory>
{
    private static final String ALIGNER_INDEXES_ACTIONNAME = "Preparing Aligner Indexes";

    private SequenceTaskHelper _taskHelper;

    protected PrepareAlignerIndexesTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(PrepareAlignerIndexesTask.class);
            setJoin(true);
        }

        @Override
        public boolean isParticipant(PipelineJob job)
        {
            SequenceTaskHelper taskHelper = new SequenceTaskHelper(job);
            return taskHelper.getSettings().isDoAlignment();
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return "CREATING REFERENCE LIBRARY";
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(ALIGNER_INDEXES_ACTIONNAME);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            PrepareAlignerIndexesTask task = new PrepareAlignerIndexesTask(this, job);

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

    public RecordedActionSet run() throws PipelineJobException
    {
        PipelineJob job = getJob();
        _taskHelper = new SequenceTaskHelper(job);
        getHelper().setWorkDir(_taskHelper.getSupport().getAnalysisDirectory());

        getJob().getLogger().info("Creating Reference Library");
        RecordedAction action = prepareReferenceLibrary();

        return new RecordedActionSet(action);
    }

    private RecordedAction prepareReferenceLibrary() throws PipelineJobException
    {
        RecordedAction action = new RecordedAction(ALIGNER_INDEXES_ACTIONNAME);

        File sharedDirectory = new File(getHelper().getSupport().getAnalysisDirectory(), SequenceTaskHelper.SHARED_SUBFOLDER_NAME);
        File refFasta = new File(sharedDirectory,  getHelper().getSettings().getRefDbFilename());
        if (!refFasta.exists())
        {
            throw new PipelineJobException("Reference fasta does not exist: " + refFasta.getPath());
        }
        getHelper().addInput(action, ReferenceLibraryTask.REFERENCE_DB_FASTA, refFasta);
        getHelper().addOutput(action, ReferenceLibraryTask.REFERENCE_DB_FASTA, refFasta);

        FastaIndexer indexer = new FastaIndexer(getJob().getLogger());
        File refFastaIndex = indexer.execute(refFasta);
        if (!refFastaIndex.exists())
        {
            throw new PipelineJobException("Reference fasta index does not exist: " + refFastaIndex.getPath());
        }
        getHelper().addOutput(action, "Reference DB FASTA Index", refFastaIndex);

        createAlignerIndex(refFasta);

        getHelper().addOutput(action, ReferenceLibraryTask.REFERENCE_DB_FASTA_OUTPUT, sharedDirectory);
        for (File f : sharedDirectory.listFiles())
        {
            if (!f.equals(refFasta) && !f.equals(refFastaIndex))
            {
                getHelper().addDeferredIntermediateFile(f);
            }
        }

        return action;
    }

    private void createAlignerIndex(File refFasta) throws PipelineJobException
    {
        String aligner = getHelper().getSettings().getAligner();

        SequenceAlignmentTask.ALIGNER type = SequenceAlignmentTask.ALIGNER.valueOf(aligner);
        type.createIndex(refFasta, getJob().getLogger());
    }
}
