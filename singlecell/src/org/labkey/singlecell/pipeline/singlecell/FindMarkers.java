package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FindMarkers extends AbstractOosapStep
{
    public FindMarkers(PipelineContext ctx, FindMarkers.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("FindMarkers", "Find Markers", "OOSAP", "This will run Final_All_Markers on the input object(s), save the results as a TSV.", Arrays.asList(
                    ToolParameterDescriptor.create("identFields", "Identity Field(s)", "When running FindMarkers, these field(s) will be used to group the data, identify markers for each group of cells. Enter one field per row.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("allowBlank", false);
                        put("height", 200);
                        put("delimiter", ",");
                    }}, null)
            ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }

        @Override
        public FindMarkers create(PipelineContext ctx)
        {
            return new FindMarkers(ctx, this);
        }
    }

    @Override
    public boolean createsSeuratObjects()
    {
        return false;
    }


}
