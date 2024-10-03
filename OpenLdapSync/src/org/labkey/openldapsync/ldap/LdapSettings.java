package org.labkey.openldapsync.ldap;

import org.apache.commons.lang3.StringUtils;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.ConfigurationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 1/21/13
 * Time: 11:58 AM
 */
public class LdapSettings
{
    public static final String PROPERTY_CATEGORY = "ldk.ldapConfig";
    public static final String PROPERTY_CATEGORY_ENCRYPTED = "ldk.ldapConfigEncrypted";

    public static final String BASE_SEARCH_PROP = "baseSearchString";
    public static final String GROUP_SEARCH_PROP = "groupSearchString";
    public static final String USER_SEARCH_PROP = "userSearchString";
    public static final String GROUP_FILTER_PROP = "groupFilterString";
    public static final String USER_FILTER_PROP = "userFilterString";

    public static final String EMAIL_FIELD_PROP = "emailFieldMapping";
    public static final String DISPLAYNAME_FIELD_PROP = "displayNameFieldMapping";
    public static final String UID_FIELD_PROP = "uidFieldMapping";
    public static final String PHONE_FIELD_PROP = "phoneNumberFieldMapping";
    public static final String FIRSTNAME_FIELD_PROP = "firstNameFieldMapping";
    public static final String LASTNAME_FIELD_PROP = "lastNameFieldMapping";
    public static final String IM_FIELD_PROP = "imFieldMapping";


    public static final String LABKEY_EMAIL_PROP = "labkeyAdminEmail";

    public static final String USER_DELETE_PROP = "userDeleteBehavior";
    public static final String GROUP_DELETE_PROP = "groupDeleteBehavior";
    public static final String USER_INFO_CHANGED_PROP = "userInfoChangedBehavior";
    public static final String USERACCOUNTCONTROL_PROP = "userAccountControlBehavior";

    public static final String GROUP_OBJECTCLASS_PROP = "groupObjectClass";
    public static final String USER_OBJECTCLASS_PROP = "userObjectClass";
    public static final String GROUP_NAME_SUFFIX_PROP = "groupSyncNameSuffix";

    public static final String MEMBER_SYNC_PROP = "memberSyncMode";

    public static final String ENABLED_PROP = "enabled";
    public static final String FREQUENCY_PROP = "frequency";
    public static final String SYNC_MODE_PROP = "syncMode";

    public static final String ALLOWED_DN_PROP = "allowedDn";

    public static final String HOST_PROP = "host";
    public static final String PORT_PROP = "port";
    public static final String PRINCIPAL_PROP = "principal";
    public static final String CREDENTIALS_PROP = "credentials";
    public static final String USE_SSL_PROP = "useSSL";
    public static final String SSL_PROTOCOL_PROP = "sslProtocol";

    public static final String DEFAULT_EMAIL_FIELD_VAL = "mail";
    public static final String DEFAULT_DISPLAY_NAME_VAL = "displayName";
    public static final String DEFAULT_LAST_NAME_VAL = "sn";
    public static final String DEFAULT_FIRST_NAME_VAL = "givenName";
    public static final String DEFAULT_PHONE_VAL = "telephoneNumber";
    public static final String DEFAULT_IM_VAL = "im";
    public static final String DEFAULT_UID_VAL = "userPrincipalName";
    public static final String DEFAULT_USERCLASS_VAL = "user";
    public static final String DEFAULT_GROUPCLASS_VAL = "group";

    public static final String DELIM = "<>";

    private final Map<String, Object> _settings;

    public LdapSettings()
    {
        _settings = createSettingsMap();
    }

