package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class CustomGSEA extends AbstractCellMembraneStep
{
    final static String DELIM = "<>";

    public CustomGSEA(PipelineContext ctx, CustomGSEA.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CustomGSEA", "ssGSEA (Custom)", "escape/ssGSEA", "The seurat object will be subset based on the expression below, which is passed directly to Seurat's subset(subset = X).", Arrays.asList(
                    SeuratToolParameter.create("geneSets", "Gene Sets(s)", "This should contain one gene module per line, where the module is in the format (no spaces): SetName:Gene1,Gene2,Gene3. The first token is the name given to the score and the second is a comma-delimited list of gene names.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("allowBlank", false);
                        put("replaceAllWhitespace", true);
                        put("height", 150);
                        put("width", 600);
                        put("delimiter", DELIM);
                        put("stripCharsRe", "/['\"]/g");
                    }}, null).delimiter(DELIM),
                    SeuratToolParameter.create("assayName", "Input Assay Name", "The assay holding the source data.", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, "RNA"),
                    SeuratToolParameter.create("outputAssayName", "Output Assay Name", "The assay to hold the resulting scores.", "textfield", new JSONObject(){{
                        put("allowBlank", false);
                    }}, "escape.ssGSEA"),
                    SeuratToolParameter.create("performDimRedux", "Perform DimRedux", "If true, the standard seurat PCA/FindClusters/UMAP process will be run on the escape data. This may be most useful when using a customGeneSet or a smaller set of features/pathways", "checkbox", new JSONObject(){{

                    }}, false, null, true)
            ), List.of("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }


        @Override
        public CustomGSEA create(PipelineContext ctx)
        {
            return new CustomGSEA(ctx, this);
        }
    }
}
