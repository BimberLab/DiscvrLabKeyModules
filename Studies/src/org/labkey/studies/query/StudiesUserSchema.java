package org.labkey.studies.query;

import com.google.gwt.user.client.ui.TabBar;
import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ldk.table.ContainerScopedTable;
import org.labkey.api.ldk.table.CustomPermissionsTable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.studies.StudiesService;
import org.labkey.api.studies.security.StudiesDataAdminPermission;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.studies.StudiesSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.studies.StudiesSchema.TABLE_ANCHOR_EVENTS;
import static org.labkey.studies.StudiesSchema.TABLE_COHORTS;
import static org.labkey.studies.StudiesSchema.TABLE_EXPECTED_TIMEPOINTS;
import static org.labkey.studies.StudiesSchema.TABLE_STUDIES;
import static org.labkey.studies.StudiesSchema.TABLE_TIMEPOINT_TO_DATE;
import static org.labkey.studies.query.LookupSetsManager.TABLE_LOOKUPS;
import static org.labkey.studies.query.LookupSetsManager.TABLE_LOOKUP_SETS;

public class StudiesUserSchema extends SimpleUserSchema
{
    private static final Logger _log = LogHelper.getLogger(StudiesUserSchema.class, "Messages related to Studies Service");
    private static final String TABLE_EVENT_TYPES = "studyEventTypes";

    public StudiesUserSchema(User user, Container container, DbSchema dbschema)
    {
        super(StudiesSchema.NAME, "", user, container, dbschema);
    }

    @Override
    public Set<String> getTableNames()
    {
        Set<String> available = new CaseInsensitiveTreeSet(super.getTableNames());
        available.add(TABLE_EVENT_TYPES);
        available.addAll(getPropertySetNames().keySet());

        return Collections.unmodifiableSet(available);
    }

    @Override
    public Set<String> getVisibleTableNames()
    {
        return getTableNames();
    }

    private Container getTargetContainer()
    {
        return getContainer().isWorkbookOrTab() ? getContainer().getParent() : getContainer();
    }

    public static void repopulateCaches(User u, Container c)
    {
        DbSchema schema = StudiesSchema.getInstance().getSchema();
        StudiesUserSchema us = new StudiesUserSchema(u, c, schema);

        LookupSetsManager.get().getCache().clear();
        us.getPropertySetNames();
    }

    private Map<String, Map<String, Object>> getPropertySetNames()
    {
        Map<String, Map<String, Object>> nameMap = (Map<String, Map<String, Object>>) LookupSetsManager.get().getCache().get(LookupSetTable.getCacheKey(getTargetContainer()));
        if (nameMap != null)
        {
            return nameMap;
        }

        _log.debug("Populating lookup tables in StudiesUserSchema.getPropertySetNames() for container: " + getTargetContainer().getName());
        nameMap = new CaseInsensitiveHashMap<>();

        TableSelector ts = new TableSelector(_dbSchema.getTable(TABLE_LOOKUP_SETS), new SimpleFilter(FieldKey.fromString("container"), getTargetContainer().getId()), null);
        Map<String, Object>[] rows = ts.getMapArray();
        if (rows.length > 0)
        {
            Set<String> existing = super.getTableNames();
            for (Map<String, Object> row : rows)
            {
                String setname = (String)row.get("setname");
                if (setname != null && !existing.contains(setname))
                    nameMap.put(setname, row);
            }
        }

        nameMap = Collections.unmodifiableMap(nameMap);
        LookupSetsManager.get().getCache().put(LookupSetTable.getCacheKey(getTargetContainer()), nameMap);

        return nameMap;
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        if (TABLE_LOOKUP_SETS.equalsIgnoreCase(name))
        {
            ContainerScopedTable<SimpleUserSchema> ret = new LookupSetsTable<>(this, createSourceTable(name), cf, "setname");
            ret.addPermissionMapping(InsertPermission.class, StudiesDataAdminPermission.class);
            ret.addPermissionMapping(UpdatePermission.class, StudiesDataAdminPermission.class);
            ret.addPermissionMapping(DeletePermission.class, StudiesDataAdminPermission.class);
            return ret.init();
        }
        else if (TABLE_LOOKUPS.equalsIgnoreCase(name))
        {
            CustomPermissionsTable<SimpleUserSchema> ret = new CustomPermissionsTable<>(this, createSourceTable(name), cf);
            ret.addPermissionMapping(InsertPermission.class, StudiesDataAdminPermission.class);
            ret.addPermissionMapping(UpdatePermission.class, StudiesDataAdminPermission.class);
            ret.addPermissionMapping(DeletePermission.class, StudiesDataAdminPermission.class);
            ret.addPermissionMapping(ReadPermission.class, StudiesDataAdminPermission.class);
            return ret.init();
        }
        else if (TABLE_STUDIES.equalsIgnoreCase(name))
        {
            return createStudiesTable(name, cf, false);
        }
        else if (TABLE_COHORTS.equalsIgnoreCase(name))
        {
            return createStudyDesignTable(name, cf, true);
        }
        else if (TABLE_ANCHOR_EVENTS.equalsIgnoreCase(name))
        {
            return createStudyDesignTable(name, cf, true);
        }
        else if (TABLE_EXPECTED_TIMEPOINTS.equalsIgnoreCase(name))
        {
            return createStudyDesignTable(name, cf, true);
        }
        else if (TABLE_TIMEPOINT_TO_DATE.equalsIgnoreCase(name))
        {
            return createStudyDesignTable(name, cf, true);
        }
        else if (TABLE_EVENT_TYPES.equalsIgnoreCase(name))
        {
            return createEventTypesTable(getContainer());
        }

        //try to find it in propertySets
        Map<String, Map<String, Object>> nameMap = getPropertySetNames();
        if (nameMap.containsKey(name))
            return createForPropertySet(this, cf, name, nameMap.get(name));

        return super.createTable(name, cf);
    }

