package org.labkey.jbrowse;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.GenomeTrigger;

/**
 * Created by bimber on 8/25/2014.
 */
public class JBrowseGenomeTrigger implements GenomeTrigger
{
    public JBrowseGenomeTrigger()
    {

    }

    @Override
    public String getName()
    {
        return "JBrowse";
    }

    @Override
    public void onCreate(Container c, User u, Logger log, int genomeId)
    {
        ensureGenomePrepared(c, u, log, genomeId);
    }

    @Override
    public void onTrackAdd(Container c, User u, Logger log, int genomeId, int trackId)
    {
        log.info("processing new track for genome: " + genomeId);
        this.onRecreate(c, u, log, genomeId);
    }

    @Override
    public void onRecreate(Container c, User u, Logger log, int genomeId)
    {
        log.info("Ensuring genome resources are prepared");
        ensureGenomePrepared(c, u, log, genomeId);
    }

    private void ensureGenomePrepared(Container c, User u, Logger log, int genomeId)
    {
        try
        {
            JBrowseManager.get().ensureGenomePrepared(c, u, genomeId, log);
        }
        catch (Exception e)
        {
            log.error("Error creating JBrowse session", e);
        }
    }

    @Override
    public void onDelete(Container c, User u, Logger log, int genomeId)
    {
        //TODO
    }

    @Override
    public boolean isAvailable(Container c)
    {
        return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(JBrowseModule.class));
    }
}
