package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.run.alignment.AlignerIndexUtil;
import org.labkey.sequenceanalysis.run.util.FastaIndexer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 9/20/2016.
 */
public class CacheAlignerIndexesTask extends WorkDirectoryTask<CacheAlignerIndexesTask.Factory>
{
    private static final String ACTION_NAME = "Prepare Aligner Indexes";

    protected CacheAlignerIndexesTask(CacheAlignerIndexesTask.Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, CacheAlignerIndexesTask.Factory>
    {
        public Factory()
        {
            super(CacheAlignerIndexesTask.class);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return PipelineJob.TaskStatus.running.toString();
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(ACTION_NAME);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new CacheAlignerIndexesTask(this, job);
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    @NotNull
    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        ReferenceGenome referenceGenome = getPipelineJob().getReferenceGenome();
        if (referenceGenome == null)
        {
            throw new PipelineJobException("No reference genome was cached prior to preparing aligned indexes");
        }

        File refFasta = referenceGenome.getSourceFastaFile();
        if (!refFasta.exists())
        {
            throw new PipelineJobException("Reference fasta does not exist: " + refFasta.getPath());
        }

        File refFastaIdx = new File(referenceGenome.getSourceFastaFile().getPath() + ".fai");
        if (!refFastaIdx.exists())
        {
            new FastaIndexer(getJob().getLogger()).execute(refFasta);
        }

        SequencePipelineService.get().ensureSequenceDictionaryExists(refFasta, getJob().getLogger(), false);

        JobContextImpl ctx = new JobContextImpl(getJob(), null, new JSONObject(), _wd.getDir(), null, _wd);

        //pre-cache aligner indexes
        for (PipelineStepProvider<AlignmentStep> provider : SequencePipelineService.get().getProviders(AlignmentStep.class))
        {
            if (provider instanceof AbstractAlignmentStepProvider)
            {
                if (((AbstractAlignmentStepProvider)provider).isAlwaysCacheIndex())
                {
                    getJob().getLogger().info("preparing index for: " + provider.getName());
                    AlignmentStep alignmentStep = provider.create(ctx);

                    boolean hasIndex = AlignerIndexUtil.hasCachedIndex(alignmentStep.getPipelineCtx(), alignmentStep.getIndexCachedDirName(), referenceGenome);
                    if (hasIndex)
                    {
                        getJob().getLogger().info("cached aligner index exists, skipping step");
                        continue;
                    }

                    //create locally first
                    alignmentStep.createIndex(referenceGenome, _wd.getDir());
                    File outDir = new File(_wd.getDir(), alignmentStep.getIndexCachedDirName());

                    AlignerIndexUtil.saveCachedIndex(false, ctx, outDir, provider.getName(), referenceGenome);

                    try
                    {
                        FileUtils.deleteDirectory(outDir);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
            }
        }

        return new RecordedActionSet();
    }

    private ReferenceLibraryPipelineJob getPipelineJob()
    {
        return (ReferenceLibraryPipelineJob)getJob();
    }
}
