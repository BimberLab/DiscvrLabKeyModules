package org.labkey.openldapsync.ldap;

import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.AuditTypeProvider;
import org.labkey.api.audit.query.AbstractAuditDomainKind;
import org.labkey.api.audit.query.DefaultAuditTypeTable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 8/12/13
 */
public class LdapSyncAuditProvider extends AbstractAuditTypeProvider implements AuditTypeProvider
{
    public static final String COLUMN_NAME_USERS_AND_GROUPS_ADDED = "TotalUsersAndGroupsAdded";
    public static final String COLUMN_NAME_USERS_AND_GROUPS_REMOVED = "TotalUsersAndGroupsRemoved";
    public static final String COLUMN_NAME_MEMBERSHIPS_CHANGED = "TotalMembershipsChanged";

    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_CREATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_IMPERSONATED_BY));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_USERS_AND_GROUPS_ADDED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_USERS_AND_GROUPS_REMOVED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_MEMBERSHIPS_CHANGED));
        defaultVisibleColumns.add(FieldKey.fromParts(COLUMN_NAME_COMMENT));
    }

    @Override
    public String getEventName()
    {
        return LdapSyncRunner.AUDIT_EVENT_TYPE;
    }

    @Override
    public String getLabel()
    {
        return "LDAP Sync Events";
    }

    @Override
    public String getDescription()
    {
        return "Contains the history LDAP sync events and summary of changes made";
    }

    @Override
    public TableInfo createTableInfo(UserSchema userSchema, ContainerFilter cf)
    {
        DefaultAuditTypeTable table = new DefaultAuditTypeTable(this, createStorageTableInfo(), userSchema, cf, defaultVisibleColumns)
        {
            @Override
            protected void initColumn(BaseColumnInfo col)
            {
                if (COLUMN_NAME_USERS_AND_GROUPS_ADDED.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Total Users/Groups Added");
                }
                else if (COLUMN_NAME_USERS_AND_GROUPS_REMOVED.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Total Users/Groups Removed");
                }
                else if (COLUMN_NAME_MEMBERSHIPS_CHANGED.equalsIgnoreCase(col.getName()))
                {
                    col.setLabel("Total Group Members Added or Removed");
                }
            }
        };
        return table;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    protected AbstractAuditDomainKind getDomainKind()
    {
        return new LdapSyncAuditDomainKind();
    }

    @Override
    public Map<FieldKey, String> legacyNameMap()
    {
        Map<FieldKey, String> legacyMap =  super.legacyNameMap();
        legacyMap.put(FieldKey.fromParts("intKey1"), COLUMN_NAME_USERS_AND_GROUPS_ADDED);
        legacyMap.put(FieldKey.fromParts("intKey2"), COLUMN_NAME_USERS_AND_GROUPS_REMOVED);
        legacyMap.put(FieldKey.fromParts("intKey3"), COLUMN_NAME_MEMBERSHIPS_CHANGED);

        return legacyMap;
    }

    @Override
    public <K extends AuditTypeEvent> Class<K> getEventClass()
    {
        return (Class<K>)LdapSyncAuditEvent.class;
    }

    public static void addAuditEntry(User user, int usersAdded, int usersRemoved, int usersInactivated, int usersModified, int groupsAdded, int groupsRemoved, int membershipsAdded, int membershipsRemoved)
    {
        String comment = String.format("LDAP Sync Summary: users added: %s, users removed: %s, users inactivated: %s, users modified: %s, groups added: %s, groups removed: %s, memberships added: %s, memberships removed: %s", usersAdded, usersRemoved, usersInactivated, usersModified, groupsAdded, groupsRemoved, membershipsAdded, membershipsRemoved);
        LdapSyncAuditProvider.LdapSyncAuditEvent event = new LdapSyncAuditProvider.LdapSyncAuditEvent(ContainerManager.getRoot().getId(), comment);

        event.setTotalUsersAndGroupsAdded(usersAdded + groupsAdded);
        event.setTotalUsersAndGroupsRemoved(usersRemoved + groupsRemoved);
        event.setTotalMembershipsChanged(membershipsAdded + membershipsRemoved);

        AuditLogService.get().addEvent(user, event);
    }

    public static class LdapSyncAuditEvent extends AuditTypeEvent
    {
        private int _totalUsersAndGroupsAdded;
        private int _totalUsersAndGroupsRemoved;
        private int _totalMembershipsChanged;

        public LdapSyncAuditEvent()
        {
            super();
        }

        public LdapSyncAuditEvent(String container, String comment)
        {
            super(LdapSyncRunner.AUDIT_EVENT_TYPE, container, comment);
        }

        public int getTotalUsersAndGroupsAdded()
        {
            return _totalUsersAndGroupsAdded;
        }

        public void setTotalUsersAndGroupsAdded(int totalUsersAndGroupsAdded)
        {
            _totalUsersAndGroupsAdded = totalUsersAndGroupsAdded;
        }

        public int getTotalUsersAndGroupsRemoved()
        {
            return _totalUsersAndGroupsRemoved;
        }

        public void setTotalUsersAndGroupsRemoved(int totalUsersAndGroupsRemoved)
        {
            _totalUsersAndGroupsRemoved = totalUsersAndGroupsRemoved;
        }

        public int getTotalMembershipsChanged()
        {
            return _totalMembershipsChanged;
        }

        public void setTotalMembershipsChanged(int totalMembershipsChanged)
        {
            _totalMembershipsChanged = totalMembershipsChanged;
        }
    }

    public static class LdapSyncAuditDomainKind extends AbstractAuditDomainKind
    {
        public static final String NAME = "LdapSyncAuditDomain";
        public static String NAMESPACE_PREFIX = "Audit-" + NAME;

        private final Set<PropertyDescriptor> _fields;

        public LdapSyncAuditDomainKind()
        {
            super(LdapSyncRunner.AUDIT_EVENT_TYPE);

            Set<PropertyDescriptor> fields = new LinkedHashSet<>();
            fields.add(createPropertyDescriptor(COLUMN_NAME_USERS_AND_GROUPS_ADDED, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_USERS_AND_GROUPS_REMOVED, PropertyType.INTEGER));
            fields.add(createPropertyDescriptor(COLUMN_NAME_MEMBERSHIPS_CHANGED, PropertyType.INTEGER));
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
