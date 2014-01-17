package org.labkey.laboratory.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.MemTracker;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 6/27/13
 * Time: 6:20 PM
 */
public class FreezerTriggerHelper
{
    private Container _container;
    private User _user;
    private TableInfo _table;

    private Map<String, Map<String, Integer>> _cachedRows = new HashMap<String, Map<String, Integer>>();

    private FreezerTriggerHelper(String containerId, int userId)
    {
        _container = ContainerManager.getForId(containerId);
        if (_container == null)
            throw new IllegalArgumentException("Unknown container: " + containerId);

        _container = _container.isWorkbook() ? _container.getParent() : _container;

        _user = UserManager.getUser(userId);
        if (_user == null)
            throw new IllegalArgumentException("Unknown user: " + userId);

        UserSchema us = QueryService.get().getUserSchema(_user, _container, "laboratory");
        if (us == null)
            throw new IllegalArgumentException("Unable to find schema laboratory");

        _table = us.getTable("samples");
        if (_table == null)
            throw new IllegalArgumentException("Unable to find table laboratory.samples");

        MemTracker.getInstance().put(this);
    }

    public static FreezerTriggerHelper create(String containerId, int userId)
    {
        return new FreezerTriggerHelper(containerId, userId);
    }

    public boolean isSamplePresent(String location, String freezer, String cane, String box, String box_row, String box_column, Integer rowId)
    {
        String key = getKey(location, freezer, cane, box, box_row, box_column);
        Map<String, Integer> cached = getFreezerRows(freezer);

        if (cached.containsKey(key))
        {
            if (rowId != null && rowId.equals(cached.get(key)))
            {
                //this indicates the matching sample is the same as the incoming sample, such a row update
                return false;
            }

            //this means we have a newly inserted sample matching an existing one
            return true;
        }
        else
        {
            cacheValue(freezer, key, rowId);
            return false;
        }
    }

    public String getKey(String location, String freezer, String cane, String box, String box_row, String box_column)
    {
        List<String> tokens = new ArrayList<String>();

        if (!StringUtils.isEmpty(location))
            tokens.add("location: " + location);
        if (!StringUtils.isEmpty(freezer))
            tokens.add("freezer: " + freezer);
        if (!StringUtils.isEmpty(cane))
            tokens.add("cane: " + cane);
        if (!StringUtils.isEmpty(box))
            tokens.add("box: " + box);
        if (!StringUtils.isEmpty(box_row))
            tokens.add("box_row: " + box_row);
        if (!StringUtils.isEmpty(box_column))
            tokens.add("box_column: " + box_column);

        return StringUtils.join(tokens, "||");
    }

    @NotNull
    private Map<String, Integer> getFreezerRows(String freezer)
    {
        if (_cachedRows.containsKey(freezer))
                return _cachedRows.get(freezer);

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("freezer"), freezer, CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("dateremoved"), null, CompareType.ISBLANK);

        TableSelector ts = new TableSelector(_table, filter, null);

        final Map<String, Integer> keys = new HashMap<String, Integer>();
        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                keys.put(getKey(rs.getString("location"), rs.getString("freezer"), rs.getString("cane"), rs.getString("box"), rs.getString("box_row"), rs.getString("box_column")), rs.getInt("rowid"));
            }
        });

        _cachedRows.put(freezer, keys);

        return keys;
    }

    private void cacheValue(String freezer, String key, Integer rowId)
    {
        _cachedRows.get(freezer).put(key, rowId);
    }
}
