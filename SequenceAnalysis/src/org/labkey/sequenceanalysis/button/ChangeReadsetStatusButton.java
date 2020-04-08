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
public class ChangeReadsetStatusButton extends SimpleButtonConfigFactory
{
    public ChangeReadsetStatusButton()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Change Readset Status", "SequenceAnalysis.window.ChangeReadsetStatusWindow.buttonHandlerForReadsets(dataRegionName);", Arrays.asList(ClientDependency.supplierFromPath("sequenceanalysis/window/ChangeReadsetStatusWindow.js")));
    }
}
