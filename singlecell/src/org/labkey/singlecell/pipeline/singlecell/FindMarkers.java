package org.labkey.singlecell.pipeline.singlecell;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.stream.Collectors;

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
                    }}, null),
                    SeuratToolParameter.create("testsToUse", "Tests To Use", "The set of tests to perform.", "ldk-simplecombo", new JSONObject()
                    {{
                        put("multiSelect", true);
                        put("allowBlank", false);
                        put("storeValues", "wilcox;bimod;roc;t;negbinom;poisson;LR;MAST;DESeq2");
                        put("initialValues", "wilcox;MAST");
                        put("delimiter", ";");
                        put("joinReturnValue", true);
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

    @Override
    public String getFileSuffix()
    {
        return "markers";
    }
}
