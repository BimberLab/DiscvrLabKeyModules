package org.labkey.singlecell.pipeline.singlecell;

import au.com.bytecode.opencsv.CSVReader;
import org.json.old.JSONObject;
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

public class RunVision extends AbstractCellMembraneStep
{
    public RunVision(PipelineContext ctx, RunVision.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunVision", "Run VISION", "VISION", "This will run VISION, a tool for functional interpretation of scRNA-seq data, saving the result to an RDS file.", Arrays.asList(
                    SeuratToolParameter.create("metadataCols", "Field(s)", "This will subset the seuratObj to just these columns prior to running VISION.", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("allowBlank", false);
                        put("height", 200);
                        put("delimiter", ",");
                    }}, "nCount_RNA,ClusterNames_0.2,ClusterNames_0.4,ClusterNames_0.6,ClusterNames_0.8", "metadataCols", true, true).delimiter(",")
                ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }

        @Override
        public RunVision create(PipelineContext ctx)
        {
            return new RunVision(ctx, this);
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
        return "vision";
    }

    @Override
    public Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Output output = super.execute(ctx, inputObjects, outputPrefix);

        File saved = new File(ctx.getOutputDir(), "visionFiles.txt");
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
                so.setCategory("VISION Analysis");
                so.setLibrary_id(ctx.getSequenceSupport().getCachedGenomes().iterator().next().getGenomeId());
                so.setReadset(wrapper.getReadsetId());
                so.setName(wrapper.getDatasetName() == null ? wrapper.getDatasetId() : wrapper.getDatasetName() + ": VISION Analysis");

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
