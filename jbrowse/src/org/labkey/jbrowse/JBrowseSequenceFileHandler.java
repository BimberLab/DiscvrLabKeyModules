package org.labkey.jbrowse;

import org.labkey.api.data.Container;
import org.labkey.api.ldk.table.ButtonConfigFactory;
import org.labkey.api.ldk.table.SimpleButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceFileHandler;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by bimber on 8/26/2014.
 */
public class JBrowseSequenceFileHandler implements SequenceFileHandler
{
    public JBrowseSequenceFileHandler()
    {

    }

    @Override
    public boolean canProcess(File f)
    {
        return JBrowseManager.get().canDisplayAsTrack(f);
    }

    @Override
    public ButtonConfigFactory getButtonConfig()
    {
        return new SimpleButtonConfigFactory(ModuleLoader.getInstance().getModule(JBrowseModule.class), "View In JBrowse", "JBrowse.window.ViewOutputsWindow.buttonHandler(dataRegionName);", new LinkedHashSet<>(Arrays.asList(ClientDependency.fromFilePath("jbrowse/window/ViewOutputsWindow.js"))));
    }

    @Override
    public ActionURL getSuccessURL(Container c, User u, List<Integer> outputFileIds)
    {
        return null;
    }
}
