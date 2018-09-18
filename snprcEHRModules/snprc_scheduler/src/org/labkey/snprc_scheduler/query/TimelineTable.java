package org.labkey.snprc_scheduler.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleQueryUpdateService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.snprc_scheduler.Snprc_schedulerSchemaZ;
import org.labkey.snprc_scheduler.Snprc_schedulerUserSchemaZ;
import org.labkey.snprc_scheduler.domains.Timeline;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TimelineTable extends SimpleUserSchema.SimpleTable<Snprc_schedulerUserSchemaZ>
{

    /**
     *
     * Created by thawkins on 9/13/2018.
     * <p>
     * Create the simple table.
     * @param schema
     * @param table
     */

    public TimelineTable(Snprc_schedulerUserSchemaZ schema, TableInfo table)
    {
        super(schema, table);
    }

    @Override
    public SimpleUserSchema.SimpleTable init()
    {
        super.init();

        // initialize virtual columns here
        // HasItems = true if the timeline has timeline items assigned
        SQLFragment hasItemsSql = new SQLFragment();
        hasItemsSql.append("(CASE WHEN EXISTS (SELECT pr.TimelineId FROM ");
        hasItemsSql.append(Snprc_schedulerSchemaZ.getInstance().getTableInfoTimeline(), "t");
        hasItemsSql.append(" JOIN ");
        hasItemsSql.append(Snprc_schedulerSchemaZ.getInstance().getTableInfoTimelineItem(), "tl");
        hasItemsSql.append(" ON t.TimelineId = tl.TimelineId )");
        hasItemsSql.append(" THEN 'true' ELSE 'false' END)");
        ExprColumn hasItemsCol = new ExprColumn(this, "HasItems", hasItemsSql, JdbcType.BOOLEAN);
        addColumn(hasItemsCol);

        return this;
    }

    public boolean isTimelineInUse(int timelineId)
    {
        Set<String> cols = new HashSet<>();
        cols.add("HasItems");
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("TimelineId"), timelineId, CompareType.EQUAL);
        TableSelector ts = new TableSelector(this, cols, filter, null);
        Map<String, Object> result = ts.getMap();

        return Boolean.parseBoolean((String) result.get("HasItems"));
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new TimelineTable.UpdateService(this);
    }

    protected class UpdateService extends SimpleQueryUpdateService
    {
        public UpdateService(SimpleUserSchema.SimpleTable ti)
        {
            super(ti, ti.getRealTable());
        }


        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
        {
            int timelineId = (Integer) oldRowMap.get(Timeline.TIMELINE_ID);

            // Cannot delete a timeline that is in use (it has timelineItems assigned)
            if (isTimelineInUse(timelineId))
                throw new QueryUpdateServiceException("Timeline is in use, cannot be deleted.");

            return super.deleteRow(user, container, oldRowMap);
        }

        private TableInfo getTableInfo(@NotNull UserSchema schema, @NotNull String table)
        {
            TableInfo tableInfo = schema.getTable(table);
            if (tableInfo == null)
                throw new IllegalStateException("TableInfo not found for: " + table);

            return tableInfo;
        }

        private QueryUpdateService getQueryUpdateService(@NotNull TableInfo table)
        {
            QueryUpdateService qus = table.getUpdateService();
            if (qus == null)
                throw new IllegalStateException(table.getName() + " query update service");

            return qus;
        }
    }
}
