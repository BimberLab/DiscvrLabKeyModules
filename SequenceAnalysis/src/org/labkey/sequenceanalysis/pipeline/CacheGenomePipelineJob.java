package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileUrls;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenomeManager;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CacheGenomePipelineJob extends PipelineJob
{
    private Map<Integer, File> _genomeMap;

    public static class Provider extends PipelineProvider
    {
        public static final String NAME = "cacheGenomePipeline";

        public Provider(Module owningModule)
        {
            super(NAME, owningModule);
        }

        @Override
        public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
        {

        }
    }

    //For serialization:
    protected CacheGenomePipelineJob()
    {

    }

    public CacheGenomePipelineJob(Container c, User user, PipeRoot pipeRoot, Map<Integer, File> genomeMap, File outputDir)
    {
        super(Provider.NAME, new ViewBackgroundInfo(c, user, null), pipeRoot);

        _genomeMap = genomeMap;

        setLogFile(new File(outputDir, FileUtil.makeFileNameWithTimestamp("cacheGenomes", "log")));

    }

    public Map<Integer, File> getGenomeMap()
    {
        return _genomeMap;
    }

    public void setGenomeMap(Map<Integer, File> genomeMap)
    {
        _genomeMap = genomeMap;
    }

    @Override
    public URLHelper getStatusHref()
    {
        return PageFlowUtil.urlProvider(FileUrls.class).urlBegin(getContainer());
    }

    @Override
    public String getDescription()
    {
        return "Caches reference genomes to a remote filesystem";
    }

    public static class CacheGenomesTask extends PipelineJob.Task<CacheGenomesTask.Factory>
    {
        protected CacheGenomesTask(Factory factory, PipelineJob job)
        {
            super(factory, job);
        }

        public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
        {
            public Factory()
            {
                super(CacheGenomesTask.class);
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
                return Arrays.asList("JBrowse");
            }

            public PipelineJob.Task createTask(PipelineJob job)
            {
                return new CacheGenomesTask(this, job);
            }

            public boolean isJobComplete(PipelineJob job)
            {
                return false;
            }
        }

        @Override
        public @NotNull RecordedActionSet run() throws PipelineJobException
        {
            CacheGenomePipelineJob job = (CacheGenomePipelineJob)getJob();

            File cacheDir = SequencePipelineService.get().getRemoteGenomeCacheDirectory();
            if (cacheDir == null)
            {
                return new RecordedActionSet();
            }

            if (!cacheDir.exists())
            {
                cacheDir.mkdirs();
            }

            for (Integer genomeId : job.getGenomeMap().keySet())
            {
                File fasta = job.getGenomeMap().get(genomeId);
                ReferenceGenome rg = new ReferenceGenomeImpl(fasta, null, genomeId, null);

                ReferenceGenomeManager.get().cacheGenomeLocally(rg, getJob().getLogger());
            }

            return new RecordedActionSet();
        }
    }
}