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

package org.labkey.onprc_billing;

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.ldk.notification.Notification;
import org.labkey.api.ldk.notification.NotificationService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.onprc_billing.notification.BillingValidationNotification;
import org.labkey.onprc_billing.pipeline.BillingPipelineJob;
import org.labkey.onprc_billing.security.ONPRCBillingAdminPermission;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ONPRC_BillingController extends SpringActionController
{
    public static final String NAME = "onprc_billing";

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ONPRC_BillingController.class);

    public ONPRC_BillingController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(UpdatePermission.class)
    @CSRF
    public class RunBillingPipelineAction extends ApiAction<BillingPipelineForm>
    {
        public ApiResponse execute(BillingPipelineForm form, BindException errors) throws PipelineJobException
        {
            Map<String, Object> resultProperties = new HashMap<>();

            try
            {
                PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(getContainer());
                File analysisDir = BillingPipelineJob.createAnalysisDir(pipelineRoot, form.getProtocolName());
                PipelineService.get().queueJob(new BillingPipelineJob(getContainer(), getUser(), getViewContext().getActionURL(), pipelineRoot, analysisDir, form));

                resultProperties.put("success", true);
            }
            catch (PipelineValidationException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            return new ApiSimpleResponse(resultProperties);
        }
    }

    public static class BillingPipelineForm
    {
        private String _protocolName;
        private Date _startDate;
        private Date _endDate;
        private String _comment;

        public String getProtocolName()
        {
            return _protocolName;
        }

        public void setProtocolName(String protocolName)
        {
            _protocolName = protocolName;
        }

        public Date getStartDate()
        {
            return _startDate;
        }

        public void setStartDate(Date startDate)
        {
            _startDate = startDate;
        }

        public Date getEndDate()
        {
            return _endDate;
        }

        public void setEndDate(Date endDate)
        {
            _endDate = endDate;
        }

        public String getComment()
        {
            return _comment;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }
    }

    @RequiresPermission(ONPRCBillingAdminPermission.class)
    public class DeleteBillingPeriodAction extends ConfirmAction<QueryForm>
    {
        public void validateCommand(QueryForm form, Errors errors)
        {
            Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), true);
            if (ids.size() == 0)
            {
                errors.reject(ERROR_MSG, "Must select at least one item to delete");
            }
        }

        @Override
        public ModelAndView getConfirmView(QueryForm form, BindException errors) throws Exception
        {
            Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), true);

            StringBuilder msg = new StringBuilder("You have selected " + ids.size() + " billing runs to delete.  This will also delete: <p>");
            for (String m : ONPRC_BillingManager.get().deleteBillingRuns(getUser(), ids, true))
            {
                msg.append(m).append("<br>");
            }

            msg.append("<p>Are you sure you want to do this?");

            return new HtmlView(msg.toString());
        }

        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), true);
            ONPRC_BillingManager.get().deleteBillingRuns(getUser(), ids, false);

            return true;
        }

        public URLHelper getSuccessURL(QueryForm form)
        {
            URLHelper url = form.getReturnURLHelper();
            return url != null ? url : QueryService.get().urlFor(getUser(), getContainer(), QueryAction.executeQuery, ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_INVOICE_RUNS);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class BillingValidationAction extends SimpleViewAction<BillingValidationForm>
    {
        private String _title = null;

        public ModelAndView getView(BillingValidationForm form, BindException errors) throws Exception
        {
            if (form.getKey() == null)
            {
                errors.reject(ERROR_MSG, "No notification provided");
                return null;
            }

            Notification n = NotificationService.get().getNotification(form.getKey());
            if (n == null)
            {
                errors.reject(ERROR_MSG, "Unknown notification: " + form.getKey());
                return null;
            }

            if (!n.isAvailable(getContainer()))
            {
                return new HtmlView("The notification " + form.getKey() + " is not available in this container");
            }

            _title = n.getName();

            BillingValidationNotification v = new BillingValidationNotification();
            Container financeContainer = ONPRC_BillingManager.get().getBillingContainer(getContainer());

            StringBuilder sb = new StringBuilder();
            sb.append(v.runValidation(financeContainer, getUser(), form.getStart(), form.getEnd()));

            return new HtmlView(sb.toString());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_title == null ? "Notification" : _title);
        }
    }

    public static class BillingValidationForm
    {
        private String _key;
        private Date _start;
        private Date _end;

        public String getKey()
        {
            return _key;
        }

        public void setKey(String key)
        {
            _key = key;
        }

        public Date getStart()
        {
            return _start;
        }

        public void setStart(Date start)
        {
            _start = start;
        }

        public Date getEnd()
        {
            return _end;
        }

        public void setEnd(Date end)
        {
            _end = end;
        }
    }
}