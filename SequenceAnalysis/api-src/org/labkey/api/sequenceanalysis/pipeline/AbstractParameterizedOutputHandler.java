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
package org.labkey.api.sequenceanalysis.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by bimber on 2/10/2015.
 */
abstract public class AbstractParameterizedOutputHandler<T> implements ParameterizedOutputHandler<T>
{
    private Module _owner;
    private String _name;
    private String _description;
    private LinkedHashSet<String> _dependencies = new LinkedHashSet<>();
    private List<ToolParameterDescriptor> _parameters = new ArrayList<>();

    public AbstractParameterizedOutputHandler(Module owner, String name, String description, LinkedHashSet<String> dependencies, List<ToolParameterDescriptor> parameters)
    {
        _owner = owner;
        _name = name;
        _description = description;

        _dependencies.add("/sequenceanalysis/window/OutputHandlerWindow.js");
        if (dependencies != null)
            _dependencies.addAll(dependencies);
        if (parameters != null)
            _parameters.addAll(parameters);
    }

    @Override
    final public String getName()
    {
        return _name;
    }

    @Override
    final public String getDescription()
    {
        return _description;
    }

    @Override
    final public String getButtonJSHandler()
    {
        return "SequenceAnalysis.window.OutputHandlerWindow.buttonHandler";
    }

    @Override
    final public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return null;
    }

    @Override
    final public Module getOwningModule()
    {
        return _owner;
    }

    @Override
    final public LinkedHashSet<String> getClientDependencies()
    {
        return _dependencies;
    }

    @Override
    public boolean useWorkbooks()
    {
        return true;
    }

    @Override
    final public List<ToolParameterDescriptor> getParameters()
    {
        return _parameters;
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    public List<String> getClientCommandArgs(JSONObject params) throws PipelineJobException
    {
        return getClientCommandArgs(" ", params);
    }

    public List<String> getClientCommandArgs(String separator, JSONObject params) throws PipelineJobException
    {
        List<String> ret = new ArrayList<>();
        List<ToolParameterDescriptor> toolParameterDescriptors = getParameters();
        for (ToolParameterDescriptor desc : toolParameterDescriptors)
        {
            if (desc.getCommandLineParam() != null)
            {
                String key = desc.getName();
                String val = StringUtils.trimToNull(params.optString(key));
                if (val != null)
                {
                    ret.addAll(desc.getCommandLineParam().getArguments(separator, val));
                }
            }
        }

        return ret;
    }
}
