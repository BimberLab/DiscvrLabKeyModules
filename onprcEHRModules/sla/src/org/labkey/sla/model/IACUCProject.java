package org.labkey.sla.model;

public class IACUCProject
{
    private Integer _project;
    private String _protocol;
    private String _account;
    private String _name;
    private String _title;
    private Integer _investigatorid;

    public Integer getProject()
    {
        return _project;
    }

    public void setProject(Integer project)
    {
        _project = project;
    }

    public String getProtocol()
    {
        return _protocol;
    }

    public void setProtocol(String protocol)
    {
        _protocol = protocol;
    }

    public String getAccount()
    {
        return _account;
    }

    public void setAccount(String account)
    {
        _account = account;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getTitle()
    {
        return _title;
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public Integer getInvestigatorid()
    {
        return _investigatorid;
    }

    public void setInvestigatorid(Integer investigatorid)
    {
        _investigatorid = investigatorid;
    }
}