    private TableInfo createStudiesTable(String name, ContainerFilter cf, boolean addTriggers)
    {
        CustomPermissionsTable<?> ret = createStudyDesignTable(name, cf, addTriggers);

        final String chr = ret.getSqlDialect().isPostgreSQL() ? "chr" : "char";
        SQLFragment sql1 = new SQLFragment("(SELECT ").append(ret.getSqlDialect().getGroupConcat(new SQLFragment("c.label"), true, true, new SQLFragment(chr + "(10)"))).append(" as expr FROM " + StudiesSchema.NAME + "." + TABLE_COHORTS + " c WHERE c.studyId = " + ExprColumn.STR_TABLE_ALIAS + ".rowId)");
        ExprColumn col1 = new ExprColumn(ret, "cohorts", sql1, JdbcType.VARCHAR, ret.getColumn("rowid"));
        col1.setLabel("Cohort(s)");
        col1.setDescription("This column lists the cohort labels for this study");

        ret.addColumn(col1);

        return ret;
    }

    private CustomPermissionsTable<?> createStudyDesignTable(String name, ContainerFilter cf, boolean addTriggers)
    {
        CustomPermissionsTable<SimpleUserSchema> ret = new CustomPermissionsTable<>(this, createSourceTable(name), cf);
        ret.addPermissionMapping(InsertPermission.class, StudiesDataAdminPermission.class);
        ret.addPermissionMapping(UpdatePermission.class, StudiesDataAdminPermission.class);
        ret.addPermissionMapping(DeletePermission.class, StudiesDataAdminPermission.class);

        if (addTriggers)
        {
            ret.addTriggerFactory(new StudiesTriggerFactory());
        }

        return ret.init();
    }

    private LookupSetTable createForPropertySet(StudiesUserSchema us, ContainerFilter cf, String setName, Map<String, Object> map)
    {
        SchemaTableInfo table = _dbSchema.getTable(TABLE_LOOKUPS);
        LookupSetTable ret = new LookupSetTable(us, table, cf, setName, map);
        ret.addPermissionMapping(InsertPermission.class, StudiesDataAdminPermission.class);
        ret.addPermissionMapping(UpdatePermission.class, StudiesDataAdminPermission.class);
        ret.addPermissionMapping(DeletePermission.class, StudiesDataAdminPermission.class);
        return ret.init();
    }

    private TableInfo createEventTypesTable(Container container)
    {
        StringBuilder sql = new StringBuilder("SELECT * FROM (");
        final int startLength = sql.length();
        StudiesService.get().getEventProviders(container).forEach(ep -> {
            if (sql.length() > startLength)
            {
                sql.append("UNION ALL\n");
            }

            sql.append("SELECT ").
                    append("'").append(ep.getName()).append("' AS name, ").
                    append("'").append(ep.getLabel()).append("' AS label, ").
                    append("'").append(ep.getDescription()).append("' AS description\n");
        });

        sql.append(") x");

        QueryDefinition qd = QueryService.get().createQueryDef(getUser(), getContainer(), this, TABLE_EVENT_TYPES);
        qd.setSql(sql.toString());

        List<QueryException> errors = new ArrayList<>();
        TableInfo ti = qd.getTable(errors, true);
        if (!errors.isEmpty())
        {
            _log.error("Problem with studyEventTypes query");
            for (QueryException e : errors)
            {
                _log.error(e.getMessage());
            }
        }

        if (ti instanceof AbstractTableInfo ati)
        {
            ati.setTitle("Study Event Types");
            ati.getMutableColumn("name").setLabel("Name");
            ati.getMutableColumn("label").setLabel("Label");
            ati.getMutableColumn("description").setLabel("Description");
        }

        return ti;
    }
}
