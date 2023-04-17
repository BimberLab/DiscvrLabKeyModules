package org.labkey.openldapsync.ldap;

import org.apache.commons.lang3.StringUtils;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.cursor.SearchCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapInvalidDnException;
import org.apache.directory.api.ldap.model.message.SearchRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.ldap.client.api.DefaultPoolableLdapConnectionFactory;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 1/20/13
 * Time: 9:00 PM
 */
public class LdapConnectionWrapper
{
    private DefaultPoolableLdapConnectionFactory _pool = null;
    private LdapConnectionConfig _cfg = null;
    private LdapConnection _connection = null;
    private LdapSettings _settings;
    private static final Logger _log = LogManager.getLogger(LdapConnectionWrapper.class);
    private boolean doLog = false;

    public LdapConnectionWrapper() throws LdapException
    {
        init();
    }

    protected void init() throws LdapException
    {
        try
        {
            _settings = new LdapSettings();
            _cfg = getLdapConnectionConfig();
            _pool = new DefaultPoolableLdapConnectionFactory(_cfg);
        }
        catch (Exception e)
        {
            throw new LdapException(e);
        }
    }

    public boolean connect() throws LdapException
    {
        _connection = getConnection();

        return true;
    }

    private void ensureConnected() throws LdapException
    {
        if (_connection == null)
            throw new LdapException("Either connect() was not called, or the connection has been nulled");

        if (!_connection.isConnected())
            throw new LdapException("The connection is not connected");

        if (!_connection.isAuthenticated())
            throw new LdapException("The connect has not authenticated");
    }

    public void disconnect() throws LdapException
    {
        if (_connection != null)
        {
            try
            {
                _connection.close();
            }
            catch (IOException e)
            {
                throw new LdapException(e);
            }
        }
    }

    private LdapConnection getConnection() throws LdapException
    {
        try
        {
            LdapConnection connection = _pool.makeObject().getObject();
            connection.bind();

            if (!connection.isConnected())
                throw new LdapException("The connection is not connected");

            if (!connection.isAuthenticated())
                throw new LdapException("The connect has not authenticated");

            return connection;
        }
        catch (Exception e)
        {
            throw new LdapException(e);
        }
    }

    private void possiblyLog(String msg)
    {
        if (doLog)
        {
            _log.info(msg);
        }
    }

    Boolean _isMemberOfSupported = null;

    public boolean isMemberOfSupported() throws LdapException
    {
        if (_isMemberOfSupported == null)
        {
            ensureConnected();

            //if this query returns any records, assume memberOf is supported on this server
            boolean hasResult = false;
            SearchRequestImpl sr = new SearchRequestImpl();
            sr.setBase(new Dn(_settings.getCompleteUserSearchString()));
            sr.setFilter("(memberOf=*)");
            sr.setScope(SearchScope.SUBTREE);
            sr.setSizeLimit(1L);
            try (SearchCursor cursor = _connection.search(sr))
            {
                while (cursor.next())
                {
                    hasResult = true;
                    break;
                }

            }
            catch (Exception e)
            {
                throw new LdapException(e);
            }

            _isMemberOfSupported = hasResult;
        }

        return _isMemberOfSupported;
    }

    public List<LdapEntry> getGroupMembers(String dn) throws LdapException
    {
        return isMemberOfSupported() ? getGroupMembersUsingMemberOf(dn) : getGroupMembersWithoutMemberOf(dn);
    }

    private List<LdapEntry> getGroupMembersUsingMemberOf(String dn) throws LdapException
    {
        ensureConnected();

        try
        {
            try (EntryCursor cursor = _connection.search(_settings.getCompleteUserSearchString(), _settings.getCompleteGroupMemberFilterString(dn), SearchScope.SUBTREE, "*"))
            {
                List<LdapEntry> users = new ArrayList<>();
                while (cursor.next())
                {
                    Entry entry = cursor.get();
                    users.add(new LdapEntry(entry, _settings));
                }

                return users;
            }
        }
        catch (Exception e)
        {
            throw new LdapException(e);
        }
    }

    /**
     * Gets the group members using the memberUid attribute.
     * This method is necessary if the LDAP server has no memberOf group overlay, and so instead we need to iterate
     * through what the LDAP search returns and find the memberUid attribute (which are the users) in each group
     */
    public List<LdapEntry> getGroupMembersWithoutMemberOf(String dn) throws LdapException
    {
        String filter = "(objectclass=" + _settings.getGroupObjectClass() + ")";
        try (EntryCursor cursor = _connection.search(new Dn(dn), filter, SearchScope.SUBTREE, "member", "memberUid"))
        {
            List<LdapEntry> users = new ArrayList<>();
            Set<String> userIds = new HashSet<>();

            //iterate through the results from LDAP and gather userIds
            while (cursor.next())
            {
                Entry group = cursor.get();
                Iterator<Attribute> attributeIterator = group.getAttributes().iterator();

                while (attributeIterator.hasNext())
                {
                    Attribute a = attributeIterator.next();
                    if ("memberUid".equalsIgnoreCase(a.getId()))
                    {
                        a.forEach((val) -> {
                            userIds.add("(uid=" + val + ")");
                        });

                    }
                    else if ("member".equalsIgnoreCase(a.getId()))
                    {
                        a.forEach((val) -> {
                            try
                            {
                                Rdn rdn = new Dn(val.toString()).getRdn();
                                if (!"cn".equalsIgnoreCase(rdn.getType()))
                                {
                                    _log.error("Member attribute was not CN: " + val + ".  was: " + rdn.getType());
                                    return;
                                }

                                userIds.add("(" + rdn.getName() + ")");
                            }
                            catch (LdapInvalidDnException e)
                            {
                                _log.error("Invalid DN for member attribute: " + val);
                            }

                        });
                    }
                    else
                    {
                        _log.error("Unknown attribute: " + a.getId());
                        continue;
                    }
                }
            }

            if (!userIds.isEmpty())
            {
                String filterUsers = "(|".concat(StringUtils.join(userIds, "")).concat(")");
                String userFilter = _settings.getCompleteUserFilterString(filterUsers);
                try (EntryCursor LDAPUserEntry = _connection.search(_settings.getCompleteUserSearchString(), userFilter, SearchScope.SUBTREE, "*"))
                {
                    while (LDAPUserEntry.next())
                    {
                        users.add(new LdapEntry(LDAPUserEntry.get(), _settings));
                    }
                }
            }

            return users;
        }
        catch (Exception e)
        {
            throw new LdapException(e);
        }
    }

