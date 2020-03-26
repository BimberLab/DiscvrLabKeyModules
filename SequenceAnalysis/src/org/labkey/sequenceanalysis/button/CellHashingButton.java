package org.labkey.sequenceanalysis.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.analysis.CellHashingHandler;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * Created by bimber on 7/12/2017.
 */
public class CellHashingButton extends SimpleButtonConfigFactory
{
    public CellHashingButton()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Generate Cell Hashing Calls", "SequenceAnalysis.window.OutputHandlerWindow.readsetButtonHandler(dataRegionName, '" + CellHashingHandler.class.getName() + "');", new LinkedHashSet<>(Arrays.asList(ClientDependency.fromModuleName("ldk"), ClientDependency.fromModuleName("laboratory"), ClientDependency.fromPath("sequenceanalysis/window/OutputHandlerWindow.js"))));
        setPermission(UpdatePermission.class);
    }
}