package org.labkey.laboratory;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 1/19/13
 * Time: 9:26 AM
 */
public class AdditionalDataSource extends AbstractDataSource
{
    private LaboratoryService.NavItemCategory _itemType;
    private String _category;
    private static final Logger _log = Logger.getLogger(AdditionalDataSource.class);

    public AdditionalDataSource(LaboratoryService.NavItemCategory itemType, String label, @Nullable String containerId, String schemaName, String queryName, String category)
    {
        super(label, containerId, schemaName, queryName);
        _itemType = itemType;
        _category = category;
    }

    public static AdditionalDataSource getFromParts(Container c, User u, String itemType, String label, @Nullable String containerId, String schemaName, String queryName, String category) throws IllegalArgumentException
    {
        AdditionalDataSource.validateKey(c, u, containerId, schemaName, queryName, label, itemType, category);

        LaboratoryService.NavItemCategory cat = LaboratoryService.NavItemCategory.valueOf(itemType);
        return new AdditionalDataSource(cat, label, containerId, schemaName, queryName, category);
    }

    public static AdditionalDataSource getFromPropertyManager(Container c, User u, String key, String value) throws IllegalArgumentException
    {
        if (value == null)
            return null;

        try
        {
            JSONObject json = new JSONObject(value);
            String schemaName = json.getString("schemaName");
            String queryName = json.getString("queryName");
            String containerId = json.getString("containerId");
            String label = json.getString("label");
            String itemType = json.getString("itemType");
            String category = json.getString("category");

            validateKey(c, u, containerId, schemaName, queryName, label, itemType, category);
            LaboratoryService.NavItemCategory cat = LaboratoryService.NavItemCategory.valueOf(itemType);

            return new AdditionalDataSource(cat, label, containerId, schemaName, queryName, category);
        }
        catch (JSONException e)
        {
            _log.error("Malformed data source saved in " + c.getPath() + ": " + e.getMessage() + ".  was: " + value);
            return null;
        }
    }

    public String getPropertyManagerKey()
    {
        return getForKey(getSchemaName()) + DELIM + getForKey(getQueryName()) + DELIM + getForKey(getContainerId()) + DELIM + getForKey(getLabel()) + DELIM + getForKey(getItemType().name())  + DELIM + getForKey(getCategory());
    }

    public String getPropertyManagerValue()
    {
        JSONObject json = new JSONObject();
        json.put("containerId", getContainerId());
        json.put("schemaName", getSchemaName());
        json.put("queryName", getQueryName());
        json.put("itemType", getItemType());
        json.put("category", getCategory());
        json.put("label", getLabel());

        return json.toString();
    }

    public LaboratoryService.NavItemCategory getItemType()
    {
        return _itemType;
    }

    public String getCategory()
    {
        return _category;
    }

    private static boolean validateKey(Container defaultContainer, User u, @Nullable String containerId, String schemaName, String queryName, String label, String navItemCategory, String category) throws IllegalArgumentException
    {
        Container target;
        if (containerId == null)
            target = defaultContainer;
        else
            target = ContainerManager.getForId(containerId);

        if (target == null)
            target = defaultContainer;

        UserSchema us = QueryService.get().getUserSchema(u, target, schemaName);
        if (target == null)
        {
            throw new IllegalArgumentException("Unknown schema in saved data source: " + schemaName);
        }

        QueryDefinition qd = us.getQueryDefForTable(queryName);
        if (qd == null)
        {
            throw new IllegalArgumentException("Unknown query in saved data source: " + queryName);
        }

        if (StringUtils.trimToNull(label) == null)
            throw new IllegalArgumentException("Label must not be blank");

        if (StringUtils.trimToNull(category) == null)
            throw new IllegalArgumentException("Category must not be blank");

        if (StringUtils.trimToNull(navItemCategory) == null)
            throw new IllegalArgumentException("Item type must not be blank");

        try
        {
            LaboratoryService.NavItemCategory.valueOf(navItemCategory);
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException("Unknown value for item type: " + navItemCategory);
        }

        return true;
    }

    @Override
    public JSONObject toJSON(Container c, User u, boolean includeTotals)
    {
        JSONObject json = super.toJSON(c, u, includeTotals);
        json.put("category", getCategory());
        if (getItemType() != null)
            json.put("itemType", getItemType().name());

        return json;
    }
}
