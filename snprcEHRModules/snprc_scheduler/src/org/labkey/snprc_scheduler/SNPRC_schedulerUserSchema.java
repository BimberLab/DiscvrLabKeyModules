package org.labkey.snprc_scheduler;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.CustomPermissionsTable;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.snprc_scheduler.query.TimelineItemTable;
import org.labkey.snprc_scheduler.query.TimelineTable;


/**
 * Created by thawkins on 9/14/2018.
 */

public class SNPRC_schedulerUserSchema extends SimpleUserSchema
{

    private boolean _permissionCheck = true;

    public SNPRC_schedulerUserSchema(User user, Container container)
    {
        super(SNPRC_schedulerSchema.NAME, null, user, container, SNPRC_schedulerSchema.getInstance().getSchema());
    }
    public SNPRC_schedulerUserSchema(String name, @Nullable String description, User user, Container container, DbSchema dbschema)
    {
        super(name, description, user, container, dbschema);
    }

    public boolean getPermissionCheck()
    {
        return _permissionCheck;
    }

    public enum TableType
    {
        Timeline
                {
                    @Override
                    public TableInfo createTable(SNPRC_schedulerUserSchema schema)
                    {
                        return new TimelineTable(schema, SNPRC_schedulerSchema.getInstance().getTableInfoTimeline()).init();
                    }
                },
        TimelineAnimalJunction
            {
                @Override
                public TableInfo createTable(SNPRC_schedulerUserSchema schema)
                {
                    return new TimelineTable(schema, SNPRC_schedulerSchema.getInstance().getTableInfoTimelineAnimalJunction()).init();
                }
            },
        TimelineProjectItem
                {
                    @Override
                    public TableInfo createTable(SNPRC_schedulerUserSchema schema)
                    {
                        return new TimelineTable(schema, SNPRC_schedulerSchema.getInstance().getTableInfoTimelineProjectItem()).init();
                    }
                },
        TimelineItem
                {
                    @Override
                    public TableInfo createTable(SNPRC_schedulerUserSchema schema)
                    {
                        return new TimelineItemTable(schema, SNPRC_schedulerSchema.getInstance().getTableInfoTimelineItem()).init();
                    }
                },
        Schedule
                {
                    @Override
                    public TableInfo createTable(SNPRC_schedulerUserSchema schema)
                    {
                        return new TimelineTable(schema, SNPRC_schedulerSchema.getInstance().getTableInfoSchedule()).init();
                    }
                };


        // pass through tables not in the enum
        public abstract TableInfo createTable(SNPRC_schedulerUserSchema schema);
        }


    private TableInfo getCustomPermissionTable(TableInfo schemaTable, Class<? extends Permission> perm)
    {
        CustomPermissionsTable result = new CustomPermissionsTable(this, schemaTable);
        result.addPermissionMapping(InsertPermission.class, perm);
        result.addPermissionMapping(UpdatePermission.class, perm);
        result.addPermissionMapping(DeletePermission.class, perm);

        return result.init();
    }


    @Override
    @Nullable
    public TableInfo createTable(String name)
    {
        if (name != null)
        {
            TableType tableType = null;
            for (TableType t : TableType.values())
            {
                // Make the enum name lookup case insensitive
                if (t.name().equalsIgnoreCase(name.toLowerCase()))
                {
                    tableType = t;
                    break;
                }
            }
            if (tableType != null)
            {
                return tableType.createTable(this);
            }

        }
        return null;
    }
}
