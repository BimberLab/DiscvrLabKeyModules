package org.labkey.sequenceanalysis.analysis;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.ldk.table.ButtonConfigFactory;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceFileHandler;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by bimber on 8/26/2014.
 */
public class VariantSearchHandler implements SequenceFileHandler
{
    public VariantSearchHandler()
    {

    }

    @Override
    public boolean canProcess(File f)
    {
        FileType ft = new FileType(Arrays.asList("vcf", "gvcf"), "vcf", FileType.gzSupportLevel.SUPPORT_GZ);

        return ft.isType(f);
    }

    @Override
    public ButtonConfigFactory getButtonConfig()
    {
        return new SimpleButtonConfigFactory(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Variant Search", "SequenceAnalysis.Buttons.sequenceOutputHandler(dataRegionName, " + PageFlowUtil.jsString(VariantSearchHandler.class.getName()) + ");", new LinkedHashSet<>(Arrays.asList(ClientDependency.fromPath("sequenceanalysis/sequenceAnalysisButtons.js"))));
    }

    @Override
    public ActionURL getSuccessURL(Container c, User u, List<Integer> outputFileIds)
    {
        return DetailsURL.fromString("/sequenceAnalysis/variantSearch.view?outputFileIds=" + StringUtils.join(outputFileIds, ";"), c).getActionURL();
    }
}
