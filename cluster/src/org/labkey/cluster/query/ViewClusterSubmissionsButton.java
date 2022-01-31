package org.labkey.cluster.query;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.cluster.ClusterModule;

import java.util.Arrays;

public class ViewClusterSubmissionsButton extends SimpleButtonConfigFactory
{
    public ViewClusterSubmissionsButton()
    {
        super(ModuleLoader.getInstance().getModule(ClusterModule.class), "View Cluster Submissions", "Cluster.Utils.buttonHandlerForSubmissions(dataRegionName);", Arrays.asList(
                ClientDependency.supplierFromPath("Ext4"),
                ClientDependency.supplierFromPath("cluster/window/Buttons.js")
        ));
    }
}