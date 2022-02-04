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
            super("RunScGate", "Run scGate", "scGate", "This will run scGate with the default built-in models and create a consensus call.", Arrays.asList(
                    SeuratToolParameter.create("modelNames", "Model(s)", "The set of scGate modules to test.", "ldk-simplecombo", new JSONObject()
                    {{
                        put("multiSelect", true);
                        put("allowBlank", false);
                        put("storeValues", "Bcell;Tcell;NK;Myeloid;Stromal;pDC;Erythrocyte;Epithelial;Platelet-MK");
                        put("initialValues", "Bcell;Tcell;NK;Myeloid;Stromal;pDC;Erythrocyte;Epithelial;Platelet-MK");
                        put("delimiter", ";");
                        put("joinReturnValue", true);
                    }}, null, null, true, true)
            ), null, null);
        }

        @Override
        public RunScGate create(PipelineContext ctx)
        {
            return new RunScGate(ctx, this);
        }
    }
}
