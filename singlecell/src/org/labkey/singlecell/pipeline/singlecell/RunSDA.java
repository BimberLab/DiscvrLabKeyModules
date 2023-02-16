package org.labkey.singlecell.pipeline.singlecell;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
                    SeuratToolParameter.create("maxAllowableCells", "Max Allowable Cells", "If the input cells are above this value, an error will be thrown. This is designed to limit the amount of data passed to SDA. Use -1 for no limit.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", -1);
                    }}, 150000, null, true),
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
                    SeuratToolParameter.create("minFeatureCount", "Min Feature Count", "This is used to filter genes. Only features with total expression across all cells greater than or equal to this value are included. The default of 1 will include all expressed genes.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 1, "minFeatureCount", false),
                    SeuratToolParameter.create("minCellsExpressingFeature", "Min Cells Expressing Feature", "Can be used with perCellExpressionThreshold to drop features present in limited cells. Only features detected above perCellExpressionThreshold in at least minCellsExpressingFeature will be retained. If this value is less than one, it is interpreted as a percentage of total cells. If above one, it is interpreted as the min number of cells", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("decimalPrecision", 2);
                    }}, 0, "minCellsExpressingFeature", false),
                    SeuratToolParameter.create("perCellExpressionThreshold", "Per Cell Expression Threshold", "Can be used with perCellExpressionThreshold to drop features present in limited cells. Only features detected above perCellExpressionThreshold in at least minCellsExpressingFeature will be retained", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 0, "perCellExpressionThreshold", false),
                    SeuratToolParameter.create("maxFeaturesDiscarded", "Max Features Discarded", "After filtering, if fewer than this number of features remain, an error will be thrown. This is a guard against overly aggressive filtering. This can either be an integer (meaning number of features), or 0-1 (which is interpreted as a percent of the input features)", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("decimalPrecision", 2);
                    }}, 0.75),
                    SeuratToolParameter.create("minLibrarySize", "Min Library Size", "Passed to dropsim::normaliseDGE() min_library_size", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 50),
                    SeuratToolParameter.create("max_iter", "Max Iterations", "Passed directly to SDAtools::run_SDA()", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 10000),
                    SeuratToolParameter.create("save_freq", "Max Iterations", "Passed directly to SDAtools::run_SDA()", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 10000),
                    SeuratToolParameter.create(SEURAT_THREADS, "Max Threads", "The number of threads to use. Cannot be higher than the threads allocated to the job.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 8),
                    SeuratToolParameter.create("storeGoEnrichment", "Perform/Store GO Enrichment", null, "checkbox", null, true),
                    SeuratToolParameter.create("fieldNames", "Fields To Plot", "Enter one field name per line", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("allowBlank", false);
                        put("height", 150);
                        put("delimiter", ",");
                        put("stripCharsRe", "/['\"]/g");
                    }}, null, null, true).delimiter(",")
                ), List.of("/sequenceanalysis/field/TrimmingTextArea.js"), null);
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

    @Override
    public Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Output output = super.execute(ctx, inputObjects, outputPrefix);

        File saved = new File(ctx.getOutputDir(), "sdaFiles.txt");
        if (!saved.exists())
        {
            throw new PipelineJobException("Unable to find file: " + saved.getPath());
        }

        try (CSVReader reader = new CSVReader(Readers.getReader(saved), '\t'))
        {
            String[] line;
            while ((line = reader.readNext()) != null)
            {
                File rds = new File(ctx.getOutputDir(), line[1]);
                if (!rds.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + rds.getPath());
                }

                final String datasetId = line[0];
                Set<SeuratObjectWrapper> wrappers = inputObjects.stream().filter(x -> datasetId.equals(x.getDatasetId())).collect(Collectors.toSet());
                if (wrappers.size() == 0)
                {
                    throw new PipelineJobException("Unable to find seurat object wrapper for: " + datasetId);
                }
                else if (wrappers.size() > 1)
                {
                    throw new PipelineJobException("More than one seurat object wrapper matched: " + datasetId + ", found: " + wrappers.stream().map(SeuratObjectWrapper::getDatasetId).collect(Collectors.joining(", ")));
                }

                SeuratObjectWrapper wrapper = wrappers.iterator().next();

                SequenceOutputFile so = new SequenceOutputFile();
                so.setFile(rds);
                so.setCategory("SDA Results");
                so.setLibrary_id(ctx.getSequenceSupport().getCachedGenomes().iterator().next().getGenomeId());
                so.setReadset(wrapper.getReadsetId());
                so.setName(wrapper.getDatasetName() == null ? wrapper.getDatasetId() : wrapper.getDatasetName() + ": SDA Analysis");

                String jobDescription = StringUtils.trimToNull(ctx.getParams().optString("jobDescription"));
                if (jobDescription != null)
                {
                    so.setDescription(jobDescription);
                }

                ctx.getFileManager().addSequenceOutput(so);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        ctx.getFileManager().addIntermediateFile(saved);

        return output;
    }

}


