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

package org.labkey.cluster;

import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HtmlView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

public class ClusterController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(ClusterController.class);
    public static final String NAME = "cluster";

    public ClusterController()
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
            return new HtmlView("This will run a very simple test pipeline job against all configured cluster engines.  This is designed to help make sure your site's configuration is functional.  Do you want to continue?<br><br>");
        }

        public boolean handlePost(Object form, BindException errors) throws Exception
        {
            for (RemoteExecutionEngine e : PipelineJobService.get().getRemoteExecutionEngines())
            {
                if (e instanceof RemoteClusterEngine)
                {
                    ((RemoteClusterEngine)e).runTestJob(getContainer(), getUser());
                }
            }

            return true;
        }
    }

    @RequiresSiteAdmin
    public class ForcePipelineCancelAction extends ConfirmAction<ForcePipelineCancelForm>
    {
        public void validateCommand(ForcePipelineCancelForm form, Errors errors)
        {

        }

        public URLHelper getSuccessURL(ForcePipelineCancelForm form)
        {
            return PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(getContainer());
        }

        public ModelAndView getConfirmView(ForcePipelineCancelForm form, BindException errors) throws Exception
        {
            return new HtmlView("This will change the status of the pipeline job with the provided ID to Cancelled.  It is intended to help the situation when the normal UI leave a job in a perpetual 'Cancelling' state." +
                    "To continue, enter the Job ID and hit submit:<br><br>" +
                    "<label>Enter Job ID: </label><input name=\"jobId\"><br>");
        }

        public boolean handlePost(ForcePipelineCancelForm form, BindException errors) throws Exception
        {
            PipelineStatusFile sf = PipelineService.get().getStatusFile(form.getJobId());
            if (sf == null)
            {
                errors.reject(ERROR_MSG, "Unable to find job: " + form.getJobId());
                return false;
            }

            if (!PipelineJob.TaskStatus.cancelling.name().equalsIgnoreCase(sf.getStatus()))
            {
                errors.reject(ERROR_MSG, "This should only be used on jobs with status cancelling.  Was: " + sf.getStatus());
                return false;
            }

            sf.setStatus(PipelineJob.TaskStatus.cancelled.name().toUpperCase());
            sf.save();

            return true;
        }
    }

    public static class ForcePipelineCancelForm
    {
        private int jobId;

        public int getJobId()
        {
            return jobId;
        }

        public void setJobId(int jobId)
        {
            this.jobId = jobId;
        }
    }
}