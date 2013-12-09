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
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.DataSetTable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 1/11/13
 * Time: 5:42 PM
 */
public class DemographicsSource extends AbstractDataSource
{
    private String _targetColumn;
    private static final Logger _log = Logger.getLogger(DemographicsSource.class);
    
    public DemographicsSource(String label, String containerId, String schemaName, String queryName, String targetColumn)
    {
        super(label, containerId, schemaName, queryName);
        _targetColumn = targetColumn;
    }

    public static DemographicsSource getFromParts(Container c, User u, String label, String containerId, String schemaName, String queryName, String targetColumn) throws IllegalArgumentException
    {
        DemographicsSource.validateKey(c, u, containerId, schemaName, queryName, targetColumn, label);
        return new DemographicsSource(label, containerId, schemaName, queryName, targetColumn);
    }

    public static DemographicsSource getFromPropertyManager(Container c, User u, String key, String value) throws IllegalArgumentException
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
            String targetColumn = json.getString("targetColumn");

            validateKey(c, u, containerId, schemaName, queryName, targetColumn, label);

            return new DemographicsSource(label, containerId, schemaName, queryName, targetColumn);
        }
        catch (JSONException e)
        {
            _log.error("Malformed demographics source saved in " + c.getPath() + ": " + e.getMessage() + ".  value was: " + value);
            return null;
        }
    }

    public String getTargetColumn()
    {
        return _targetColumn;
    }

    @Override
    public String getPropertyManagerKey()
    {
        return getForKey(getSchemaName()) + DELIM + getForKey(getQueryName()) + DELIM + getForKey(getContainerId()) + DELIM + getForKey(getTargetColumn()) + DELIM + getForKey(getLabel());
    }

    public String getPropertyManagerValue()
    {
        JSONObject json = new JSONObject();
        json.put("containerId", getContainerId());
        json.put("schemaName", getSchemaName());
        json.put("queryName", getQueryName());
        json.put("targetColumn", getTargetColumn());
        json.put("label", getLabel());

        return json.toString();
    }

    @Override
    public JSONObject toJSON(Container c, User u, boolean includeTotals)
    {
        JSONObject json = super.toJSON(c, u, includeTotals);
        json.put("targetColumn", getTargetColumn());
        
        return json;
    }

    public static boolean validateKey(Container defaultContainer, User u, @Nullable String containerId, String schemaName, String queryName, String targetColumn, String label) throws IllegalArgumentException
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

        if (targetColumn == null)
        {
            throw new IllegalArgumentException("Missing targetColumn");
        }

        List<QueryException> errors = new ArrayList<QueryException>();
        TableInfo ti = qd.getTable(errors,  true);
        if (errors.size() != 0 || ti == null)
        {
            _log.error("Unable to create TableInfo for query: " + queryName + ". there were " + errors.size() + " errors");
            for (QueryException e : errors)
            {
                _log.error(e.getMessage());
            }
            if (errors.size() > 0)
                throw new IllegalArgumentException("Unable to create table for query: " + queryName, errors.get(0));
            else
                throw new IllegalArgumentException("Unable to create table for query: " + queryName);
        }

        ColumnInfo col = ti.getColumn(targetColumn);
        if (col == null)
        {
            throw new IllegalArgumentException("Unable to find column: " + targetColumn);
        }
        targetColumn = col.getName(); //normalize string

        //NOTE: this is not an ideal solution.  for now, special-case demographics datasets.
        //It is problematic to flag the participant column as a keyfield, since other places in the code expect a single PK column
        if (!ti.getPkColumnNames().contains(targetColumn))
        {
            if (ti instanceof DataSetTable)
            {
                DataSet ds = ((DataSetTable)ti).getDataSet();
                if (!(ds.isDemographicData() && ds.getStudy().getSubjectColumnName().equalsIgnoreCase(col.getName())))
                {
                    throw new IllegalArgumentException("Target column is not a key field: " + targetColumn);
                }
            }
            else
            {
                throw new IllegalArgumentException("Target column is not a key field: " + targetColumn);
            }
        }

        if (!col.getJdbcType().equals(JdbcType.VARCHAR))
        {
            throw new IllegalArgumentException("The selected target column is not a string: " + targetColumn);
        }

        if (StringUtils.trimToNull(label) == null)
            throw new IllegalArgumentException("Label must not be blank");

        return true;
    }
}