    public static void setLdapSettings(Map<String, String> props, Map<String, String> encryptedProps) throws ConfigurationException
    {
        //validate
        String email = props.get(LABKEY_EMAIL_PROP);
        if (email != null)
        {
            try
            {
                ValidEmail e = new ValidEmail(email);
                User u = UserManager.getUser(e);
                if (u == null)
                {
                    throw new ConfigurationException("Unable to find user for admin email: " + email);
                }

                if (!u.hasRootAdminPermission())
                {
                    throw new ConfigurationException("User is not a site admin or does not have root admin permission: " + u.getEmail());
                }
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                throw new ConfigurationException("Improper email: " + email);
            }
        }

        WritablePropertyMap writableProps = PropertyManager.getWritableProperties(PROPERTY_CATEGORY, true);
        writableProps.clear();

        writableProps.putAll(props);
        writableProps.save();

        WritablePropertyMap encryptedWritableProps = PropertyManager.getEncryptedStore().getWritableProperties(PROPERTY_CATEGORY_ENCRYPTED, true);
        encryptedWritableProps.clear();

        encryptedWritableProps.putAll(encryptedProps);
        encryptedWritableProps.save();

        LdapScheduler.get().onSettingsChange();
    }

    public Map<String, Object> getSettingsMap()
    {
        return Collections.unmodifiableMap(_settings);
    }

    public Map<String, Object> createSettingsMap()
    {
        Map<String, Object> ret = new HashMap<>();

        Map<String, String> map = PropertyManager.getProperties(PROPERTY_CATEGORY);
        for (String key : map.keySet())
        {
            if (key.equals(ALLOWED_DN_PROP) && StringUtils.trimToNull(map.get(key)) != null)
            {
                ret.put(key, map.get(key).split(DELIM));
            }
            else if (key.equals(FREQUENCY_PROP) && StringUtils.trimToNull(map.get(key)) != null)
            {
                ret.put(key, Integer.parseInt(map.get(key)));
            }
            else if (key.equals(PORT_PROP) && StringUtils.trimToNull(map.get(key)) != null)
            {
                ret.put(key, Integer.parseInt(map.get(key)));
            }
            else if (key.equals(ENABLED_PROP) && StringUtils.trimToNull(map.get(key)) != null)
            {
                ret.put(key, Boolean.parseBoolean(map.get(key)));
            }
            else if (key.equals(USE_SSL_PROP) && StringUtils.trimToNull(map.get(key)) != null)
            {
                ret.put(key, Boolean.parseBoolean(map.get(key)));
            }
            else
            {
                ret.put(key, map.get(key));
            }
        }

        Map<String, String> encryptedMap = PropertyManager.getEncryptedStore().getProperties(PROPERTY_CATEGORY_ENCRYPTED);
        ret.putAll(encryptedMap);

        if (isMissingOrEmpty(ret, GROUP_OBJECTCLASS_PROP))
            ret.put(GROUP_OBJECTCLASS_PROP, DEFAULT_GROUPCLASS_VAL);

        if (isMissingOrEmpty(ret, USER_OBJECTCLASS_PROP))
            ret.put(USER_OBJECTCLASS_PROP, DEFAULT_USERCLASS_VAL);

        if (isMissingOrEmpty(ret, EMAIL_FIELD_PROP))
            ret.put(EMAIL_FIELD_PROP, DEFAULT_EMAIL_FIELD_VAL);

        if (isMissingOrEmpty(ret, DISPLAYNAME_FIELD_PROP))
            ret.put(DISPLAYNAME_FIELD_PROP, DEFAULT_DISPLAY_NAME_VAL);

        if (isMissingOrEmpty(ret, LASTNAME_FIELD_PROP))
            ret.put(LASTNAME_FIELD_PROP, DEFAULT_LAST_NAME_VAL);

        if (isMissingOrEmpty(ret, FIRSTNAME_FIELD_PROP))
            ret.put(FIRSTNAME_FIELD_PROP, DEFAULT_FIRST_NAME_VAL);

        if (!ret.containsKey(IM_FIELD_PROP))
            ret.put(IM_FIELD_PROP, DEFAULT_IM_VAL);

        if (isMissingOrEmpty(ret, PHONE_FIELD_PROP))
            ret.put(PHONE_FIELD_PROP, DEFAULT_PHONE_VAL);

        if (isMissingOrEmpty(ret, UID_FIELD_PROP))
            ret.put(UID_FIELD_PROP, DEFAULT_UID_VAL);

        boolean useSSL = ret.containsKey(USE_SSL_PROP) ? (Boolean)ret.get(USE_SSL_PROP) : false;
        if (!ret.containsKey(PORT_PROP))
            ret.put(PORT_PROP, (useSSL ? 636 : 389));

        if (isMissingOrEmpty(ret, MEMBER_SYNC_PROP))
            ret.put(MEMBER_SYNC_PROP, MemberSyncMode.noAction.name());

        return ret;
    }

