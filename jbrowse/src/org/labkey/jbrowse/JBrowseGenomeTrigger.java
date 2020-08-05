package org.labkey.jbrowse;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.GenomeTrigger;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.jbrowse.model.Database;
import org.labkey.jbrowse.pipeline.JBrowseSessionPipelineJob;

import java.io.IOException;

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
        createDefaultSession(c, u, log, genomeId);
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
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("libraryId"), genomeId);
        filter.addCondition(FieldKey.fromString("primarydb"), true);
        TableSelector ts = new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES), filter, null);
        if (!ts.exists())
        {
            log.info("creating default jbrowse session for genome");
            createDefaultSession(c, u, log, genomeId);
        }
        else
        {
            try
            {
                log.info("attempting to recreate jbrowse session");
                Database db = ts.getObject(Database.class);
                PipeRoot root = PipelineService.get().getPipelineRootSetting(db.getContainerObj());
                PipelineService.get().queueJob(JBrowseSessionPipelineJob.recreateDatabase(db.getContainerObj(), u, root, db.getObjectId()));
            }
            catch (PipelineValidationException e)
            {
                log.error("problem recreating jbrowse session", e);
            }
        }

        //TODO: consider updating existing sessions that are based on this genome?
    }

    private void createDefaultSession(Container c, User u, Logger log, int genomeId)
    {
        try
        {
            TableSelector ts = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("reference_libraries"), PageFlowUtil.set("name"));
            String genomeName = ts.getObject(genomeId, String.class);

            JBrowseManager.get().createDatabase(c, u, genomeName, "This is the default database automatically created for this genome.", genomeId, null, null, true, true, false);
        }
        catch (IOException e)
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
