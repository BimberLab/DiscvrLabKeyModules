package org.labkey.onprc_billing.dataentry;

import org.labkey.api.ehr.dataentry.AnimalDetailsFormSection;
import org.labkey.api.ehr.dataentry.DataEntryFormContext;
import org.labkey.api.ehr.dataentry.FormSection;
import org.labkey.api.ehr.dataentry.TaskForm;
import org.labkey.api.ehr.dataentry.TaskFormSection;
import org.labkey.api.module.Module;
import org.labkey.api.view.template.ClientDependency;

import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 11/12/13
 * Time: 5:25 PM
 */
public class ChargesFormType extends TaskForm
{
    public static final String NAME = "miscCharges";

    public ChargesFormType(DataEntryFormContext ctx, Module owner)
    {
        super(ctx, owner, NAME, "Misc Charges", "Billing", Arrays.asList(
                new TaskFormSection(),
                new AnimalDetailsFormSection(),
                new ChargesInstructionFormSection(),
                new ChargesFormSection()
        ));

        addClientDependency(ClientDependency.fromPath("onprc_billing/panel/ChargesInstructionPanel.js"));
        addClientDependency(ClientDependency.fromPath("onprc_billing/buttons/financeButtons.js"));
    }

    @Override
    protected List<String> getMoreActionButtonConfigs()
    {
        List<String> defaultButtons = super.getMoreActionButtonConfigs();
        defaultButtons.add("COPY_TASK");

        return defaultButtons;
    }
}