    //for automated testing purposes
    protected Map<String, Object> getMutableSettings()
    {
        return _settings;
    }

    private boolean isMissingOrEmpty(Map<String, Object> ret, String prop)
    {
        return !ret.containsKey(prop) || ret.get(prop) == null || StringUtils.trimToNull(ret.get(prop).toString()) == null;
    }

    public boolean isEnabled()
    {
        return _settings.containsKey(ENABLED_PROP) && (Boolean)_settings.get(ENABLED_PROP);
    }

    public String getUserSearchString()
    {
        return (String)_settings.get(USER_SEARCH_PROP);
    }

    public String getGroupSearchString()
    {
        return (String)_settings.get(GROUP_SEARCH_PROP);
    }

    public String getUserFilterString()
    {
        return (String)_settings.get(USER_FILTER_PROP);
    }

    public String getUserObjectClass()
    {
        return (String)_settings.get(USER_OBJECTCLASS_PROP);
    }

    public String getGroupObjectClass()
    {
        return (String)_settings.get(GROUP_OBJECTCLASS_PROP);
    }

    public String getGroupFilterString()
    {
        return (String)_settings.get(GROUP_FILTER_PROP);
    }

    public String getCompleteUserFilterString(String... extraFilters)
    {
        List<String> filters = new ArrayList<>();
        if (getUserObjectClass() != null)
        {
            filters.add("(objectclass=" + getUserObjectClass() + ")");
        }

        if (getUserFilterString() != null)
        {
            filters.add(getUserFilterString());
        }

        if (extraFilters != null)
        {
           filters.addAll(Arrays.asList(extraFilters));
        }

        return "(&" + StringUtils.join(filters, "") + ")";
    }

    public String getCompleteGroupMemberFilterString(String dn)
    {
        String userFilter = getCompleteUserFilterString();
        return "(&(memberOf=" + dn + ")" + userFilter + ")";
    }

    public String getCompleteGroupFilterString()
    {
        String groupFilter = getGroupFilterString();
        String filter = "(objectclass=" + getGroupObjectClass() + ")";
        if (groupFilter != null)
        {
            filter = "(&" + filter + groupFilter + ")";
        }

        return filter;
    }

    public String getCompleteGroupSearchString()
    {
        StringBuilder sb = new StringBuilder();
        String delim = "";
        String search = getGroupSearchString();

        if (search != null)
        {
            sb.append(search);
            delim = ",";
        }

        String base = getBaseSearchString();
        if (base != null)
        {
            sb.append(delim).append(base);
        }

        return sb.toString();
    }

    public String getCompleteUserSearchString()
    {
        StringBuilder sb = new StringBuilder();
        String delim = "";

        String search = getUserSearchString();
        if (search != null)
        {
            sb.append(search);
            delim = ",";
        }

        String base = getBaseSearchString();
        if (base != null)
        {
            sb.append(delim).append(base);
        }

        return sb.toString();
    }

    public MemberSyncMode getMemberSyncMode()
    {
        if (!_settings.containsKey(MEMBER_SYNC_PROP))
            return null;

        return MemberSyncMode.valueOf((String)_settings.get(MEMBER_SYNC_PROP));
    }

    public String getGroupNameSuffix()
    {
        return (String)_settings.get(GROUP_NAME_SUFFIX_PROP);
    }

    public boolean overwriteUserInfoIfChanged()
    {
        return "true".equals(_settings.get(USER_INFO_CHANGED_PROP));
    }

    public boolean deleteGroupWhenRemovedFromLdap()
    {
        return "delete".equals(_settings.get(GROUP_DELETE_PROP));
    }

    public boolean shouldReadUserAccountControl()
    {
        return "true".equals(_settings.get(USERACCOUNTCONTROL_PROP));
    }

    public boolean deleteUserWhenRemovedFromLdap()
    {
        return "delete".equals(_settings.get(USER_DELETE_PROP));
    }

    public List<String> getGroupWhiteList()
    {
        if (!_settings.containsKey(ALLOWED_DN_PROP))
            return Collections.emptyList();

        return Arrays.asList((String[])_settings.get(ALLOWED_DN_PROP));
    }

