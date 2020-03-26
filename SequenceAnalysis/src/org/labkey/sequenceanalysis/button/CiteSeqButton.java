package org.labkey.sequenceanalysis.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.analysis.CellHashingHandler;
import org.labkey.sequenceanalysis.analysis.CiteSeqHandler;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * Created by bimber on 7/12/2017.
 */
public class CiteSeqButton extends SimpleButtonConfigFactory
{
    public CiteSeqButton()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Generate CITE-seq Table", "SequenceAnalysis.window.OutputHandlerWindow.readsetButtonHandler(dataRegionName, '" + CiteSeqHandler.class.getName() + "');", new LinkedHashSet<>(Arrays.asList(ClientDependency.fromModuleName("ldk"), ClientDependency.fromModuleName("laboratory"), ClientDependency.fromPath("sequenceanalysis/window/OutputHandlerWindow.js"))));
        setPermission(UpdatePermission.class);
    }
}