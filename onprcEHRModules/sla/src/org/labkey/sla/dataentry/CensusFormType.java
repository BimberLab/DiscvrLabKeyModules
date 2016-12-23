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
        super(ctx, owner, NAME, NAME, "SLA", Arrays.asList(
                        new TaskFormSection(),
                        new CensusFormSection())
        );

        addClientDependency(ClientDependency.fromPath("sla/model/sources/SLA.js"));
        addClientDependency(ClientDependency.fromPath("onprc_ehr/form/field/ONPRC_ProjectField.js"));
        addClientDependency(ClientDependency.fromPath("onprc_ehr/form/field/onprc_SlaCensusConfig.js"));


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
