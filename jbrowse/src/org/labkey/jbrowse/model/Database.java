package org.labkey.jbrowse.model;

import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.jbrowse.JBrowseSchema;

import java.util.List;

/**
 * User: bimber
 * Date: 7/14/2014
 * Time: 2:54 PM
 */
public class Database
{
    private int _rowId;
    private String _name;
    private String _description;
    private String _jobid;
    private Integer _libraryId;
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

    public List<DatabaseMember> getMembers()
    {
        if (_members == null)
        {

        }

        return _members;
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
                TableInfo tableJsonFiles = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_JSONFILES);
                return new TableSelector(tableJsonFiles, new SimpleFilter(FieldKey.fromString("objectid"), _jsonFile, CompareType.EQUAL), null).getObject(JsonFile.class);
            }

            return null;
        }
    }
}
