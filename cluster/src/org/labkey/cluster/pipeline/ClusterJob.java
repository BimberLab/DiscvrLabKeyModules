package org.labkey.cluster.pipeline;

import java.util.Date;

/**
 * Created by bimber on 10/31/2015.
 */
public class ClusterJob
{
    private int _rowId;
    private String _jobId;
    private int _statusFileId;
    private String _status;
    private Boolean _hasStarted;
    private String _clusterId;
    private Date _lastStatusCheck;
    private String _container;
    private Date _created;
    private Integer _createdBy;
    private Date _modified;
    private Integer _modifiedBy;
    private String _location;
    private String _activeTaskId;
    private String _clusterUser;

    private String _hostname;
    private Date _logModified;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getJobId()
    {
        return _jobId;
    }

    public void setJobId(String jobId)
    {
        _jobId = jobId;
    }

    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        _status = status;
    }

    public Boolean getHasStarted()
    {
        return _hasStarted;
    }

    public void setHasStarted(Boolean hasStarted)
    {
        _hasStarted = hasStarted;
    }

    public String getClusterId()
    {
        return _clusterId;
    }

    public void setClusterId(String clusterId)
    {
        _clusterId = clusterId;
    }

    public Date getLastStatusCheck()
    {
        return _lastStatusCheck;
    }

    public void setLastStatusCheck(Date lastStatusCheck)
    {
        _lastStatusCheck = lastStatusCheck;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public Integer getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(Integer createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public Integer getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(Integer modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public String getLocation()
    {
        return _location;
    }

    public void setLocation(String location)
    {
        _location = location;
    }

    public String getActiveTaskId()
    {
        return _activeTaskId;
    }

    public void setActiveTaskId(String activeTaskId)
    {
        _activeTaskId = activeTaskId;
    }

    public String getClusterUser()
    {
        return _clusterUser;
    }

    public void setClusterUser(String clusterUser)
    {
        _clusterUser = clusterUser;
    }

    public int getStatusFileId()
    {
        return _statusFileId;
    }

    public void setStatusFileId(int statusFileId)
    {
        _statusFileId = statusFileId;
    }

    public String getHostname()
    {
        return _hostname;
    }

    public void setHostname(String hostname)
    {
        _hostname = hostname;
    }

    public Date getLogModified()
    {
        return _logModified;
    }

    public void setLogModified(Date logModified)
    {
        _logModified = logModified;
    }
}
