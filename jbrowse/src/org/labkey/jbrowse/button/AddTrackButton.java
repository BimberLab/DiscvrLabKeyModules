package org.labkey.jbrowse.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.jbrowse.JBrowseModule;

import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 7/16/2014
 * Time: 5:37 PM
 */
public class AddTrackButton extends SimpleButtonConfigFactory
{
    public AddTrackButton()
    {
        super(ModuleLoader.getInstance().getModule(JBrowseModule.class), "Add To JBrowse Session", "JBrowse.window.DatabaseWindow.trackHandler(dataRegionName);", List.of(ClientDependency.supplierFromPath("jbrowse/window/DatabaseWindow.js")));
        setPermission(UpdatePermission.class);
    }
}
