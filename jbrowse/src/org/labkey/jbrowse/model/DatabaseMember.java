package org.labkey.jbrowse.model;

import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.jbrowse.JBrowseSchema;

public class DatabaseMember
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
