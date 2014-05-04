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
import java.util.Collection;
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
}
