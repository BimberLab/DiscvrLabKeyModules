/*
 * Copyright (c) 2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.openldapsync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.commons.lang3.StringUtils;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapOperationException;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.openldapsync.ldap.LdapConnectionWrapper;
import org.labkey.openldapsync.ldap.LdapEntry;
import org.labkey.openldapsync.ldap.LdapSettings;
import org.labkey.openldapsync.ldap.LdapSyncRunner;
import org.springframework.validation.BindException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenLdapSyncController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(OpenLdapSyncController.class);
    public static final String NAME = "openldapsync";

    private static Logger _log = Logger.getLogger(OpenLdapSyncController.class);

    public OpenLdapSyncController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class InitiateLdapSyncAction extends MutatingApiAction<InitiateLdapSyncForm>
    {
        public ApiResponse execute(InitiateLdapSyncForm form, BindException errors) throws Exception
        {
            try
            {
                LdapSyncRunner runner = new LdapSyncRunner();
                if (form.isForPreview())
                    runner.setPreviewOnly(true);

                runner.doSync();

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("messages", runner.getMessages());

                return new ApiSimpleResponse(result);
            }
            catch (LdapException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }
        }
    }

    public static class InitiateLdapSyncForm
    {
        private boolean _forPreview = false;

        public boolean isForPreview()
        {
            return _forPreview;
        }

        public void setForPreview(boolean forPreview)
        {
            _forPreview = forPreview;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ListLdapGroupsAction extends ReadOnlyApiAction<LdapForm>
    {
        public ApiResponse execute(LdapForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            LdapConnectionWrapper wrapper = new LdapConnectionWrapper();

            try
            {
                wrapper.connect();
                List<LdapEntry> groups = wrapper.listAllGroups();
                JSONArray groupsArr = new JSONArray();
                for (LdapEntry e : groups)
                {
                    JSONObject json = new JSONObject();
                    json.put("dn", e.getDn().getName());
                    json.put("name", e.getDisplayName());
                    groupsArr.put(json);
                }
                resp.put("groups", groupsArr);
            }
            catch (LdapException e)
            {
                _log.error(e);
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }
            finally
            {
                wrapper.disconnect();
            }

            return resp;
        }
    }

    @JsonIgnoreProperties({
            "completeUserSearchString",
            "completeGroupSearchString",
            "completeGroupFilterString",
            "completeUserFilterString",
            "completeGroupMemberFilterString"
    })
    public static class LdapForm {
        private String _host;
        private Integer _port;
        private String _principal;
        private String _credentials;
        private boolean _useSSL = false;
        private String _sslProtocol;

        private String _baseSearchString;
        private String _userSearchString;
        private String _groupSearchString;
        private String _userFilterString;
        private String _groupFilterString;
        private String _groupObjectClass;
        private String _userObjectClass;
        private String _groupSyncNameSuffix;

        private String _emailFieldMapping;
        private String _displayNameFieldMapping;
        private String _phoneNumberFieldMapping;
        private String _uidFieldMapping;
        private String _firstNameFieldMapping;
        private String _lastNameFieldMapping;
        private String _imFieldMapping;

        private String _userDeleteBehavior;
        private String _groupDeleteBehavior;
        private String _memberSyncMode;
        private String _userInfoChangedBehavior;
        private String _userAccountControlBehavior;

        private boolean _enabled;
        private Integer _frequency;
        private String _syncMode;
        private String _labkeyAdminEmail;

        private JSONArray _allowedDn;

        public String getHost()
        {
            return _host;
        }

        public void setHost(String host)
        {
            _host = host;
        }

        public Integer getPort()
        {
            return _port;
        }

        public void setPort(Integer port)
        {
            _port = port;
        }

        public String getPrincipal()
        {
            return _principal;
        }

        public void setPrincipal(String principal)
        {
            _principal = principal;
        }

        public String getCredentials()
        {
            return _credentials;
        }

        public void setCredentials(String credentials)
        {
            _credentials = credentials;
        }

        public boolean isUseSSL()
        {
            return _useSSL;
        }

        public void setUseSSL(boolean useSSL)
        {
            _useSSL = useSSL;
        }

        public String getSslProtocol()
        {
            return _sslProtocol;
        }

        public void setSslProtocol(String sslProtocol)
        {
            _sslProtocol = sslProtocol;
        }

        public String getBaseSearchString()
        {
            return _baseSearchString;
        }

        public void setBaseSearchString(String baseSearchString)
        {
            _baseSearchString = baseSearchString;
        }

        public String getUserSearchString()
        {
            return _userSearchString;
        }

        public void setUserSearchString(String userSearchString)
        {
            _userSearchString = userSearchString;
        }

        public String getGroupSearchString()
        {
            return _groupSearchString;
        }

        public void setGroupSearchString(String groupSearchString)
        {
            _groupSearchString = groupSearchString;
        }

        public String getUserFilterString()
        {
            return _userFilterString;
        }

        public void setUserFilterString(String userFilterString)
        {
            _userFilterString = userFilterString;
        }

        public String getGroupFilterString()
        {
            return _groupFilterString;
        }

        public void setGroupFilterString(String groupFilterString)
        {
            _groupFilterString = groupFilterString;
        }

        public String getGroupObjectClass()
        {
            return _groupObjectClass;
        }

        public void setGroupObjectClass(String groupObjectClass)
        {
            _groupObjectClass = groupObjectClass;
        }

        public String getUserObjectClass()
        {
            return _userObjectClass;
        }

        public void setUserObjectClass(String userObjectClass)
        {
            _userObjectClass = userObjectClass;
        }

        public String getGroupSyncNameSuffix()
        {
            return _groupSyncNameSuffix;
        }

        public void setGroupSyncNameSuffix(String groupSyncNameSuffix)
        {
            _groupSyncNameSuffix = groupSyncNameSuffix;
        }

        public String getEmailFieldMapping()
        {
            return _emailFieldMapping;
        }

        public void setEmailFieldMapping(String emailFieldMapping)
        {
            _emailFieldMapping = emailFieldMapping;
        }

        public String getDisplayNameFieldMapping()
        {
            return _displayNameFieldMapping;
        }

        public void setDisplayNameFieldMapping(String displayNameFieldMapping)
        {
            _displayNameFieldMapping = displayNameFieldMapping;
        }

        public String getPhoneNumberFieldMapping()
        {
            return _phoneNumberFieldMapping;
        }

        public void setPhoneNumberFieldMapping(String phoneNumberFieldMapping)
        {
            _phoneNumberFieldMapping = phoneNumberFieldMapping;
        }

        public String getUidFieldMapping()
        {
            return _uidFieldMapping;
        }

        public void setUidFieldMapping(String uidFieldMapping)
        {
            _uidFieldMapping = uidFieldMapping;
        }

        public String getUserDeleteBehavior()
        {
            return _userDeleteBehavior;
        }

        public void setUserDeleteBehavior(String userDeleteBehavior)
        {
            _userDeleteBehavior = userDeleteBehavior;
        }

        public String getGroupDeleteBehavior()
        {
            return _groupDeleteBehavior;
        }

        public void setGroupDeleteBehavior(String groupDeleteBehavior)
        {
            _groupDeleteBehavior = groupDeleteBehavior;
        }

        public String getFirstNameFieldMapping()
        {
            return _firstNameFieldMapping;
        }

        public void setFirstNameFieldMapping(String firstNameFieldMapping)
        {
            _firstNameFieldMapping = firstNameFieldMapping;
        }

        public String getLastNameFieldMapping()
        {
            return _lastNameFieldMapping;
        }

        public void setLastNameFieldMapping(String lastNameFieldMapping)
        {
            _lastNameFieldMapping = lastNameFieldMapping;
        }

        public String getImFieldMapping()
        {
            return _imFieldMapping;
        }

        public void setImFieldMapping(String imFieldMapping)
        {
            _imFieldMapping = imFieldMapping;
        }

        public String getUserInfoChangedBehavior()
        {
            return _userInfoChangedBehavior;
        }

        public void setUserInfoChangedBehavior(String userInfoChangedBehavior)
        {
            _userInfoChangedBehavior = userInfoChangedBehavior;
        }

        public String getUserAccountControlBehavior()
        {
            return _userAccountControlBehavior;
        }

        public void setUserAccountControlBehavior(String userAccountControlBehavior)
        {
            _userAccountControlBehavior = userAccountControlBehavior;
        }

        public Boolean isEnabled()
        {
            return _enabled;
        }

        public void setEnabled(boolean enabled)
        {
            _enabled = enabled;
        }

        public Integer getFrequency()
        {
            return _frequency;
        }

        public void setFrequency(Integer frequency)
        {
            _frequency = frequency;
        }

        public String getLabkeyAdminEmail()
        {
            return _labkeyAdminEmail;
        }

        public void setLabkeyAdminEmail(String labkeyAdminEmail)
        {
            _labkeyAdminEmail = labkeyAdminEmail;
        }

        public JSONArray getAllowedDn()
        {
            return _allowedDn;
        }

        public void setAllowedDn(JSONArray allowedDn)
        {
            _allowedDn = allowedDn;
        }

        public String getSyncMode()
        {
            return _syncMode;
        }

        public void setSyncMode(String syncMode)
        {
            _syncMode = syncMode;
        }

        public String getMemberSyncMode()
        {
            return _memberSyncMode;
        }

        public void setMemberSyncMode(String memberSyncMode)
        {
            _memberSyncMode = memberSyncMode;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class TestLdapConnectionAction extends MutatingApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            LdapConnectionWrapper wrapper = null;

            try
            {
                wrapper = new LdapConnectionWrapper();
                wrapper.connect();
                resp.put("success", true);
            }
            catch (LdapOperationException e)
            {
                _log.error("unable to connect to LDAP server: " + e.getMessage(), e);
                if (e.getResultCode() != null)
                {
                    _log.error("Result code: " + e.getResultCode().name());
                }

                if (e.getResolvedDn() != null)
                {
                    _log.error("DN: " + e.getResolvedDn().getNormName() + " / " + e.getResolvedDn().getName());
                }

                errors.reject(ERROR_MSG, e.getMessage() == null ? "unable to connect to LDAP server" : e.getMessage());
                return null;
            }
            catch (Exception e)
            {
                _log.error(e);
                errors.reject(ERROR_MSG, e.getMessage() == null ? "unable to connect to LDAP server" : e.getMessage());
                return null;
            }
            finally
            {
                if (wrapper != null)
                {
                    wrapper.disconnect();
                }
            }

            return resp;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class GetLdapSettingsAction extends ReadOnlyApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> result = new HashMap<>();

            LdapSettings settings = new LdapSettings();
            Map<String, Object> props = settings.getSettingsMap();
            result.putAll(props);

            result.put("completeUserSearchString", settings.getCompleteUserSearchString());
            result.put("completeGroupSearchString", settings.getCompleteGroupSearchString());

            result.put("completeGroupFilterString", settings.getCompleteGroupFilterString());
            result.put("completeUserFilterString", settings.getCompleteUserFilterString());
            result.put("completeGroupMemberFilterString", settings.getCompleteGroupMemberFilterString("{groupDn}"));

            return new ApiSimpleResponse(result);
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(AdminOperationsPermission.class)
    public class SetLdapSettingsAction extends MutatingApiAction<LdapForm>
    {
        public ApiResponse execute(LdapForm form, BindException errors)
        {
            Map<String, String> props = new HashMap<>();
            Map<String, String> encryptedProps = new HashMap<>();

            //connection
            if (form.getHost() != null)
                props.put(LdapSettings.HOST_PROP, form.getHost());

            if (form.getPort() != null && form.getPort() > 0)
                props.put(LdapSettings.PORT_PROP, String.valueOf(form.getPort()));

            if (form.getPrincipal() != null)
                encryptedProps.put(LdapSettings.PRINCIPAL_PROP, form.getPrincipal());

            if (form.getCredentials() != null)
                encryptedProps.put(LdapSettings.CREDENTIALS_PROP, form.getCredentials());

            props.put(LdapSettings.USE_SSL_PROP, String.valueOf(form.isUseSSL()));

            if (form.getSslProtocol() != null)
                props.put(LdapSettings.SSL_PROTOCOL_PROP, form.getSslProtocol());

            //search strings
            if (form.getBaseSearchString() != null)
                props.put(LdapSettings.BASE_SEARCH_PROP, form.getBaseSearchString());

            if (form.getUserSearchString() != null)
                props.put(LdapSettings.USER_SEARCH_PROP, form.getUserSearchString());

            if (form.getGroupSearchString() != null)
                props.put(LdapSettings.GROUP_SEARCH_PROP, form.getGroupSearchString());

            if (form.getGroupFilterString() != null)
                props.put(LdapSettings.GROUP_FILTER_PROP, form.getGroupFilterString());

            if (form.getGroupObjectClass() != null)
                props.put(LdapSettings.GROUP_OBJECTCLASS_PROP, form.getGroupObjectClass());

            if (form.getUserFilterString() != null)
                props.put(LdapSettings.USER_FILTER_PROP, form.getUserFilterString());

            if (form.getUserObjectClass() != null)
                props.put(LdapSettings.USER_OBJECTCLASS_PROP, form.getUserObjectClass());

            if (form.getGroupSyncNameSuffix() != null)
                props.put(LdapSettings.GROUP_NAME_SUFFIX_PROP, form.getGroupSyncNameSuffix());

            //behaviors
            if (form.getUserDeleteBehavior() != null)
                props.put(LdapSettings.USER_DELETE_PROP, form.getUserDeleteBehavior());

            if (form.getGroupDeleteBehavior() != null)
                props.put(LdapSettings.GROUP_DELETE_PROP, form.getGroupDeleteBehavior());

            if (form.getMemberSyncMode() != null)
                props.put(LdapSettings.MEMBER_SYNC_PROP, form.getMemberSyncMode());

            if (form.getUserInfoChangedBehavior() != null)
                props.put(LdapSettings.USER_INFO_CHANGED_PROP, form.getUserInfoChangedBehavior());

            if (form.getUserAccountControlBehavior() != null)
                props.put(LdapSettings.USERACCOUNTCONTROL_PROP, form.getUserAccountControlBehavior());

            //field mapping
            if (form.getDisplayNameFieldMapping() != null)
                props.put(LdapSettings.DISPLAYNAME_FIELD_PROP, form.getDisplayNameFieldMapping());

            if (form.getFirstNameFieldMapping() != null)
                props.put(LdapSettings.FIRSTNAME_FIELD_PROP, form.getFirstNameFieldMapping());

            if (form.getLastNameFieldMapping() != null)
                props.put(LdapSettings.LASTNAME_FIELD_PROP, form.getLastNameFieldMapping());

            if (form.getImFieldMapping() != null)
                props.put(LdapSettings.IM_FIELD_PROP, form.getImFieldMapping());

            if (form.getEmailFieldMapping() != null)
                props.put(LdapSettings.EMAIL_FIELD_PROP, form.getEmailFieldMapping());

            if (form.getPhoneNumberFieldMapping() != null)
                props.put(LdapSettings.PHONE_FIELD_PROP, form.getPhoneNumberFieldMapping());

            if (form.getUidFieldMapping() != null)
                props.put(LdapSettings.UID_FIELD_PROP, form.getUidFieldMapping());

            //other settings
            if (form.isEnabled() != null)
                props.put(LdapSettings.ENABLED_PROP, form.isEnabled().toString());

            if (form.getFrequency() != null)
                props.put(LdapSettings.FREQUENCY_PROP, form.getFrequency().toString());

            if (form.getSyncMode() != null)
                props.put(LdapSettings.SYNC_MODE_PROP, form.getSyncMode());

            if (form.getAllowedDn() != null && form.getAllowedDn().length() > 0)
            {
                String allowed = StringUtils.join(form.getAllowedDn().toArray(), LdapSettings.DELIM);
                props.put(LdapSettings.ALLOWED_DN_PROP, allowed);
            }

            if (form.getLabkeyAdminEmail() != null)
            {
                props.put(LdapSettings.LABKEY_EMAIL_PROP, form.getLabkeyAdminEmail());
            }

            LdapSettings.setLdapSettings(props, encryptedProps);

            return new ApiSimpleResponse("success", true);
        }
    }
}