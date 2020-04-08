package org.labkey.sequenceanalysis.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.analysis.MultiQCHandler;

import java.util.Arrays;

/**
 * Created by bimber on 7/12/2017.
 */
public class RunMultiQCButton extends SimpleButtonConfigFactory
{
    public RunMultiQCButton()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Run MultiQC", "SequenceAnalysis.window.OutputHandlerWindow.readsetButtonHandler(dataRegionName, '" + MultiQCHandler.class.getName() + "');", Arrays.asList(ClientDependency.supplierFromModuleName("ldk"), ClientDependency.supplierFromModuleName("laboratory"), ClientDependency.supplierFromPath("sequenceanalysis/window/OutputHandlerWindow.js")));
        setPermission(UpdatePermission.class);
    }
}