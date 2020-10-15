package org.labkey.sequenceanalysis.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.GenomeTrigger;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
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

        File logFileDir = new File(pipeRoot.getRootPath(), CacheGenomePipelineJob.Provider.NAME);
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
}
