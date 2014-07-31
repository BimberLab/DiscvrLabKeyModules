package org.labkey.sequenceanalysis.api.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;

import java.util.Collection;
import java.util.List;

/**
 * User: bimber
 * Date: 6/14/2014
 * Time: 2:44 PM
 */
abstract public class AbstractAnalysisStepProvider<StepType extends AnalysisStep> extends AbstractPipelineStepProvider<StepType>
{
    public AbstractAnalysisStepProvider(String name, String label, String description, @Nullable List<ToolParameterDescriptor> parameters, @Nullable Collection<String> clientDependencyPaths, @Nullable String websiteURL)
    {
        super(name, label, description, parameters, clientDependencyPaths, websiteURL);
    }
}
