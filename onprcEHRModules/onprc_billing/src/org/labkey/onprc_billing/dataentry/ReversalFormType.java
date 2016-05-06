package org.labkey.onprc_billing.dataentry;

import org.labkey.api.ehr.dataentry.DataEntryFormContext;
import org.labkey.api.ehr.dataentry.FormSection;
import org.labkey.api.ehr.dataentry.TaskForm;
import org.labkey.api.ehr.dataentry.TaskFormSection;
import org.labkey.api.module.Module;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.onprc_billing.security.ONPRCBillingAdminPermission;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 11/12/13
 * Time: 5:25 PM
 */
public class ReversalFormType extends TaskForm
{
    public static final String NAME = "Reversals";

    public ReversalFormType(DataEntryFormContext ctx, Module owner)
    {
        super(ctx, owner, NAME, "Reversals/Adjustments", "Billing", Arrays.asList(
                new TaskFormSection(),
                new AdjustmentFormSection()
        ));

        addClientDependency(ClientDependency.fromPath("onprc_billing/model/sources/Reversals.js"));
        addClientDependency(ClientDependency.fromPath("onprc_billing/buttons/financeButtons.js"));

        for (FormSection s : getFormSections())
        {
            s.addConfigSource("Reversals");
        }
    }

    @Override
    public boolean isVisible()
    {
        return false;
    }

    @Override
    protected List<String> getButtonConfigs()
    {
        List<String> defaultButtons = new ArrayList<String>();
        defaultButtons.add("FINANCESUBMIT");

        return defaultButtons;
    }

    @Override
    protected List<String> getMoreActionButtonConfigs()
    {
        return Collections.emptyList();
    }

    /**
     * The intent is to prevent read access to the majority of users
     */
    @Override
    public boolean canRead()
    {
        if (!getCtx().getContainer().hasPermission(getCtx().getUser(), ONPRCBillingAdminPermission.class))
            return false;

        return super.canRead();
    }

    @Override
    public boolean canInsert()
    {
        if (!getCtx().getContainer().hasPermission(getCtx().getUser(), ONPRCBillingAdminPermission.class))
            return false;

        return super.canInsert();
    }
}
