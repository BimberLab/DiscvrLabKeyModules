package org.labkey.onprc_billing.query;

import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * User: bimber
 * Date: 11/26/13
 * Time: 4:10 PM
 */
public class BillingAuditViewFactory extends SimpleAuditViewFactory
{
    private static final BillingAuditViewFactory _instance = new BillingAuditViewFactory();
    public static final String AUDIT_EVENT_TYPE = "BillingAuditEvent";

    public static BillingAuditViewFactory getInstance()
    {
        return _instance;
    }

    private BillingAuditViewFactory()
    {

    }

    public String getEventType()
    {
        return AUDIT_EVENT_TYPE;
    }

    public String getName()
    {
        return "Billing Audit Events";
    }

    @Override
    public String getDescription()
    {
        return "Contains records of changes to data required for ONPRC billing";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        return createHistoryView(context, null);
    }

    public static void addAuditEntry(Container container, User user, String tableName, String objectId, String comment)
    {
        AuditLogEvent event = new AuditLogEvent();
        event.setContainerId(ContainerManager.getRoot().getId());
        event.setEventType(AUDIT_EVENT_TYPE);
        event.setCreatedBy(user);

        event.setComment(comment);
        event.setKey1(tableName);
        event.setKey2(objectId);
        event.setContainerId(container.getId());

        event.setCreated(new Date());
        AuditLogService.get().addEvent(event);
    }

    public AuditLogQueryView createHistoryView(ViewContext context, SimpleFilter extraFilter)
    {
        SimpleFilter filter = new SimpleFilter();

        if (null != extraFilter)
            filter.addAllClauses(extraFilter);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setSort(new Sort("-Date"));

        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("Comment"));
        columns.add(FieldKey.fromParts("Key1"));
        columns.add(FieldKey.fromParts("Key2"));
        columns.add(FieldKey.fromParts("IntKey1"));
        columns.add(FieldKey.fromParts("IntKey2"));
        columns.add(FieldKey.fromParts("IntKey3"));

        return columns;
    }

    public void setupTable(FilteredTable table, UserSchema schema)
    {
        super.setupTable(table, schema);

        ColumnInfo keyCol1 = table.getColumn("Key1");
        if (keyCol1 != null)
        {
            keyCol1.setLabel("Table Name");
        }

        ColumnInfo keyCol2 = table.getColumn("Key2");
        if (keyCol2 != null)
        {
            keyCol2.setLabel("Record ObjectId");
        }
    }
}

