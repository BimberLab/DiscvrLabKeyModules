package org.labkey.onprc_billing.button;

import org.labkey.api.ehr.buttons.EHRShowEditUIButton;
import org.labkey.api.ehr.security.EHRProjectEditPermission;
import org.labkey.api.ldk.buttons.ShowEditUIButton;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.Permission;
import org.labkey.onprc_billing.security.ONPRCBillingAdminPermission;

import java.util.HashMap;
import java.util.Map;

/**

 */
public class ProjectEditButton extends EHRShowEditUIButton
{
    public ProjectEditButton(Module owner, String schemaName, String queryName)
    {
        this(owner, schemaName, queryName, ONPRCBillingAdminPermission.class);
    }

    public ProjectEditButton(Module owner, String schemaName, String queryName, Class<? extends Permission> clazz)
    {
        super(owner, schemaName, queryName, clazz);

        Map<String, String> urlParams = new HashMap<>();
        urlParams.put("project", "query.project~eq");
        urlParams.put("protocol", "query.protocol~eq");
        urlParams.put("query.viewName", "query.viewName");
        setUrlParamMap(urlParams);
    }
}
