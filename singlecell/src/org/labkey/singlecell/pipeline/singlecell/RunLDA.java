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
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RunLDA extends AbstractCellMembraneStep
{
    public RunLDA(PipelineContext ctx, RunLDA.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunLDA", "Run LDA", "CellMembrane/LDA", "This will run LDA on the seurat object.", Arrays.asList(
                    SeuratToolParameter.create("ntopics", "Num Topics", "The number of topics to generate. Can either be a single value or a comma-separated list.", "textfield", new JSONObject(){{

                    }}, 30, null, true, true).delimiter(","),
                    SeuratToolParameter.create("maxAllowableCells", "Max Allowable Cells", "If the input cells are above this value, an error will be thrown. This is designed to limit the amount of data passed to LDA. Use -1 for no limit.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", -1);
                    }}, 150000, null, true),
                    SeuratToolParameter.create("varFeatures", "# Variable Features", "The number of variable features to select.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 5000),
                    SeuratToolParameter.create(SEURAT_THREADS, "Max Threads", "The number of threads to use. Cannot be higher than the threads allocated to the job.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 8)
                ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }

        @Override
        public RunLDA create(PipelineContext ctx)
        {
            return new RunLDA(ctx, this);
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
        return "lda";
    }

    @Override
    public Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Output output = super.execute(ctx, inputObjects, outputPrefix);

        File saved = new File(ctx.getOutputDir(), "ldaFiles.txt");
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
                so.setCategory("LDA Results");
                so.setLibrary_id(ctx.getSequenceSupport().getCachedGenomes().iterator().next().getGenomeId());
                so.setReadset(wrapper.getReadsetId());
                so.setName(wrapper.getDatasetName() == null ? wrapper.getDatasetId() : wrapper.getDatasetName() + ": LDA Analysis");

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


