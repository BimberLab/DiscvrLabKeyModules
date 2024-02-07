package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.List;

public class RunScMetabolism extends AbstractCellMembraneStep
{
    public RunScMetabolism(PipelineContext ctx, RunScMetabolism.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunScMetabolism", "scMetabolism", "scMetabolism", "This will run scMetabolism to score enrichment of metabolic pathways.", List.of(
                    SeuratToolParameter.create("metabolismTypes", "Metabolism Type(s)", "The databases to use", "ldk-simplecombo", new JSONObject()
                    {{
                        put("multiSelect", true);
                        put("allowBlank", false);
                        put("storeValues", "KEGG;REACTOME");
                        put("initialValues", "KEGG;REACTOME");
                        put("delimiter", ";");
                        put("joinReturnValue", true);
                    }}, "KEGG;REACTOME", null, true, true).delimiter(";")
            ), null, null);
        }

        @Override
        public RunScMetabolism create(PipelineContext ctx)
        {
            return new RunScMetabolism(ctx, this);
        }
    }

    @Override
    public boolean createsSeuratObjects()
    {
        return true;
    }

    @Override
    public String getFileSuffix()
    {
        return "scMetabolism";
    }
}
