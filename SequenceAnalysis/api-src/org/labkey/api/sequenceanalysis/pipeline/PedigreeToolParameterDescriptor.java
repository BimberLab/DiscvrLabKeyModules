package org.labkey.api.sequenceanalysis.pipeline;

import org.json.JSONObject;

public class PedigreeToolParameterDescriptor extends ToolParameterDescriptor
{
    public static String NAME = "pedigreeSource";

    private final boolean _isRequired;

    public PedigreeToolParameterDescriptor()
    {
        this(true);
    }

    public PedigreeToolParameterDescriptor(final boolean isRequired)
    {
        super(null, NAME, "Pedigree Source", "This is the table used for pedigree data", "laboratory-pedigreeselectorfield", "laboratory.subjects", new JSONObject(){{
            put("allowBlank", !isRequired);
        }});

        _isRequired = isRequired;
    }

    public boolean isRequired()
    {
        return _isRequired;
    }

    public static String getClientDependencyPath()
    {
        return "/laboratory/field/PedigreeSelectorField.js";
    }
}