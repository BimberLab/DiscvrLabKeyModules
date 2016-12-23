package org.labkey.sla.model;

import org.labkey.api.util.GUID;

public class Vendor
{
    private Integer _rowid;
    private GUID _objectid;
    private String _name;

    public Integer getRowid()
    {
        return _rowid;
    }

    public void setRowid(Integer rowid)
    {
        _rowid = rowid;
    }

    public GUID getObjectid()
    {
        return _objectid;
    }

    public void setObjectid(GUID objectid)
    {
        _objectid = objectid;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }
}
