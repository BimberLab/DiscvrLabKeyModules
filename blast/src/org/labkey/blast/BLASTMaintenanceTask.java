package org.labkey.blast;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.DefaultSystemMaintenanceTask;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.blast.model.BlastJob;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 7/20/2014
 * Time: 3:34 PM
 */
public class BLASTMaintenanceTask extends DefaultSystemMaintenanceTask
{
    private static Logger _log = Logger.getLogger(BLASTMaintenanceTask.class);

    public BLASTMaintenanceTask()
    {

    }

    @Override
    public String getDescription()
    {
        return "Delete BLAST Artifacts";
    }

    @Override
    public String getName()
    {
        return "DeleteBlastArtifacts";
    }

    @Override
    public void run()
    {
        //delete BLAST jobs not flagged to persist
        TableInfo blastJobs = DbSchema.get(BLASTSchema.NAME).getTable(BLASTSchema.TABLE_BLAST_JOBS);
        TableSelector ts = new TableSelector(blastJobs);
        List<BlastJob> jobs = ts.getArrayList(BlastJob.class);
        Set<String> allowablePaths = new CaseInsensitiveHashSet();
        for (BlastJob j : jobs)
        {
            File output = j.getExpectedOutputFile();
            if (!j.isSaveResults())
            {
                if (output != null && output.exists())
                {
                    _log.info("deleting old BLAST output: " + output.getName());
                    output.delete();
                }
            }
            else
            {
                allowablePaths.add(output.getAbsolutePath());
            }

            File input = j.getExpectedInputFile();
            if (!j.isSaveResults())
            {
                if (input != null && input.exists())
                {
                    _log.info("deleting old BLAST input: " + input.getName());
                    input.delete();
                }
            }
            else
            {
                allowablePaths.add(input.getAbsolutePath());
            }

            File log = new File(j.getOutputDir(), "blast-" + j.getObjectid() + ".log");
            if (!j.isSaveResults())
            {
                if (log != null && log.exists())
                {
                    _log.info("deleting old BLAST pipeline log: " + log.getName());
                    log.delete();
                }
            }
            else
            {
                allowablePaths.add(log.getAbsolutePath());
            }

            //now look for orphan files under the file root
            if (!allowablePaths.isEmpty())
            {
                processContainer(ContainerManager.getRoot(), allowablePaths);
            }
        }

        SQLFragment sql = new SQLFragment("DELETE FROM blast." + BLASTSchema.TABLE_BLAST_JOBS + " WHERE saveResults = ?", false);
        new SqlExecutor(blastJobs.getSchema()).execute(sql);

        //delete blast databases not connected to a known record
        File dbDir = BLASTManager.get().getDatabaseDir();
        if (dbDir != null && dbDir.exists())
        {
            TableInfo databases = DbSchema.get(BLASTSchema.NAME).getTable(BLASTSchema.TABLE_DATABASES);
            TableSelector databaseTs = new TableSelector(databases, PageFlowUtil.set("objectid"));
            List<String> dbNames = databaseTs.getArrayList(String.class);
            for (File f : dbDir.listFiles())
            {
                if (BLASTWrapper.DB_TYPE.isType(f))
                {
                    if (!dbNames.contains(FileUtil.getBaseName(f)) && !dbNames.contains(f.getName().replaceAll("\\.[0-9]+\\.idx", "")))
                    {
                        _log.info("deleting unused BLAST db: " + f.getName());
                        f.delete();
                    }
                }
            }

            for (String dbName : dbNames)
            {
                File expected = new File(dbDir, dbName + ".nhr");
                if (!expected.exists())
                {
                    _log.error("BLAST db does not exist: " + expected.getPath());
                }
            }
        }
    }

    private void processContainer(Container c, Set<String> allowablePaths)
    {
        File outputDir = BLASTManager.get().getBlastRoot(c, false);
        if (outputDir != null && outputDir.exists())
        {
            for (File f : outputDir.listFiles())
            {
                if (!allowablePaths.contains(f.getAbsolutePath()))
                {
                    _log.info("deleting BLAST file: " + f.getPath());
                }
            }
        }

        List<Container> children = c.getChildren();
        if (!children.isEmpty())
        {
            for (Container child : children)
            {
                processContainer(child, allowablePaths);
            }
        }
    }
}
