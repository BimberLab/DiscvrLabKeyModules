package org.labkey.api.sequenceanalysis.pipeline;

import org.json.JSONObject;

public class PedigreeToolParameterDescriptor extends ToolParameterDescriptor
{
    public static String NAME = "pedigreeSource";

    public PedigreeToolParameterDescriptor()
    {
        super(null, NAME, "Pedigree Source", "This is the table used for pedigree data", "laboratory-pedigreeselectorfield", "laboratory.subjects", new JSONObject(){{
            put("allowBlank", false);
        }});
    }

    public static String getClientDependencyPath()
    {
        return "/laboratory/field/PedigreeSelectorField.js";
    }
}