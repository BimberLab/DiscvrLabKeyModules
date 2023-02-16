/*
 * Copyright (c) 2014-2015 LabKey Corporation
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

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 6/18/2014
 * Time: 9:29 PM
 */
abstract public class AbstractCommandPipelineStep<Wrapper extends CommandWrapper> extends AbstractPipelineStep
{
    private final Wrapper _wrapper;

    public AbstractCommandPipelineStep(PipelineStepProvider<?> provider, PipelineContext ctx, Wrapper wrapper)
    {
        super(provider, ctx);
        _wrapper = wrapper;
    }

    public List<String> getClientCommandArgs() throws PipelineJobException
    {
        return getClientCommandArgs(" ");
    }

    public List<String> getClientCommandArgs(String separator) throws PipelineJobException
    {
        return getClientCommandArgs(separator, Collections.emptySet());
    }

    public List<String> getClientCommandArgs(String separator, Set<String> paramBlacklist) throws PipelineJobException
    {
        List<String> ret = new ArrayList<>();
        List<ToolParameterDescriptor> params = getProvider().getParameters();
        for (ToolParameterDescriptor desc : params)
        {
            if (desc.getCommandLineParam() != null)
            {
                if (paramBlacklist == null || !paramBlacklist.contains(desc.getName()))
                    ret.addAll(desc.getCommandLineParam().getArguments(separator, desc.extractValueForCommandLine(getPipelineCtx().getJob(), getProvider(), getStepIdx())));
            }
        }

        return ret;
    }

    public Wrapper getWrapper()
    {
        return _wrapper;
    }
}
