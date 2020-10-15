package org.labkey.sequenceanalysis.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileUrls;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.GenomeTrigger;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CacheGenomeTrigger implements GenomeTrigger
{
    @Override
    public String getName()
    {
        return null;
    }

    @Override
    public void onCreate(Container c, User u, Logger log, int genomeId)
    {
        possiblyCache(c, u, log, genomeId);
    }

    @Override
    public void onRecreate(Container c, User u, Logger log, int genomeId)
    {
        possiblyCache(c, u, log, genomeId);
    }

    @Override
    public void onDelete(Container c, User u, Logger log, int genomeId)
    {

    }

    private void possiblyCache(Container c, User u, Logger log, int genomeId)
    {
        try
        {
            Map<Integer, File> genomeMap = new HashMap<>();
            ReferenceGenome rg = SequenceAnalysisService.get().getReferenceGenome(genomeId, u);
            genomeMap.put(rg.getGenomeId(), rg.getSourceFastaFile());
            cacheGenomes(c, u, genomeMap, log);
        }
        catch (PipelineJobException e)
        {
            log.error(e);
        }
    }

    @Override
    public boolean isAvailable(Container c)
    {
        return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME));
    }

    public static void cacheGenomes(Container c, User u, Map<Integer, File> genomeMap, Logger log)
    {
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(c);

        File logFileDir = new File(pipeRoot.getRootPath(), CacheGenomePipelineJobProvider.NAME);
        if (!logFileDir.exists())
        {
            logFileDir.mkdirs();
        }

        CacheGenomePipelineJob job = new CacheGenomePipelineJob(c, u, pipeRoot, genomeMap, logFileDir);

        try
        {
            PipelineService.get().queueJob(job);
        }
        catch (PipelineValidationException e)
        {
            log.error(e.getMessage(), e);
        }
    }

    public static class CacheGenomePipelineJob extends PipelineJob
    {
        private Map<Integer, File> _genomeMap;

        //For serialization:
        protected CacheGenomePipelineJob()
        {

        }

        public CacheGenomePipelineJob(Container c, User user, PipeRoot pipeRoot, Map<Integer, File> genomeMap, File outputDir)
        {
            super(CacheGenomePipelineJobProvider.NAME, new ViewBackgroundInfo(c, user, null), pipeRoot);

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
    }

    public static class CacheGenomePipelineJobProvider extends PipelineProvider
    {
        public static final String NAME = "cacheGenomePipeline";

        public CacheGenomePipelineJobProvider(Module owningModule)
        {
            super(NAME, owningModule);
        }

        @Override
        public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
        {

        }
    }

}
