package org.labkey.sequenceanalysis.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * Created by bimber on 1/5/2015.
 */
public class GenomeLoadButton extends SimpleButtonConfigFactory
{
    public GenomeLoadButton()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Load Genome From NCBI", "SequenceAnalysis.window.GenomeLoadWindow.buttonHandler();", new LinkedHashSet<>(Arrays.asList(ClientDependency.fromModuleName("ldk"), ClientDependency.fromModuleName("laboratory"), ClientDependency.fromPath("sequenceanalysis/window/GenomeLoadWindow.js"))));
    }
}
