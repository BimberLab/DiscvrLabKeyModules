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
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.template.PageConfig;
import org.labkey.galaxyintegration.api.GalaxyService;
import org.labkey.galaxyintegration.pipeline.ExpRunCreator;
import org.labkey.galaxyintegration.pipeline.GalaxyProvenanceImporterTask;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletResponse;
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
                errors.reject(ERROR_MSG, "Must provide the host name");
                return null;
            }

            GalaxyIntegrationManager.get().saveApiKey(getUser(), form.getHostName(), form.getUrl(), form.getApiKey());

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class SetApiKeyForm
    {
        private String _apiKey;
        private String _hostName;
        private String _url;

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

        public String getUrl()
        {
            return _url;
        }

        public void setUrl(String url)
        {
            _url = url;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @CSRF
    public class GetApiKeysAction extends ApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            Map<String, JSONObject> hosts = new HashMap<>();
            for (String hostName : GalaxyService.get().getServerHostNames(getUser()))
            {
                hosts.put(hostName, GalaxyIntegrationManager.get().getServerSettings(getUser(), hostName));
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
    public class ImportDatasetAction extends ExportAction<GetProvenanceForm>
    {
        @Override
        public void export(GetProvenanceForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.Print);
            getPageConfig().setShowHeader(false);
            getPageConfig().setNavTrail(null);
            getPageConfig().setTitle(null);

            if (StringUtils.isEmpty(form.getHostName()) || StringUtils.isEmpty(form.getApiKey()))
            {
                response.getWriter().write("Error: Must provide hostName and API Key");
                return;
            }

            JSONObject json = GalaxyIntegrationManager.get().getServerSettings(getUser(), form.getHostName());
            if (json == null)
            {
                response.getWriter().write("Error: Unknown galaxy host: " + form.getHostName() + ", must register this galaxy instance with your server for the user: " + getUser().getDisplayName(getUser()));
                return;
            }

            String url = json.getString("url");
            if (StringUtils.trimToNull(url) == null)
            {
                response.getWriter().write("Error: No url saved for host: " +  form.getHostName() + " for user: " + getUser().getDisplayName(getUser()));
                return;
            }

            if (StringUtils.isEmpty(form.getRunName()))
            {
                response.getWriter().write("Error: Run name not provided");
                return;
            }

            GalaxyProvenanceImporterTask task = new GalaxyProvenanceImporterTask(getUser(), getContainer(), form.getHostName(), form.getApiKey(), form.getHistoryId(), form.getDatasetId(), form.getRunName());
            RecordedActionSet actions = task.run();
            ExpRun run = new ExpRunCreator().createRun(actions, getContainer(), getUser(), form.getRunName(), form.getDescription());

            StringBuilder html = new StringBuilder();
            if ("json".equals(form.getOutputFormat()))
            {
                JSONObject j = new JSONObject();
                j.put("success", true);
                if (run != null)
                {
                    j.put("runId", run.getRowId());
                }

                html.append(j.toString());
            }
            else
            {
                DetailsURL runUrl = DetailsURL.fromString("/experiment/showRunGraphDetail.view", getContainer());
                html.append("<h3>A record of the provenance of the selected dataset has been imported into LabKey.  <a href=\"" + AppProps.getInstance().getBaseServerUrl() + runUrl.getActionURL().toString() + "rowId=" + run.getRowId() + "\">Click here to view this information</a> (login may be required)</h3>");
            }


            response.getWriter().write(html.toString());
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
        private String _outputFormat;

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

        public String getOutputFormat()
        {
            return _outputFormat;
        }

        public void setOutputFormat(String outputFormat)
        {
            _outputFormat = outputFormat;
        }
    }
}