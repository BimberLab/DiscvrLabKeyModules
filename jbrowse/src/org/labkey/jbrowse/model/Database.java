package org.labkey.jbrowse.model;

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
    }
}
