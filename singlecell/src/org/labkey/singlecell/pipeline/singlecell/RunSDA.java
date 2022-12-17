package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;

public class RunSDA extends AbstractCellMembraneStep
{
    public RunSDA(PipelineContext ctx, RunSDA.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunSDA", "Run SDA", "CellMembrane/SDA", "This will run SDA on the seurat object.", Arrays.asList(
                    SeuratToolParameter.create("numComps", "Num Comps", "Passed to SDAtools::run_SDA(). 30 is a good minimum but depends on input data complexity.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 50),
                    SeuratToolParameter.create("featureInclusionList", "Genes to Include", "These genes, entered comma-separated or one/line, will be added to the default Seurat::VariableFeatures gene set when running PCA", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/(^['\"]+)|(['\"]+$)/g");
                    }}, null).delimiter(","),
                    SeuratToolParameter.create("featureExclusionList", "Genes to Exclude", "These genes, entered comma-separated or one/line, will be excluded from the genes passed to RunPCA (which is otherwise determined by Seurat::VariableFeatures)", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/(^['\"]+)|(['\"]+$)/g");
                    }}, null).delimiter(","),
                    SeuratToolParameter.create("minAsinhThreshold", "asinh Threshold", "This is used to filter genes. Only features with asinh(TOTAL_COUNTS) above this value are included.", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("decimalPrecision", 2);
                    }}, 0.5, "minAsinhThreshold", false),
                    SeuratToolParameter.create("minLibrarySize", "Min Library Size", "Passed to dropsim::normaliseDGE() min_library_size", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 50),
                    SeuratToolParameter.create("max_iter", "Max Iterations", "Passed directly to SDAtools::run_SDA()", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 10000),
                    SeuratToolParameter.create(SEURAT_THREADS, "Max Threads", "The number of threads to use. Cannot be higher than the threads allocated to the job.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 8)
                    ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }


        @Override
        public RunSDA create(PipelineContext ctx)
        {
            return new RunSDA(ctx, this);
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
        return "sda";
    }
}


