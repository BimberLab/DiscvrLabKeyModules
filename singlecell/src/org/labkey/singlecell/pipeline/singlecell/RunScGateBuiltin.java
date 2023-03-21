package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunScGateBuiltin extends AbstractRiraStep
{
    public RunScGateBuiltin(PipelineContext ctx, RunScGateBuiltin.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunScGateBuildin", "Run scGate (built-in)", "scGate", "This will run scGate with the default built-in models and create a consensus call.", Arrays.asList(
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
                    }}, true),
                    SeuratToolParameter.create("assayName", "Assay Name", "Passed directly to UCell::AddModuleScore_UCell.", "textfield", new JSONObject(){{

                    }}, "RNA")
                ), null, null);
        }

        @Override
        public RunScGateBuiltin create(PipelineContext ctx)
        {
            return new RunScGateBuiltin(ctx, this);
        }
    }
}
