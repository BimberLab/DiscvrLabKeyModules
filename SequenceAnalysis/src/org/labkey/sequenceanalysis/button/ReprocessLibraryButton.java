package org.labkey.sequenceanalysis.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.util.Arrays;

/**
 * User: bimber
 * Date: 7/16/2014
 * Time: 5:37 PM
 */
public class ReprocessLibraryButton extends SimpleButtonConfigFactory
{
    public ReprocessLibraryButton()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Re-process Selected", "SequenceAnalysis.window.ReprocessLibraryWindow.buttonHandler(dataRegionName);", Arrays.asList(ClientDependency.supplierFromPath("sequenceanalysis/window/ReprocessLibraryWindow.js")));
    }
}
