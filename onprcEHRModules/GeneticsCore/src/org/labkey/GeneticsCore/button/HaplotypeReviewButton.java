package org.labkey.GeneticsCore.button;

import org.labkey.GeneticsCore.GeneticsCoreModule;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UserDefinedButtonConfig;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.view.template.ClientDependency;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * User: bimber
 * Date: 7/16/2014
 * Time: 5:37 PM
 */
public class HaplotypeReviewButton extends SimpleButtonConfigFactory
{
    public HaplotypeReviewButton()
    {
        super(ModuleLoader.getInstance().getModule(GeneticsCoreModule.class), "Haplotype Review", "GeneticsCore.buttons.haplotypeHandler(dataRegionName);", new LinkedHashSet<>(Arrays.asList(ClientDependency.fromPath("geneticscore/buttons.js"))));
    }

    @Override
    public UserDefinedButtonConfig createBtn(TableInfo ti)
    {
        UserDefinedButtonConfig ret = super.createBtn(ti);
        ret.setRequiresSelectionMaxCount(0);
        ret.setRequiresSelectionMaxCount(1);

        return ret;
    }
}
