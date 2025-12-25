package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.List;

public class PerformTcrClustering extends AbstractTcrClustRStep
{
    public PerformTcrClustering(PipelineContext ctx, PerformTcrClustering.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("PerformTcrClustering", "tcrClustR", "tcrClustR", "This will run tcrClustR to cluster TCRs by similarity.", List.of(
                SeuratToolParameter.create("organism", "Organism", "The organism to use",  "ldk-simplecombo", new JSONObject()
                    {{
                        put("multiSelect", false);
                        put("allowBlank", false);
                        put("storeValues", "human;rhesus;mouse");
                        put("initialValues", "human");
                        put("delimiter", ";");
                        put("joinReturnValue", true);
                    }}, null),
                    SeuratToolParameter.create("chains", "Chains", "The chains to process",  "ldk-simplecombo", new JSONObject()
                    {{
                        put("multiSelect", true);
                        put("allowBlank", false);
                        put("storeValues", "TRA;TRB;TRG;TRD");
                        put("initialValues", "TRA;TRB;TRG;TRD");
                        put("delimiter", ";");
                        put("joinReturnValue", true);
                    }}, "TRA;TRB;TRG;TRD", null, true, true)
            ), null, "https://github.com/bimberlabinternal/tcrClustR/");
        }

        @Override
        public PerformTcrClustering create(PipelineContext ctx)
        {
            return new PerformTcrClustering(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "tcr";
    }
}
