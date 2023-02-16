package org.labkey.api.sequenceanalysis.pipeline;

import org.json.JSONObject;

import java.util.Map;

/**
 * Created by bimber on 8/27/2016.
 */
public interface HasJobParams
{
    Map<String, String> getJobParams();

    JSONObject getParameterJson();
}
