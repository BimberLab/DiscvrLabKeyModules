package org.labkey.jbrowse;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.User;
import org.labkey.jbrowse.model.JsonFile;
import org.labkey.jbrowse.pipeline.JBrowseSessionPipelineJob;

import java.io.IOException;

/**
 * Created by bimber on 11/3/2016.
 */
public class JBrowseServiceImpl extends JBrowseService
{
    private static final JBrowseServiceImpl _instance = new JBrowseServiceImpl();
    private final Logger _log = Logger.getLogger(JBrowseServiceImpl.class);

    private JBrowseServiceImpl()
    {

    }

    public static JBrowseServiceImpl get()
    {
        return _instance;
    }

    public String prepareOutputFile(User u, Logger log, Integer outputFileId, boolean forceRecreateJson, JSONObject additionalConfig) throws IOException
    {
        JBrowseRoot root = new JBrowseRoot(log);

        JsonFile ret = root.prepareOutputFile(u, outputFileId, forceRecreateJson, additionalConfig);

        return ret.getObjectId();
    }

    public void reprocessDatabase(Container c, User u, String databaseGuid) throws PipelineValidationException
    {
        PipeRoot root = PipelineService.get().getPipelineRootSetting(c);

        PipelineService.get().queueJob(JBrowseSessionPipelineJob.recreateDatabase(c, u, root, databaseGuid));
    }
}
