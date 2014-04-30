package org.labkey.onprc_billing.dataentry;

import org.labkey.api.data.Container;
import org.labkey.api.ehr.dataentry.AbstractFormSection;
import org.labkey.api.ehr.dataentry.DataEntryFormContext;
import org.labkey.api.ehr.dataentry.FormElement;
import org.labkey.api.security.User;
import org.labkey.api.view.template.ClientDependency;

import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 6/9/13
 * Time: 4:15 PM
 */
public class ChargesAdvancedInstructionFormSection extends AbstractFormSection
{
    public ChargesAdvancedInstructionFormSection()
    {
        super("ChargesInstruction", "Instructions", "onprc-chargesadvancedinstructionpanel");

        addClientDependency(ClientDependency.fromFilePath("onprc_billing/panel/ChargesAdvancedInstructionPanel.js"));
        setSupportFormSort(false);
    }

    @Override
    protected List<FormElement> getFormElements(DataEntryFormContext ctx)
    {
        return Collections.emptyList();
    }
}
