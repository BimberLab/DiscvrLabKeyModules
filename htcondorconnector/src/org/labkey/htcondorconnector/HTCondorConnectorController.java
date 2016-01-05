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

import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.htcondorconnector.pipeline.HTCondorExecutionEngine;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

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
}