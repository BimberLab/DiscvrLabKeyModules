package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunStarCAT extends AbstractCellMembraneStep
{
    public RunStarCAT(PipelineContext ctx, RunStarCAT.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunStarCAT", "Run StarCAT", "CellMembrane/StarCAT", "This will run StarCAT to project cells onto a reference (e.g. TCAT.V1) and append per-cell scores and program usage to metadata.", Arrays.asList(
                    SeuratToolParameter.create("reference", "Reference", "Either a built-in StarCAT reference name (e.g. TCAT.V1, MYELOID.GLIOMA.V1, BONEMARROW.CD34POS.HSPC.V1) or a path to a custom reference .tsv/.txt file.", "ldk-simplecombo", new JSONObject()
                    {{
                        put("multiSelect", false);
                        put("allowBlank", false);
                        put("editable", true);
                        put("forceSelection", false);
                        put("storeValues", "TCAT.V1;MYELOID.GLIOMA.V1;BONEMARROW.CD34POS.HSPC.V1");
                        put("initialValues", "TCAT.V1");
                        put("delimiter", ";");
                        put("joinReturnValue", true);
                    }}, "TCAT.V1"),
                    SeuratToolParameter.create("assayName", "Assay Name", "The name of the assay containing the counts matrix passed to StarCAT.", "textfield", new JSONObject()
                    {{
                        put("allowBlank", false);
                    }}, "RNA")
            ), null, null);
        }

        @Override
        public RunStarCAT create(PipelineContext ctx)
        {
            return new RunStarCAT(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "starcat";
    }
}
