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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SeuratCiteSeqHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    protected FileType _fileType = new FileType(".seurat.rds", false);
    public static final String CATEGORY = "Seurat CITE-Seq Count Matrix";

    public SeuratCiteSeqHandler()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "Seurat GEX/CITE-seq Counts", "This will run CiteSeqCount to generate a sample-to-cellbarcode TSV based on the cell barcodes present in the saved Seurat object.", null, Arrays.asList(
                ToolParameterDescriptor.create("editDistance", "Edit Distance", null, "ldk-integerfield", null, 2),
                ToolParameterDescriptor.create("excludeFailedcDNA", "Exclude Failed cDNA", "If selected, cDNAs with non-blank status fields will be omitted", "checkbox", null, true),
                ToolParameterDescriptor.create("minCountPerCell", "Min Reads/Cell (Cell Hashing)", null, "ldk-integerfield", null, 5),
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately..", "checkbox", new JSONObject()
                {{
                    put("checked", true);
                }}, false)
        ));
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
            CellHashingService.get().prepareHashingAndCiteSeqFilesIfNeeded(outputDir, job, support,"readsetId", params.optBoolean("excludeFailedcDNA", true), false, true);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, SequenceOutputHandler.JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());

            Map<Integer, Integer> readsetToCiteSeq = CellHashingServiceImpl.getCachedCiteSeqReadsetMap(ctx.getSequenceSupport());
            ctx.getLogger().debug("total cached readset to GEX/citeseq pairs: " + readsetToCiteSeq.size());

            for (SequenceOutputFile so : inputFiles)
            {
                ctx.getLogger().info("processing file: " + so.getName());

                File barcodes = SeuratCellHashingHandler.getBarcodesFromSeurat(so.getFile());

                Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                if (rs == null)
                {
                    throw new PipelineJobException("Unable to find readset for outputfile: " + so.getRowid());
                }
                else if (rs.getReadsetId() == null)
                {
                    throw new PipelineJobException("Readset lacks a rowId for outputfile: " + so.getRowid());
                }

                Readset citeseqReadset = ctx.getSequenceSupport().getCachedReadset(readsetToCiteSeq.get(rs.getReadsetId()));
                if (citeseqReadset == null)
                {
                    throw new PipelineJobException("Unable to find Cite-seq readset for GEX readset: " + rs.getReadsetId());
                }

                File adtWhitelist = CellHashingServiceImpl.getValidCiteSeqBarcodeFile(ctx.getSourceDirectory(), so.getReadset());
                File citeSeqMatrix = CellRangerCellHashingHandler.processBarcodeFile(ctx, barcodes, rs, citeseqReadset, so.getLibrary_id(), action, getClientCommandArgs(ctx.getParams()), false, CATEGORY, true, adtWhitelist, false);
                if (!citeSeqMatrix.exists())
                {
                    throw new PipelineJobException("Unable to find expected file: " + citeSeqMatrix.getPath());
                }
            }

            ctx.addActions(action);
        }
    }
}
