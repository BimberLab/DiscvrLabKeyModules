package org.labkey.sequenceanalysis.button;

import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * User: bimber
 * Date: 7/16/2014
 * Time: 5:37 PM
 */
public class CompareVariantsButton extends SimpleButtonConfigFactory
{
    public CompareVariantsButton()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Compare Variants", "SequenceAnalysis.Buttons.compareVariantsHandler(dataRegionName);", new LinkedHashSet<>(Arrays.asList(ClientDependency.fromFilePath("sequenceanalysis/sequenceAnalysisButtons.js"))));
    }
}
