/*
 * Copyright (c) 2014 LabKey Corporation
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

package org.labkey.galaxyintegration;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.galaxyintegration.api.GalaxyService;
import org.labkey.galaxyintegration.pipeline.ExpRunCreator;
import org.labkey.galaxyintegration.pipeline.GalaxyProvenanceImporterTask;
import org.springframework.validation.BindException;

import java.util.HashMap;
import java.util.Map;

public class GalaxyIntegrationController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(GalaxyIntegrationController.class);
    public static final String NAME = "galaxyintegration";
    private static final Logger _log = Logger.getLogger(GalaxyIntegrationController.class);

    public GalaxyIntegrationController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(UpdatePermission.class)
    @CSRF
    public class SetApiKeyAction extends ApiAction<SetApiKeyForm>
    {
        @Override
        public Object execute(SetApiKeyForm form, BindException errors) throws Exception
        {
            if (StringUtils.trimToNull(form.getHostName()) == null)
            {
                errors.reject(ERROR_MSG, "Must provide the host name for the key");
                return null;
            }

            GalaxyIntegrationManager.get().saveApiKey(getUser(), form.getHostName(), form.getApiKey());

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class SetApiKeyForm
    {
        private String _apiKey;
        private String _hostName;

        public String getApiKey()
        {
            return _apiKey;
        }

        public void setApiKey(String apiKey)
        {
            _apiKey = apiKey;
        }

        public String getHostName()
        {
            return _hostName;
        }

        public void setHostName(String hostName)
        {
            _hostName = hostName;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @CSRF
    public class GetApiKeysAction extends ApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            Map<String, String> hosts = new HashMap<>();
            for (String hostName : GalaxyService.get().getServerHostNames(getUser()))
            {
                hosts.put(hostName, GalaxyIntegrationManager.get().getUserApiKey(getUser(), hostName));
            }

            Map<String, Object> ret = new HashMap<>();
            ret.put("hosts", hosts);
            ret.put("success", true);

            return new ApiSimpleResponse(ret);
        }
    }

    @RequiresPermission(ReadPermission.class)
    //NOTE: disabled or calls from galaxy will fail
    //@CSRF
    public class ImportDatasetAction extends ApiAction<GetProvenanceForm>
    {
        @Override
        public ApiResponse execute(GetProvenanceForm form, BindException errors) throws Exception
        {
            if (StringUtils.isEmpty(form.getHostName()) || StringUtils.isEmpty(form.getApiKey()))
            {
                errors.reject(ERROR_MSG, "Must provide hostName and API Key");
                return null;
            }

            if (StringUtils.isEmpty(form.getRunName()))
            {
                errors.reject(ERROR_MSG, "Run name not provided");
                return null;
            }

            GalaxyProvenanceImporterTask task = new GalaxyProvenanceImporterTask(getUser(), getContainer(), form.getHostName(), form.getApiKey(), form.getHistoryId(), form.getDatasetId(), form.getRunName());
            RecordedActionSet actions = task.run();
            ExpRun run = new ExpRunCreator().createRun(actions, getContainer(), getUser(), form.getRunName(), form.getDescription());

            JSONObject j = new JSONObject();
            j.put("success", true);
            if (run != null)
            {
                j.put("runId", run.getRowId());
            }

            return new ApiSimpleResponse(j);
        }
    }

    public static class GetProvenanceForm
    {
        private String _hostName;
        private String _historyId;
        private String _datasetId;
        private String _runName;
        private String _description;
        private String _apiKey;

        public String getDatasetId()
        {
            return _datasetId;
        }

        public void setDatasetId(String datasetId)
        {
            _datasetId = datasetId;
        }

        public String getHistoryId()
        {
            return _historyId;
        }

        public void setHistoryId(String historyId)
        {
            _historyId = historyId;
        }

        public String getHostName()
        {
            return _hostName;
        }

        public void setHostName(String hostName)
        {
            _hostName = hostName;
        }

        public String getRunName()
        {
            return _runName;
        }

        public void setRunName(String runName)
        {
            _runName = runName;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getApiKey()
        {
            return _apiKey;
        }

        public void setApiKey(String apiKey)
        {
            _apiKey = apiKey;
        }
    }
}