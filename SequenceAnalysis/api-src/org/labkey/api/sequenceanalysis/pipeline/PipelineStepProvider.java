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


import org.json.JSONObject;
import org.labkey.api.view.template.ClientDependency;

import java.util.Collection;
import java.util.List;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:51 PM
 *
 * This describes a tool in a sequence pipeline, enumerated by PipelineStep.StepType.  It provides description and config used
 * when generating client UI, and also a factory to create a PipelineStep, which is what runs the task.  This step is frequently
 * a wrapper around a command line tool.  Because is it possible for a single underlying tool to provide multiple processing steps,
 * the provider can return true from canCombine(), which would result in a single instance of the tool being called, allowing it to
 * read the config from all steps and run as 1 instance.  FASTQ/BAM tools are the most common example of this.
 */
public interface PipelineStepProvider<StepType extends PipelineStep>
{
    StepType create(PipelineContext context);

    /**
     * @return The name of this pipeline step.  It should be unique within other tools that are part of this pipeline StepType.
     */
    String getName();

    /**
     * @return The label to be used for this pipeline step in the UI.
     */
    String getLabel();

    /**
     * @return Optional.  The name of the underlying tool used in this step
     */
    String getToolName();

    /**
     * @return Optional.  If provided, the UI will provide a link to this tool's website
     */
    String getWebsiteURL();

    /**
     * @return A description of this step, which will be shown in the client UI
     */
    String getDescription();

    /**
     * @return A list of any addition JS or CSS files required to display this UI on the client.
     */
    Collection<ClientDependency> getClientDependencies();

    /**
     * @return A list of the input paramters used by this tool.  Most often they correspond to command line parameters, but
     * this does not necessarily need to be the case.
     */
    List<ToolParameterDescriptor> getParameters();

    /**
     * @return The tool parameter matching the supplied name
     */
    ToolParameterDescriptor getParameterByName(String name);

    default boolean hasParameter(String name)
    {
        return getParameterByName(name) != null;
    }

    /**
     * Creates the JSON object sent to the client that is used to build the client UI
     */
    JSONObject toJSON();

    Class<StepType> getStepClass();

    /**
     * Allows a given step to combine itself w/ a neighboring step to save compute time.  Should return a new provider, which will
     * replace both original provider.  Return null for no changes.
     */
    PipelineStepProvider<StepType> combineSteps(int existingStepIdx, PipelineStepCtx toCombine);
}
