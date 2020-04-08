package org.labkey.sequenceanalysis.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.util.Arrays;

/**
 * Created by bimber on 7/12/2017.
 */
public class AddSraRunButton extends SimpleButtonConfigFactory
{
    public AddSraRunButton()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Add/Update SRA Run", "SequenceAnalysis.window.AddSraRunWindow.buttonHandler(dataRegionName);", Arrays.asList(ClientDependency.supplierFromModuleName("ldk"), ClientDependency.supplierFromModuleName("laboratory"), ClientDependency.supplierFromPath("sequenceanalysis/window/AddSraRunWindow.js")));
        setPermission(UpdatePermission.class);
    }
}