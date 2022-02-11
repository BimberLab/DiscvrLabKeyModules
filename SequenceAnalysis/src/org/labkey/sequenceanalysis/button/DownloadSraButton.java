package org.labkey.sequenceanalysis.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.RestoreSraDataHandler;

import java.util.Arrays;

/**
 * Created by bimber on 7/12/2017.
 */
public class DownloadSraButton extends SimpleButtonConfigFactory
{
    public DownloadSraButton()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Restore Archived SRA Data", "SequenceAnalysis.window.OutputHandlerWindow.readsetButtonHandler(dataRegionName, '" + RestoreSraDataHandler.class.getName() + "');", Arrays.asList(ClientDependency.supplierFromModuleName("ldk"), ClientDependency.supplierFromModuleName("laboratory"), ClientDependency.supplierFromPath("sequenceanalysis/window/OutputHandlerWindow.js")));
        setPermission(UpdatePermission.class);
    }
}