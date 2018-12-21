package org.labkey.openldapsync.ldap;

import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.Group;
import org.labkey.api.security.InvalidGroupMembershipException;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.NotFoundException;
import org.labkey.openldapsync.OpenLdapSyncSchema;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 1/21/13
 * Time: 5:18 PM
 */
public class LdapSyncRunner implements Job
{
    private static final Logger _log = Logger.getLogger(LdapSyncRunner.class);
    private LdapSettings _settings;
    private LdapConnectionWrapper _wrapper;
    private Map<String, LdapSyncModel> _syncedRecordMap = new HashMap<>();
    private boolean _previewOnly = false;
    private List<String> _messages = new ArrayList<>();
    private boolean _doDetailedLogging = true;

    private Map<String, Integer> _usersSynced = new HashMap<>();
    private Map<String, Integer> _groupsSynced = new HashMap<>();

    public static final String AUDIT_EVENT_TYPE = "LdapAuditEvent";

    private int _usersAdded = 0;
    private int _usersRemoved = 0;
    private int _usersInactivated = 0;
    private int _usersModified = 0;
    private int _groupsAdded = 0;
    private int _groupsRemoved = 0;
    private int _membershipsAdded = 0;
    private int _membershipsRemoved = 0;

    public LdapSyncRunner()
    {

    }

    public void execute(JobExecutionContext context) throws JobExecutionException
    {
        try
        {
            doSync();
        }
        catch (LdapException e)
        {
            _log.error("Problem running LDAP sync", e);
        }
    }

    public void doSync() throws LdapException
    {
        _log.info("LDAP Sync started");

        _settings = new LdapSettings();

        //ignore enabled if we're just previewing results
        if (!_settings.isEnabled() && !_previewOnly)
        {
            log("Sync not enabled, aborting");
            return;
        }

        _settings.validateSettings();
        _wrapper = new LdapConnectionWrapper();
        if (_previewOnly)
        {
            _wrapper.setDoLog(true);
        }

        try
        {
            _wrapper.connect();

            initPreviouslySyncedRecords();

            LdapSettings.LdapSyncMode mode = _settings.getSyncMode();
            if (LdapSettings.LdapSyncMode.usersOnly.equals(mode))
            {
                syncAllUsers();
                //ignore groups
            }
            else if (LdapSettings.LdapSyncMode.groupWhitelist.equals(mode))
            {
                for (String name : _settings.getGroupWhiteList())
                {
                    List<LdapEntry> users = _wrapper.getGroupMembers(name);
                    for (LdapEntry user : users)
                    {
                        syncUser(user);
                    }
                }
                handleUsersRemovedFromLdap();

                syncGroupsAndMembers(_settings.getGroupWhiteList());
            }
            else if (LdapSettings.LdapSyncMode.usersAndGroups.equals(mode))
            {
                syncAllUsers();
                syncGroupsAndMembers(null);
            }
        }
        finally
        {
            _wrapper.disconnect();
        }

        _log.info("LDAP sync complete");

        log(getSummaryText());
        updateLdapTable();
        deactivatePrincipalsPreviouslySynced();
        writeAuditTrail();
    }

    private void log(String message)
    {
        _messages.add(message);

        if (_doDetailedLogging && !_previewOnly)
            _log.info(message);
    }

    private String getSummaryText()
    {
        return String.format("LDAP Sync Summary: users added: %s, users removed: %s, users inactivated: %s, users modified: %s, groups added: %s, groups removed: %s, memberships added: %s, memberships removed: %s", _usersAdded, _usersRemoved, _usersInactivated, _usersModified, _groupsAdded, _groupsRemoved, _membershipsAdded, _membershipsRemoved);
    }

    private void syncGroupsAndMembers(List<String> included) throws LdapException
    {
        //first find groups that are included
        Set<LdapEntry> groups = new HashSet<>();
        if (included == null)
            groups.addAll(_wrapper.listAllGroups());
        else
        {
            for (String dn : included)
            {
                try
                {
                    LdapEntry group = _wrapper.getGroup(dn);
                    if (group != null)
                        groups.add(group);
                    else
                    {
                        log("Unable to find LDAP entity with DN: " + dn);
                    }
                }
                catch (LdapException e)
                {
                    log(e.getMessage());
                }
            }
        }

        for (LdapEntry group : groups)
        {
            syncGroupAndMembers(group);
        }

        handleGroupsRemovedFromLdap();
    }

