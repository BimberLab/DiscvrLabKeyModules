package org.labkey.jbrowse.model;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileContentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.JobRunner;
import org.labkey.jbrowse.JBrowseSchema;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * User: bimber
 * Date: 7/14/2014
 * Time: 2:54 PM
 */
public class Database
{
    private static final Logger _log = LogManager.getLogger(Database.class);

    private int _rowId;
    private String _name;
    private String _description;
    private String _jobid;
    private Integer _libraryId;
    private String _jsonConfig;
    private String _objectId;
    private String _container;
    private List<DatabaseMember> _members = null;

    public Database()
    {

    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public Container getContainerObj()
    {
        return _container == null ? null : ContainerManager.getForId(_container);
    }

    public String getObjectId()
    {
        return _objectId;
    }

    public void setObjectId(String objectId)
    {
        _objectId = objectId;
    }

    public String getJobid()
    {
        return _jobid;
    }

    public void setJobid(String jobid)
    {
        _jobid = jobid;
    }

    public Integer getLibraryId()
    {
        return _libraryId;
    }

    public void setLibraryId(Integer libraryId)
    {
        _libraryId = libraryId;
    }

    public String getJsonConfig()
    {
        return _jsonConfig;
    }

    public void setJsonConfig(String jsonConfig)
    {
        _jsonConfig = jsonConfig;
    }

    public List<DatabaseMember> getMembers()
    {
        if (_members == null)
        {

        }

        return _members;
    }

    public static void onDatabaseDelete(String containerId, final String databaseId, boolean asyncDelete) throws IOException
    {
        Container c = ContainerManager.getForId(containerId);
        if (c == null)
        {
            return;
        }

        //delete children
        TableInfo ti = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASE_MEMBERS);
        int deleted = Table.delete(ti, new SimpleFilter(FieldKey.fromString("database"), databaseId, CompareType.EQUAL));

        //then delete files
        FileContentService fileService = FileContentService.get();
        File fileRoot = fileService == null ? null : fileService.getFileRoot(c, FileContentService.ContentType.files);
        if (fileRoot == null || !fileRoot.exists())
        {
            return;
        }

        File jbrowseDir = new File(fileRoot, ".jbrowse");
        if (!jbrowseDir.exists())
        {
            return;
        }

        File databaseDir = new File(jbrowseDir, "databases");
        if (!databaseDir.exists())
        {
            return;
        }

        final File databaseDir2 = new File(databaseDir, databaseId);
        if (databaseDir2.exists())
        {
            _log.info("deleting jbrowse database dir: " + databaseDir2.getPath());
            if (!asyncDelete)
            {
                FileUtils.deleteDirectory(databaseDir2);
            }
            else
            {
                JobRunner.getDefault().execute(new Runnable(){
                    public void run()
                    {
                        try
                        {
                            _log.info("deleting jbrowse database dir async: " + databaseDir2.getPath());
                            FileUtils.deleteDirectory(databaseDir2);
                        }
                        catch (IOException e)
                        {
                            _log.error("Error background deleting JBrowse database dir for: " + databaseId, e);
                        }
                    }
                });
            }
        }
    }

    public static class DatabaseMember
    {
        private int _rowId;
        private String _database;
        private String _jsonFile;
        private String _category;
        private String _container;

        public DatabaseMember()
        {

        }

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public String getDatabase()
        {
            return _database;
        }

        public void setDatabase(String database)
        {
            _database = database;
        }

        public String getJsonFile()
        {
            return _jsonFile;
        }

        public void setJsonFile(String jsonFile)
        {
            _jsonFile = jsonFile;
        }

        public String getContainer()
        {
            return _container;
        }

        public void setContainer(String container)
        {
            _container = container;
        }

        public String getCategory()
        {
            return _category;
        }

        public void setCategory(String category)
        {
            _category = category;
        }

        public JsonFile getJson()
        {
            if (_jsonFile != null)
            {
                TableInfo tableJsonFiles = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
                return new TableSelector(tableJsonFiles, new SimpleFilter(FieldKey.fromString("objectid"), _jsonFile, CompareType.EQUAL), null).getObject(JsonFile.class);
            }

            return null;
        }
    }
}
