package org.labkey.jbrowse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.jbrowse.model.JBrowseSession;
import org.labkey.jbrowse.model.JsonFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            // TODO: ultimately remove this. This is related to the migration from JB1 to JB2
            TableInfo jsonFiles = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
            List<String> toDelete = new TableSelector(jsonFiles, PageFlowUtil.set("objectid"), new SimpleFilter(FieldKey.fromString("sequenceid"), null, CompareType.NONBLANK), null).getArrayList(String.class);
            if (!toDelete.isEmpty())
            {
                log.info("deleted " + toDelete.size() + " legacy NT JsonFile records");
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
                    //or library tracks
                    " (SELECT count(rowid) FROM sequenceanalysis.reference_library_tracks d WHERE d.rowid = " + JBrowseSchema.TABLE_JSONFILES + ".trackid) = 0 AND " +
                    //or outputfiles
                    " (SELECT count(rowid) FROM sequenceanalysis.outputfiles d WHERE d.rowid = " + JBrowseSchema.TABLE_JSONFILES + ".outputfile) = 0 "
            ));

            if (deleted2 > 0)
                log.info("deleted " + deleted2 + " JSON files because they are not used by any sessions");

            //second pass at orphan JSONFiles.  Note: these might be referenced by a database_member record still.
            toDelete = new SqlSelector(JBrowseSchema.getInstance().getSchema(), new SQLFragment("SELECT objectid FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_JSONFILES +
                    " WHERE " +
                    //library tracks
                    " ( " + JBrowseSchema.TABLE_JSONFILES + ".trackid IS NOT NULL AND (SELECT count(rowid) FROM sequenceanalysis.reference_library_tracks d WHERE d.rowid = " + JBrowseSchema.TABLE_JSONFILES + ".trackid) = 0) OR " +
                    //outputfiles
                    " (" + JBrowseSchema.TABLE_JSONFILES + ".outputfile IS NOT NULL AND (SELECT count(rowid) FROM sequenceanalysis.outputfiles d WHERE d.rowid = " + JBrowseSchema.TABLE_JSONFILES + ".outputfile) = 0)"
            )).getArrayList(String.class);

            if (!toDelete.isEmpty())
            {
                log.info("deleting " + toDelete.size() + " JSON files because they reference non-existent tracks, sequences or outputfiles");
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
        log.info("processing container: " + c.getPath());

        File jbrowseRoot = JBrowseManager.get().getBaseDir(c, false);

        //find jsonfiles we expect to exist
        TableInfo tableJsonFiles = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
        final Set<File> expectedDirs = new HashSet<>();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("container"), c.getId());
        filter.addCondition(FieldKey.fromString("sequenceid"), null, CompareType.ISBLANK);

        TableSelector ts = new TableSelector(tableJsonFiles, filter, null);
        Map<String, JsonFile> rowMap = ts.getArrayList(JsonFile.class).stream().collect(Collectors.toMap(JsonFile::getObjectId, Function.identity()));

        // Also check for genomes from this container, and any additional JsonFiles they may have:
        TableInfo tableGenomes = DbSchema.get(JBrowseManager.SEQUENCE_ANALYSIS, DbSchemaType.Module).getTable("reference_libraries");
        TableSelector ts2 = new TableSelector(tableGenomes, PageFlowUtil.set("rowid"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
        if (ts2.exists())
        {
            User u = LDKService.get().getBackgroundAdminUser();
            if (u == null)
            {
                log.warn("In order to ensure genomes are prepared for JBrowse, the LDK module property BackgroundAdminUser must be set");
            }
            else
            {
                List<Integer> genomes = ts2.getArrayList(Integer.class);
                log.info("total genomes in folder: " + genomes.size());
                for (Integer genomeId : genomes)
                {
                    JBrowseSession session = JBrowseSession.getGenericGenomeSession(genomeId);
                    for (JsonFile json : session.getJsonFiles(u, true))
                    {
                        rowMap.put(json.getObjectId(), json);
                        if (json.getBaseDir() != null)
                        {
                            expectedDirs.add(json.getBaseDir());
                            if (!json.getBaseDir().exists())
                            {
                                log.error("expected jbrowse folder does not exist: " + json.getBaseDir().getPath());
                            }
                        }
                    }
                }
            }
        }
        
        if (jbrowseRoot != null && jbrowseRoot.exists())
        {
            for (JsonFile json : rowMap.values())
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

            log.info("expected resource folders: " + expectedDirs.size());
            for (String dir : Arrays.asList("tracks", "data", "references", "databases"))
            {
                File childDir = new File(jbrowseRoot, dir);
                if (childDir.exists())
                {
                    log.info("deleting legacy jbrowse " + dir + " dir: " + childDir.getPath());
                    if (SystemUtils.IS_OS_WINDOWS)
                    {
                        FileUtils.deleteDirectory(childDir);
                    }
                    else
                    {
                        try
                        {
                            new SimpleScriptWrapper(log).execute(Arrays.asList("rm", "-Rf", childDir.getPath()));
                        }
                        catch (PipelineJobException e)
                        {
                            log.error("Unable to delete directory: " + childDir.getPath(), e);
                        }
                    }
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

        log.info("total JsonFiles in folder: " + rowMap.size());
        for (JsonFile j : rowMap.values())
        {
            if (j.needsProcessing())
            {
                File expectedFile = j.getLocationOfProcessedTrack(false);
                boolean error = false;
                if (j.shouldBeCopiedToProcessDir() && expectedFile != null && !expectedFile.exists())
                {
                    log.error("Missing expected file: " + expectedFile.getPath());
                    error = true;
                }

                if (!j.doExpectedSearchIndexesExist())
                {
                    log.error("Missing trix search indexes: " + expectedFile.getPath());
                    error = true;
                }

                if (j.shouldHaveFreeTextSearch() && !j.getExpectedLocationOfLuceneIndex(false).exists())
                {
                    log.error("Missing lucene search indexes: " + expectedFile.getPath());
                    error = true;
                }

                if (error)
                {
                    try
                    {
                        j.prepareResource(log, false, true);
                    }
                    catch (Exception e)
                    {
                        log.error("Unable to process JsonFile: " + j.getObjectId(), e);
                    }
                }
            }
        }

        for (Container child : c.getChildren())
        {
            processContainer(child, log);
        }
    }
}
