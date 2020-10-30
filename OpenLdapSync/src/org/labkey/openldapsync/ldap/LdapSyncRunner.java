package org.labkey.openldapsync.ldap;

import org.apache.commons.lang.StringUtils;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapInvalidDnException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.InvalidGroupMembershipException;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.NotFoundException;
import org.labkey.openldapsync.OpenLdapSyncModule;
import org.labkey.openldapsync.OpenLdapSyncSchema;
import org.labkey.security.xml.GroupEnumType;
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

import static org.labkey.openldapsync.ldap.LdapSettings.DEFAULT_DISPLAY_NAME_VAL;
import static org.labkey.openldapsync.ldap.LdapSettings.DEFAULT_EMAIL_FIELD_VAL;
import static org.labkey.openldapsync.ldap.LdapSettings.DEFAULT_FIRST_NAME_VAL;
import static org.labkey.openldapsync.ldap.LdapSettings.DEFAULT_LAST_NAME_VAL;
import static org.labkey.openldapsync.ldap.LdapSettings.DEFAULT_PHONE_VAL;
import static org.labkey.openldapsync.ldap.LdapSettings.DEFAULT_UID_VAL;
import static org.labkey.openldapsync.ldap.LdapSettings.DISPLAYNAME_FIELD_PROP;
import static org.labkey.openldapsync.ldap.LdapSettings.FIRSTNAME_FIELD_PROP;

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

    private Container _userGroupContainer = ContainerManager.getRoot();

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

        performSync();
    }

    //This is separated to facilitate testing
    protected void performSync() throws LdapException
    {
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

    private String getNameForGroup(LdapEntry group) throws LdapException
    {
        String groupName = group.getDisplayName();
        if (groupName == null)
        {
            throw new LdapException("Unable to find displayname for group: " + group.getDn().getName());
        }

        //concatenate to the group name if the field is not empty
        if (StringUtils.trimToNull(_settings.getGroupNameSuffix()) != null)
        {
            //Note: do not trim the suffix, as this allows the admin to provide leading whitespace.  for example: the suffix " (LDAP)" would result in "MyGroup (LDAP)"
            groupName = groupName.concat(_settings.getGroupNameSuffix());
        }

        return groupName;
    }

    private void syncGroupAndMembers(LdapEntry group) throws LdapException
    {
        String groupName = getNameForGroup(group);

        Group existingGroup = null;
        Integer groupId = null;
        try
        {
            groupId = SecurityManager.getGroupId(getUserGroupContainer(), groupName);
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
            existingGroup = createGroup(groupName);
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
                    model.setContainer(getUserGroupContainer().getId());
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
                    model.setContainer(getUserGroupContainer().getId());
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

                String im = ldapEntry.getIM();
                if (im != null)
                    newUser.setIM(im);

                UserManager.updateUser(_settings.getLabKeyAdminUser(), newUser);

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

        String im = ldapEntry.getIM();
        if (im != null && !im.equals(existing.getIM()))
        {
            changed = true;
            existing.setIM(im);
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

    private Container getUserGroupContainer()
    {
        return _userGroupContainer;
    }

    private Group createGroup(String groupName)
    {
        //need to create group.  deal with membership later
        log("Creating group from from LDAP: " + groupName);
        _groupsAdded++;

        if (!_previewOnly)
        {
            return SecurityManager.createGroup(getUserGroupContainer(), groupName);
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

    public static class TestCase extends Assert
    {
        private static final String PROJECT_NAME = "LdapSyncTestProject";

        private static final String EMAIL1 = "LdapSyncTestUser@example.com";
        private static final String EMAIL2 = "LdapSyncTestUser2@example.com";

        private static final String GROUP_NAME1 = "LdapSyncTestGroup1";
        private static final String GROUP_NAME2 = "LdapSyncTestGroup2";

        private MockLdapEntry getUserEntry(String email, LdapSettings settings) throws Exception
        {
            ValidEmail ve = new ValidEmail(email);

            Map<String, String> props = new HashMap<>();
            props.put(DEFAULT_EMAIL_FIELD_VAL, email);
            props.put(DEFAULT_DISPLAY_NAME_VAL, "Test user");
            props.put(DEFAULT_UID_VAL, ve.getPersonal());
            props.put(DEFAULT_PHONE_VAL, "(123) 456-7890");
            props.put(DEFAULT_FIRST_NAME_VAL, "Test");
            props.put(DEFAULT_LAST_NAME_VAL, "User");

            String dn = "uid=" + ve.getPersonal() + ",cn=users,dc=example,dc=com";

            return new MockLdapEntry(dn, props, settings);
        }

        @Before
        public void doSetup() throws Exception
        {
            cleanup();

            Container project = ContainerManager.getForPath(PROJECT_NAME);
            if (project == null)
            {
                project = ContainerManager.createContainer(ContainerManager.getRoot(), PROJECT_NAME);
                Set<Module> modules = new HashSet<>();
                modules.addAll(project.getActiveModules());
                modules.add(ModuleLoader.getInstance().getModule(OpenLdapSyncModule.NAME));
                project.setActiveModules(modules);
            }
        }

        private DummyConnectionWrapper getConnectionWrapper(LdapSettings settings) throws Exception
        {
            DummyConnectionWrapper wrapper = new DummyConnectionWrapper();

            wrapper.addUser(getUserEntry(EMAIL1, settings));

            String groupDn = "cn=" + GROUP_NAME1 + ",cn=groups,dc=example,dc=com";
            MockLdapEntry mg = MockLdapEntry.createGroup(groupDn, GROUP_NAME1, settings);
            wrapper.addGroup(groupDn, mg);

            String groupDn2 = "cn=" + GROUP_NAME2 + ",cn=groups,dc=example,dc=com";
            MockLdapEntry mg2 = MockLdapEntry.createGroup(groupDn2, GROUP_NAME2, settings);
            wrapper.addGroup(groupDn2, mg2);

            wrapper.setGroupMembers(mg, wrapper._users);
            wrapper.setGroupMembers(mg2, wrapper._users);

            return wrapper;
        }
        
        private Container getProject()
        {
            return ContainerManager.getForPath(PROJECT_NAME);
        }

        @After
        public void cleanup() throws Exception
        {
//            cleanUsersAndGroups();
//
//            Container project = getProject();
//            if (project != null)
//            {
//                ContainerManager.delete(project, TestContext.get().getUser());
//            }
        }

        private void cleanUsersAndGroups() throws Exception
        {
            if (getProject() == null)
            {
                return;
            }

            //cleanup users
            for (String email : Arrays.asList(EMAIL1, EMAIL2))
            {
                UserPrincipal up = SecurityManager.getPrincipal(email, getProject());
                if (up != null)
                {
                    _log.info("Cleaning up user: " + up.getUserId());
                    UserManager.deleteUser(up.getUserId());
                }
            }

            //cleanup groups
            for (String groupName : Arrays.asList(GROUP_NAME1, GROUP_NAME2))
            {
                _log.info("Cleaning up group: "+ groupName);
                Group g = GroupManager.getGroup(getProject(), groupName, GroupEnumType.PROJECT);
                if (g != null)
                    SecurityManager.deleteGroup(g);
            }
        }

        private LdapSyncRunner getRunner() throws Exception
        {
            //always use this so we force consistent values, even if this server has custom site-wide settings.
            return getRunner(new MutatableLdapSettings());
        }
        
        private LdapSyncRunner getRunner(LdapSettings settings) throws Exception
        {
            LdapSyncRunner runner = new LdapSyncRunner();
            runner._userGroupContainer = getProject();
            runner._wrapper = getConnectionWrapper(settings);
            runner._settings = settings;
            runner.setPreviewOnly(false);

            return runner;
        }

        @Test
        public void testBasicSync() throws Exception
        {
            LdapSyncRunner runner = getRunner();

            //can call methods here, and test outcome (inspect tracking variables)
            runner.syncAllUsers();
            assertEquals("Incorrect number of users added", 1, runner._usersAdded);
            runner.syncGroupsAndMembers(null);
            assertEquals("Incorrect number of memberships added", 2, runner._membershipsAdded);

            //reset.  expect no inserts since users were created above
            runner = getRunner();
            runner.syncAllUsers();
            assertEquals("Incorrect number of users added", 0, runner._usersAdded);
            runner.syncGroupsAndMembers(null);
            assertEquals("Incorrect number of memberships added", 0, runner._membershipsAdded);

            cleanUsersAndGroups();
        }

        @Test
        public void testCustomUserProperties() throws Exception
        {
            MutatableLdapSettings settings = new MutatableLdapSettings();
            settings.setProperty(DISPLAYNAME_FIELD_PROP, "customDisplayName");
            settings.setProperty(FIRSTNAME_FIELD_PROP, "customFirstName");
            
            LdapSyncRunner runner = getRunner(settings);
            MockLdapEntry user = getUserEntry(EMAIL2, settings);
            final String displayName = "CustomDisplay1";
            final String firstName = "CustomFirstName1";
            user._otherProps.put("customDisplayName", displayName);
            user._otherProps.remove(DEFAULT_DISPLAY_NAME_VAL);

            user._otherProps.put("customFirstName", firstName);
            user._otherProps.remove("givenName");

            assertEquals("Incorrect displayName", displayName, user.getDisplayName());
            assertEquals("Incorrect firstName", firstName, user.getFirstName());

            ((DummyConnectionWrapper)runner._wrapper)._users.clear();
            ((DummyConnectionWrapper)runner._wrapper).addUser(user);

            runner.syncAllUsers();

            User up = UserManager.getUser(user.getValidEmail());
            assertNotNull("User not created: " + EMAIL2, up);
            assertEquals("Incorrect displayName", displayName, up.getDisplayName(TestContext.get().getUser()));
            assertEquals("Incorrect firstName", firstName, up.getFirstName());
        }

        @Test
        public void testIndividualOperations() throws Exception
        {
            //here we just get the first user and test one
            LdapSyncRunner runner = getRunner();
            LdapEntry le = runner._wrapper.listAllUsers().get(0);
            runner.createUser(le);

            //check that the user got created
            UserPrincipal up = SecurityManager.getPrincipal(le.getEmail(), getProject());
            assertNotNull("User was not created", up);

            //create a group
            String groupName = runner._wrapper.listAllGroups().get(0).getDisplayName();
            runner.createGroup(groupName);
            Group g = GroupManager.getGroup(getProject(), groupName, GroupEnumType.PROJECT);
            assertNotNull("Group was not created", g);

            //add member to the group
            runner.addMember(g, up);
            Set<UserPrincipal> existingMembers = SecurityManager.getAllGroupMembers(g, MemberType.ALL_GROUPS_AND_USERS);
            assertTrue("Member not added", existingMembers.contains(up));

            //delete member from group
            runner.deleteMember(g, up);
            existingMembers = SecurityManager.getAllGroupMembers(g, MemberType.ALL_GROUPS_AND_USERS);
            assertFalse("Member not deleted", existingMembers.contains(up));

            cleanUsersAndGroups();
        }

        // This can be used to return LdapEntry objects to support some degree of automated testing without needing a functional LDAP Server
        public class DummyConnectionWrapper extends LdapConnectionWrapper
        {
            private List<LdapEntry> _users = new ArrayList<>();
            private Map<String, MockLdapEntry> _groupMap = new HashMap<>();
            private Map<MockLdapEntry, List<LdapEntry>> _groupMemberMap = new HashMap<>();

            public DummyConnectionWrapper() throws LdapException
            {

            }

            @Override
            protected void init() throws LdapException
            {
                //no-op
            }

            public void addUser(LdapEntry e)
            {
                _users.add(e);
            }
            
            public void addGroup(String dn, MockLdapEntry e)
            {
                _groupMap.put(dn, e);
            }
            
            public void setGroupMembers(MockLdapEntry group, List<LdapEntry> members)
            {
                _groupMemberMap.put(group, members);    
            }
            
            @Override
            public List<LdapEntry> getGroupMembers(String dn)
            {
                return _groupMemberMap.get(_groupMap.get(dn));
            }

            @Override
            public LdapEntry getGroup(String dn)
            {
                return _groupMap.get(dn);
            }

            @Override
            public List<LdapEntry> listAllGroups()
            {
                return new ArrayList<>(_groupMap.values());
            }

            @Override
            public List<LdapEntry> listAllUsers()
            {
                return _users;
            }
        }

        private static class MockLdapEntry extends LdapEntry
        {
            private Dn _dn;

            private Map<String, String> _otherProps = new HashMap<>();

            //represents an LDAP user entry
            public MockLdapEntry(String dn, Map<String, String> otherProps, LdapSettings settings) throws LdapInvalidDnException
            {
                super(settings);
                _dn = new Dn(dn);
                _otherProps.putAll(otherProps);
            }

            public static MockLdapEntry createGroup(String dn, String groupName, LdapSettings settings) throws LdapInvalidDnException
            {
                Map<String, String> props = new HashMap<>();
                props.put(DEFAULT_DISPLAY_NAME_VAL, groupName);

                return new MockLdapEntry(dn, props, settings);
            }

            @Override
            public Dn getDn()
            {
                return _dn;
            }

            @Override
            protected String getAttribute(String alias)
            {
                return _otherProps.get(alias);
            }

            @Override
            public boolean isEnabled()
            {
                return true;
            }
        }
        
        public static class MutatableLdapSettings extends LdapSettings
        {
            public MutatableLdapSettings()
            {
                super();

                //reset these values so we get consistent behavior even if this instance has custom properties
                setProperty(EMAIL_FIELD_PROP, DEFAULT_EMAIL_FIELD_VAL);
                setProperty(DISPLAYNAME_FIELD_PROP, DEFAULT_DISPLAY_NAME_VAL);
                setProperty(UID_FIELD_PROP, DEFAULT_UID_VAL);
                setProperty(PHONE_FIELD_PROP, DEFAULT_PHONE_VAL);
                setProperty(FIRSTNAME_FIELD_PROP, DEFAULT_FIRST_NAME_VAL);
                setProperty(LASTNAME_FIELD_PROP, DEFAULT_FIRST_NAME_VAL);
            }
            
            public void setProperty(String prop, String value)
            {
                getMutableSettings().put(prop, value);
            }

            @Override
            public User getLabKeyAdminUser()
            {
                return TestContext.get().getUser();
            }
        }
    }
}
