package org.labkey.jbrowse;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.jbrowse.model.JBrowseSession;
import org.labkey.jbrowse.model.JsonFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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
                JBrowseSession db = JBrowseSession.getForId(objectId);
                Table.delete(JBrowseSchema.getInstance().getSchema().getTable(JBrowseSchema.TABLE_DATABASES), objectId);

                JBrowseSession.onDatabaseDelete(db.getContainer(), db.getObjectId());
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
                    //TODO: ultimately completely remove these
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
                    //TODO: ultimately completely remove these
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

    private void processContainer(Container c, Logger log) throws IOException, PipelineJobException
    {
        File jbrowseRoot = JBrowseManager.get().getBaseDir(c, false);

        //find jsonfiles we expect to exist
        TableInfo tableJsonFiles = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
        final Set<File> expectedDirs = new HashSet<>();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("container"), c.getId());
        //TODO: eventually delete these legacy rows
        filter.addCondition(FieldKey.fromString("sequenceid"), null, CompareType.ISBLANK);

        TableSelector ts = new TableSelector(tableJsonFiles, filter, null);
        List<JsonFile> rows = ts.getArrayList(JsonFile.class);
        if (jbrowseRoot != null && jbrowseRoot.exists())
        {
            log.info("processing container: " + c.getPath());
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
            for (String dir : Arrays.asList("tracks", "data", "references", "databases"))
            {
                File childDir = new File(jbrowseRoot, dir);
                if (childDir.exists())
                {
                    //TODO: eventually delete these?
                    log.info("deleting legacy jbrowse " + dir + " dir: " + childDir.getPath());
                    //FileUtils.deleteDirectory(childDir);
                }
            }

            File resourceDir = new File(jbrowseRoot, "resources");
            if (resourceDir.exists())
            {
                for (File childDir : resourceDir.listFiles())
                {
                    if (!expectedDirs.contains(childDir))
                    {
                        log.info("deleting resource dir: " + childDir.getPath());
                        FileUtils.deleteDirectory(childDir);
                    }
                }
            }
        }

        for (JsonFile j : rows)
        {
            if (j.needsProcessing())
            {
                File expectedFile = j.getLocationOfProcessedTrack(false);
                boolean error = false;
                if (!j.isGzipped() && !expectedFile.exists())
                {
                    log.error("Missing expected file: " + expectedFile.getPath());
                    error = true;
                }

                if (!j.doExpectedSearchIndexesExist())
                {
                    log.error("Missing search indexes: " + expectedFile.getPath());
                    error = true;
                }

                if (error)
                {
                    j.prepareResource(log, false, true);
                }
            }
        }

        for (Container child : c.getChildren())
        {
            processContainer(child, log);
        }
    }
}
