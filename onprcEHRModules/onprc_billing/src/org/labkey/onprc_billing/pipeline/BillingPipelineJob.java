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
package org.labkey.onprc_billing.pipeline;

import org.apache.commons.lang3.time.DateUtils;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileUrls;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.onprc_billing.ONPRC_BillingController;

import java.io.File;
import java.util.Calendar;
import java.util.Date;

/**
 * User: bimber
 * Date: 9/10/13
 * Time: 7:19 PM
 */
public class BillingPipelineJob extends PipelineJob implements BillingPipelineJobSupport
{
    private File _analysisDir;
    private ONPRC_BillingController.BillingPipelineForm _form;

    public BillingPipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, File analysisDir, ONPRC_BillingController.BillingPipelineForm form)
    {
        super(null, new ViewBackgroundInfo(c, user, url), pipeRoot);

        _analysisDir = analysisDir;
        setLogFile(new File(analysisDir, FileUtil.makeFileNameWithTimestamp("billingPipeline", "log")));
        _form = form;
    }

    public static File createAnalysisDir(PipeRoot pipeRoot, String name) throws PipelineValidationException
    {
        String trialName = FileUtil.makeLegalName(name);
        File analysisDir = new File(pipeRoot.getRootPath(), trialName);
        int suffix = 0;
        while (analysisDir.exists())
        {
            suffix++;
            trialName = FileUtil.makeLegalName(name) + "." + suffix;
            analysisDir = new File(pipeRoot.getRootPath(), trialName);
        }

        analysisDir.mkdirs();

        return analysisDir;
    }

    @Override
    public String getDescription()
    {
        return "Billing Run";
    }

    @Override
    public ActionURL getStatusHref()
    {
        return PageFlowUtil.urlProvider(FileUrls.class).urlBegin(getContainer());
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(BillingPipelineJob.class));
    }

    public Date getStartDate()
    {
        Date ret = _form.getStartDate() == null ? null : DateUtils.truncate(_form.getStartDate(), Calendar.DATE);
        return ret;
    }

    public Date getEndDate()
    {
        Date ret = _form.getEndDate() == null ? null : DateUtils.truncate(_form.getEndDate(), Calendar.DATE);
        return ret;
    }

    public String getComment()
    {
        return _form.getComment();
    }

    public String getName()
    {
        return _form.getProtocolName();
    }

    public File getAnalysisDir()
    {
        return _analysisDir;
    }
}
