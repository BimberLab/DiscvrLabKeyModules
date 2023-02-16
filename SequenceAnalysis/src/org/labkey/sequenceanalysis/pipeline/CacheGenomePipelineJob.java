package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.lang3.StringUtils;
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
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
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
import java.util.Set;
import java.util.stream.Collectors;

public class CacheGenomePipelineJob extends PipelineJob
{
    private Map<Integer, File> _genomeMap;
    private boolean _deleteOtherFolders = false;

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

    public CacheGenomePipelineJob(Container c, User user, PipeRoot pipeRoot, Map<Integer, File> genomeMap, File outputDir, boolean deleteOtherFolders)
    {
        super(Provider.NAME, new ViewBackgroundInfo(c, user, null), pipeRoot);

        _genomeMap = genomeMap;
        _deleteOtherFolders = deleteOtherFolders;

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

    public boolean isDeleteOtherFolders()
    {
        return _deleteOtherFolders;
    }

    public void setDeleteOtherFolders(boolean deleteOtherFolders)
    {
        _deleteOtherFolders = deleteOtherFolders;
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

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(CacheGenomePipelineJob.class));
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

            @Override
            public List<FileType> getInputTypes()
            {
                return Collections.emptyList();
            }

            @Override
            public String getStatusName()
            {
                return PipelineJob.TaskStatus.running.toString();
            }

            @Override
            public List<String> getProtocolActionNames()
            {
                return List.of("JBrowse");
            }

            @Override
            public PipelineJob.Task createTask(PipelineJob job)
            {
                return new CacheGenomesTask(this, job);
            }

            @Override
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
                throw new PipelineJobException("This job should not have been initiated unless REMOTE_GENOME_CACHE_DIR is set");
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

            if (job.isDeleteOtherFolders())
            {
                Set<String> whitelist = job.getGenomeMap().keySet().stream().map(String::valueOf).collect(Collectors.toSet());
                File[] toDelete = cacheDir.listFiles((file) -> {
                    return !whitelist.contains(file.getName());
                });

                if (toDelete != null && toDelete.length > 0)
                {
                    getJob().getLogger().info("Folders will be deleted: " + StringUtils.join(toDelete, ", "));
                    //TODO: verify
                }
            }

            return new RecordedActionSet();
        }
    }
}