package org.labkey.extscheduler.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.StringExpression;
import org.labkey.extscheduler.ExtSchedulerManager;
import org.springframework.validation.Errors;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Set;

public class EventsQueryView extends QueryView
{
    public EventsQueryView(UserSchema schema, QuerySettings settings, @Nullable Errors errors)
    {
        super(schema, settings, errors);
    }

    @Override
    protected void addDetailsAndUpdateColumns(List<DisplayColumn> ret, TableInfo table)
    {
        StringExpression urlUpdate = urlExpr(QueryAction.updateQueryRow);

        if (urlUpdate != null)
        {
            UpdateColumn update = new UpdateColumn(urlUpdate)
            {
                @Override
                public void addQueryFieldKeys(Set<FieldKey> keys)
                {
                    super.addQueryFieldKeys(keys);
                    ColumnInfo createdByCol = getTable().getColumn("CreatedBy");
                    if (createdByCol != null)
                        keys.add(createdByCol.getFieldKey());
                }

                @Override
                public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                {
                    boolean isAdmin = getContainer().hasPermission(getUser(), AdminPermission.class);
                    boolean isOwner = ExtSchedulerManager.getInstance().isEventOwner(getUser(), ctx.getRow());
                    boolean isCreator = ExtSchedulerManager.getInstance().isEventCreator(getUser(), ctx.getRow());
                    boolean isInPast = ExtSchedulerManager.getInstance().isEventInPast(ctx.getRow());

                    // admins can edit all, non-admins can edit future events which they own/created
                    if (isAdmin || ((isOwner || isCreator) && !isInPast))
                        super.renderGridCellContents(ctx, out);
                    else
                        out.write("&nbsp;");
                }
            };
            ret.add(0, update);
        }
    }
}
