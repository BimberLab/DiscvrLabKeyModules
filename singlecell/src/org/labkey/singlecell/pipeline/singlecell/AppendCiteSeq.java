package org.labkey.singlecell.pipeline.singlecell;

import org.apache.commons.io.FileUtils;
import org.json.old.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellOutput;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.singlecell.CellHashingServiceImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppendCiteSeq extends AbstractCellHashingCiteseqStep
{
    public AppendCiteSeq(PipelineContext ctx, Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("AppendCiteSeq", "Possibly Append CITE-seq Data", "CellMembrane", "If available, this will process and append CITE-seq data to the Seurat object(s).", getParams(), null, null);
        }

        @Override
        public AppendCiteSeq create(PipelineContext ctx)
        {
            return new AppendCiteSeq(ctx, this);
        }
    }

    private static List<ToolParameterDescriptor> getParams()
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>();
        ret.add(SeuratToolParameter.create("normalizeMethod", "Normalization Method", "", "ldk-simplecombo", new JSONObject(){{
            put("storeValues", "dsb;clr");
            put("initialValues", "clr");
        }}, "clr"));

        ret.add(SeuratToolParameter.create("runCellBender", "Run CellBender", "If checked, cellbender will be run on the raw count matrix to remove background/ambient RNA signal", "checkbox", new JSONObject(){{

        }}, false));

        ret.add(SeuratToolParameter.create("dropAggregateBarcodes", "Drop Aggregate Barcodes", "If checked, any barcodes marked as protein aggregates by cellranger will be dropped.", "checkbox", new JSONObject(){{
            put("checked", true);
        }}, true));

        return ret;
    }

    @Override
    public boolean requiresCiteSeq(SequenceOutputHandler.JobContext ctx)
    {
        return true;
    }

    @Override
    public String getFileSuffix()
    {
        return "cite";
    }

    @Override
    public Collection<String> getRLibraries()
    {
        return PageFlowUtil.set("CellMembrane");
    }

    @Override
    public String getDockerContainerName()
    {
        return AbstractCellMembraneStep.CONTAINER_NAME;
    }

    @Override
    protected Map<Integer, File> prepareCountData(SingleCellOutput output, SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Map<Integer, File> dataIdToCalls = new HashMap<>();

        boolean dropAggregateBarcodes = getProvider().getParameterByName("dropAggregateBarcodes").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, true);
        for (SeuratObjectWrapper wrapper : inputObjects)
        {
            File localCopyUmiCountDir = null;
            File localAggregateCountFile = null;
            if (wrapper.getSequenceOutputFileId() == null)
            {
                throw new PipelineJobException("Append CITE-seq is only support using seurat objects will a single input dataset. Consider moving this step easier in your pipeline, before merging or subsetting");
            }

            //NOTE: by leaving null, it will simply drop the barcode prefix. Upstream checks should ensure this is a single-readset object
            File allCellBarcodes = CellHashingServiceImpl.get().getCellBarcodesFromSeurat(wrapper.getFile());
            File cellBarcodesParsed = CellHashingServiceImpl.get().subsetBarcodes(allCellBarcodes, null);
            ctx.getFileManager().addIntermediateFile(cellBarcodesParsed);

            Readset parentReadset = ctx.getSequenceSupport().getCachedReadset(wrapper.getSequenceOutputFile().getReadset());
            if (parentReadset == null)
            {
                throw new PipelineJobException("Unable to find readset for outputfile: " + wrapper.getSequenceOutputFileId());
            }

            if (CellHashingService.get().usesCiteSeq(ctx.getSequenceSupport(), Collections.singletonList(wrapper.getSequenceOutputFile())))
            {
                File existingCountMatrixUmiDir = CellHashingService.get().getExistingFeatureBarcodeCountDir(parentReadset, CellHashingService.BARCODE_TYPE.citeseq, ctx.getSequenceSupport());
                localCopyUmiCountDir = new File(ctx.getOutputDir(), "citeseqRawCounts." + parentReadset.getReadsetId());
                try
                {
                    if (localCopyUmiCountDir.exists())
                    {
                        FileUtils.deleteDirectory(localCopyUmiCountDir);
                    }

                    FileUtils.copyDirectory(existingCountMatrixUmiDir, localCopyUmiCountDir);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
                ctx.getFileManager().addIntermediateFile(localCopyUmiCountDir);

                if (dropAggregateBarcodes)
                {
                    File aggregateCountFile = new File(existingCountMatrixUmiDir.getParentFile(), "antibody_analysis/aggregate_barcodes.csv");
                    if (!aggregateCountFile.exists())
                    {
                        throw new PipelineJobException("Unable to find aggregate count file: " + aggregateCountFile.getPath());
                    }
                    localAggregateCountFile = new File(ctx.getOutputDir(), localCopyUmiCountDir.getName() + ".aggregateCounts.csv");
                    try
                    {
                        if (localAggregateCountFile.exists())
                        {
                            localAggregateCountFile.delete();
                        }

                        FileUtils.copyFile(aggregateCountFile, localAggregateCountFile);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                    ctx.getFileManager().addIntermediateFile(localAggregateCountFile);
                }

                File validAdt = CellHashingServiceImpl.get().getValidCiteSeqBarcodeMetadataFile(ctx.getSourceDirectory(), parentReadset.getReadsetId());
                if (!validAdt.exists())
                {
                    throw new PipelineJobException("Unable to find ADT metadata. expected: " + validAdt.getPath());
                }

                try
                {
                    FileUtils.copyFile(validAdt, getAdtMetadata(localCopyUmiCountDir));
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
            else
            {
                ctx.getLogger().info("CITE-seq not used, skipping: " + parentReadset.getName());
            }

            dataIdToCalls.put(wrapper.getSequenceOutputFileId(), localCopyUmiCountDir);
        }

        return dataIdToCalls;
    }

    public File getAdtMetadata(File countMatrixDir)
    {
        return new File(countMatrixDir, "adtMetadata.txt");
    }

    @Override
    protected Chunk createDataChunk(Map<Integer, File> hashingData, File outputDir)
    {
        Chunk ret = super.createDataChunk(hashingData, outputDir);

        List<String> lines = new ArrayList<>();

        lines.add("featureMetadataFiles <- list(");
        for (Integer key : hashingData.keySet())
        {
            if (hashingData.get(key) == null)
            {
                lines.add("\t'" + key + "' = NULL,");
            }
            else
            {
                lines.add("\t'" + key + "' = '" + getRelativePath(getAdtMetadata(hashingData.get(key)), outputDir) + "',");
            }
        }

        // Remove trailing comma:
        int lastIdx = lines.size() - 1;
        lines.set(lastIdx, lines.get(lastIdx).replaceAll(",$", ""));

        lines.add(")");
        lines.add("");

        ret.bodyLines.addAll(lines);

        return ret;
    }

    @Override
    public boolean isIncluded(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputs) throws PipelineJobException
    {
        return CellHashingService.get().usesCiteSeq(ctx.getSequenceSupport(), inputs);
    }
}
