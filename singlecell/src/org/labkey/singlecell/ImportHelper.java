package org.labkey.singlecell;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;

import java.util.HashMap;
import java.util.Map;

public class ImportHelper
{
    private Container _container;
    private User _user;
    private TableInfo _table;
    private TableInfo _sortTable;

    private static final Logger _log = LogManager.getLogger(ImportHelper.class);

    private Map<String, UserSchema> _userSchemaMap = new HashMap<>();

    private ImportHelper(String containerId, int userId, String queryName)
    {
        String schemaName = SingleCellSchema.NAME;

        _container = ContainerManager.getForId(containerId);
        if (_container == null)
            throw new IllegalArgumentException("Unknown container: " + containerId);

        _container = _container.isWorkbook() ? _container.getParent() : _container;

        _user = UserManager.getUser(userId);
        if (_user == null)
            throw new IllegalArgumentException("Unknown user: " + userId);

        UserSchema us = getUserSchema(schemaName);
        if (us == null)
            throw new IllegalArgumentException("Unknown schema: " + schemaName);

        _table = us.getTable(queryName);
        if (_table == null)
            throw new IllegalArgumentException("Unknown table: " + schemaName + "." + queryName);

        _sortTable = us.getTable("sorts");
        if (_sortTable == null)
            throw new IllegalArgumentException("Unknown table: " + schemaName + "." + queryName);

        MemTracker.getInstance().put(this);
    }

    public static ImportHelper create(String containerId, int userId, String queryName)
    {
        return new ImportHelper(containerId, userId, queryName);
    }

    private UserSchema getUserSchema(String name)
    {
        if (_userSchemaMap.containsKey(name))
            return _userSchemaMap.get(name);

        UserSchema us = QueryService.get().getUserSchema(_user, _container, name);
        _userSchemaMap.put(name, us);

        return us;
    }

    public Map<String, Integer> getInitialWells()
    {
        TableSelector ts = new TableSelector(_table, PageFlowUtil.set("plateId", "well", "rowid"));
        final Map<String, Integer> ret = new HashMap<>();
        ts.forEachResults(rs -> {
            String key = (rs.getString(FieldKey.fromString("plateId")) + "<>" + rs.getString(FieldKey.fromString("well"))).toUpperCase();
            ret.put(key, rs.getInt(FieldKey.fromString("rowId")));
        });

        return ret;
    }

    private Map<Integer, String> sortToContainer = null;

    public String getContainerForSort(int sortId)
    {
        if (sortToContainer == null)
        {
            sortToContainer = new HashMap<>();
            new TableSelector(_sortTable, PageFlowUtil.set("rowId", "container")).forEachResults(rs -> {
                sortToContainer.put(rs.getInt(FieldKey.fromString("rowId")), rs.getString(FieldKey.fromString("container")));
            });
        }

        if (sortToContainer != null && sortToContainer.containsKey(sortId))
        {
            return sortToContainer.get(sortId);
        }

        String containerId = new TableSelector(_sortTable, PageFlowUtil.set("container")).getObject(sortId, String.class);
        sortToContainer.put(sortId, containerId);

        return containerId;
    }
}
