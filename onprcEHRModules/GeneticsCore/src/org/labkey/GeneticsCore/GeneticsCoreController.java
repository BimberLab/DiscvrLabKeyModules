package org.labkey.GeneticsCore;

import org.apache.log4j.Logger;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.UpdatePermission;
import org.springframework.validation.BindException;

import java.lang.String;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/1/13
 * Time: 11:59 AM
 */
public class GeneticsCoreController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(GeneticsCoreController.class);
    private static final Logger _log = Logger.getLogger(GeneticsCoreController.class);

    public GeneticsCoreController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class ManageFlagsAction extends ApiAction<ManageFlagsForm>
    {
        public ApiResponse execute(ManageFlagsForm form, BindException errors) throws Exception
        {
            Map<String, Object> resp = new HashMap<String, Object>();

            if (form.getFlag() == null)
            {
                errors.reject(ERROR_MSG, "No flag supplied");
                return null;
            }

            if (form.getAnimalIds() == null || form.getAnimalIds().length == 0)
            {
                errors.reject(ERROR_MSG, "No animal IDs supplied");
                return null;
            }

            //TODO: check permissions?
            try
            {
                String mode = form.getMode();
                if ("add".equals(mode))
                {
                    if (form.getDate() == null)
                    {
                        errors.reject(ERROR_MSG, "Must supply a date");
                        return null;
                    }

                    GeneticsCoreManager.get().ensureFlagActive(getUser(), getContainer(), form.getFlag(), form.getDate(), form.getRemark(), form.getAnimalIds());
                }
                else if ("remove".equals(mode))
                {
                    if (form.getEnddate() == null)
                    {
                        errors.reject(ERROR_MSG, "Must supply an end date");
                        return null;
                    }

                    GeneticsCoreManager.get().terminateFlagsIfExists(getUser(), getContainer(), form.getFlag(), form.getEnddate(), form.getAnimalIds());
                }
                else
                {
                    errors.reject(ERROR_MSG, "Unknown mode, must either be add or remove");
                    return null;
                }

                resp.put("success", true);
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            return new ApiSimpleResponse(resp);
        }
    }

    public static class ManageFlagsForm
    {
        private String _flag;
        private Date _date;
        private Date _enddate;
        private String _remark;
        private String[] _animalIds;
        private String _mode;

        public String getFlag()
        {
            return _flag;
        }

        public void setFlag(String flag)
        {
            _flag = flag;
        }

        public Date getDate()
        {
            return _date;
        }

        public void setDate(Date date)
        {
            _date = date;
        }

        public Date getEnddate()
        {
            return _enddate;
        }

        public void setEnddate(Date enddate)
        {
            _enddate = enddate;
        }

        public String getRemark()
        {
            return _remark;
        }

        public void setRemark(String remark)
        {
            _remark = remark;
        }

        public String[] getAnimalIds()
        {
            return _animalIds;
        }

        public void setAnimalIds(String[] animalIds)
        {
            _animalIds = animalIds;
        }

        public String getMode()
        {
            return _mode;
        }

        public void setMode(String mode)
        {
            _mode = mode;
        }
    }
}
