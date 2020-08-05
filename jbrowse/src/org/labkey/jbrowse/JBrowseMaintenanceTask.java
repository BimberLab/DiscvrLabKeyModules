package org.labkey.jbrowse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.jbrowse.model.Database;
import org.labkey.jbrowse.model.JsonFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 7/20/2014
 * Time: 3:34 PM
 */
public class JBrowseMaintenanceTask implements MaintenanceTask
{
    public JBrowseMaintenanceTask()
    {

    }

    @Override
    public String getDescription()
    {
        return "Delete JBrowse Artifacts";
    }

    @Override
    public String getName()
    {
        return "DeleteJBrowseArtifacts";
    }

    @Override
    public void run(Logger log)
    {
        //find sessions linked to a non-existent genome
        SQLFragment sqlDB = new SQLFragment("SELECT d.objectid FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_DATABASES + " d LEFT JOIN sequenceanalysis.reference_libraries l on (d.libraryId = l.rowid) WHERE l.rowid IS NULL");
        SqlSelector ss = new SqlSelector(JBrowseSchema.getInstance().getSchema().getScope(), sqlDB);
        if (ss.exists())
        {
            List<String> toDelete = ss.getArrayList(String.class);
            for (String objectId : toDelete)
            {
                log.info("deleting JBrowse session because genome does not exist: " + objectId);
                Database db = new TableSelector(JBrowseSchema.getInstance().getSchema().getTable(JBrowseSchema.TABLE_DATABASES)).getObject(objectId, Database.class);
                Table.delete(JBrowseSchema.getInstance().getSchema().getTable(JBrowseSchema.TABLE_DATABASES), objectId);

                try
                {
                    Database.onDatabaseDelete(db.getContainer(), db.getObjectId(), false);
                }
                catch (IOException e)
                {
                    log.error("error deleting JBrowse session: " + e.getMessage(), e);
                }
            }
        }

        //delete JSON in output dir not associated with DB record
        try
        {
            //delete sessions marked as temporary
            int sessionsDeleted = Table.delete(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES), new SimpleFilter(FieldKey.fromString("temporary"), true));
            if (sessionsDeleted > 0)
                log.info("deleted " + sessionsDeleted + " temporary jbrowse sessions");

            //then orphan DB members
            int deleted = new SqlExecutor(JBrowseSchema.getInstance().getSchema()).execute(new SQLFragment("DELETE FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_DATABASE_MEMBERS + " WHERE (SELECT count(objectid) FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_DATABASES + " d WHERE d.objectid = " + JBrowseSchema.TABLE_DATABASE_MEMBERS + "." + JBrowseSchema.getInstance().getSqlDialect().makeLegalIdentifier("database") + ") = 0"));
            if (deleted > 0)
                log.info("deleted " + deleted + " orphan database members");

            //finally orphan JSONFiles
            int deleted2 = new SqlExecutor(JBrowseSchema.getInstance().getSchema()).execute(new SQLFragment("DELETE FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_JSONFILES +
                    " WHERE " +
                    //find JSONFiles not associated with a database_member record
                    " (SELECT count(rowid) FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_DATABASE_MEMBERS + " d WHERE d.jsonfile = " + JBrowseSchema.TABLE_JSONFILES + ".objectid) = 0 AND " +
                    //or library reference sequences
                    " (SELECT count(rowid) FROM sequenceanalysis.reference_library_members d WHERE d.ref_nt_id = " + JBrowseSchema.TABLE_JSONFILES + ".sequenceid) = 0 AND " +
                    //or library tracks
                    " (SELECT count(rowid) FROM sequenceanalysis.reference_library_tracks d WHERE d.rowid = " + JBrowseSchema.TABLE_JSONFILES + ".trackid) = 0 AND " +
                    //or outputfiles
                    " (SELECT count(rowid) FROM sequenceanalysis.outputfiles d WHERE d.rowid = " + JBrowseSchema.TABLE_JSONFILES + ".outputfile) = 0 "
            ));

            if (deleted2 > 0)
                log.info("deleted " + deleted2 + " JSON files because they are not used by any sessions");

            //second pass at orphan JSONFiles.  Note: these might be referenced by a database_member record still.
            List<String> toDelete = new SqlSelector(JBrowseSchema.getInstance().getSchema(), new SQLFragment("SELECT objectid FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_JSONFILES +
                    " WHERE " +
                    //reference NT sequences
                    " (" + JBrowseSchema.TABLE_JSONFILES + ".sequenceid IS NOT NULL AND (SELECT count(rowid) FROM sequenceanalysis.ref_nt_sequences d WHERE d.rowid = " + JBrowseSchema.TABLE_JSONFILES + ".sequenceid) = 0) OR " +
                    //library tracks
                    " ( " + JBrowseSchema.TABLE_JSONFILES + ".trackid IS NOT NULL AND (SELECT count(rowid) FROM sequenceanalysis.reference_library_tracks d WHERE d.rowid = " + JBrowseSchema.TABLE_JSONFILES + ".trackid) = 0) OR " +
                    //outputfiles
                    " (" + JBrowseSchema.TABLE_JSONFILES + ".outputfile IS NOT NULL AND (SELECT count(rowid) FROM sequenceanalysis.outputfiles d WHERE d.rowid = " + JBrowseSchema.TABLE_JSONFILES + ".outputfile) = 0)"
            )).getArrayList(String.class);

            if (!toDelete.isEmpty())
            {
                log.info("deleting " + toDelete.size() + " JSON files because they reference non-existent tracks, sequences or outputfiles");
                TableInfo jsonFiles = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
                for (String key : toDelete)
                {
                    Table.delete(jsonFiles, key);

                    int d = new SqlExecutor(JBrowseSchema.getInstance().getSchema()).execute(new SQLFragment("DELETE FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_DATABASE_MEMBERS + " WHERE jsonfile = ?", key));
                    if (d > 0)
                    {
                        log.info("also deleted " + d + " orphan database member record(s) for: " + key);
                    }
                }
            }

            //now iterate every container and find orphan files
            processContainer(ContainerManager.getRoot(), log);
        }
        catch (Exception e)
        {
            log.error(e.getMessage(), e);
        }
    }

    private void processContainer(Container c, Logger log) throws IOException
    {
        JBrowseRoot root = new JBrowseRoot(log);
        if (root != null)
        {
            File jbrowseRoot = JBrowseRoot.getBaseDir(c, false);
            if (jbrowseRoot != null && jbrowseRoot.exists())
            {
                log.info("processing container: " + c.getPath());

                //find jsonfiles we expect to exist
                TableInfo tableJsonFiles = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
                final Set<File> expectedDirs = new HashSet<>();
                TableSelector ts = new TableSelector(tableJsonFiles, new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
                List<JsonFile> rows = ts.getArrayList(JsonFile.class);
                for (JsonFile json : rows)
                {
                    if (json.getBaseDir() != null)
                    {
                        expectedDirs.add(json.getBaseDir());
                        if (!json.getBaseDir().exists())
                        {
                            log.error("expected jbrowse folder does not exist: " + json.getBaseDir().getPath());
                        }
                    }
                }
                log.info("expected jsonfiles: " + expectedDirs.size());

                File trackDir = new File(jbrowseRoot, "tracks");
                if (trackDir.exists())
                {
                    for (File childDir : trackDir.listFiles())
                    {
                        if (!expectedDirs.contains(childDir))
                        {
                            log.info("deleting track dir: " + childDir.getPath());
                            FileUtils.deleteDirectory(childDir);

                            //TODO: determine databases that might use this and delete symlinks
                        }
                    }
                }

                File dataDir = new File(jbrowseRoot, "data");
                if (dataDir.exists())
                {
                    log.info("deleting legacy jbrowse data dir: " + dataDir.getPath());
                    FileUtils.deleteDirectory(dataDir);
                }

                File referenceDir = new File(jbrowseRoot, "references");
                if (referenceDir.exists())
                {
                    for (File childDir : referenceDir.listFiles())
                    {
                        if (!expectedDirs.contains(childDir))
                        {
                            log.info("deleting reference sequence dir: " + childDir.getPath());
                            FileUtils.deleteDirectory(childDir);
                        }
                    }
                }

                //also databases
                Set<String> toRecreate = new HashSet<>();
                TableInfo tableJsonDatabases = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES);
                TableSelector ts2 = new TableSelector(tableJsonDatabases, Collections.singleton("objectid"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
                List<String> expectedDatabases = ts2.getArrayList(String.class);
                File databaseDir = new File(jbrowseRoot, "databases");
                if (databaseDir.exists())
                {
                    for (File childDir : databaseDir.listFiles())
                    {
                        if (!expectedDatabases.contains(childDir.getName()))
                        {
                            log.info("deleting jbrowse database dir: " + childDir.getPath());
                            FileUtils.deleteDirectory(childDir);
                            continue;
                        }

                        for (String dirName : Arrays.asList("tracks"))
                        {
                            File subdir = new File(childDir, dirName);
                            if (subdir.exists())
                            {
                                for (File child : subdir.listFiles())
                                {
                                    if (detectBrokenSymlink(log, child))
                                    {
                                        toRecreate.add(childDir.getName());
                                    }
                                }
                            }
                        }
                    }
                }

                for (String objectId : expectedDatabases)
                {
                    File dir = new File(databaseDir, objectId);
                    if (!dir.exists())
                    {
                        log.error("missing expected DB directory: " + dir.getPath());
                        toRecreate.add(objectId);
                    }
                }

                if (!toRecreate.isEmpty())
                {
                    log.info("re-creating " + toRecreate.size() + " jbrowse sessions");
                    log.info(StringUtils.join(toRecreate, ";"));
                    for (String objectId : toRecreate)
                    {
                        recreateSession(c, objectId, log);
                    }
                }
            }
        }

        for (Container child : c.getChildren())
        {
            processContainer(child, log);
        }
    }

    private boolean detectBrokenSymlink(Logger log, File dir)
    {
        if (Files.isSymbolicLink(dir.toPath()) && !dir.exists())
        {
            log.error("possible broken symlink: " + dir.getPath());
            return true;
        }

        return false;
    }

    private void recreateSession(final Container c, final String databaseId, final Logger log)
    {
        final User adminUser = LDKService.get().getBackgroundAdminUser();
        if (adminUser == null)
        {
            log.error("LDK module BackgroundAdminUser property not set.  If this is set, JBrowseMaintenanceTask could automatically submit repair jobs.");
            return;
        }

        JobRunner jr = JobRunner.getDefault();
        jr.execute(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    JBrowseService.get().reprocessDatabase(c, adminUser, databaseId);
                }
                catch (PipelineValidationException e)
                {
                    log.error(e.getMessage(), e);
                }
            }
        });
    }
}
