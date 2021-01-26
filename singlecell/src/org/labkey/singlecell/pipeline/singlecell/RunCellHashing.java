package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.singlecell.pipeline.SingleCellOutput;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.singlecell.analysis.CellRangerSeuratHandler;
import org.labkey.singlecell.analysis.SeuratCellHashingHandler;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RunCellHashing extends AbstractCellHashRStep
{
    public RunCellHashing(PipelineContext ctx, RunCellHashing.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunCellHashing", "Possibly Run/Store Cell Hashing", "cellhashR", "If available, this will run cellhashR to score cells by sample.", CellHashingService.get().getDefaultHashingParams(false), null, null);
        }

        @Override
        public RunCellHashing create(PipelineContext ctx)
        {
            return new RunCellHashing(ctx, this);
        }
    }

    @Override
    protected Map<Integer, File> prepareCountData(SingleCellOutput output, SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Map<Integer, File> dataIdToCalls = new HashMap<>();

        for (SeuratObjectWrapper wrapper : inputObjects)
        {
            File finalOutput = null;
            if (wrapper.getSequenceOutputFileId() == null)
            {
                throw new PipelineJobException("Computing and appending Hashing or CITE-seq is only supported using seurat objects will a single input dataset. Consider moving this step easier in your pipeline, before merging or subsetting");
            }

            File allCellBarcodes = SeuratCellHashingHandler.getCellBarcodesFromSeurat(wrapper.getFile());

            //NOTE: by leaving null, it will simply drop the barcode prefix. Upstream checks should ensure this is a single-readset object
            File cellBarcodesParsed = CellRangerSeuratHandler.subsetBarcodes(allCellBarcodes, null);
            ctx.getFileManager().addIntermediateFile(cellBarcodesParsed);

            Readset parentReadset = ctx.getSequenceSupport().getCachedReadset(wrapper.getSequenceOutputFile().getReadset());
            if (parentReadset == null)
            {
                throw new PipelineJobException("Unable to find readset for outputfile: " + wrapper.getSequenceOutputFileId());
            }

            List<String> htosPerReadset = CellHashingService.get().getHtosForParentReadset(parentReadset.getReadsetId(), ctx.getSourceDirectory(), ctx.getSequenceSupport());
            if (htosPerReadset.size() > 1)
            {
                ctx.getLogger().info("Total barcodes for readset: " + htosPerReadset.size());

                CellHashingService.CellHashingParameters params = CellHashingService.CellHashingParameters.createFromStep(ctx, this, CellHashingService.BARCODE_TYPE.hashing, null, parentReadset, cellBarcodesParsed);
                params.outputCategory = SeuratCellHashingHandler.CATEGORY;
                params.genomeId = wrapper.getSequenceOutputFile().getLibrary_id();
                params.cellBarcodeWhitelistFile = cellBarcodesParsed;
                params.allowableHtoOrCiteseqBarcodes = htosPerReadset;

                finalOutput = CellHashingService.get().processCellHashingOrCiteSeqForParent(parentReadset, output, ctx, params);
            }
            else if (htosPerReadset.size() == 1)
            {
                ctx.getLogger().info("Only single HTO used for lane, skipping cell hashing calling");
            }
            else
            {
                ctx.getLogger().info("No HTOs found for readset");
            }

            dataIdToCalls.put(wrapper.getSequenceOutputFileId(), finalOutput);
        }

        return dataIdToCalls;
    }
}
