package org.labkey.sla.model;

import org.labkey.api.util.GUID;

public class PurchaseDraftForm
{
    private Integer _rowid;
    private Integer _owner;
    private String _content;
    private GUID _containerid;
    private boolean _toBeDeleted;

    public Integer getRowid()
    {
        return _rowid;
    }

    public void setRowid(Integer rowid)
    {
        _rowid = rowid;
    }

    public Integer getOwner()
    {
        return _owner;
    }

    public void setOwner(Integer owner)
    {
        _owner = owner;
    }

    public String getContent()
    {
        return _content;
    }

    public void setContent(String content)
    {
        _content = content;
    }

    public GUID getContainerid()
    {
        return _containerid;
    }

    public void setContainerid(GUID containerid)
    {
        _containerid = containerid;
    }

    public boolean isToBeDeleted()
    {
        return _toBeDeleted;
    }

    public void setToBeDeleted(boolean toBeDeleted)
    {
        _toBeDeleted = toBeDeleted;
    }
}
