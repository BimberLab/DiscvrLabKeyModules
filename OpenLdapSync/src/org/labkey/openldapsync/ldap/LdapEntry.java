package org.labkey.openldapsync.ldap;

import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapInvalidAttributeValueException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.security.ValidEmail;

import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 1/21/13
 * Time: 8:24 AM
 */
public class LdapEntry
{
    private static final Logger _log = Logger.getLogger(LdapEntry.class);

    private Entry _entry;
    private LdapSettings _settings;
    private Map<String, Object> _attributes = new HashMap<>();

    protected LdapEntry()
    {
        //testing purposes only
    }

    public LdapEntry(Entry e, LdapSettings settings)
    {
        _entry = e;
        _settings = settings;

        //TODO: convert most to fields, also allow site config to map field names to concepts
        _attributes.put("dn", e.getDn().getName());
        _attributes.put("description", getAttributeSafe(e, "description"));
        _attributes.put("name", getAttributeSafe(e, "name"));

        _attributes.put("displayName", getAttributeSafe(e, "displayName"));
        _attributes.put("givenName", getAttributeSafe(e, "givenName"));
        _attributes.put("sn", getAttributeSafe(e, "sn"));
        _attributes.put("mail", getAttributeSafe(e, "mail"));

        _attributes.put("sAMAccountName", getAttributeSafe(e, "sAMAccountName"));
        _attributes.put("objectSid", getAttributeSafe(e, "objectSid"));
        _attributes.put("objectGUID", getAttributeSafe(e, "objectGUID"));
    }

    private String getAttributeSafe(Entry e, String attrName)
    {
        try
        {
            Attribute att = e.get(attrName);
            return att == null ? null : att.getString();
        }
        catch (LdapInvalidAttributeValueException ex)
        {
            //ignore?
        }

        return null;
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject(_attributes);
        return json;
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
        try
        {
            Attribute a = _entry.get(_settings.getEmailMapping());
            return a == null ? null : a.getString();
        }
        catch (LdapInvalidAttributeValueException e)
        {
            //not sure what's best here
        }
        return null;
    }

    public boolean isEnabled()
    {
        if (!_settings.shouldReadUserAccountControl())
            return true;

        try
        {
            Attribute a = _entry.get("userAccountControl");
            if (a == null || a.getString() == null)
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
                Integer value = Integer.parseInt(a.getString());
                return (value.intValue() & 2) == 0;
            }
            catch (NumberFormatException e)
            {
                _log.error("Invalid value for userAccountControl: " + a.getString() + " for user: " + getEmail());
                return false;
            }
        }
        catch (LdapInvalidAttributeValueException e)
        {
            //not sure what's best here
        }
        return true;
    }

    public String getDisplayName()
    {
        try
        {
            Attribute a = _entry.get(_settings.getDisplayNameMapping());
            if (a == null)
                a = _entry.get("name");

            return a == null ? null : a.getString();
        }
        catch (LdapInvalidAttributeValueException e)
        {
            //not sure what's best here
        }
        return null;
    }

    public String getLastName() throws LdapInvalidAttributeValueException
    {
        try
        {
            Attribute a = _entry.get(_settings.getLastNameMapping());
            return a == null ? null : a.getString();
        }
        catch (LdapInvalidAttributeValueException e)
        {
            //not sure what's best here
        }
        return null;
    }

    public String getFirstName() throws LdapInvalidAttributeValueException
    {
        try
        {
            Attribute a = _entry.get(_settings.getFirstNameMapping());
            return a == null ? null : a.getString();
        }
        catch (LdapInvalidAttributeValueException e)
        {
            //not sure what's best here
        }
        return null;
    }

    public String getPhone() throws LdapInvalidAttributeValueException
    {
        try
        {
            Attribute a = _entry.get(_settings.getPhoneMapping());
            return a == null ? null : a.getString();
        }
        catch (LdapInvalidAttributeValueException e)
        {
            //not sure what's best here
        }
        return null;
    }

    public String getUID() throws LdapInvalidAttributeValueException
    {
        try
        {
            Attribute a = _entry.get(_settings.getUIDMapping());
            return a == null ? null : a.getString();
        }
        catch (LdapInvalidAttributeValueException e)
        {
            //not sure what's best here
        }
        return null;
    }
}
