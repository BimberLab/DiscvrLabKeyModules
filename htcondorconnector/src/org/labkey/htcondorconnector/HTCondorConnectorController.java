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

package org.labkey.htcondorconnector;

import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.htcondorconnector.pipeline.HTCondorExecutionEngine;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

public class HTCondorConnectorController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(HTCondorConnectorController.class);
    public static final String NAME = "htcondorconnector";

    public HTCondorConnectorController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminPermission.class)
    public class RunTestPipelineAction extends ConfirmAction<Object>
    {
        public void validateCommand(Object form, Errors errors)
        {

        }

        public URLHelper getSuccessURL(Object form)
        {
            return getContainer().getStartURL(getUser());
        }

        public ModelAndView getConfirmView(Object form, BindException errors) throws Exception
        {
            return new HtmlView("This will run a very simple test pipeline job against all configured HTCondor engines.  This is designed to help make sure your site's configuration is functional.  Do you want to continue?<br><br>");
        }

        public boolean handlePost(Object form, BindException errors) throws Exception
        {
            HTCondorExecutionEngine.TestCase.runTestJob(getContainer(), getUser());

            return true;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @CSRF
    public class GetSettingsAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors)
        {
            JSONObject resultProperties = new JSONObject();
            String[] configKeys = {HTCondorConnectorManager.PREVENT_NEW_JOBS};

            resultProperties.put("configKeys", configKeys);
            resultProperties.put("config", PropertyManager.getProperties(HTCondorConnectorManager.CONFIG_PROPERTY_DOMAIN));

            return new ApiSimpleResponse(resultProperties);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    @CSRF
    public class SetSettingsAction extends ApiAction<SettingsForm>
    {
        public ApiResponse execute(SettingsForm form, BindException errors)
        {
            if (!getContainer().isRoot())
            {
                errors.reject(ERROR_MSG, "HTCondor settings can only be set at the site level");
                return null;
            }

            Map<String, String> configMap = new HashMap<>();
            configMap.put(HTCondorConnectorManager.PREVENT_NEW_JOBS, String.valueOf(form.isPreventNewJobs()));

            try
            {
                HTCondorConnectorManager.get().saveSettings(configMap);
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
        private boolean _preventNewJobs = false;

        public boolean isPreventNewJobs()
        {
            return _preventNewJobs;
        }

        public void setPreventNewJobs(boolean preventNewJobs)
        {
            _preventNewJobs = preventNewJobs;
        }
    }
}