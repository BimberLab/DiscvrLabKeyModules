package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class CustomUCell extends AbstractRiraStep
{
    final static String DELIM = "<>";

    public CustomUCell(PipelineContext ctx, CustomUCell.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CustomUCell", "UCell (Custom)", "UCell/RIRA", "The seurat object will be subset based on the expression below, which is passed directly to Seurat's subset(subset = X).", Arrays.asList(
                    ToolParameterDescriptor.create("geneSets", "Gene Sets(s)", "This should contain one UCell module per line, where the module is in the format (no spaces): SetName:Gene1,Gene2,Gene3. The first token is the name given to UCell and the second is a comma-delimited list of gene names.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("allowBlank", false);
                        put("replaceAllWhitespace", true);
                        put("height", 150);
                        put("width", 600);
                        put("delimiter", DELIM);
                        put("stripCharsRe", "/['\"]/g");
                    }}, null),
                    SeuratToolParameter.create("storeRanks", "Store Ranks", "Passed directly to UCell::AddModuleScore_UCell.", "checkbox", new JSONObject(){{

                    }}, false),
                    SeuratToolParameter.create("assayName", "Assay Name", "Passed directly to UCell::AddModuleScore_UCell.", "textfield", new JSONObject(){{

                    }}, "RNA")
            ), List.of("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }


        @Override
        public CustomUCell create(PipelineContext ctx)
        {
            return new CustomUCell(ctx, this);
        }
    }
}
