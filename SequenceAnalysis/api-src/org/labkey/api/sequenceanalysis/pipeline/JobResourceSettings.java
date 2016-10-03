package org.labkey.api.sequenceanalysis.pipeline;

import org.labkey.api.data.Container;

import java.util.List;

/**
 * Created by bimber on 9/29/2016.
 */
public interface JobResourceSettings
{
    public boolean isAvailable(Container c);

    public List<ToolParameterDescriptor> getParams();
}
