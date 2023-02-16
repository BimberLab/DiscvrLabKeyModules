package org.labkey.sequenceanalysis.analysis;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class SbtGeneCountHandler implements SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private final FileType _txtType = new FileType(List.of(".txt"), ".txt", false, FileType.gzSupportLevel.NO_GZ);

    public SbtGeneCountHandler()
    {

    }

    @Override
    public String getName()
    {
        return "Append SBT To Gene Counts";
    }

    @Override
    public String getDescription()
    {
        return "This will gather SBT data associated with the readsets used to make this combined gene table, and output them as a table suitable to append or analyze in concert with this table.";
    }

    @Override
    public String getButtonJSHandler()
    {
        return null;
    }

    @Override
    public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return DetailsURL.fromString("/sequenceanalysis/sbtToGeneTable.view?outputFileId=" + outputFileIds.iterator().next(), c).getActionURL();
    }

    @Override
    public boolean useWorkbooks()
    {
        return true;
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
    }

    @Override
    public LinkedHashSet<String> getClientDependencies()
    {
        return null;
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getCategory() != null && f.getCategory().startsWith("Gene Count Table") && (_txtType.isType(f.getFile()));
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
        return new Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    private static class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }
}
