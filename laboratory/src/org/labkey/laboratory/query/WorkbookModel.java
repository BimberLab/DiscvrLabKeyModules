package org.labkey.laboratory.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

/**
 * User: bimber
 * Date: 3/30/13
 * Time: 11:58 AM
 */
public class WorkbookModel
{
    private Integer _workbookId;
    private String _containerId;
    private String _materials;
    private String _methods;
    private String _results;
    private String _exptGroup;
    private String[] _tags;

    public WorkbookModel()
    {

    }

    public Integer getWorkbookId()
    {
        return _workbookId;
    }

    public void setWorkbookId(Integer workbookId)
    {
        _workbookId = workbookId;
    }

    public String getContainer()
    {
        return _containerId;
    }

    public void setContainer(String container)
    {
        _containerId = container;
    }

    public String getMaterials()
    {
        return _materials;
    }

    public void setMaterials(String materials)
    {
        _materials = materials;
    }

    public String getMethods()
    {
        return _methods;
    }

    public void setMethods(String methods)
    {
        _methods = methods;
    }

    public String getResults()
    {
        return _results;
    }

    public void setResults(String results)
    {
        _results = results;
    }

    public String getExptGroup()
    {
        return _exptGroup;
    }

    public void setExptGroup(String exptGroup)
    {
        _exptGroup = exptGroup;
    }

    private Container _getContainer()
    {
        if (_containerId == null)
            throw new IllegalArgumentException("The containerId has not been set");

        Container c = ContainerManager.getForId(_containerId);
        if (c == null)
            throw new IllegalArgumentException("Unknown container: " + _containerId);

        return c;
    }

    public void setDescription(String description, User u)
    {
        try
        {
            ContainerManager.updateDescription(_getContainer(), description, u);
        }
        catch (ValidationException e)
        {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public String getDescription()
    {
        return _getContainer().getDescription();
    }

    public String[] getTags()
    {
        return _tags;
    }

    public void setTags(String[] tags)
    {
        _tags = tags;
    }
}