    public String getBaseSearchString()
    {
        return (String)_settings.get(BASE_SEARCH_PROP);
    }

    public LdapSyncMode getSyncMode()
    {
        if (!_settings.containsKey(SYNC_MODE_PROP))
            return null;

        return LdapSyncMode.valueOf((String)_settings.get(SYNC_MODE_PROP));
    }

    public enum MemberSyncMode
    {
        mirror(),
        removeDeletedLdapUsers(),
        noAction;

        MemberSyncMode()
        {

        }
    }

    public String getEmailMapping()
    {
        return (String)_settings.get(EMAIL_FIELD_PROP);
    }

    public String getDisplayNameMapping()
    {
        return (String)_settings.get(DISPLAYNAME_FIELD_PROP);
    }

    public String getLastNameMapping()
    {
        return (String)_settings.get(LASTNAME_FIELD_PROP);
    }

    public String getFirstNameMapping()
    {
        return (String)_settings.get(FIRSTNAME_FIELD_PROP);
    }

    public String getPhoneMapping()
    {
        return (String)_settings.get(PHONE_FIELD_PROP);
    }

    public String getIMMapping()
    {
        return (String)_settings.get(IM_FIELD_PROP);
    }

    public String getUIDMapping()
    {
        return (String)_settings.get(UID_FIELD_PROP);
    }

    public String getLabKeyAdminEmail()
    {
        return (String)_settings.get(LABKEY_EMAIL_PROP);
    }

    public User getLabKeyAdminUser()
    {
        String email = getLabKeyAdminEmail();
        if (email == null)
            return null;

        try
        {
            ValidEmail e = new ValidEmail(email);
            User u = UserManager.getUser(e);

            return u;
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            //this should get caught upstream
            return null;
        }
    }

    public Integer getFrequency()
    {
        return _settings.get(FREQUENCY_PROP) == null ? null : (Integer)_settings.get(FREQUENCY_PROP);
    }

    /**
     * Provides a brief sanity check of the settings, designed to identify problems if a sync will run.
     * @throws LdapException
     */
    public void validateSettings() throws LdapException
    {
        String email = getLabKeyAdminEmail();
        if (email == null)
        {
            throw new LdapException("LabKey admin email not set");
        }

        User u = getLabKeyAdminUser();
        if (u == null)
        {
            throw new LdapException("Unable to find user for email: " + email);
        }

        if (!u.hasRootAdminPermission())
        {
            throw new LdapException("User is not a site admin: " + u.getEmail());
        }

        LdapSyncMode mode = getSyncMode();
        if (mode == null)
        {
            throw new LdapException("Sync type not set");
        }

        if (LdapSyncMode.groupWhitelist.equals(mode))
        {
            if (getGroupWhiteList().size() == 0)
            {
                throw new LdapException("Cannot choose to sync based on specific groups unless you provide a list of groups to sync");
            }
        }

        MemberSyncMode memberSyncMode = getMemberSyncMode();
        if (memberSyncMode == null)
        {
            throw new LdapException("Member sync type not set");
        }

        if (isEnabled() && getFrequency() == null)
        {
            throw new LdapException("LDAP sync is enabled, but no scheduling frequency was set");
        }
    }

    public enum LdapSyncMode
    {
        usersOnly(),
        usersAndGroups(),
        groupWhitelist();
        //groupBlacklist();

        LdapSyncMode()
        {

        }
    }

    public String getLdapHost()
    {
        return (String)_settings.get(HOST_PROP);
    }

    public Integer getLdapPort()
    {
        return (Integer)_settings.get(PORT_PROP);
    }

    public String getCredentials()
    {
        return (String)_settings.get(CREDENTIALS_PROP);
    }

    public String getPrincipal()
    {
        return (String)_settings.get(PRINCIPAL_PROP);
    }

    public boolean isUseSSL()
    {
        return _settings.containsKey(USE_SSL_PROP) ? (Boolean)_settings.get(USE_SSL_PROP) : false;
    }

    public String getSslProtocol()
    {
        return (String)_settings.get(SSL_PROTOCOL_PROP);
    }
}
