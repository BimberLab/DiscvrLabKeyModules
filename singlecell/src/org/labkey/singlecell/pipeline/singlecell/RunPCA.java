package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class RunPCA extends AbstractOosapStep
{
    public RunPCA(PipelineContext ctx, RunPCA.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunPCA", "Run PCA", "OOSAP", "This will run standard Seurat PCA-related steps.", Arrays.asList(
                    ToolParameterDescriptor.create("variableGenesWhitelist", "Genes to Add to VariableFeatures", "These genes, entered comma-separated or one/line, will be adding to the default Seurat::VariableFeatures gene set when running PCA", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                    }}, null),
                    ToolParameterDescriptor.create("variableGenesBlacklist", "Genes to Exclude From VariableFeatures", "These genes, entered comma-separated or one/line, will be excluded from the genes passed to RunPCA (which is otherwise determined by Seurat::VariableFeatures)", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                    }}, null),
                    ToolParameterDescriptor.create("npcs", "NPCs", "Total Number of PCs to compute and store", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 50)
            ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }


        @Override
        public RunPCA create(PipelineContext ctx)
        {
            return new RunPCA(ctx, this);
        }
    }
}

