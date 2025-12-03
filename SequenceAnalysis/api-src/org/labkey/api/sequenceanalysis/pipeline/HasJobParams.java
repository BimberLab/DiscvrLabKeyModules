package org.labkey.api.sequenceanalysis.pipeline;

import org.json.JSONObject;
import org.labkey.vfs.FileLike;

import java.util.Map;

/**
 * Created by bimber on 8/27/2016.
 */
public interface HasJobParams
{
    Map<String, String> getJobParams();

    JSONObject getParameterJson();

    FileLike getParametersFile();
}
