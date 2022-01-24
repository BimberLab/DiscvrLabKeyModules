package org.labkey.singlecell.pipeline.singlecell;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.singlecell.CellHashingServiceImpl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SeuratPrototype extends AbstractCellMembraneStep
{
    public SeuratPrototype(PipelineContext ctx, SeuratPrototype.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("SeuratPrototype", "Create Seurat Prototype", "CellMembrane", "This will tag the output of this job as a seurat prototype, which is designed to be a building block for subsequent analyses.", Arrays.asList(
                    SeuratToolParameter.create("requireHashing", "Require Hashing, If Used", "If this dataset uses cell hashing, hashing calls are required", "checkbox", null, true),
                    //Reject based on hashing criteria:
                    SeuratToolParameter.create("maxHashingPctFail", "Hashing Max Fraction Failed", "The maximum fraction of cells that can have no call (i.e. not singlet or doublet). Otherwise it will fail the job. This is a number 0-1.", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 2);
                    }}, null),
                    SeuratToolParameter.create("maxHashingPctDiscordant", "Hashing Max Fraction Discordant", "The maximum fraction of cells that can have discordant calls. High discordance is usually an indication of either poor quality data, or one caller performing badly.This is a number 0-1.", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 2);
                    }}, 0.1),

                    SeuratToolParameter.create("requireCiteSeq", "Require Cite-Seq, If Used", "If this dataset uses CITE-seq, cite-seq data are required", "checkbox", null, true),

                    SeuratToolParameter.create("requireSaturation", "Require Per-Cell Saturation", "If this dataset uses TCR sequencing, these data are required", "checkbox", null, true),
                    SeuratToolParameter.create("minSaturation", "Min Average GEX Saturation", "The minimum average per-cell saturation. This is a number 0-100.", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 2);
                    }}, 0.5),

                    SeuratToolParameter.create("dietSeurat", "Run DietSeurat", "If checked, DietSeurat will be run, which removes reductions and extraneous data to save file size.", "checkbox", null, true),

                    SeuratToolParameter.create("requireSingleR", "Require SingleR", "If checked, SingleR calls are required to pass", "checkbox", null, true)
            ), null, null);
        }

        @Override
        public SeuratPrototype create(PipelineContext ctx)
        {
            return new SeuratPrototype(ctx, this);
        }
    }

    @Override
    public void init(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {
        if (inputFiles.size() > 1)
        {
            throw new PipelineJobException("Seurat prototype step expects this job to have a single input. Consider selecting the option to run jobs individually instead of merged");
        }

        if (inputFiles.get(0).getReadset() == null)
        {
            throw new PipelineJobException("Seurat prototype step expects all inputs to have a readset ID.");
        }
    }

    @Override
    protected Chunk createParamChunk(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Chunk ret = super.createParamChunk(ctx, inputObjects, outputPrefix);

        ret.bodyLines.add("usesHashing <- list()");
        ret.bodyLines.add("usesCiteSeq <- list()");

        for (SeuratObjectWrapper so : inputObjects)
        {
            Readset parentReadset = ctx.getSequenceSupport().getCachedReadset(so.getSequenceOutputFile().getReadset());
            if (parentReadset == null)
            {
                throw new PipelineJobException("Unable to find readset for outputfile: " + so.getSequenceOutputFileId());
            }

            Set<String> htosPerReadset = CellHashingServiceImpl.get().getHtosForParentReadset(parentReadset.getReadsetId(), ctx.getSourceDirectory(), ctx.getSequenceSupport(), false);
            ret.bodyLines.add("usesHashing[['" + so.getDatasetId() + "']] <- " + (htosPerReadset.size() > 1 ? "TRUE" : "FALSE"));

            boolean usesCiteseq = CellHashingService.get().usesCiteSeq(ctx.getSequenceSupport(), Collections.singletonList(so.getSequenceOutputFile()));
            ret.bodyLines.add("usesCiteSeq[['" + so.getDatasetId() + "']] <- " + (usesCiteseq ? "TRUE" : "FALSE"));
        }

        return ret;
    }

    @Override
    protected void onFailure(SequenceOutputHandler.JobContext ctx) throws PipelineJobException
    {
        RunCellHashing.copyHtmlLocally(ctx);
    }

    @Override
    public Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Output output = super.execute(ctx, inputObjects, outputPrefix);

        for (SeuratObjectWrapper wrapper : output.getSeuratObjects())
        {
            if (wrapper.getReadsetId() == null)
            {
                throw new PipelineJobException("Missing readset Id: " + wrapper.getDatasetId());
            }

            SequenceOutputFile so = new SequenceOutputFile();
            so.setFile(wrapper.getFile());
            so.setCategory("Seurat Object Prototype");

            String readsetName = ctx.getSequenceSupport().getCachedReadset(wrapper.getReadsetId()).getName();
            so.setReadset(wrapper.getReadsetId());
            so.setName(readsetName + ": Prototype Seurat Object");

            ctx.getFileManager().addSequenceOutput(so);
        }

        return output;
    }
}
