package org.labkey.jbrowse;

import org.json.old.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.view.ActionURL;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by bimber on 8/26/2014.
 */
public class JBrowseSequenceOutputHandler implements SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public JBrowseSequenceOutputHandler()
    {

    }

    @Override
    public String getName()
    {
        return "View In JBrowse";
    }

    @Override
    public String getDescription()
    {
        return "If the file types are supported, they will be imported into the JBrowse genome browser, allowing you to view and inspect the data.";
    }

    @Override
    public String getButtonJSHandler()
    {
        return "JBrowse.window.DatabaseWindow.outputFilesHandler";
    }

    @Override
    public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return null;
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(JBrowseModule.class);
    }

    @Override
    public LinkedHashSet<String> getClientDependencies()
    {
        return new LinkedHashSet<>(Arrays.asList("jbrowse/window/DatabaseWindow.js"));
    }

    @Override
    public boolean useWorkbooks()
    {
        return true;
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && JBrowseManager.get().canDisplayAsTrack(f.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return false;
    }

    @Override
    public boolean doRunLocal()
    {
        return true;
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

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            throw new UnsupportedOperationException("JBrowse output handler should not be called through this path");
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }
}
