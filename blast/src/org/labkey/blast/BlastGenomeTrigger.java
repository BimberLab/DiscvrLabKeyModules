package org.labkey.blast;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.GenomeTrigger;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.blast.pipeline.BlastDatabasePipelineJob;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created by bimber on 8/25/2014.
 */
public class BlastGenomeTrigger implements GenomeTrigger
{
    public BlastGenomeTrigger()
    {

    }

    @Override
    public String getName()
    {
        return "BLAST";
    }

    @Override
    public void onCreate(Container c, User u, Logger log, int genomeId)
    {
        try
        {
            BLASTManager.get().createDatabase(c, u, genomeId);
        }
        catch (IOException e)
        {
            log.error(e);
        }
    }

    @Override
    public void onRecreate(Container c, User u, Logger log, int genomeId)
    {
        try
        {
            //find if there's an existing BLAST DB
            List<String> existingDbs = new TableSelector(BLASTSchema.getInstance().getSchema().getTable(BLASTSchema.TABLE_DATABASES), PageFlowUtil.set("objectid"), new SimpleFilter(FieldKey.fromString("libraryId"), genomeId), null).getArrayList(String.class);
            if (!existingDbs.isEmpty())
            {
                PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
                for (String databaseGuid : existingDbs)
                {
                    log.info("recreating existing BLAST database: " + databaseGuid);
                    PipelineService.get().queueJob(BlastDatabasePipelineJob.recreate(c, u, null, root, databaseGuid));
                }
            }
            else
            {
                log.info("creating BLAST database");
                BLASTManager.get().createDatabase(c, u, genomeId);
            }
        }
        catch (IOException | PipelineValidationException | PipelineJobException e)
        {
            log.error(e);
        }
    }

    @Override
    public void onDelete(Container c, User u, Logger log, int genomeId)
    {
        List<String> existingDbs = new TableSelector(BLASTSchema.getInstance().getSchema().getTable(BLASTSchema.TABLE_DATABASES), PageFlowUtil.set("objectid"), new SimpleFilter(FieldKey.fromString("libraryId"), genomeId), null).getArrayList(String.class);
        if (!existingDbs.isEmpty())
        {
            for (String databaseGuid : existingDbs)
            {
                String containerId = new TableSelector(BLASTSchema.getInstance().getSchema().getTable(BLASTSchema.TABLE_DATABASES), PageFlowUtil.set("container"), new SimpleFilter(FieldKey.fromString("objectid"), databaseGuid), null).getObject(String.class);
                if (containerId == null)
                {
                    continue;
                }

                Container dbContainer = ContainerManager.getForId(containerId);
                if (dbContainer == null)
                {
                    continue;
                }

                File rootDir = BLASTManager.get().getBlastRoot(dbContainer, false);
                if (rootDir != null)
                {
                    log.info("deleting files for BLAST DB: " + databaseGuid);
                    for (File f : rootDir.listFiles())
                    {
                        if (f.getName().startsWith(databaseGuid))
                        {
                            log.info("deleting file: " + f.getName());
                            f.delete();
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean isAvailable(Container c)
    {
        return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(BLASTModule.class));
    }
}