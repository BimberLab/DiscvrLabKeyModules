/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.sequenceanalysis.run;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.reports.RScriptEngineFactory;
import org.labkey.api.services.ServiceRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 3/24/2015.
 */
public class RCommandWrapper extends AbstractCommandWrapper
{
    public RCommandWrapper(Logger logger)
    {
        super(logger);
    }

    public void executeScript(List<String> params) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getRPath());
        args.addAll(params);

        execute(args);
    }

    private String getRPath()
    {
        String exePath = "Rscript";

        //NOTE: this was added to better support team city agents, where R is not in the PATH, but RHOME is defined
        String packagePath = inferRPath(getLogger());
        if (StringUtils.trimToNull(packagePath) != null)
        {
            exePath = (new File(packagePath, exePath)).getPath();
        }

        return exePath;
    }

    private String inferRPath(Logger log)
    {
        String path;

        //preferentially use R config setup in scripting props.  only works if running locally.
        if (PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.WebServer)
        {
            LabkeyScriptEngineManager svc = ServiceRegistry.get().getService(LabkeyScriptEngineManager.class);
            for (ExternalScriptEngineDefinition def : svc.getEngineDefinitions())
            {
                if (RScriptEngineFactory.isRScriptEngine(def.getExtensions()))
                {
                    path = new File(def.getExePath()).getParent();
                    log.info("Using RSciptEngine path: " + path);
                    return path;
                }
            }
        }

        //then pipeline config
        String packagePath = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("R");
        if (StringUtils.trimToNull(packagePath) != null)
        {
            log.info("Using path from pipeline config: " + packagePath);
            return packagePath;
        }

        //then RHOME
        Map<String, String> env = System.getenv();
        if (env.containsKey("RHOME"))
        {
            log.info("Using path from RHOME: " + env.get("RHOME"));
            return env.get("RHOME");
        }

        //else assume it's in the PATH
        log.info("Unable to infer R path, using null");

        return null;
    }
}
