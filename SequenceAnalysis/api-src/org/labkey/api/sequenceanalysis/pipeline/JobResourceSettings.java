package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Created by bimber on 9/29/2016.
 */
public interface JobResourceSettings
{
    boolean isAvailable(Container c);

    List<ToolParameterDescriptor> getParams();

    Collection<String> getDockerVolumes(Container c);

    default @Nullable File inferDockerVolume(File input)
    {
        return null;
    }
}
