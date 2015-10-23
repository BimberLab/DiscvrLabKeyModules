package org.labkey.jbrowse;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.DefaultSystemMaintenanceTask;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.jbrowse.model.JsonFile;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 7/20/2014
 * Time: 3:34 PM
 */
public class JBrowseMaintenanceTask extends DefaultSystemMaintenanceTask
{
    private static Logger _log = Logger.getLogger(JBrowseMaintenanceTask.class);

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
    public void run()
    {
        //delete JSON in output dir not associated with DB record
        try
        {
            //delete sessions marked as temporary
            Table.delete(JBrowseSchema.getInstance().getSchema().getTable(JBrowseSchema.TABLE_DATABASES), new SimpleFilter(FieldKey.fromString("temporary"), true));

            //then orphan DB members
            int deleted = new SqlExecutor(JBrowseSchema.getInstance().getSchema()).execute(new SQLFragment("DELETE FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_DATABASE_MEMBERS + " WHERE (SELECT count(objectid) FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_DATABASES + " d WHERE d.objectid = " + JBrowseSchema.TABLE_DATABASE_MEMBERS + "." + JBrowseSchema.getInstance().getSqlDialect().makeLegalIdentifier("database") + ") = 0"));
            if (deleted > 0)
                _log.info("deleted " + deleted + " orphan database members");

            //finally orphan JSONFiles
            int deleted2 = new SqlExecutor(JBrowseSchema.getInstance().getSchema()).execute(new SQLFragment("DELETE FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_JSONFILES +
                    " WHERE " +
                    //find JSONFiles not associated with a database_member record
                    " (SELECT count(rowid) FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_DATABASE_MEMBERS + " d WHERE d.jsonfile = " + JBrowseSchema.TABLE_JSONFILES + ".objectid) = 0 AND " +
                    //or library reference sequences
                    " (SELECT count(rowid) FROM sequenceanalysis.reference_library_members d WHERE d.ref_nt_id = " + JBrowseSchema.TABLE_JSONFILES + ".sequenceid) = 0 AND " +
                    //or library tracks
                    " (SELECT count(rowid) FROM sequenceanalysis.reference_library_tracks d WHERE d.rowid = " + JBrowseSchema.TABLE_JSONFILES + ".trackid) = 0 "
            ));

            if (deleted2 > 0)
                _log.info("deleted " + deleted2 + " JSON files because they are not used by any sessions");

            //now iterate every container and find orphan files
            processContainer(ContainerManager.getRoot());
        }
        catch (Exception e)
        {
            _log.error(e.getMessage(), e);
        }
    }

    private void processContainer(Container c) throws IOException
    {
        JBrowseRoot root = new JBrowseRoot(_log);
        if (root != null)
        {
            File jbrowseRoot = root.getBaseDir(c, false);
            if (jbrowseRoot != null && jbrowseRoot.exists())
            {
                //find jsonfiles we expect to exist
                TableInfo tableJsonFiles = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_JSONFILES);
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
                            _log.error("expected jbrowse folder does not exist: " + json.getBaseDir().getPath());
                        }
                    }
                }

                File trackDir = new File(jbrowseRoot, "tracks");
                if (trackDir.exists())
                {
                    for (File childDir : trackDir.listFiles())
                    {
                        if (!expectedDirs.contains(childDir))
                        {
                            _log.info("deleting track dir: " + childDir.getPath());
                            FileUtils.deleteDirectory(childDir);
                        }
                    }
                }

                File dataDir = new File(jbrowseRoot, "data");
                if (dataDir.exists())
                {
                    _log.info("deleting legacy jbrowse data dir: " + dataDir.getPath());
                    FileUtils.deleteDirectory(dataDir);
                }

                File referenceDir = new File(jbrowseRoot, "references");
                if (referenceDir.exists())
                {
                    for (File childDir : referenceDir.listFiles())
                    {
                        if (!expectedDirs.contains(childDir))
                        {
                            _log.info("deleting reference sequence dir: " + childDir.getPath());
                            FileUtils.deleteDirectory(childDir);
                        }
                    }
                }

                //also databases
                TableInfo tableJsonDatabases = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_DATABASES);
                TableSelector ts2 = new TableSelector(tableJsonDatabases, Collections.singleton("objectid"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
                List<String> expectedDatabases = ts2.getArrayList(String.class);
                File databaseDir = new File(jbrowseRoot, "databases");
                if (databaseDir.exists())
                {
                    for (File childDir : databaseDir.listFiles())
                    {
                        if (!expectedDatabases.contains(childDir.getName()))
                        {
                            _log.info("deleting jbrowse database dir: " + childDir.getPath());
                            FileUtils.deleteDirectory(childDir);
                        }
                    }
                }
                else if (!expectedDatabases.isEmpty() && !databaseDir.exists())
                {
                    _log.error("missing expected database directory: " + databaseDir.getPath());
                }
                else if (!expectedDatabases.isEmpty() && databaseDir.list().length == 0)
                {
                    _log.error("database directory is empty: " + databaseDir.getPath());
                }
            }
        }

        for (Container child : c.getChildren())
        {
            processContainer(child);
        }
    }
}
