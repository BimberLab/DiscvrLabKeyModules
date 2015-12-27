package org.labkey.onprc_billing.query;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 1/7/14
 * Time: 6:24 PM
 */
public class BillingAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String AUDIT_EVENT_TYPE = "BillingAuditEvent";

    public static final String COLUMN_NAME_TABLE_NAME = "TableName";
    public static final String COLUMN_NAME_OBJECTID = "ObjectId";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_TABLE_NAME));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_OBJECTID));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new BillingAuditDomainKind();
    }

    @Override
    public String getEventName()
    {
        return AUDIT_EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "Billing Audit Events";
    }

    @Override
    public String getDescription()
    {
        return "Contains records of changes to data required for ONPRC billing";
    }
    
    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyNames = super.legacyNameMap();
        legacyNames.put(FieldKey.fromParts("key1"), COLUMN_NAME_TABLE_NAME);
        legacyNames.put(FieldKey.fromParts("key2"), COLUMN_NAME_OBJECTID);

        return legacyNames;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)BillingAuditEvent.class;
    }

    public static void addAuditEntry(Container container, User user, String tableName, String objectId, String comment)
    {
        BillingAuditProvider.BillingAuditEvent event = new BillingAuditProvider.BillingAuditEvent(container.getId(), comment);

        event.setTableName(tableName);
        event.setObjectId(objectId);

        AuditLogService.get().addEvent(user, event);
    }

    public static class BillingAuditEvent extends AuditTypeEvent
    {
        private String _tableName;
        private String _objectId;

        public BillingAuditEvent()
        {
            super();
        }

        public BillingAuditEvent(String container, String comment)
        {
            super(AUDIT_EVENT_TYPE, container, comment);
        }

        public String getTableName()
        {
            return _tableName;
        }

        public void setTableName(String tableName)
        {
            _tableName = tableName;
        }

        public String getObjectId()
        {
            return _objectId;
        }

        public void setObjectId(String objectId)
        {
            _objectId = objectId;
        }
    }

    public static class BillingAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "BillingAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public BillingAuditDomainKind()
        {
            super(AUDIT_EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_TABLE_NAME, PropertyType.STRING));
            fields.add(createPropertyDescriptor(COLUMN_NAME_OBJECTID, PropertyType.STRING));
            _fields = Collections.unmodifiableSet(fields);
        }

        @Override
        public Set<PropertyDescriptor> getProperties()
        {
            return _fields;
        }

        @Override
        protected String getNamespacePrefix()
        {
            return NAMESPACE_PREFIX;
        }

        @Override
        public String getKindName()
        {
            return NAME;
        }
    }    
}
