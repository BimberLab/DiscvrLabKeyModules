/*
 * Copyright (c) 2018 LabKey Corporation
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

package org.labkey.snprc_scheduler;

import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.snd.SNDService;
import org.labkey.api.snprc_scheduler.SNPRC_schedulerService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.snprc_scheduler.domains.Timeline;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SNPRC_schedulerController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SNPRC_schedulerController.class);
    public static final String NAME = "snprc_scheduler";

    public SNPRC_schedulerController()
    {
        setActionResolver(_actionResolver);
    }

    //http://deepthought:8080/labkey/snprc_scheduler/snprc/Begin.view?
    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Procedure scheduling", new ActionURL(BeginAction.class, getContainer()));
            return root;
        }

        @Override
        public ModelAndView getView(Object bla, BindException errors)
        {
            return new JspView<>("/org/labkey/snprc_scheduler/view/schedule.jsp");
        }
    }

    // http://deepthought:8080/labkey/snprc_scheduler/snprc/getActiveTimelines.view?ProjectId=1&RevisionNum=1
    @RequiresPermission(ReadPermission.class)
    public class getActiveTimelines extends ApiAction<Timeline>
    {
        @Override
        public ApiResponse execute(Timeline timeline, BindException errors)
        {
            Map<String, Object> props = new HashMap<>();

            if (timeline.getProjectId() != null && timeline.getRevisionNum() != null)
            {
                try
                {
                    List<JSONObject> timelines = SNPRC_schedulerService.get().getActiveTimelines(getContainer(), getUser(),
                            timeline.getProjectId(), timeline.getRevisionNum(), new BatchValidationException());

                    props.put("success", true);
                    props.put("rows", timelines);
                }
                catch (ApiUsageException e)
                {
                    props.put("success", false);
                    props.put("message", e.getMessage());
                }
            }
            else
            {
                props.put("success", false);
                props.put("message", "ProjectId and RevisionNum are required");
            }
            return new ApiSimpleResponse(props);
        }
    }
    // http://deepthought:8080/labkey/snprc_scheduler/snprc/getActiveProjects.view?
    @RequiresPermission(ReadPermission.class)
    public class getActiveProjects extends ApiAction<SimpleApiJsonForm>
    {
        @Override
        public ApiResponse execute(SimpleApiJsonForm simpleApiJsonForm, BindException errors)
        {
            Map<String, Object> props = new HashMap<>();

            // add filters to remove colony maintenance, behavior, clinical, and legacy projects
            SimpleFilter [] filters = new SimpleFilter[2];
            filters[0] = new SimpleFilter(FieldKey.fromParts("ReferenceId"), 4000, CompareType.LT);
            filters[1] = new SimpleFilter(FieldKey.fromParts("ReferenceId"), 0, CompareType.GT);

            List<JSONObject> projects = SNDService.get().getActiveProjects(getContainer(), getUser(), filters);
            if (projects.size() > 0)
            {
                props.put("success", true);
                props.put("rows", projects);
            }
            else
            {
                props.put("success", false);
                props.put("message", "No Active Projects");
            }


            return new ApiSimpleResponse(props);
        }
    }
}