package org.labkey.sla.dataentry;

import org.labkey.api.ehr.dataentry.DataEntryFormContext;
import org.labkey.api.ehr.dataentry.FormSection;
import org.labkey.api.ehr.dataentry.TaskForm;
import org.labkey.api.ehr.dataentry.TaskFormSection;
import org.labkey.api.module.Module;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sla.security.SLAEntryPermission;

import java.util.Arrays;

/**

 */
public class CensusFormType extends TaskForm
{
    public static final String NAME = "SLA Census";

    public CensusFormType(DataEntryFormContext ctx, Module owner)
    {
        super(ctx, owner, NAME, NAME, "SLA", Arrays.<FormSection>asList(
                        new TaskFormSection(),
                        new CensusFormSection())
        );

        addClientDependency(ClientDependency.fromFilePath("sla/model/sources/SLA.js"));

        for (FormSection s : this.getFormSections())
        {
            s.addConfigSource("SLA");
        }
    }

    @Override
    protected boolean canInsert()
    {
        if (!getCtx().getContainer().hasPermission(getCtx().getUser(), SLAEntryPermission.class))
            return false;

        return super.canInsert();
    }
}
