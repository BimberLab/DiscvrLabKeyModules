package org.labkey.sla.model;

public class Investigator
{
    private Integer _rowid;
    private String _firstname;
    private String _lastname;

    public Integer getRowid()
    {
        return _rowid;
    }

    public void setRowid(Integer rowid)
    {
        _rowid = rowid;
    }

    public String getFirstname()
    {
        return _firstname;
    }

    public void setFirstname(String firstname)
    {
        _firstname = firstname;
    }

    public String getLastname()
    {
        return _lastname;
    }

    public void setLastname(String lastname)
    {
        _lastname = lastname;
    }
}
