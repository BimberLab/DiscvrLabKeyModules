package org.labkey.GeneticsCore.button;

import org.labkey.GeneticsCore.GeneticsCoreModule;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * User: bimber
 * Date: 7/16/2014
 * Time: 5:37 PM
 */
public class PublishSBTResultsButton extends SimpleButtonConfigFactory
{
    public PublishSBTResultsButton()
    {
        super(ModuleLoader.getInstance().getModule(GeneticsCoreModule.class), "Publish/Cache Selected", "GeneticsCore.window.PublishResultsWindow.buttonHandler(dataRegionName);", new LinkedHashSet<>(Arrays.asList(ClientDependency.fromPath("geneticscore/window/PublishResultsWindow.js"))));
    }
}
