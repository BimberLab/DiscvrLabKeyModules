package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class FindMarkers extends AbstractCellMembraneStep
{
    public FindMarkers(PipelineContext ctx, FindMarkers.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("FindMarkers", "Find Markers", "CellMembrane/Seurat", "This will run Final_All_Markers on the input object(s), save the results as a TSV.", Arrays.asList(
                    SeuratToolParameter.create("identFields", "Identity Field(s)", "When running FindMarkers, these field(s) will be used to group the data, identify markers for each group of cells. Enter one field per row.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("allowBlank", false);
                        put("height", 200);
                        put("delimiter", ",");
                        put("stripCharsRe", "/['\"]/g");
                    }}, null).delimiter(","),
                    SeuratToolParameter.create("testsToUse", "Tests To Use", "The set of tests to perform.", "ldk-simplecombo", new JSONObject()
                    {{
                        put("multiSelect", true);
                        put("allowBlank", false);
                        put("storeValues", "wilcox;bimod;roc;t;negbinom;poisson;LR;MAST;DESeq2");
                        put("initialValues", "wilcox");
                        put("delimiter", ";");
                        put("joinReturnValue", true);
                    }}, null, null, false, true),
                    SeuratToolParameter.create("pValThreshold", "pVal Threshold", "Only genes with adjusted p-values below this will be reported", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("decimalPrecision", 5);
                    }}, 0.001),
                    SeuratToolParameter.create("foldChangeThreshold", "Log2 Fold-Change Threshold", "Only genes with log2-foldchange above this will be reported", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 0.25),
                    SeuratToolParameter.create("minPct", "Min Pct", "Only test genes that are detected in a minimum fraction of min.pct cells in either of the two populations", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 0.1),
                    SeuratToolParameter.create("minDiffPct", "Min Diff Pct", "Only test genes that show a minimum difference in the fraction of detection between the two groups", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 0.1, "minDiffPct", false),
                    getSeuratThreadsParam()
                ), List.of("/sequenceanalysis/field/TrimmingTextArea.js"), null);
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

    @Override
    public String getFileSuffix()
    {
        return "markers";
    }
}
