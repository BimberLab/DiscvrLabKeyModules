package org.labkey.jbrowse;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * User: bimber
 * Date: 7/20/2014
 * Time: 3:34 PM
 */
public class JBrowseMaintenanceTask implements SystemMaintenance.MaintenanceTask
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
    public boolean canDisable()
    {
        return true;
    }

    @Override
    public boolean hideFromAdminPage() { return false; }

    @Override
    public void run()
    {
        //delete JSON in output dir not associated with DB record
        File dbDir = JBrowseManager.get().getJBrowseRoot();
        if (dbDir != null && dbDir.exists())
        {
            JBrowseRoot root = JBrowseRoot.getRoot();

            try
            {
                //delete sessions marked as temporary
                Table.delete(JBrowseSchema.getInstance().getSchema().getTable(JBrowseSchema.TABLE_DATABASES), new SimpleFilter(FieldKey.fromString("temporary"), true));

                //then orphan members
                int deleted = new SqlExecutor(JBrowseSchema.getInstance().getSchema()).execute(new SQLFragment("DELETE FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_DATABASE_MEMBERS + " WHERE (SELECT count(objectid) FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_DATABASES + " d WHERE d.objectid = " + JBrowseSchema.TABLE_DATABASE_MEMBERS + ".database) = 0"));
                if (deleted > 0)
                    _log.info("deleted " + deleted + " orphan database members");

                //finally orphan JSONFiles
                int deleted2 = new SqlExecutor(JBrowseSchema.getInstance().getSchema()).execute(new SQLFragment("DELETE FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_JSONFILES + " WHERE (SELECT count(rowid) FROM " + JBrowseSchema.NAME + "." + JBrowseSchema.TABLE_DATABASE_MEMBERS + " d WHERE d.jsonfile = " + JBrowseSchema.TABLE_JSONFILES + ".objectid) = 0"));
                if (deleted2 > 0)
                    _log.info("deleted " + deleted2 + " JSON files because they are not used by any sessions");

                TableInfo jsonFiles = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_JSONFILES);
                List<String> sequenceFolders = new TableSelector(jsonFiles, PageFlowUtil.set("sequenceid"), new SimpleFilter(FieldKey.fromString("sequenceId"), null, CompareType.NONBLANK), null).getArrayList(String.class);
                List<String> trackFolders = new TableSelector(jsonFiles, PageFlowUtil.set("trackId"), new SimpleFilter(FieldKey.fromString("trackId"), null, CompareType.NONBLANK), null).getArrayList(String.class);

                //reference
                for (File f : root.getReferenceDir().listFiles())
                {
                    if (!sequenceFolders.contains(f.getName()))
                    {
                        _log.info("deleting unused JBrowse reference directory: " + f.getName());
                        if (f.isDirectory())
                        {
                            FileUtils.deleteDirectory(f);
                        }
                        else
                        {
                            f.delete();
                        }
                    }
                }

                //tracks
                for (File f : root.getTracksDir().listFiles())
                {
                    if (!trackFolders.contains(f.getName()))
                    {
                        _log.info("deleting unused JBrowse track directory: " + f.getName());
                        if (f.isDirectory())
                        {
                            FileUtils.deleteDirectory(f);
                        }
                        else
                        {
                            f.delete();
                        }
                    }
                }

                //databases
                TableInfo databases = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_DATABASES);
                List<String> dbFolders = new TableSelector(databases, PageFlowUtil.set("objectid")).getArrayList(String.class);
                for (File f : root.getDatabaseDir().listFiles())
                {
                    if (!dbFolders.contains(f.getName()))
                    {
                        _log.info("deleting unused JBrowse database directory: " + f.getName());
                        if (f.isDirectory())
                        {
                            FileUtils.deleteDirectory(f);
                        }
                        else
                        {
                            f.delete();
                        }
                    }
                }
            }
            catch (IOException e)
            {
                _log.error(e.getMessage(), e);
            }
        }
    }
}