    private void syncAllUsers() throws LdapException
    {
        List<LdapEntry> users = _wrapper.listAllUsers();
        for (LdapEntry entry : users)
        {
            syncUser(entry);
        }

        handleUsersRemovedFromLdap();
    }

    private void handleUsersRemovedFromLdap() throws LdapException
    {
        //find any previously synced users that are no longer present
        List<LdapSyncModel> records = getPreviouslySyncedRecords(PrincipalType.USER);
        for (LdapSyncModel model : records)
        {
            if (!_usersSynced.containsKey(model.getSourceId()))
            {
                User toRemove = UserManager.getUser(model.getLabkeyId());
                if (toRemove != null)
                {
                    //need to either delete or deactivate
                    if (_settings.deleteUserWhenRemovedFromLdap())
                    {
                        deleteUser(toRemove);
                    }
                    else
                    {
                        setUserActive(toRemove, false, "the user is not a member of synced LDAP groups");
                    }
                }
            }
        }
    }

    private void setUserActive(User u, boolean active, String reason)
    {
        try
        {
            log("Changing active state of user: " + u.getEmail() + " to " + active + (reason == null ? "" : ", reason: " + reason));
            _usersInactivated++;

            if (!_previewOnly)
            {
                UserManager.setUserActive(_settings.getLabKeyAdminUser(), u, active);
            }
        }
        catch (SecurityManager.UserManagementException e)
        {
            _log.error("Unable to deactive user: " + u.getEmail());
        }
    }

    private void handleGroupsRemovedFromLdap() throws LdapException
    {
        //find any previously synced groups that are no longer present
        List<LdapSyncModel> records = getPreviouslySyncedRecords(PrincipalType.GROUP);
        for (LdapSyncModel model : records)
        {
            if (!_groupsSynced.containsKey(model.getSourceId()))
            {
                Group toRemove = SecurityManager.getGroup(model.getLabkeyId());
                if (toRemove != null)
                {
                    //need to either delete or deactivate
                    if (_settings.deleteGroupWhenRemovedFromLdap())
                    {
                        deleteGroup(toRemove);
                    }
                }
            }
        }
    }

    private void syncUser(LdapEntry ldapEntry) throws LdapException
    {
        //verify whether the user has been synced before
        User existing = UserManager.getUser(ldapEntry.getValidEmail());
        if (existing != null)
        {
            //NOTE: we disable users in LK if not active in LDAP, but not the reverse since a user could be disabled in LK intentionally.
            //there can often be a lag between a person leaving and actually having their account disabled in LDAP
            boolean isEnabled = ldapEntry.isEnabled();
            if (!isEnabled && isEnabled != existing.isActive())
            {
                setUserActive(existing, isEnabled, "copied from the LDAP entry");
                _usersInactivated++;
            }


            if (_settings.overwriteUserInfoIfChanged())
            {
                syncUserAttributes(ldapEntry, existing);
            }
        }
        else
        {
            if (ldapEntry.isEnabled())
                existing = createUser(ldapEntry);
        }

        if (existing != null)
            _usersSynced.put(ldapEntry.getDn().getName(), existing.getUserId());
    }

    private void syncGroupAndMembers(LdapEntry group) throws LdapException
    {
        //verify whether the group has been synced before
        String name = group.getDisplayName();
        if (name == null)
        {
            throw new LdapException("Unable to find name for group: " + group.getDn().getName());
        }

        Group existingGroup = null;
        Integer groupId = null;
        try
        {
            groupId = SecurityManager.getGroupId(ContainerManager.getRoot(), name);
        }
        catch (NotFoundException e)
        {
            //ignore
        }

        if (groupId != null)
            existingGroup = SecurityManager.getGroup(groupId);

        if (!group.isEnabled())
        {
            //this will cause the group to get deleted downstream
            existingGroup = null;
        }
        else if (existingGroup == null)
        {
            existingGroup = createGroup(group);
        }
        else
        {
            //group has no attributes that we need to sync.  membership is handled separately
        }

        if (existingGroup != null)
        {
            _groupsSynced.put(group.getDn().getName(), existingGroup.getUserId());
            syncGroupMembership(group, existingGroup);
        }
    }

