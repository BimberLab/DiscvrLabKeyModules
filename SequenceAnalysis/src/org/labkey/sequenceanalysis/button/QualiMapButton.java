package org.labkey.sequenceanalysis.button;

import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.util.QualiMapRunner;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * User: bimber
 * Date: 7/16/2014
 * Time: 5:37 PM
 */
public class QualiMapButton extends SimpleButtonConfigFactory
{
    public QualiMapButton()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "QualiMap Report", "SequenceAnalysis.Buttons.generateQualiMapReport(dataRegionName, 'analysisIds');", new LinkedHashSet<>(Arrays.asList(ClientDependency.fromPath("sequenceanalysis/sequenceanalysisButtons.js"))));
    }

    @Override
    public boolean isAvailable(TableInfo ti)
    {
        try
        {
            QualiMapRunner.isQualiMapDirValid();
            return true;
        }
        catch (ConfigurationException e)
        {

        }

        return false;
    }
}
