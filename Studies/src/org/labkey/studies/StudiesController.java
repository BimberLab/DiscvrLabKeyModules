package org.labkey.studies;

import org.apache.logging.log4j.Logger;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.studies.study.StudyDefinition;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.validation.BindException;

import java.util.Map;

public class StudiesController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(StudiesController.class);
    public static final String NAME = "studies";

    private static final Logger _log = LogHelper.getLogger(StudiesController.class, "Messages from StudiesController");

    public StudiesController()
    {
        setActionResolver(_actionResolver);
    }


    @RequiresPermission(AdminPermission.class)
    public static class UpdateStudyDefinitionAction extends MutatingApiAction<SimpleApiJsonForm>
    {
        @Override
        public Object execute(SimpleApiJsonForm json, BindException errors) throws Exception
        {
            try
            {
                StudyDefinition sd = StudyDefinition.fromJson(json.getJsonObject());
                sd = StudiesManager.get().insertOrUpdateStudyDefinition(sd, getContainer(), getUser());

                return new ApiSimpleResponse(Map.of("success", true, "studyDefinition", sd));
            }
            catch (Exception e)
            {
                _log.error("Unable to import study definition", e);
                return new ApiSimpleResponse("success", false);
            }
        }
    }
}
