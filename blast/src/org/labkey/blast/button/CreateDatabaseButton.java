package org.labkey.blast.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.blast.BLASTModule;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * User: bimber
 * Date: 7/16/2014
 * Time: 5:37 PM
 */
public class CreateDatabaseButton extends SimpleButtonConfigFactory
{
    public CreateDatabaseButton()
    {
        super(ModuleLoader.getInstance().getModule(BLASTModule.class), "Create BLAST Database", "BLAST.window.DatabaseWindow.buttonHandler(dataRegionName);", new LinkedHashSet<>(Arrays.asList(ClientDependency.fromPath("blast/window/DatabaseWindow.js"))));
    }
}
