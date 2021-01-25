package org.labkey.api.singlecell.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;

public class SeuratToolParameter extends ToolParameterDescriptor
{
    private String _rName;
    private boolean _includeIfEmptyOrNull;

    public SeuratToolParameter(String name, String label, String description, String fieldXtype, @Nullable Object defaultValue, @Nullable JSONObject additionalExtConfig, String rName, boolean includeIfEmptyOrNull)
    {
        super(null, name, label, description, fieldXtype, defaultValue, additionalExtConfig);

        _rName = rName;
        _includeIfEmptyOrNull = includeIfEmptyOrNull;
    }

    public static SeuratToolParameter create(String name, String label, String description, String fieldXtype, @Nullable JSONObject additionalExtConfig, @Nullable Object defaultValue)
    {
        return new SeuratToolParameter(name, label, description, fieldXtype, defaultValue, additionalExtConfig, null, true);
    }

    public static SeuratToolParameter create(String name, String label, String description, String fieldXtype, @Nullable JSONObject additionalExtConfig, @Nullable Object defaultValue, String rName, boolean includeIfEmptyOrNull)
    {
        return new SeuratToolParameter(name, label, description, fieldXtype, defaultValue, additionalExtConfig, rName, includeIfEmptyOrNull);
    }

    public String getVariableName()
    {
        return _rName == null ? getName() : _rName;
    }

    public boolean shouldIncludeInMarkdown(PipelineJob job, PipelineStepProvider provider, int stepIdx)
    {
        if (!_includeIfEmptyOrNull)
        {
            return StringUtils.trimToNull(extractValue(job, provider, stepIdx)) != null;
        }

        return true;
    }
}
