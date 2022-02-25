package org.labkey.singlecell.pipeline.singlecell;

import au.com.bytecode.opencsv.CSVReader;
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
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.labkey.singlecell.analysis.AbstractSingleCellHandler.SEURAT_PROTOTYPE;

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
                    SeuratToolParameter.create("requireCiteSeq", "Require Cite-Seq, If Used", "If this dataset uses CITE-seq, cite-seq data are required", "checkbox", null, true),

                    SeuratToolParameter.create("requireSaturation", "Require Per-Cell Saturation", "If this dataset uses TCR sequencing, these data are required", "checkbox", null, true),
                    SeuratToolParameter.create("minSaturation", "Min Average GEX Saturation", "The minimum average per-cell saturation. This is a number 0-100.", "ldk-numberfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 2);
                    }}, 0.5),

                    SeuratToolParameter.create("dietSeurat", "Run DietSeurat", "If checked, DietSeurat will be run, which removes reductions and extraneous data to save file size.", "checkbox", null, true),

                    SeuratToolParameter.create("requireSingleR", "Require SingleR", "If checked, SingleR calls, including singleRConsensus are required to pass", "checkbox", null, true),
                    SeuratToolParameter.create("requireScGate", "Require scGate", "If checked, scGateConsensus calls are required to pass", "checkbox", null, true)
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

        if (ctx.getSequenceSupport().getCachedGenomes().size() > 1)
        {
            throw new PipelineJobException("Expected seurat prototype step to use a single genome");
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
    public Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Output output = super.execute(ctx, inputObjects, outputPrefix);

        if (ctx.getSequenceSupport().getCachedGenomes().size() > 1)
        {
            throw new PipelineJobException("Expected seurat prototype step to use a single genome");
        }

        for (SeuratObjectWrapper wrapper : output.getSeuratObjects())
        {
            if (wrapper.getReadsetId() == null)
            {
                throw new PipelineJobException("Missing readset Id: " + wrapper.getDatasetId());
            }

            SequenceOutputFile so = new SequenceOutputFile();
            so.setFile(wrapper.getFile());
            so.setCategory(SEURAT_PROTOTYPE);
            so.setLibrary_id(ctx.getSequenceSupport().getCachedGenomes().iterator().next().getGenomeId());

            String readsetName = ctx.getSequenceSupport().getCachedReadset(wrapper.getReadsetId()).getName();
            so.setReadset(wrapper.getReadsetId());
            so.setName(readsetName + ": Prototype Seurat Object");

            List<String> descriptions = new ArrayList<>();
            File metaTable = CellHashingServiceImpl.get().getMetaTableFromSeurat(so.getFile());
            try (CSVReader reader = new CSVReader(Readers.getReader(metaTable), ','))
            {
                String[] line;

                int totalCells = 0;
                int totalSinglet = 0;
                int totalDiscordant = 0;
                int totalDoublet = 0;
                double totalSaturation = 0.0;

                int hashingIdx = -1;
                int saturationIdx = -1;
                boolean hashingUsed = true;
                while ((line = reader.readNext()) != null)
                {
                    if (hashingIdx == -1)
                    {
                        hashingIdx = Arrays.asList(line).indexOf("HTO.Classification");
                        if (hashingIdx == -1)
                        {
                            ctx.getLogger().debug("HTO.Classification field not present, skipping");
                            hashingUsed = false;
                            hashingIdx = -2;
                        }

                        saturationIdx = Arrays.asList(line).indexOf("Saturation.RNA");
                        if (saturationIdx == -1)
                        {
                            throw new PipelineJobException("Unable to find Saturation.RNA field in file: " + metaTable.getName());
                        }
                    }
                    else
                    {
                        totalCells++;
                        if (hashingUsed && hashingIdx >= 0)
                        {
                            String val = line[hashingIdx];
                            if ("Singlet".equals(val))
                            {
                                totalSinglet++;
                            }
                            else if ("Doublet".equals(val))
                            {
                                totalDoublet++;
                            }
                            else if ("Discordant".equals(val))
                            {
                                totalDiscordant++;
                            }
                            else if ("NotUsed".equals(val))
                            {
                                hashingUsed = false;
                            }
                        }

                        double saturation = Double.parseDouble(line[saturationIdx]);
                        totalSaturation += saturation;
                    }
                }

                NumberFormat pf = NumberFormat.getPercentInstance();
                pf.setMaximumFractionDigits(2);

                NumberFormat decimal = DecimalFormat.getNumberInstance();
                decimal.setGroupingUsed(false);

                descriptions.add("Total Cells: " + decimal.format(totalCells));
                if (hashingUsed)
                {
                    descriptions.add("Total Singlet: " + decimal.format(totalSinglet));
                    descriptions.add("% Singlet: " + pf.format((double) totalSinglet / (double) totalCells));
                    descriptions.add("% Doublet: " + pf.format((double) totalDoublet / (double) totalCells));
                    descriptions.add("% Discordant: " + pf.format((double) totalDiscordant / (double) totalCells));
                }
                else
                {
                    descriptions.add("Hashing not used");
                }

                descriptions.add("Mean RNA Saturation: " + (totalSaturation / (double) totalCells));
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            if (ctx.getParams().optBoolean("singleCellRawData.PrepareRawCounts.useSoupX", false))
            {
                descriptions.add("SoupX: true");
            }

            String hashingMethods = ctx.getParams().optString("singleCell.RunCellHashing.consensusMethods");
            if (hashingMethods != null)
            {
                descriptions.add("Hashing: " + hashingMethods);
            }

            String citeNormalize = ctx.getParams().optString("singleCell.AppendCiteSeq.normalizeMethod");
            if (citeNormalize != null)
            {
                descriptions.add("Cite-seq Normalization: " + citeNormalize);
            }

            if (ctx.getParams().optBoolean("singleCell.AppendCiteSeq.runCellBender", false))
            {
                descriptions.add("Cite-seq/CellBender: true");
            }

            so.setDescription(StringUtils.join(descriptions, "\n"));

            ctx.getFileManager().addSequenceOutput(so);
        }

        return output;
    }
}
