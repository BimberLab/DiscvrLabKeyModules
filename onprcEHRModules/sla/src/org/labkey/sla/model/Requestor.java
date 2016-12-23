package org.labkey.sla.model;

import org.labkey.api.util.GUID;

public class Requestor
{
    private Integer _rowid;
    private GUID _objectid;
    private String _lastname;
    private String _firstname;
    private String _phone;
    private String _email;
    private Integer _userid;

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

    public String getLastname()
    {
        return _lastname;
    }

    public void setLastname(String lastname)
    {
        _lastname = lastname;
    }

    public String getFirstname()
    {
        return _firstname;
    }

    public void setFirstname(String firstname)
    {
        _firstname = firstname;
    }

    public String getPhone()
    {
        return _phone;
    }

    public void setPhone(String phone)
    {
        _phone = phone;
    }

    public String getEmail()
    {
        return _email;
    }

    public void setEmail(String email)
    {
        _email = email;
    }

    public Integer getUserid()
    {
        return _userid;
    }

    public void setUserid(Integer userid)
    {
        _userid = userid;
    }
}
