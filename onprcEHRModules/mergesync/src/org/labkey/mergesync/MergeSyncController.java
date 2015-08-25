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

package org.labkey.mergesync;

import org.apache.commons.beanutils.ConversionException;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
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

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MergeSyncController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(MergeSyncController.class);
    public static final String NAME = "mergesync";
    
    public MergeSyncController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminPermission.class)
    @CSRF
    public class SetEtlDetailsAction extends ApiAction<EtlAdminForm>
    {
        public ApiResponse execute(EtlAdminForm form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();

            PropertyManager.PropertyMap configMap = PropertyManager.getWritableProperties(MergeSyncManager.CONFIG_PROPERTY_DOMAIN, true);

            configMap.put(MergeSyncManager.DATA_SOURCE_PROP_NAME, form.getDataSourceName());
            configMap.put(MergeSyncManager.SCHEMA_PROP_NAME, form.getSchemaName());
            configMap.put(MergeSyncManager.MERGE_USER_PROP_NAME, form.getMergeUserName());
            if (form.getLastRun() != null)
            {
                try
                {
                    Date lastRun = ConvertHelper.convert(form.getLastRun(), Date.class);

                    configMap.put(MergeSyncManager.LAST_RUN_PROP_NAME, ((Long)lastRun.getTime()).toString());
                }
                catch (ConversionException e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                    return null;
                }
            }
            configMap.put(MergeSyncManager.SYNC_INTERVAL_PROP_NAME, form.getSyncInterval() == null ? null : form.getSyncInterval().toString());

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

                configMap.put(MergeSyncManager.LABKEY_USER_PROP_NAME, form.getLabkeyUser());
            }

            if (form.getLabkeyContainer() != null)
            {
                if (ContainerManager.getForPath(form.getLabkeyContainer()) == null)
                {
                    errors.reject(ERROR_MSG, "Invalid container: " + form.getLabkeyContainer());
                    return null;
                }

                configMap.put(MergeSyncManager.LABKEY_CONTAINER_PROP_NAME, form.getLabkeyContainer());
            }

            if (form.getPullEnabled() != null)
                configMap.put(MergeSyncManager.PULL_ENABLED_PROP_NAME, form.getPullEnabled().toString());

            if (form.getPushEnabled() != null)
                configMap.put(MergeSyncManager.PUSH_ENABLED_PROP_NAME, form.getPushEnabled().toString());

            if (form.getSyncAnimalsAndProjects() != null)
                configMap.put(MergeSyncManager.SYNC_ANIMALS_PROP_NAME, form.getSyncAnimalsAndProjects().toString());

            configMap.save();

            //if config was changed and the ETL is current scheduled to run, we need to restart it
            MergeSyncManager.get().onSettingsChange();

            resultProperties.put("success", true);

            return new ApiSimpleResponse(resultProperties);
        }
    }

    public static class EtlAdminForm
    {
        private Boolean _pullEnabled;
        private Boolean _pushEnabled;
        private Boolean _syncAnimalsAndProjects;
        private String _dataSourceName;
        private String _schemaName;
        private String _labkeyUser;
        private String _lastRun;
        private String _labkeyContainer;
        private Integer _syncInterval;
        private String _mergeUserName;

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

        public Boolean getPullEnabled()
        {
            return _pullEnabled;
        }

        public void setPullEnabled(Boolean pullEnabled)
        {
            _pullEnabled = pullEnabled;
        }

        public Boolean getPushEnabled()
        {
            return _pushEnabled;
        }

        public void setPushEnabled(Boolean pushEnabled)
        {
            _pushEnabled = pushEnabled;
        }

        public Boolean getSyncAnimalsAndProjects()
        {
            return _syncAnimalsAndProjects;
        }

        public void setSyncAnimalsAndProjects(Boolean syncAnimalsAndProjects)
        {
            _syncAnimalsAndProjects = syncAnimalsAndProjects;
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

        public Integer getSyncInterval()
        {
            return _syncInterval;
        }

        public void setSyncInterval(Integer syncInterval)
        {
            _syncInterval = syncInterval;
        }

        public String getLastRun()
        {
            return _lastRun;
        }

        public void setLastRun(String lastRun)
        {
            _lastRun = lastRun;
        }

        public String getMergeUserName()
        {
            return _mergeUserName;
        }

        public void setMergeUserName(String mergeUserName)
        {
            _mergeUserName = mergeUserName;
        }
    }

    @RequiresPermission(AdminPermission.class)
    @CSRF
    public class GetEtlDetailsAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();
            resultProperties.put("pullEnabled", MergeSyncManager.get().isPullEnabled());
            resultProperties.put("pushEnabled", MergeSyncManager.get().isPushEnabled());
            resultProperties.put("lastRun", MergeSyncManager.get().getLastRun());
            resultProperties.put("nextRun", MergeSyncManager.get().getNextRun());
            resultProperties.put("syncAnimalsAndProjects", MergeSyncManager.get().doSyncAnimalsAndProjects());

            String[] etlConfigKeys = {MergeSyncManager.DATA_SOURCE_PROP_NAME, MergeSyncManager.SCHEMA_PROP_NAME, MergeSyncManager.LABKEY_CONTAINER_PROP_NAME, MergeSyncManager.LABKEY_USER_PROP_NAME, MergeSyncManager.SYNC_INTERVAL_PROP_NAME, MergeSyncManager.MERGE_USER_PROP_NAME};

            resultProperties.put("configKeys", etlConfigKeys);
            resultProperties.put("config", PropertyManager.getProperties(MergeSyncManager.CONFIG_PROPERTY_DOMAIN));

            return new ApiSimpleResponse(resultProperties);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class PullFromMergeAction extends RedirectAction<Object>
    {
        public boolean doAction(Object form, BindException errors) throws Exception
        {
            try
            {
                JobRunner.getDefault().execute(new Runnable(){
                    public void run()
                    {
                        new MergeSyncRunner().pullResultsFromMerge();
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
                MergeSyncManager.get().validateSettings();
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
        }

        public ActionURL getSuccessURL(Object form)
        {
            return DetailsURL.fromString("/mergeSync/begin.view", getContainer()).getActionURL();
        }
    }

    @RequiresPermission(AdminPermission.class)
    @CSRF
    public class ResyncRunsAction extends ApiAction<ResyncRunsForm>
    {
        public ApiResponse execute(ResyncRunsForm form, BindException errors)
        {
            if (form.getPks() == null || form.getPks().length == 0)
            {
                errors.reject(ERROR_MSG, "Must supply a list of PKs to sync");
                return null;
            }

            MergeSyncRunner runner = new MergeSyncRunner();
            try
            {
                for (String pk : form.getPks())
                {
                    String[] tokens = pk.split("<>");
                    if (tokens.length != 2 || tokens[0] == null || tokens[1] == null)
                    {
                        errors.reject(ERROR_MSG, "Invalid PK: " + pk);
                        return null;
                    }
                    else
                    {
                        Integer accession = Integer.parseInt(tokens[0]);
                        Integer testId = Integer.parseInt(tokens[1]);
                        runner.syncSingleRun(getContainer(), getUser(), accession, testId);
                    }
                }
            }
            catch (NumberFormatException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }
            catch (RuntimeException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }
            catch (SQLException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class ResyncRunsForm
    {
        private String[] _pks;

        public String[] getPks()
        {
            return _pks;
        }

        public void setPks(String[] pks)
        {
            _pks = pks;
        }
    }
}