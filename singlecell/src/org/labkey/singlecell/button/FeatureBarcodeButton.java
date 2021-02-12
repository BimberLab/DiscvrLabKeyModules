package org.labkey.singlecell.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.singlecell.SingleCellModule;
import org.labkey.singlecell.run.CellRangerFeatureBarcodeHandler;

import java.util.Arrays;

/**
 * Created by bimber on 7/12/2017.
 */
public class FeatureBarcodeButton extends SimpleButtonConfigFactory
{
    public FeatureBarcodeButton()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "Generate Feature Barcode Counts", "SequenceAnalysis.window.OutputHandlerWindow.readsetButtonHandler(dataRegionName, '" + CellRangerFeatureBarcodeHandler.class.getName() + "');", Arrays.asList(ClientDependency.supplierFromModuleName("ldk"), ClientDependency.supplierFromModuleName("laboratory"), ClientDependency.supplierFromPath("sequenceanalysis/window/OutputHandlerWindow.js")));
        setPermission(UpdatePermission.class);
    }
}
