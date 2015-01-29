package org.labkey.sequenceanalysis.pipeline;

import htsjdk.samtools.BAMIndexer;
import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.ValidationStringency;
import org.apache.commons.io.FileUtils;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.util.FileType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 8/5/2014.
 */
public class AlignmentNormalizationTask extends WorkDirectoryTask<AlignmentNormalizationTask.Factory>
{
    private static final String ACTION_NAME = "Normalizing Alignments";
    private SequenceTaskHelper _taskHelper;

    protected AlignmentNormalizationTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(AlignmentNormalizationTask.class);
        }

        public String getStatusName()
        {
            return ACTION_NAME;
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(ACTION_NAME);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            AlignmentNormalizationTask task = new AlignmentNormalizationTask(this, job);
            return task;
        }

        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(new FileType(".bam"));
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            _taskHelper = new SequenceTaskHelper(getJob(), _wd);
            RecordedAction action = new RecordedAction(ACTION_NAME);

            //move and make sure BAMs are indexed
            FileType bamFile = new FileType("bam");
            for (File originalFile : getTaskHelper().getSupport().getInputFiles())
            {
                getJob().getLogger().info("processing file: " + originalFile.getPath());
                if (!bamFile.isType(originalFile))
                {
                    getJob().getLogger().error("File is not a BAM file, skipping: " + originalFile.getName());
                    continue;
                }

                File movedFile = new File(getTaskHelper().getSupport().getAnalysisDirectory(), originalFile.getName());

                //delete original, if required
                String handling = getTaskHelper().getFileManager().getInputfileTreatment();
                action.addInput(movedFile, "BAM Index");
                if ("delete".equals(handling))
                {
                    getJob().getLogger().info("moving BAM to: " + movedFile.getPath());
                    FileUtils.moveFile(originalFile, movedFile);
                }
                else
                {
                    getJob().getLogger().info("copying BAM to: " + movedFile.getPath());
                    FileUtils.copyFile(originalFile, movedFile);
                }

                File indexFile = new File(originalFile.getPath() + ".bai");
                File movedIndexFile = new File(movedFile.getPath() + ".bai");
                if (indexFile.exists())
                {
                    action.addInput(movedFile, "BAM Index");
                    if ("delete".equals(handling))
                    {
                        getJob().getLogger().info("BAM index exists, moving");
                        FileUtils.moveFile(indexFile, movedIndexFile);
                    }
                    else
                    {
                        getJob().getLogger().info("BAM index exists, copying");
                        FileUtils.copyFile(indexFile, movedIndexFile);
                    }
                }
                else
                {
                    getJob().getLogger().info("creating BAM index");
                    //TODO: SamReaderFactory fact = SamReaderFactory.make();
                    try (SAMFileReader reader = new SAMFileReader(movedFile))
                    {
                        reader.setValidationStringency(ValidationStringency.SILENT);

                        getJob().getLogger().info("\tcreating BAM index");
                        BAMIndexer.createIndex(reader, movedIndexFile);
                    }
                }

                action.addOutput(movedFile, SequenceAlignmentTask.FINAL_BAM_ROLE, false);
                action.addOutput(movedIndexFile, SequenceAlignmentTask.FINAL_BAM_INDEX_ROLE, false);
            }

            return new RecordedActionSet(Arrays.asList(action));
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public SequenceTaskHelper getTaskHelper()
    {
        return _taskHelper;
    }
}
