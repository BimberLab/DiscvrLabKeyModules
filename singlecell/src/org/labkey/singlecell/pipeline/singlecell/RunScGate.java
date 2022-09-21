package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
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
            super("RunScGate", "Run scGate", "scGate", "This will run scGate with the default built-in models and create a consensus call.", Arrays.asList(
                    SeuratToolParameter.create("useRhesusDefaults", "Use Rhesus Default", "If checked, this will use the rhesus defaults, RIRA::RunScGateWithRhesusModels, and ignore the selections below", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    SeuratToolParameter.create("modelNames", "Model(s)", "The set of scGate modules to test.", "ldk-simplecombo", new JSONObject()
                    {{
                        put("multiSelect", true);
                        put("storeValues", "Bcell;Tcell;NK;Myeloid;Stromal;pDC;Erythrocyte;Epithelial;Platelet_MK");
                        put("initialValues", "Bcell;Tcell;NK;Myeloid;Stromal;pDC;Epithelial;Epithelial;Platelet_MK");
                        put("delimiter", ";");
                        put("joinReturnValue", true);
                    }}, null, null, true, true),
                    SeuratToolParameter.create("consensusModels", "Models for Consensus", "The subset of models to be considered for the consensus call.", "ldk-simplecombo", new JSONObject()
                    {{
                        put("multiSelect", true);
                        put("storeValues", "Bcell;Tcell;NK;Myeloid;Stromal;pDC;Erythrocyte;Epithelial;Platelet_MK");
                        put("initialValues", "Bcell;Tcell;NK;Myeloid;Stromal;pDC;Epithelial");
                        put("delimiter", ";");
                        put("joinReturnValue", true);
                    }}, null, null, true, true),
                    SeuratToolParameter.create("dropAmbiguousConsensusValues", "Drop Ambiguous Consensus Values", "If checked, any consensus calls that are ambiguous will be set to NA", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true)
                ), null, null);
        }

        @Override
        public RunScGate create(PipelineContext ctx)
        {
            return new RunScGate(ctx, this);
        }
    }
}
