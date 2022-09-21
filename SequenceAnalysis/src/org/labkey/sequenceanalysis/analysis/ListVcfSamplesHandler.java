package org.labkey.sequenceanalysis.analysis;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.util.LinkedHashSet;
import java.util.List;

public class ListVcfSamplesHandler implements SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public ListVcfSamplesHandler()
    {

    }

    @Override
    public String getName()
    {
        return "List VCF Samples";
    }

    @Override
    public String getDescription()
    {
        return "This will list the samples in the selcted VCF(s)";
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && SequenceUtil.FILETYPE.vcf.getFileType().isType(o.getFile());
    }

    @Override
    public boolean requiresSingleGenome()
    {
        return false;
    }

    @Override
    public @Nullable String getButtonJSHandler()
    {
        return "SequenceAnalysis.window.VcfSampleWindow.buttonHandler";
    }

    @Override
    public @Nullable ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return null;
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
    }

    @Override
    public LinkedHashSet<String> getClientDependencies()
    {
        return new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/window/VcfSampleWindow.js"));
    }

    @Override
    public boolean useWorkbooks()
    {
        return false;
    }

    @Override
    public boolean doRunRemote()
    {
        return false;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return null;
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }
}
