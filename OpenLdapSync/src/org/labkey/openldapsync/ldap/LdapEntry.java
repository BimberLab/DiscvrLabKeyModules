package org.labkey.openldapsync.ldap;

import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapInvalidAttributeValueException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.security.ValidEmail;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 1/21/13
 * Time: 8:24 AM
 */
public class LdapEntry
{
    private static final Logger _log = LogManager.getLogger(LdapEntry.class);

    private Entry _entry;
    protected LdapSettings _settings;

    //testing purposes only
    protected LdapEntry(LdapSettings settings)
    {
        _settings = settings;
    }

    public LdapEntry(Entry e, LdapSettings settings)
    {
        _entry = e;
        _settings = settings;
    }

    public ValidEmail getValidEmail()
    {
        try
        {
            return new ValidEmail(getEmail());
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            //ignore
        }
        return null;
    }

    public Dn getDn()
    {
        return _entry.getDn();
    }

    public String getEmail()
    {
        return getAttribute(_settings.getEmailMapping());
    }

    public boolean isEnabled()
    {
        if (!_settings.shouldReadUserAccountControl())
            return true;

        String a = getAttribute("userAccountControl");
        if (a == null)
        {
            //only report this error for users.
            if (_entry.hasObjectClass(_settings.getUserObjectClass()))
            {
                _log.info("Unable to determine if LDAP user is active, lacked userAccountControl attribute: " + getDisplayName() + " / " + getDn());
            }

            return true;
        }

        try
        {
            Integer value = Integer.parseInt(a);
            return (value.intValue() & 2) == 0;
        }
        catch (NumberFormatException e)
        {
            _log.error("Invalid value for userAccountControl: " + a + " for user: " + getEmail());
            return false;
        }
    }

    public String getDisplayName()
    {
        String a = getAttribute(_settings.getDisplayNameMapping());
        if (a == null)
            a = getAttribute("name");

        return a;
    }

    public String getLastName()
    {
        return getAttribute(_settings.getLastNameMapping());
    }

    public String getFirstName()
    {
        return getAttribute(_settings.getFirstNameMapping());
    }

    public String getPhone()
    {
        return getAttribute(_settings.getPhoneMapping());
    }

    public String getUID()
    {
        return getAttribute(_settings.getUIDMapping());
    }

    public String getIM() throws LdapInvalidAttributeValueException
    {
        try
        {
            Attribute a = _entry.get(_settings.getIMMapping());
            return a == null ? null : a.getString();
        }
        catch (LdapInvalidAttributeValueException e)
        {
            //not sure what's best here
        }
        return null;
    }

    protected String getAttribute(String alias)
    {
        try
        {
            Attribute a = _entry.get(alias);
            return a == null ? null : a.getString();
        }
        catch (LdapInvalidAttributeValueException e)
        {
            _log.error("Invalid LDAP attribute for: " + alias, e);
        }

        return null;
    }
}
