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
import org.labkey.singlecell.SingleCellModule;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        ret.addAll(CellHashingService.get().getDefaultHashingParams(true));

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
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            CellHashingService.get().prepareHashingAndCiteSeqFilesIfNeeded(outputDir, job, support, "readsetId", params.optBoolean("excludeFailedcDNA", true), true, false);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, SequenceOutputHandler.JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            Map<Integer, Integer> readsetToHashing = CellHashingService.get().getCachedHashingReadsetMap(ctx.getSequenceSupport());
            ctx.getLogger().debug("total cached readset to hashing pairs: " + readsetToHashing.size());

            for (SequenceOutputFile so : inputFiles)
            {
                ctx.getLogger().info("processing file: " + so.getName());

                File barcodes = getBarcodesFromSeurat(so.getFile());

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

                CellRangerCellHashingHandler.processBarcodeFile(ctx, barcodes, rs, htoReadset, so.getLibrary_id(), action, getClientCommandArgs(ctx.getParams()), false, CATEGORY);
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

    public static File getBarcodesFromSeurat(File seuratObj) throws PipelineJobException
    {
        File barcodes = new File(seuratObj.getParentFile(), seuratObj.getName().replaceAll("seurat.rds", "cellBarcodes.csv"));
        if (!barcodes.exists())
        {
            throw new PipelineJobException("Unable to find expected cell barcodes file.  This might indicate the seurat object was created with an older version of the pipeline.  Expected: " + barcodes.getPath());
        }

        return barcodes;
    }
}
