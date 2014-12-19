package org.labkey.sla.dataentry;

import org.labkey.api.ehr.dataentry.SimpleFormSection;
import org.labkey.api.view.template.ClientDependency;

import java.util.List;

/**

 */
public class CensusFormSection extends SimpleFormSection
{
    public CensusFormSection()
    {
        super("sla", "census", "Census", "ehr-gridpanel");
        setTemplateMode(TEMPLATE_MODE.NONE);

        addClientDependency(ClientDependency.fromPath("sla/window/AddCensusWindow.js"));
    }

    @Override
    public List<String> getTbarButtons()
    {
        List<String> defaultButtons = super.getTbarButtons();
        defaultButtons.add(0, "CENSUS_ADD");
        defaultButtons.remove("ADDANIMALS");

        return defaultButtons;
    }

    @Override
    public List<String> getTbarMoreActionButtons()
    {
        List<String> defaultButtons = super.getTbarMoreActionButtons();

        defaultButtons.remove("GUESSPROJECT");
        defaultButtons.remove("COPY_IDS");

        return defaultButtons;
    }
}
