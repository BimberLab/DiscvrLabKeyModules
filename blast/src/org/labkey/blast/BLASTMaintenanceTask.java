package org.labkey.blast;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.blast.model.BlastJob;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 7/20/2014
 * Time: 3:34 PM
 */
public class BLASTMaintenanceTask implements MaintenanceTask
{
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
    public void run(Logger log)
    {
        //delete DBs linked to a genome that no longer exists
        SQLFragment sqlDB = new SQLFragment("SELECT d.objectid FROM " + BLASTSchema.NAME + "." + BLASTSchema.TABLE_DATABASES + " d LEFT JOIN sequenceanalysis.reference_libraries l on (d.libraryId = l.rowid) WHERE l.rowid IS NULL");
        SqlSelector ss = new SqlSelector(BLASTSchema.getInstance().getSchema().getScope(), sqlDB);
        if (ss.exists())
        {
            List<String> toDelete = ss.getArrayList(String.class);
            try (DbScope.Transaction transaction = BLASTSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                for (String objectId : toDelete)
                {
                    log.info("deleting BLAST DB because genome does not exist: " + objectId);
                    Table.delete(BLASTSchema.getInstance().getSchema().getTable(BLASTSchema.TABLE_DATABASES), objectId);

                    SqlExecutor ex = new SqlExecutor(BLASTSchema.getInstance().getSchema().getScope());
                    int deleted = ex.execute(new SQLFragment("DELETE FROM " + BLASTSchema.NAME + "." + BLASTSchema.TABLE_BLAST_JOBS + " WHERE databaseid = ? ", objectId));
                    if (deleted > 0)
                    {
                        log.info("also deleted " + deleted + " blast jobs associated with this database");
                    }
                }

                transaction.commit();
            }
        }

        //delete BLAST jobs not flagged to persist
        TableInfo blastJobs = BLASTSchema.getInstance().getSchema().getTable(BLASTSchema.TABLE_BLAST_JOBS);
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
                    log.info("deleting old BLAST output: " + output.getName());
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
                    log.info("deleting old BLAST input: " + input.getName());
                    input.delete();
                }
            }
            else
            {
                allowablePaths.add(input.getAbsolutePath());
            }

            File logFile = new File(j.getOutputDir(), "blast-" + j.getObjectid() + ".log");
            if (!j.isSaveResults())
            {
                if (logFile != null && logFile.exists())
                {
                    log.info("deleting old BLAST pipeline log: " + logFile.getName());
                    logFile.delete();
                }
            }
            else
            {
                allowablePaths.add(logFile.getAbsolutePath());
            }

            //now look for orphan files under the file root
            if (!allowablePaths.isEmpty())
            {
                processContainer(ContainerManager.getRoot(), allowablePaths, log);
            }
        }

        SQLFragment sql = new SQLFragment("DELETE FROM blast." + BLASTSchema.TABLE_BLAST_JOBS + " WHERE saveResults = ?", false);
        new SqlExecutor(blastJobs.getSchema()).execute(sql);

        processContainerDB(ContainerManager.getRoot(), log);
    }

    private void processContainerDB(Container c, Logger log)
    {
        //delete blast databases not connected to a known record
        File dbDir = BLASTManager.get().getDatabaseDir(c, false);
        if (dbDir != null && dbDir.exists())
        {
            TableInfo databases = BLASTSchema.getInstance().getSchema().getTable(BLASTSchema.TABLE_DATABASES);
            TableSelector databaseTs = new TableSelector(databases, PageFlowUtil.set("objectid"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
            List<String> dbNames = databaseTs.getArrayList(String.class);
            if (dbDir.list() == null || dbDir.list().length == 0)
            {
                if (!dbNames.isEmpty())
                {
                    log.error("BLAST DBs files not found for container: " + c.getPath());
                }

                return;
            }

            for (File f : dbDir.listFiles())
            {
                if (BLASTWrapper.DB_TYPE.isType(f))
                {
                    if (!dbNames.contains(FileUtil.getBaseName(f)) && !dbNames.contains(f.getName().replaceAll("\\.[0-9]+\\.idx", "")))
                    {
                        log.info("deleting unused BLAST db: " + f.getName());
                        f.delete();
                    }
                }
            }

            for (String dbName : dbNames)
            {
                File[] files = dbDir.listFiles(new FilenameFilter()
                {
                    @Override
                    public boolean accept(File dir, String name)
                    {
                        return name.startsWith(dbName);
                    }
                });

                if (files.length == 0)
                {
                    log.error("BLAST db not found: " + dbName + " in: " + dbDir);
                }
            }
        }

        for (Container child : c.getChildren())
        {
            processContainerDB(child, log);
        }
    }

    private void processContainer(Container c, Set<String> allowablePaths, Logger log)
    {
        File outputDir = BLASTManager.get().getBlastRoot(c, false);
        if (outputDir != null && outputDir.exists())
        {
            for (File f : outputDir.listFiles())
            {
                if (!allowablePaths.contains(f.getAbsolutePath()))
                {
                    log.info("deleting BLAST file: " + f.getPath());
                    if (f.isDirectory())
                    {
                        try
                        {
                            FileUtils.deleteDirectory(f);
                        }
                        catch (IOException e)
                        {
                            log.error("Unable to delete directory: " + f.getPath(), e);
                        }
                    }
                    else
                    {
                        boolean deleted = f.delete();
                        if (!deleted)
                        {
                            log.error("Unable to delete file: " + f.getPath());
                        }
                    }
                }
            }
        }

        List<Container> children = c.getChildren();
        if (!children.isEmpty())
        {
            for (Container child : children)
            {
                processContainer(child, allowablePaths, log);
            }
        }
    }
}
