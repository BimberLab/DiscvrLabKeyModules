/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.ogasync;

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.JobRunner;
import org.labkey.api.view.ActionURL;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.HashMap;
import java.util.Map;

public class OGASyncController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(OGASyncController.class);

    public OGASyncController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminPermission.class)
    public class RunEtlAction extends RedirectAction<Object>
    {
        public boolean doAction(Object form, BindException errors) throws Exception
        {
            try
            {
                JobRunner.getDefault().execute(new Runnable(){
                    public void run()
                    {
                        new OGASyncRunner().run();
                    }
                });
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }

            return true;
        }

        public void validateCommand(Object form, Errors errors)
        {
            try
            {
                OGASyncManager.get().validateSettings();
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
        }

        public ActionURL getSuccessURL(Object form)
        {
            return DetailsURL.fromString("/ogaSync/begin.view", getContainer()).getActionURL();
        }
    }

    @RequiresPermission(AdminPermission.class)
    @CSRF
    public class SetEtlDetailsAction extends ApiAction<EtlAdminForm>
    {
        public ApiResponse execute(EtlAdminForm form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();

            PropertyManager.PropertyMap configMap = PropertyManager.getWritableProperties(OGASyncManager.CONFIG_PROPERTY_DOMAIN, true);

            if (form.getLabkeyUser() != null)
            {
                try
                {
                    new ValidEmail(form.getLabkeyUser());
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    errors.reject(ERROR_MSG, "Invalid email: " + e.getMessage());
                    return null;
                }

                configMap.put(OGASyncManager.LABKEY_USER_PROP_NAME, form.getLabkeyUser());
            }

            if (form.getLabkeyContainer() != null)
            {
                if (ContainerManager.getForPath(form.getLabkeyContainer()) == null)
                {
                    errors.reject(ERROR_MSG, "Invalid container: " + form.getLabkeyContainer());
                    return null;
                }

                configMap.put(OGASyncManager.LABKEY_CONTAINER_PROP_NAME, form.getLabkeyContainer());
            }

            configMap.put(OGASyncManager.DATA_SOURCE_PROP_NAME, form.getDataSourceName());
            configMap.put(OGASyncManager.SCHEMA_PROP_NAME, form.getSchemaName());
            configMap.put(OGASyncManager.OGA_QUERY_PROP_NAME, form.getOgaQueryName());
            configMap.put(OGASyncManager.ALL_QUERY_PROP_NAME, form.getAllQueryName());

            if (form.getHourOfDay() != null)
            {
                if (form.getHourOfDay() > 23 || form.getHourOfDay() < -1)
                {
                    errors.reject(ERROR_MSG, "Hour of day must be between 0-23");
                    return null;
                }

                configMap.put(OGASyncManager.HOUR_PROP_NAME, form.getHourOfDay().toString());
            }

            if (form.getEtlStatus() != null)
                configMap.put(OGASyncManager.ENABLED_PROP_NAME, form.getEtlStatus().toString());

            configMap.save();

            //if config was changed and the ETL is current scheduled to run, we need to restart it
            OGASyncManager.get().onSettingsChange();

            resultProperties.put("success", true);

            return new ApiSimpleResponse(resultProperties);
        }
    }

    public static class EtlAdminForm
    {
        private Boolean _etlStatus;
        private String _labkeyUser;
        private String _labkeyContainer;
        private Integer _hourOfDay;
        private String _dataSourceName;
        private String _schemaName;
        private String _ogaQueryName;
        private String _allQueryName;

        public Boolean getEtlStatus()
        {
            return _etlStatus;
        }

        public void setEtlStatus(Boolean etlStatus)
        {
            _etlStatus = etlStatus;
        }

        public String getLabkeyUser()
        {
            return _labkeyUser;
        }

        public void setLabkeyUser(String labkeyUser)
        {
            _labkeyUser = labkeyUser;
        }

        public String getLabkeyContainer()
        {
            return _labkeyContainer;
        }

        public void setLabkeyContainer(String labkeyContainer)
        {
            _labkeyContainer = labkeyContainer;
        }

        public Integer getHourOfDay()
        {
            return _hourOfDay;
        }

        public void setHourOfDay(Integer hourOfDay)
        {
            _hourOfDay = hourOfDay;
        }

        public String getDataSourceName()
        {
            return _dataSourceName;
        }

        public void setDataSourceName(String dataSourceName)
        {
            _dataSourceName = dataSourceName;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getOgaQueryName()
        {
            return _ogaQueryName;
        }

        public void setOgaQueryName(String ogaQueryName)
        {
            _ogaQueryName = ogaQueryName;
        }

        public String getAllQueryName()
        {
            return _allQueryName;
        }

        public void setAllQueryName(String allQueryName)
        {
            _allQueryName = allQueryName;
        }
    }

    @RequiresPermission(AdminPermission.class)
    @CSRF
    public class GetEtlDetailsAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();
            resultProperties.put("enabled", OGASyncManager.get().isEnabled());
            resultProperties.put("lastRun", OGASyncManager.get().getLastRun());
            resultProperties.put("nextRun", OGASyncManager.get().getNextRun());

            String[] etlConfigKeys = {OGASyncManager.LABKEY_USER_PROP_NAME, OGASyncManager.LABKEY_CONTAINER_PROP_NAME, OGASyncManager.DATA_SOURCE_PROP_NAME, OGASyncManager.SCHEMA_PROP_NAME, OGASyncManager.OGA_QUERY_PROP_NAME, OGASyncManager.ALL_QUERY_PROP_NAME, OGASyncManager.HOUR_PROP_NAME};

            resultProperties.put("configKeys", etlConfigKeys);
            resultProperties.put("config", PropertyManager.getProperties(OGASyncManager.CONFIG_PROPERTY_DOMAIN));

            return new ApiSimpleResponse(resultProperties);
        }
    }
}