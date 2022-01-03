package org.labkey.cluster.query;

import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.cluster.ClusterModule;

import java.util.Arrays;

public class RecoverCompletedJobsButton extends SimpleButtonConfigFactory
{
    public RecoverCompletedJobsButton()
    {
        super(ModuleLoader.getInstance().getModule(ClusterModule.class), "Requeue from Cluster JSON", "Cluster.Utils.recoverCompletedJobs(dataRegionName);", Arrays.asList(
                ClientDependency.supplierFromPath("Ext4"),
                ClientDependency.supplierFromPath("cluster/window/Buttons.js")
        ));

        setPermission(AdminPermission.class);
    }

    @Override
    public boolean isAvailable(TableInfo ti)
    {
        return super.isAvailable(ti) || ContainerManager.getRoot().equals(ti.getUserSchema().getContainer());
    }
}