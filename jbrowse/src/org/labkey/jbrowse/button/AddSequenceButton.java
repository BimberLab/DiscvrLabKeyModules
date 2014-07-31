package org.labkey.jbrowse.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.jbrowse.JBrowseModule;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * User: bimber
 * Date: 7/16/2014
 * Time: 5:37 PM
 */
public class AddSequenceButton extends SimpleButtonConfigFactory
{
    public AddSequenceButton()
    {
        super(ModuleLoader.getInstance().getModule(JBrowseModule.class), "Create/Add To JBrowse Session", "JBrowse.window.DatabaseWindow.sequenceHandler(dataRegionName);", new LinkedHashSet<>(Arrays.asList(ClientDependency.fromFilePath("jbrowse/window/DatabaseWindow.js"))));
    }
}
