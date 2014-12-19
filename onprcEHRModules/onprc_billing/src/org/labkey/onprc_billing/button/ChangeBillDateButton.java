package org.labkey.onprc_billing.button;

import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.onprc_billing.ONPRC_BillingModule;
import org.labkey.onprc_billing.security.ONPRCBillingAdminPermission;

/**

 */
public class ChangeBillDateButton extends SimpleButtonConfigFactory
{
    public ChangeBillDateButton(Module owner)
    {
        super(owner, "Change Billing Date", "ONPRC_Billing.window.ChangeBillDateWindow.buttonHandler(dataRegionName);");

        setClientDependencies(ClientDependency.fromPath("onprc_billing/window/ChangeBillDateWindow.js"), ClientDependency.fromModuleName(ONPRC_BillingModule.NAME));
    }

    public boolean isAvailable(TableInfo ti)
    {
        if (!super.isAvailable(ti))
            return false;

        return ti.getUserSchema().getContainer().hasPermission(ti.getUserSchema().getUser(), ONPRCBillingAdminPermission.class);
    }
}
