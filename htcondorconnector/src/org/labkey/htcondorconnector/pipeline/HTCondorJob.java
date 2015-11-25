package org.labkey.htcondorconnector.pipeline;

import java.util.Date;

/**
 * Created by bimber on 10/31/2015.
 */
public class HTCondorJob
{
    private int _rowId;
    private String _condorId;
    private String _jobId;
    private Boolean _isActive;
    private Boolean _hadError;
    private String _clusterId;
    private String _processId;
    private String _nodeId;
    private Date _lastStatusCheck;
    private String _container;
    private Date _created;
    private Integer _createdBy;
    private Date _modified;
    private Integer _modifiedBy;

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getCondorId()
    {
        return _condorId;
    }

    public void setCondorId(String condorId)
    {
        _condorId = condorId;
    }

    public String getJobId()
    {
        return _jobId;
    }

    public void setJobId(String jobId)
    {
        _jobId = jobId;
    }

    public Boolean getIsActive()
    {
        return _isActive;
    }

    public void setIsActive(Boolean isActive)
    {
        _isActive = isActive;
    }

    public Boolean getHadError()
    {
        return _hadError;
    }

    public void setHadError(Boolean hadError)
    {
        _hadError = hadError;
    }

    public String getClusterId()
    {
        return _clusterId;
    }

    public void setClusterId(String clusterId)
    {
        _clusterId = clusterId;
    }

    public String getProcessId()
    {
        return _processId;
    }

    public void setProcessId(String processId)
    {
        _processId = processId;
    }

    public String getNodeId()
    {
        return _nodeId;
    }

    public void setNodeId(String nodeId)
    {
        _nodeId = nodeId;
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
}
