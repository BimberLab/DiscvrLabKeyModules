package org.labkey.onprc_billing.button;

import org.labkey.api.ehr.security.EHRProjectEditPermission;
import org.labkey.api.ldk.buttons.ShowEditUIButton;
import org.labkey.api.module.Module;
import org.labkey.onprc_billing.security.ONPRCBillingAdminPermission;

import java.util.HashMap;
import java.util.Map;

/**

 */
public class ChargeEditButton extends ShowEditUIButton
{
    public ChargeEditButton(Module owner, String schemaName, String queryName)
    {
        super(owner, schemaName, queryName, ONPRCBillingAdminPermission.class);

        Map<String, String> urlParams = new HashMap<>();
        urlParams.put("chargeId", "query.chargeId~eq");
        setUrlParamMap(urlParams);
    }
}