    private void syncGroupMembership(LdapEntry group, Group existingGroup) throws LdapException
    {
        List<LdapEntry> children = _wrapper.getGroupMembers(group.getDn().getName());
        //NOTE: this is potentially going to include inactive users.  in this instance i dont believe that is an issue
        Set<UserPrincipal> existingMembers = SecurityManager.getAllGroupMembers(existingGroup, MemberType.ALL_GROUPS_AND_USERS);

        Set<UserPrincipal> ldapChildren = new HashSet<>();
        for (LdapEntry child : children)
        {
            User u = UserManager.getUser(child.getValidEmail());
            if (u == null)
            {
                log("User not found in LabKey: " + child.getDisplayName() + ", cannot add to group: " + existingGroup.getName());
                continue;
            }

            ldapChildren.add(u);

            if (!existingMembers.contains(u))
                addMember(existingGroup, u);
        }

        //refresh list
        //NOTE: this is potentially going to include inactive users.  in this instance i dont believe that is an issue
        existingMembers = SecurityManager.getAllGroupMembers(existingGroup, MemberType.ALL_GROUPS_AND_USERS);
        if (!_settings.getMemberSyncMode().equals(LdapSettings.MemberSyncMode.noAction))
        {
            for (UserPrincipal u : existingMembers)
            {
                if (!ldapChildren.contains(u))
                {
                    //with this setting, we remove any UserPrincipal from the LabKey side if not present on the LDAP side
                    //the LDAP group should exactly match the LabKey group membership
                    if (_settings.getMemberSyncMode().equals(LdapSettings.MemberSyncMode.mirror))
                    {
                        deleteMember(existingGroup, u);
                    }

                    //with this setting, we remove any UserPrincipal from the LabKey side that's absent on the LDAP side,
                    // but only if that UserPrincipal was synced from LDAP.  this means a LabKey group could have additional members
                    else if (_settings.getMemberSyncMode().equals(LdapSettings.MemberSyncMode.removeDeletedLdapUsers))
                    {
                        if (u.getPrincipalType().equals(PrincipalType.GROUP))
                            continue;

                        if (_usersSynced.values().contains(u.getUserId()))
                        {
                            deleteMember(existingGroup, u);
                        }
                    }
                }
            }
        }
    }

    private void updateLdapTable() throws LdapException
    {
        if (_previewOnly)
            return;

        try
        {
            String provider = _wrapper.getProviderName();
            TableInfo ti = OpenLdapSyncSchema.getInstance().getSchema().getTable(OpenLdapSyncSchema.TABLE_LDAP_SYNC_MAP);

            User u = _settings.getLabKeyAdminUser();
            for (String dn : _usersSynced.keySet())
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("provider"), provider, CompareType.EQUAL);
                filter.addCondition(FieldKey.fromString("sourceId"), dn);
                filter.addCondition(FieldKey.fromString("labkeyId"), _usersSynced.get(dn));
                TableSelector ts = new TableSelector(ti, filter, null);

                if (ts.getRowCount() == 0)
                {
                    LdapSyncModel model = new LdapSyncModel();
                    model.setProvider(provider);
                    model.setType(String.valueOf(PrincipalType.USER.getTypeChar()));
                    model.setSourceId(dn);
                    model.setLabkeyId(_usersSynced.get(dn));
                    model.setContainer(ContainerManager.getRoot().getId());
                    model.setCreated(new Date());

                    Table.insert(u, ti, model);
                }
            }

