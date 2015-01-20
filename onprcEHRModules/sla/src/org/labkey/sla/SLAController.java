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

package org.labkey.sla;

import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.sla.etl.ETL;
import org.labkey.sla.etl.ETLRunnable;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class SLAController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SLAController.class);

    public SLAController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class RunEtlAction extends RedirectAction<Object>
    {
        public boolean doAction(Object form, BindException errors) throws Exception
        {
            ETL.run();
            return true;
        }

        public void validateCommand(Object form, Errors errors)
        {

        }

        public ActionURL getSuccessURL(Object form)
        {
            return DetailsURL.fromString("/sla/etlAdmin.view", getContainer()).getActionURL();
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ValidateEtlAction extends ConfirmAction<ValidateEtlSyncForm>
    {
        public boolean handlePost(ValidateEtlSyncForm form, BindException errors) throws Exception
        {
            if (!getUser().isSiteAdmin())
            {
                throw new UnauthorizedException("Only site admins can view this page");
            }

            ETLRunnable runnable = new ETLRunnable();
            runnable.validateEtlSync(form.isAttemptRepair());
            return true;
        }

        public ModelAndView getConfirmView(ValidateEtlSyncForm form, BindException errors) throws Exception
        {
            if (!getUser().isSiteAdmin())
            {
                throw new UnauthorizedException("Only site admins can view this page");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("The following text describes the results of comparing the LabKey data with the MSSQL records from the production instance on the same server as this DB instance.  Clicking OK will cause the system to attempt to repair any differences.  Please do this very carefully.<br>");
            sb.append("<br><br>");

            ETLRunnable runnable = new ETLRunnable();
            String msg = runnable.validateEtlSync(false);
            if (msg != null)
                sb.append(msg);
            else
                sb.append("There are no discrepancies<br>");

            return new HtmlView(sb.toString());
        }

        public void validateCommand(ValidateEtlSyncForm form, Errors errors)
        {

        }

        public ActionURL getSuccessURL(ValidateEtlSyncForm form)
        {
            return getContainer().getStartURL(getUser());
        }
    }

    public static class ValidateEtlSyncForm
    {
        private boolean _attemptRepair = false;

        public boolean isAttemptRepair()
        {
            return _attemptRepair;
        }

        public void setAttemptRepair(boolean attemptRepair)
        {
            _attemptRepair = attemptRepair;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    @CSRF
    public class SetEtlDetailsAction extends ApiAction<EtlAdminForm>
    {
        public ApiResponse execute(EtlAdminForm form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();

            PropertyManager.PropertyMap configMap = PropertyManager.getWritableProperties(ETLRunnable.CONFIG_PROPERTY_DOMAIN, true);

            boolean shouldReschedule = false;
            if (form.getLabkeyUser() != null)
                configMap.put("labkeyUser", form.getLabkeyUser());

            if (form.getLabkeyContainer() != null)
                configMap.put("labkeyContainer", form.getLabkeyContainer());

            if (form.getJdbcUrl() != null)
                configMap.put("jdbcUrl", form.getJdbcUrl());

            if (form.getJdbcDriver() != null)
                configMap.put("jdbcDriver", form.getJdbcDriver());

            if (form.getRunIntervalInMinutes() != null)
            {
                String oldValue = configMap.get("runIntervalInMinutes");
                if (!form.getRunIntervalInMinutes().equals(oldValue))
                    shouldReschedule = true;

                configMap.put("runIntervalInMinutes", form.getRunIntervalInMinutes());
            }

            if (form.getEtlStatus() != null)
                configMap.put("etlStatus", form.getEtlStatus().toString());

            configMap.save();

            PropertyManager.PropertyMap rowVersionMap = PropertyManager.getWritableProperties(ETLRunnable.ROWVERSION_PROPERTY_DOMAIN, true);
            PropertyManager.PropertyMap timestampMap = PropertyManager.getWritableProperties(ETLRunnable.TIMESTAMP_PROPERTY_DOMAIN, true);

            if (form.getTimestamps() != null)
            {
                JSONObject json = new JSONObject(form.getTimestamps());
                for (String key : rowVersionMap.keySet())
                {
                    if (json.get(key) != null)
                    {
                        //this key corresponds to the rowId of the row in the etl_runs table
                        Integer value = json.getInt(key);
                        if (value == -1)
                        {
                            rowVersionMap.put(key, null);
                            timestampMap.put(key, null);
                        }
                        else
                        {
                            TableInfo ti = SLASchema.getInstance().getSchema().getTable(SLASchema.TABLE_ETL_RUNS);
                            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowid"), value), null);
                            Map<String, Object>[] rows = ts.getMapArray();
                            if (rows.length != 1)
                                continue;

                            rowVersionMap.put(key, (String)rows[0].get("rowversion"));
                            Long date = ((Date)rows[0].get("date")).getTime();
                            timestampMap.put(key, date.toString());
                        }
                    }
                }
                rowVersionMap.save();
                timestampMap.save();
            }

            //if config was changed and the ETL is current scheduled to run, we need to restart it
            if (form.getEtlStatus() && shouldReschedule)
            {
                ETL.stop();
                ETL.start(0);
            }
            else
            {
                if (form.getEtlStatus())
                    ETL.start(0);
                else
                    ETL.stop();
            }

            resultProperties.put("success", true);

            return new ApiSimpleResponse(resultProperties);
        }
    }

    public static class EtlAdminForm
    {
        private Boolean etlStatus;
        private String config;
        private String timestamps;
        private String labkeyUser;
        private String labkeyContainer;
        private String jdbcUrl;
        private String jdbcDriver;
        private String runIntervalInMinutes;

        public Boolean getEtlStatus()
        {
            return etlStatus;
        }

        public void setEtlStatus(Boolean etlStatus)
        {
            this.etlStatus = etlStatus;
        }

        public String getConfig()
        {
            return config;
        }

        public void setConfig(String config)
        {
            this.config = config;
        }

        public String getTimestamps()
        {
            return timestamps;
        }

        public void setTimestamps(String timestamps)
        {
            this.timestamps = timestamps;
        }

        public String getLabkeyUser()
        {
            return labkeyUser;
        }

        public void setLabkeyUser(String labkeyUser)
        {
            this.labkeyUser = labkeyUser;
        }

        public String getLabkeyContainer()
        {
            return labkeyContainer;
        }

        public void setLabkeyContainer(String labkeyContainer)
        {
            this.labkeyContainer = labkeyContainer;
        }

        public String getJdbcUrl()
        {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl)
        {
            this.jdbcUrl = jdbcUrl;
        }

        public String getJdbcDriver()
        {
            return jdbcDriver;
        }

        public void setJdbcDriver(String jdbcDriver)
        {
            this.jdbcDriver = jdbcDriver;
        }

        public String getRunIntervalInMinutes()
        {
            return runIntervalInMinutes;
        }

        public void setRunIntervalInMinutes(String runIntervalInMinutes)
        {
            this.runIntervalInMinutes = runIntervalInMinutes;
        }
    }

    @AdminConsoleAction
    public class ShowEtlLogAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            PageFlowUtil.streamLogFile(response, 0, getLogFile("sla-etl.log"));
        }
    }

    private File getLogFile(String name)
    {
        File tomcatHome = new File(System.getProperty("catalina.home"));
        return new File(tomcatHome, "logs/" + name);
    }

    @RequiresPermissionClass(AdminPermission.class)
    @CSRF
    public class GetEtlDetailsAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();
            resultProperties.put("enabled", ETL.isEnabled());
            resultProperties.put("active", ETL.isRunning());
            resultProperties.put("scheduled", ETL.isScheduled());
            resultProperties.put("nextSync", ETL.nextSync());
            resultProperties.put("cannotTruncate", ETLRunnable.CANNOT_TRUNCATE);

            String[] etlConfigKeys = {"labkeyUser", "labkeyContainer", "jdbcUrl", "jdbcDriver", "runIntervalInMinutes"};

            resultProperties.put("configKeys", etlConfigKeys);
            resultProperties.put("config", PropertyManager.getProperties(ETLRunnable.CONFIG_PROPERTY_DOMAIN));
            resultProperties.put("rowversions", PropertyManager.getProperties(ETLRunnable.ROWVERSION_PROPERTY_DOMAIN));
            Map<String, String> map = PropertyManager.getProperties(ETLRunnable.TIMESTAMP_PROPERTY_DOMAIN);
            Map<String, Date> timestamps = new TreeMap<>();
            for (String key : map.keySet())
            {
                timestamps.put(key, new Date(Long.parseLong(map.get(key))));
            }
            resultProperties.put("timestamps", timestamps);

            return new ApiSimpleResponse(resultProperties);
        }
    }
}