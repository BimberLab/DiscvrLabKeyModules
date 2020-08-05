/*
 * Copyright (c) 2015 LabKey Corporation
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

package org.labkey.omerointegration;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

public class OmeroIntegrationController extends SpringActionController
{
    private final static Logger _log = LogManager.getLogger(OmeroIntegrationController.class);
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(OmeroIntegrationController.class);
    public static final String NAME = "omerointegration";

    public OmeroIntegrationController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminPermission.class)
    public class GetSettingsAction extends ReadOnlyApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();
            resultProperties.put("properties", PropertyManager.getEncryptedStore().getProperties(getContainer(), OmeroIntegrationManager.CONFIG_PROPERTY_DOMAIN));

            return new ApiSimpleResponse(resultProperties);
        }
    }

    @RequiresSiteAdmin
    public class SetSettingsAction extends MutatingApiAction<SettingsForm>
    {
        public ApiResponse execute(SettingsForm form, BindException errors)
        {
            Map<String, String> configMap = new HashMap<>();
            if (form.getOmeroUrl() != null)
                configMap.put(OmeroIntegrationManager.OMERO_URL, form.getOmeroUrl());

            if (form.getOmeroUserName() != null)
                configMap.put(OmeroIntegrationManager.OMERO_USERNAME, form.getOmeroUserName());

            if (form.getOmeroPassword() != null)
                configMap.put(OmeroIntegrationManager.OMERO_PASSWORD, form.getOmeroPassword());

            try
            {
                OmeroIntegrationManager.get().saveSettings(getContainer(), configMap);
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class SettingsForm
    {
        private String _omeroUrl;
        private String _omeroUserName;
        private String _omeroPassword;

        public String getOmeroUrl()
        {
            return _omeroUrl;
        }

        public void setOmeroUrl(String omeroUrl)
        {
            _omeroUrl = omeroUrl;
        }

        public String getOmeroUserName()
        {
            return _omeroUserName;
        }

        public void setOmeroUserName(String omeroUserName)
        {
            _omeroUserName = omeroUserName;
        }

        public String getOmeroPassword()
        {
            return _omeroPassword;
        }

        public void setOmeroPassword(String omeroPassword)
        {
            _omeroPassword = omeroPassword;
        }
    }

    public static class OmeroForm
    {
        private String _omeroId;

        public String getOmeroId()
        {
            return _omeroId;
        }

        public void setOmeroId(String omeroId)
        {
            _omeroId = omeroId;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @IgnoresTermsOfUse
    public class DownloadThumbnailAction extends ExportAction<OmeroForm>
    {
        public void export(OmeroForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            OmeroServer s = new OmeroServer(getContainer());
            try
            {
                s.validateSettings();
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return;
            }

            try
            {
                s.getThumbnail(form.getOmeroId(), response);
            }
            catch (Exception e)
            {
                _log.error(e.getMessage(), e);
                errors.reject(ERROR_MSG, e.getMessage());
            }
        }
    }
}