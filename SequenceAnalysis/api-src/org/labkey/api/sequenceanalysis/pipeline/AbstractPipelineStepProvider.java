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

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.view.template.ClientDependency;

import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * User: bimber
 * Date: 6/14/2014
 * Time: 12:37 PM
 */
abstract public class AbstractPipelineStepProvider<StepType extends PipelineStep> implements PipelineStepProvider<StepType>
{
    private final String _name;
    private final String _label;
    private final String _toolName;
    private final String _websiteURL;
    private final String _description;
    private final LinkedHashSet<String> _clientDependencyPaths;
    private List<ToolParameterDescriptor> _parameters;

    public AbstractPipelineStepProvider(String name, String label, @Nullable String toolName, String description, @Nullable List<ToolParameterDescriptor> parameters, @Nullable Collection<String> clientDependencyPaths, @Nullable String websiteURL)
    {
        _name = name;
        _label = label;
        _toolName = toolName;
        _description = description;
        _parameters = parameters == null ? Collections.emptyList() : parameters;
        _clientDependencyPaths = clientDependencyPaths == null ? new LinkedHashSet<>() : new LinkedHashSet<>(clientDependencyPaths);
        _websiteURL = websiteURL;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public String getLabel()
    {
        return _label;
    }

    @Override
    public String getToolName()
    {
        return _toolName;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    @Override
    public Collection<ClientDependency> getClientDependencies()
    {
        //NOTE: because ClientDependency.fromPath() fails on remote servers, lazily parse the paths here
        LinkedHashSet<ClientDependency> clientDependencies = new LinkedHashSet<>();
        for (String path : _clientDependencyPaths)
        {
            clientDependencies.add(ClientDependency.fromPath(path));
        }

        return clientDependencies;
    }

    @Override
    public String getWebsiteURL()
    {
        return _websiteURL;
    }

    @Override
    public List<ToolParameterDescriptor> getParameters()
    {
        return _parameters == null ? Collections.emptyList() : Collections.unmodifiableList(_parameters);
    }

    @Override
    public ToolParameterDescriptor getParameterByName(String name)
    {
        if (_parameters != null)
        {
            for (ToolParameterDescriptor t : _parameters)
            {
                if (name.equals(t.getName()))
                {
                    return t;
                }
            }
        }

        return null;
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put("name", getName());
        json.put("label", getLabel());
        json.put("toolName", getToolName());
        json.put("description", getDescription());
        json.put("websiteURL", getWebsiteURL());
        JSONArray parameters = new JSONArray();
        for (ToolParameterDescriptor td : getParameters())
        {
            parameters.put(td.toJSON());
        }
        json.put("parameters", parameters);

        return json;
    }

    @Override
    public Class<StepType> getStepClass()
    {
        return (Class<StepType>) SequencePipelineService.get().findSuperClassParameterType(getClass());
    }

    @Override
    public PipelineStepProvider<StepType> combineSteps(int existingStepIdx, PipelineStepCtx<StepType> toCombine)
    {
        return null;
    }
}
