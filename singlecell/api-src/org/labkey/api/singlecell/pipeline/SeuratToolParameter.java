package org.labkey.api.singlecell.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;

public class SeuratToolParameter extends ToolParameterDescriptor
{
    private String _rName;

    public SeuratToolParameter(String name, String label, String description, String fieldXtype, @Nullable Object defaultValue, @Nullable JSONObject additionalExtConfig, String rName)
    {
        super(null, name, label, description, fieldXtype, defaultValue, additionalExtConfig);

        _rName = rName;
    }

    public static SeuratToolParameter create(String name, String label, String description, String fieldXtype, @Nullable JSONObject additionalExtConfig, @Nullable Object defaultValue)
    {
        return new SeuratToolParameter(name, label, description, fieldXtype, defaultValue, additionalExtConfig, null);
    }

    public static SeuratToolParameter create(String name, String label, String description, String fieldXtype, @Nullable JSONObject additionalExtConfig, @Nullable Object defaultValue, String rName)
    {
        return new SeuratToolParameter(name, label, description, fieldXtype, defaultValue, additionalExtConfig, rName);
    }

    public String getVariableName()
    {
        return _rName == null ? getName() : _rName;
    }
}
