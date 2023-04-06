package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunScGate extends AbstractRiraStep
{
    public RunScGate(PipelineContext ctx, RunScGate.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunScGate", "Run scGate", "scGate", "This will run scGate with the default rhesus RIRA models and create a consensus call.", Arrays.asList(
                    SeuratToolParameter.create("dropAmbiguousConsensusValues", "Drop Ambiguous Consensus Values", "If checked, any consensus calls that are ambiguous will be set to NA", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("assayName", "Assay Name", "Passed directly to UCell::AddModuleScore_UCell.", "textfield", new JSONObject(){{

                    }}, "RNA")
                ), null, null);
        }

        @Override
        public RunScGate create(PipelineContext ctx)
        {
            return new RunScGate(ctx, this);
        }
    }
}
