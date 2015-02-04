package org.labkey.jbrowse;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.GenomeTrigger;
import org.labkey.api.util.PageFlowUtil;

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
    public void onRecreate(Container c, User u, Logger log, int genomeId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("libraryId"), genomeId);
        filter.addCondition(FieldKey.fromString("primarydb"), true);
        TableSelector ts = new TableSelector(DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_DATABASES), filter, null);
        if (!ts.exists())
        {
            createDefaultSession(c, u, log, genomeId);
        }

        //TODO: consider updating existing sessions that are based on this genome?
    }

    private void createDefaultSession(Container c, User u, Logger log, int genomeId)
    {
        try
        {
            TableSelector ts = new TableSelector(DbSchema.get(JBrowseManager.SEQUENCE_ANALYSIS).getTable("reference_libraries"), PageFlowUtil.set("name"));
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
