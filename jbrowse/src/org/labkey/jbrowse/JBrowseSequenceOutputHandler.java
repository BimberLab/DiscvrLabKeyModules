package org.labkey.jbrowse;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by bimber on 8/26/2014.
 */
public class JBrowseSequenceOutputHandler implements SequenceOutputHandler
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
        return null;
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
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        return new LinkedHashSet<>(Arrays.asList(ClientDependency.fromPath("jbrowse/window/DatabaseWindow.js")));
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && JBrowseManager.get().canDisplayAsTrack(f.getFile());
    }

    @Override
    public void processFiles(PipelineJob job, List<SequenceOutputFile> inputFiles, JSONObject params, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
    {
        throw new UnsupportedOperationException("JBrowse output handle should not be called through this path");
    }
}