            for (String dn : _groupsSynced.keySet())
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("provider"), provider, CompareType.EQUAL);
                filter.addCondition(FieldKey.fromString("sourceId"), dn);
                filter.addCondition(FieldKey.fromString("labkeyId"), _groupsSynced.get(dn));

                TableSelector ts = new TableSelector(ti, filter, null);
                if (ts.getRowCount() == 0)
                {
                    LdapSyncModel model = new LdapSyncModel();
                    model.setProvider(provider);
                    model.setType(String.valueOf(PrincipalType.GROUP.getTypeChar()));
                    model.setSourceId(dn);
                    model.setLabkeyId(_groupsSynced.get(dn));
                    model.setContainer(ContainerManager.getRoot().getId());
                    model.setCreated(new Date());

                    Table.insert(u, ti, model);
                }
            }
        }
        catch (RuntimeSQLException e)
        {
            throw new LdapException(e);
        }
    }

    private void deactivatePrincipalsPreviouslySynced()
    {
        TableInfo ti = OpenLdapSyncSchema.getInstance().getSchema().getTable(OpenLdapSyncSchema.TABLE_LDAP_SYNC_MAP);
        User u = _settings.getLabKeyAdminUser();

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("type"), "u");
        TableSelector ts = new TableSelector(ti, Collections.singleton("labkeyId"), filter, null);
        Integer[] values = ts.getArray(Integer.class);
        Set<Integer> previouslySynced = new HashSet<>(Arrays.asList(values));

        for (Integer syncedUser : _usersSynced.values())
        {
            previouslySynced.remove(syncedUser);
        }

        if (previouslySynced.size() > 0)
        {
            //TODO: determine whether to delete/deactive, etc?
            for (Integer userId : previouslySynced)
            {
                User user = UserManager.getUser(userId);
                if (user != null && user.isActive())
                    log("WARNING: The following user was synced from LDAP in the past, but was not present in this sync: " + user.getEmail());
            }
        }

        SimpleFilter filter2 = new SimpleFilter(FieldKey.fromString("type"), "g");
        TableSelector ts2 = new TableSelector(ti, Collections.singleton("labkeyId"), filter2, null);
        Integer[] values2 = ts2.getArray(Integer.class);
        Set<Integer> previouslySyncedGroups = new HashSet<>(Arrays.asList(values2));

        for (Integer syncedGroup : _groupsSynced.values())
        {
            previouslySyncedGroups.remove(syncedGroup);
        }

        if (previouslySyncedGroups.size() > 0)
        {
            //TODO: determine whether to delete/deactive, etc
            for (Integer groupId : previouslySyncedGroups)
            {
                Group group = SecurityManager.getGroup(groupId);
                if (group != null)
                    log("WARNING: The following group was synced from LDAP in the past, but was not present in this sync: " + group.getName());
            }
        }
    }

    private void writeAuditTrail()
    {
        if (_previewOnly)
            return;

        LdapSyncAuditProvider.addAuditEntry(_settings.getLabKeyAdminUser(), _usersAdded, _usersRemoved, _usersInactivated, _usersModified, _groupsAdded, _groupsRemoved, _membershipsAdded, _membershipsRemoved);
    }

    private void initPreviouslySyncedRecords()
    {
        TableInfo ti = OpenLdapSyncSchema.getInstance().getSchema().getTable(OpenLdapSyncSchema.TABLE_LDAP_SYNC_MAP);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("provider"), _wrapper.getProviderName(), CompareType.EQUAL);
        TableSelector ts = new TableSelector(ti, filter, null);

        ts.forEach(new Selector.ForEachBlock<LdapSyncModel>()
        {
            @Override
            public void exec(LdapSyncModel model)
            {
                _syncedRecordMap.put(model.getSourceId(), model);
            }
        }, LdapSyncModel.class);
    }

    private User createUser(LdapEntry ldapEntry) throws LdapException
    {
        try
        {
            ValidEmail email = ldapEntry.getValidEmail();
            if (email == null)
            {
                log("Unable to create email for user with the displayName/login of: " + ldapEntry.getDisplayName() + " / " + ldapEntry.getUID() + ".  The LDAP record listed the email as: " + ldapEntry.getEmail());
                return null;
            }

            log("Creating user from LDAP: " + email.getEmailAddress());
            _usersAdded++;

            if (!_previewOnly)
            {
                SecurityManager.NewUserStatus newUserStatus = SecurityManager.addUser(email, null);
                User newUser = newUserStatus.getUser();

                String firstName = ldapEntry.getFirstName();
                if (firstName != null)
                    newUser.setFirstName(firstName);

                String lastName = ldapEntry.getLastName();
                if (lastName != null)
                    newUser.setLastName(lastName);

                String displayName = ldapEntry.getDisplayName();
                if (displayName != null)
                    newUser.setDisplayName(displayName);

                String phone = ldapEntry.getPhone();
                if (phone != null)
                    newUser.setPhone(phone);

                return newUser;
            }
            else
                return null;
        }
        catch (SecurityManager.UserManagementException e)
        {
            throw new LdapException(e);
        }
    }

    private void syncUserAttributes(LdapEntry ldapEntry, User existing) throws LdapException
    {
        boolean changed = false;

        String firstName = ldapEntry.getFirstName();
        if (firstName != null && !firstName.equals(existing.getFirstName()))
        {
            changed = true;
            existing.setFirstName(firstName);
        }

        String lastName = ldapEntry.getLastName();
        if (lastName != null && !lastName.equals(existing.getLastName()))
        {
            changed = true;
            existing.setLastName(lastName);
        }

//        String displayName = ldapEntry.getDisplayName();
//        if (displayName != null && !displayName.equals(existing.getDisplayName(_settings.getLabKeyAdminUser())))
//        {
//            changed = true;
//            existing.setDisplayName(displayName);
//        }

        String phone = ldapEntry.getPhone();
        if (phone != null && !PageFlowUtil.formatPhoneNo(phone).equals(existing.getPhone()))
        {
            changed = true;
            existing.setPhone(phone);
        }

        String email = ldapEntry.getEmail();
        if (email != null && !email.equals(existing.getEmail()))
        {
            changed = true;
            existing.setEmail(email);
        }

        if (changed)
        {
            log("Updating user settings: " + existing.getEmail());
            _usersModified++;

            if (!_previewOnly)
            {
                UserManager.updateUser(_settings.getLabKeyAdminUser(), existing);
            }
        }
    }

    private Group createGroup(LdapEntry group)
    {
        //need to create group.  deal with membership later
        log("Creating group from from LDAP: " + group.getDisplayName());
        _groupsAdded++;

        if (!_previewOnly)
        {
            return SecurityManager.createGroup(ContainerManager.getRoot(), group.getDisplayName());
        }
        else
        {
            return null;
        }
    }

    private void deleteGroup(Group g)
    {
        log("Deleteing LabKey group: " + g.getName());
        _groupsRemoved++;

        if (!_previewOnly)
            SecurityManager.deleteGroup(g);
    }

    private void deleteUser(User u) throws LdapException
    {
        try
        {
            log("Deleting LabKey user: " + u.getEmail());
            _usersRemoved++;

            if (!_previewOnly)
                UserManager.deleteUser(u.getUserId());
        }
        catch (SecurityManager.UserManagementException e)
        {
            throw new LdapException(e);
        }
    }

    private void addMember(Group g, UserPrincipal u) throws LdapException
    {
        try
        {
            log("adding member: " + u.getName() + " to group: " + g.getName());
            _membershipsAdded++;

            if (!_previewOnly)
                SecurityManager.addMember(g, u);
        }
        catch (InvalidGroupMembershipException e)
        {
            throw new LdapException(e);
        }
    }

    private void deleteMember(Group g, UserPrincipal u)
    {
        log("deleting member: " + u.getName() + " from group: " + g.getName());
        _membershipsRemoved++;

        if (!_previewOnly)
            SecurityManager.deleteMember(g, u);
    }

    private List<LdapSyncModel> getPreviouslySyncedRecords(PrincipalType pt)
    {
        List<LdapSyncModel> models = new ArrayList<>();
        for (LdapSyncModel model : _syncedRecordMap.values())
        {
            if (PrincipalType.forChar(model.getType().charAt(0)).equals(pt))
                models.add(model);
        }

        return models;
    }

    public void setPreviewOnly(boolean previewOnly)
    {
        _previewOnly = previewOnly;
    }

    public List<String> getMessages()
    {
        return _messages;
    }

    public void setDetailedLogging(boolean doDetailedLogging)
    {
        _doDetailedLogging = doDetailedLogging;
    }

    public static class LdapSyncModel
    {
        private String _provider;
        private String _sourceId;
        private Integer _labkeyId;
        private String _type;
        private Date _created;
        private String _container;

        public String getProvider()
        {
            return _provider;
        }

        public void setProvider(String provider)
        {
            _provider = provider;
        }

        public String getSourceId()
        {
            return _sourceId;
        }

        public void setSourceId(String sourceId)
        {
            _sourceId = sourceId;
        }

        public Integer getLabkeyId()
        {
            return _labkeyId;
        }

        public void setLabkeyId(Integer labkeyId)
        {
            _labkeyId = labkeyId;
        }

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }

        public Date getCreated()
        {
            return _created;
        }

        public void setCreated(Date created)
        {
            _created = created;
        }

        public String getContainer()
        {
            return _container;
        }

        public void setContainer(String container)
        {
            _container = container;
        }
    }

    public static class TestCase
    {
        @Test
        public void testBasicSync() throws Exception
        {

        }

        // This can be used to return LdapEntry objects to support some degree of automated testing without needing a functional LDAP Server
        private class DummyConnectionWrapper extends LdapConnectionWrapper
        {
            public DummyConnectionWrapper() throws LdapException
            {
                super();
            }

            @Override
            protected void init() throws LdapException
            {
                //no-op
            }

            @Override
            public List<LdapEntry> getGroupMembers(String dn) throws LdapException
            {
                return super.getGroupMembers(dn);
            }

            @Override
            public LdapEntry getGroup(String dn) throws LdapException
            {
                return super.getGroup(dn);
            }

            @Override
            public List<LdapEntry> listAllGroups() throws LdapException
            {
                return super.listAllGroups();
            }

            @Override
            public List<LdapEntry> listAllUsers() throws LdapException
            {
                return super.listAllUsers();
            }
        }
    }
}
