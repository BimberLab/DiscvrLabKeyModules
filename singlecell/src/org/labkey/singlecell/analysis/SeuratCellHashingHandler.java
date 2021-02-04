package org.labkey.singlecell.analysis;

import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.util.FileType;
import org.labkey.singlecell.CellHashingServiceImpl;
import org.labkey.singlecell.SingleCellModule;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SeuratCellHashingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _fileType = new FileType(".seurat.rds", false);
    public static final String CATEGORY = "Seurat Cell Hashing Calls";

    public SeuratCellHashingHandler()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "Seurat GEX/Cell Hashing", "This will run CiteSeqCount/MultiSeqClassifier to generate a sample-to-cellbarcode TSV based on the cell barcodes present in the saved Seurat object.", null, getDefaultParams());
    }

    private static List<ToolParameterDescriptor> getDefaultParams()
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>();
        ret.add(ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately..", "checkbox", new JSONObject()
        {{
            put("checked", true);
        }}, false));

        ret.addAll(CellHashingService.get().getDefaultHashingParams(true, CellHashingService.BARCODE_TYPE.hashing));

        return ret;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _fileType.isType(o.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public boolean requiresSingleGenome()
    {
        return false;
    }

    public class Processor implements SequenceOutputHandler.SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile so : inputFiles)
            {
                if (so.getReadset() == null)
                {
                    throw new PipelineJobException("Seurat object lacks a readset. This might indicate this is a combo object with multiple input readsets. If so, this pipeline does not support cell hashing calling using these as inputs.");
                }
            }

            CellHashingService.get().prepareHashingAndCiteSeqFilesIfNeeded(ctx.getSourceDirectory(), ctx.getJob(), ctx.getSequenceSupport(), "readsetId", ctx.getParams().optBoolean("excludeFailedcDNA", false), true, false);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, SequenceOutputHandler.JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            Map<Integer, Integer> readsetToHashing = CellHashingServiceImpl.get().getCachedHashingReadsetMap(ctx.getSequenceSupport());
            ctx.getLogger().debug("total cached readset to hashing pairs: " + readsetToHashing.size());

            for (SequenceOutputFile so : inputFiles)
            {
                ctx.getLogger().info("processing file: " + so.getName());

                File allCellBarcodes = getCellBarcodesFromSeurat(so.getFile());

                //NOTE: by leaving null, it will simply drop the barcode prefix. Upstream checks should ensure this is a single-readset object
                File cellBarcodesParsed = CellRangerSeuratHandler.subsetBarcodes(allCellBarcodes, null);
                ctx.getFileManager().addIntermediateFile(cellBarcodesParsed);

                Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                if (rs == null)
                {
                    throw new PipelineJobException("Unable to find readset for outputfile: " + so.getRowid());
                }
                else if (rs.getReadsetId() == null)
                {
                    throw new PipelineJobException("Readset lacks a rowId for outputfile: " + so.getRowid());
                }

                Readset htoReadset = ctx.getSequenceSupport().getCachedReadset(readsetToHashing.get(rs.getReadsetId()));
                if (htoReadset == null)
                {
                    throw new PipelineJobException("Unable to find Hashing/Cite-seq readset for GEX readset: " + rs.getReadsetId());
                }

                Set<String> htosPerReadset = CellHashingServiceImpl.get().getHtosForReadset(htoReadset.getReadsetId(), ctx.getSourceDirectory());
                if (htosPerReadset.size() > 1)
                {
                    ctx.getLogger().info("Total HTOs for readset: " + htosPerReadset.size());

                    CellHashingService.CellHashingParameters parameters = CellHashingService.CellHashingParameters.createFromJson(CellHashingService.BARCODE_TYPE.hashing, ctx.getSourceDirectory(), ctx.getParams(), htoReadset, rs, null);
                    parameters.outputCategory = CATEGORY;
                    parameters.genomeId = so.getLibrary_id();
                    parameters.cellBarcodeWhitelistFile = cellBarcodesParsed;
                    parameters.allowableHtoOrCiteseqBarcodes = htosPerReadset;

                    CellHashingService.get().processCellHashingOrCiteSeq(ctx, parameters);
                }
                else if (htosPerReadset.size() == 1)
                {
                    ctx.getLogger().info("Only single HTO used for lane, skipping cell hashing calling");
                }
                else
                {
                    ctx.getLogger().info("No HTOs found for readset");
                }
            }

            ctx.addActions(action);
        }

        @Override
        public void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            for (SequenceOutputFile so : outputsCreated)
            {
                if (so.getCategory().equals(CATEGORY))
                {
                    CellHashingService.get().processMetrics(so, job, true);
                }
            }
        }
    }

    public static File getCellBarcodesFromSeurat(File seuratObj)
    {
        File barcodes = new File(seuratObj.getParentFile(), seuratObj.getName().replaceAll("seurat.rds", "cellBarcodes.csv"));
        if (!barcodes.exists())
        {
            throw new IllegalArgumentException("Unable to find expected cell barcodes file.  This might indicate the seurat object was created with an older version of the pipeline.  Expected: " + barcodes.getPath());
        }

        return barcodes;
    }
}
