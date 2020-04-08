package org.labkey.blast.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.blast.BLASTModule;

import java.util.Arrays;

/**
 * User: bimber
 * Date: 7/16/2014
 * Time: 5:37 PM
 */
public class BlastOligosButton extends SimpleButtonConfigFactory
{
    public BlastOligosButton()
    {
        super(ModuleLoader.getInstance().getModule(BLASTModule.class), "BLAST Oligos", "BLAST.window.BlastOligosWindow.buttonHandler(dataRegionName);", Arrays.asList(ClientDependency.supplierFromPath("laboratory.context"), ClientDependency.supplierFromPath("blast/window/BlastOligosWindow.js")));
    }
}
