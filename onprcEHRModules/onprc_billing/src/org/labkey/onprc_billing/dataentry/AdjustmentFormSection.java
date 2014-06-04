package org.labkey.onprc_billing.dataentry;

import java.util.ArrayList;
import java.util.List;

/**

 */
public class AdjustmentFormSection extends ChargesFormSection
{
    public AdjustmentFormSection()
    {
        super();

        setAllowBulkAdd(false);
        setTemplateMode(TEMPLATE_MODE.NONE);
        _allowRowEditing = false;
    }

    @Override
    public List<String> getTbarButtons()
    {
        List<String> defaultButtons = new ArrayList<>(super.getTbarButtons());
        defaultButtons.remove("ADDRECORD");
        defaultButtons.remove("COPYFROMSECTION");

        return defaultButtons;
    }
}
