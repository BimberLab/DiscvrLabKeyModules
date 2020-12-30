package org.labkey.singlecell.analysis;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.singlecell.SingleCellModule;

import java.util.LinkedHashSet;
import java.util.List;

public class CellRangerRawDataHandler implements SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _fileType = new FileType("cloupe", false);

    public CellRangerRawDataHandler()
    {

    }

    @Override
    public String getName()
    {
        return "Download CellRanger Count Matrices";
    }

    @Override
    public String getDescription()
    {
        return "This will download the raw and filtered gene count matrices for the selected runs.";
    }

    @Override
    public boolean requiresSingleGenome()
    {
        return false;
    }

    @Override
    public @Nullable String getButtonJSHandler()
    {
        return null;
    }

    @Override
    public @Nullable ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return DetailsURL.fromString("/singlecell/downloadLoupeData.view?outputFileIds=" + (StringUtils.join(outputFileIds, "&outputFileIds=")), c).getActionURL();
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(SingleCellModule.class);
    }

    @Override
    public LinkedHashSet<String> getClientDependencies()
    {
        return null;
    }

    @Override
    public boolean useWorkbooks()
    {
        return false;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _fileType.isType(o.getFile());
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