    public LdapEntry getGroup(String dn) throws LdapException
    {
        return getEntry(dn, _settings.getCompleteGroupFilterString());
    }

    private LdapEntry getEntry(String dn, String filter) throws LdapException
    {
        ensureConnected();

        possiblyLog("LDAP getEntry: from " + dn + ", filter: " + filter);
        try (EntryCursor cursor = _connection.search(dn, filter, SearchScope.OBJECT, "*"))
        {
            while (cursor.next())
            {
                Entry entry = cursor.get();
                if (entry != null)
                    return new LdapEntry(entry, _settings);
            }

            return null;
        }
        catch (Exception e)
        {
            throw new LdapException(e);
        }
    }

    /**
     * List all groups, based on the baseSearch and groupSearch strings, which are defined in the LDAP config
     */
    public List<LdapEntry> listAllGroups() throws LdapException
    {
        ensureConnected();

        return getChildren(new Dn(_settings.getCompleteGroupSearchString()), _settings.getCompleteGroupFilterString(), _settings.getGroupObjectClass());
    }

    /**
     * List all users, based on the baseSearch and userSearch strings, which are defined in the LDAP config
     */
    public List<LdapEntry> listAllUsers() throws LdapException
    {
        ensureConnected();
        return getChildren(new Dn(_settings.getCompleteUserSearchString()), _settings.getCompleteUserFilterString(), _settings.getUserObjectClass());
    }

    private List<LdapEntry> getChildren(Dn dn, String filter, String objectClass) throws LdapException
    {
        return getChildren(dn, filter, objectClass, new HashSet<>());
    }

    private List<LdapEntry> getChildren(Dn dn, String filter, String expectedObjectClass, HashSet<String> encountered) throws LdapException
    {
        List<LdapEntry> entries = new ArrayList<>();

        possiblyLog("LDAP getChildren: from " + dn + ", filter: " + filter);
        try (EntryCursor cursor = _connection.search(dn, filter, SearchScope.SUBTREE, "*"))
        {
            while (cursor.next())
            {
                Entry entry = cursor.get();

                if (entry.hasObjectClass(expectedObjectClass))
                {
                    entries.add(new LdapEntry(entry, _settings));
                }

                String key = entry.getDn().getName();
                if (encountered.contains(key))
                {
                    _log.info("previously encountered: " + key);
                    continue;
                }
                encountered.add(entry.getDn().getName());
            }
        }
        catch (Exception e)
        {
            throw new LdapException(e);
        }

        return entries;
    }

    public String getProviderName()
    {
        return _cfg.getLdapHost();
    }

    public LdapConnectionConfig getLdapConnectionConfig() throws LdapException
    {
        if (_settings.getLdapHost() == null)
            throw new LdapException("No value for LDAP host was provided");

        LdapConnectionConfig cfg = new LdapConnectionConfig();
        cfg.setLdapHost(_settings.getLdapHost());

        if (_settings.getLdapPort() != null)
            cfg.setLdapPort(_settings.getLdapPort());

        if (_settings.getCredentials() != null)
            cfg.setCredentials(_settings.getCredentials());

        if (_settings.getPrincipal() != null)
            cfg.setName(_settings.getPrincipal());

        if (_settings.isUseSSL())
            cfg.setUseSsl(_settings.isUseSSL());

        if (_settings.getSslProtocol() != null)
            cfg.setSslProtocol(_settings.getSslProtocol());

        //NOTE: should we also throw if missing user or credentials?   perhaps your server allows anonymous lookups?
        if (cfg.isUseSsl() && cfg.getSslProtocol() == null)
            cfg.setSslProtocol(LdapConnectionConfig.DEFAULT_SSL_PROTOCOL);

        if (cfg.getLdapPort() == 0)
            cfg.setLdapPort(cfg.isUseSsl() ? LdapConnectionConfig.DEFAULT_LDAPS_PORT : LdapConnectionConfig.DEFAULT_LDAP_PORT);

        return cfg;
    }

    public void setDoLog(boolean doLog)
    {
        this.doLog = doLog;
    }
}
