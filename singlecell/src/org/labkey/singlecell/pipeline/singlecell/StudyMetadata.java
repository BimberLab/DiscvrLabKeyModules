package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class StudyMetadata extends AbstractRDiscvrStep
{
    public StudyMetadata(PipelineContext ctx, StudyMetadata.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("StudyMetadata", "Append Study Metadata", "RDiscvr", "This uses Rdiscvr to append study-specific metadata.", Arrays.asList(
                    SeuratToolParameter.create("testsToUse", "Tests To Use", "The set of tests to perform.", "ldk-simplecombo", new JSONObject()
                    {{
                        put("multiSelect", false);
                        put("allowBlank", false);
                        put("storeValues", "PC475;TB;Malaria");
                        put("delimiter", ";");
                    }}, null, null, false, false),
                    SeuratToolParameter.create("errorIfUnknownIdsFound", "Error If Unknown Ids Found", "If true, the job will fail if the seurat object contains ID not present in the metadata", "checkbox", null, true)
            ), null, null);
        }

        @Override
        public StudyMetadata create(PipelineContext ctx)
        {
            return new StudyMetadata(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "study";
    }
}

