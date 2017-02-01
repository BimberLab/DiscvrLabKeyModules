package org.labkey.api.jbrowse;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 11/3/2016.
 */
abstract public class JBrowseService
{
    static JBrowseService _instance;

    public static JBrowseService get()
    {
        return _instance;
    }

    static public void setInstance(JBrowseService instance)
    {
        _instance = instance;
    }

    public void registerArtifacts(Container c, User u, Set<Integer> outputFileIds, Map<Integer, JSONObject> additionalConfig)
    {
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(c);

    }

    abstract public String prepareOutputFile(User u, Logger log, Integer outputFileId, boolean forceRecreateJson, @Nullable JSONObject additionalConfig) throws IOException;
}
