package org.labkey.singlecell.analysis;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.IOUtil;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.DefaultPipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.CellHashingServiceImpl;
import org.labkey.singlecell.SingleCellModule;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CellRangerCellHashingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _fileType = new FileType("cloupe", false);
    public static String CATEGORY = "10x GEX Cell Hashing Calls";

    public CellRangerCellHashingHandler()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "CellRanger GEX/Cell Hashing", "This will run CiteSeqCount/MultiSeqClassifier to generate a sample-to-cellbarcode TSV based on the filtered barcodes from CellRanger.", new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/CellRangerAggrTextarea.js")), getDefaultParams());
    }

    private static List<ToolParameterDescriptor> getDefaultParams()
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>(CellHashingService.get().getDefaultHashingParams(true));
        ret.add(
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true)
        );

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
        return new CellRangerCellHashingHandler.Processor();
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

                //find TSV:
                File perCellTsv;
                File barcodeDir = null;
                for (String dirName : Arrays.asList("filtered_gene_bc_matrices", "filtered_feature_bc_matrix"))
                {
                    File f = new File(so.getFile().getParentFile(), dirName);
                    if (f.exists())
                    {
                        barcodeDir = f;
                        break;
                    }
                }

                if (barcodeDir == null)
                {
                    //this might be a re-analysis loupe directory.  in this case, use the tsne projection.csv as the whitelist:
                    File dir = new File(so.getFile().getParentFile(), "analysis");
                    dir = new File(dir, "tsne");
                    dir = new File(dir, "2_components");
                    if (!dir.exists())
                    {
                        throw new PipelineJobException("Unable to find barcode or analysis directory: " + dir.getPath());
                    }

                    perCellTsv = new File(dir, "projection.csv");
                }
                //cellranger 2 format
                else if ("filtered_gene_bc_matrices".equals(barcodeDir.getName()))
                {
                    File[] children = barcodeDir.listFiles(new FileFilter()
                    {
                        @Override
                        public boolean accept(File pathname)
                        {
                            return pathname.isDirectory();
                        }
                    });

                    if (children == null || children.length != 1)
                    {
                        throw new PipelineJobException("Expected to find a single subfolder under: " + barcodeDir.getPath());
                    }

                    perCellTsv = new File(children[0], "barcodes.tsv");
                }
                else
                {
                    perCellTsv = new File(barcodeDir, "barcodes.tsv.gz");
                }

                if (!perCellTsv.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + perCellTsv.getPath());
                }

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

                processBarcodeFile(ctx, perCellTsv, rs, htoReadset, so.getLibrary_id(), action, getClientCommandArgs(ctx.getParams()), true, CATEGORY);
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

    public static File processBarcodeFile(SequenceOutputHandler.JobContext ctx, File perCellTsv, Readset rs, Readset htoOrCiteReadset, int genomeId, RecordedAction action, List<String> commandArgs, boolean writeLoupe, String category) throws PipelineJobException
    {
        return processBarcodeFile(ctx, perCellTsv, rs, htoOrCiteReadset, genomeId, action, commandArgs, writeLoupe, category, true);
    }

    public static File processBarcodeFile(SequenceOutputHandler.JobContext ctx, File perCellTsv, Readset rs, Readset htoOrCiteReadset, int genomeId, RecordedAction action, List<String> commandArgs, boolean writeLoupe, String category, boolean generateHtoCalls) throws PipelineJobException
    {
        return processBarcodeFile(ctx, perCellTsv, rs, htoOrCiteReadset, genomeId, action, commandArgs, writeLoupe, category, true, CellHashingServiceImpl.get().getValidHashingBarcodeFile(ctx.getSourceDirectory()), generateHtoCalls);
    }

    public static File processBarcodeFile(SequenceOutputHandler.JobContext ctx, File perCellTsv, Readset rs, Readset htoOrCiteReadset, int genomeId, RecordedAction action, List<String> commandArgs, boolean writeLoupe, String category, boolean createOutputFiles, File htoBarcodeWhitelist, boolean generateHtoCalls) throws PipelineJobException
    {
        ctx.getLogger().debug("inspecting file: " + perCellTsv.getPath());

        //prepare whitelist of cell indexes
        File cellBarcodeWhitelist = CellHashingServiceImpl.get().getValidCellIndexFile(ctx.getSourceDirectory());
        Set<String> uniqueBarcodes = new HashSet<>();
        ctx.getLogger().debug("writing cell barcodes, using file: " + perCellTsv.getPath());
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(cellBarcodeWhitelist), ',', CSVWriter.NO_QUOTE_CHARACTER);CSVReader reader = new CSVReader(IOUtil.openFileForBufferedUtf8Reading(perCellTsv), '\t'))
        {
            int rowIdx = 0;
            String[] row;
            while ((row = reader.readNext()) != null)
            {
                //skip header
                rowIdx++;
                if (rowIdx > 1)
                {
                    String barcode = row[0];

                    //NOTE: 10x appends "-1" to barcodes
                    if (barcode.contains("-"))
                    {
                        barcode = barcode.split("-")[0];
                    }

                    //This format is written out by the seurat pipeline
                    if (barcode.contains("_"))
                    {
                        barcode = barcode.split("_")[1];
                    }

                    if (!uniqueBarcodes.contains(barcode))
                    {
                        writer.writeNext(new String[]{barcode});
                        uniqueBarcodes.add(barcode);
                    }
                }
            }

            ctx.getLogger().debug("rows inspected: " + (rowIdx - 1));
            ctx.getLogger().debug("unique cell barcodes: " + uniqueBarcodes.size());
            ctx.getFileManager().addIntermediateFile(cellBarcodeWhitelist);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        //prepare whitelist of barcodes, based on cDNA records
        if (!htoBarcodeWhitelist.exists())
        {
            throw new PipelineJobException("Unable to find file: " + htoBarcodeWhitelist.getPath());
        }
        ctx.getFileManager().addIntermediateFile(htoBarcodeWhitelist);

        //run CiteSeqCount.
        List<String> extraParams = new ArrayList<>();
        extraParams.addAll(commandArgs);

        boolean scanEditDistances = ctx.getParams().optBoolean("scanEditDistances", false);
        int editDistance = ctx.getParams().optInt("editDistance", 3);
        int minCountPerCell = ctx.getParams().optInt("minCountPerCell", 3);
        boolean useSeurat = ctx.getParams().optBoolean("useSeurat", true);
        boolean useMultiSeq = ctx.getParams().optBoolean("useMultiSeq", true);

        PipelineStepOutput output = new DefaultPipelineStepOutput();
        String basename = FileUtil.makeLegalName(rs.getName());
        File cellToHto = CellHashingService.get().runCiteSeqCount(output, category, htoOrCiteReadset, htoBarcodeWhitelist, cellBarcodeWhitelist, ctx.getWorkingDirectory(), basename, ctx.getLogger(), extraParams, false, minCountPerCell, ctx.getSourceDirectory(), editDistance, scanEditDistances, rs, genomeId, generateHtoCalls, createOutputFiles, useSeurat, useMultiSeq);
        ctx.getFileManager().addStepOutputs(action, output);

        ctx.getFileManager().addOutput(action, category, cellToHto);
        File html = new File(cellToHto.getParentFile(), FileUtil.getBaseName(cellToHto.getName()) + ".html");
        if (html.exists())
        {
            ctx.getFileManager().addOutput(action, "Cell Hashing Report", html);
        }

        File citeSeqCountUnknownOutput = new File(cellToHto.getParentFile(), "citeSeqUnknownBarcodes.txt");
        ctx.getFileManager().addOutput(action,"CiteSeqCount Unknown Barcodes", citeSeqCountUnknownOutput);

        if (writeLoupe)
        {
            File forLoupe = new File(ctx.getSourceDirectory(), rs.getName() + "-CiteSeqCalls.csv");
            try (CSVReader reader = new CSVReader(Readers.getReader(cellToHto), '\t'); CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(forLoupe), ',', CSVWriter.NO_QUOTE_CHARACTER))
            {
                String[] line;
                int idx = 0;
                while ((line = reader.readNext()) != null)
                {
                    idx++;

                    if (idx > 1)
                    {
                        line[0] = line[0] + "-1";
                    }

                    writer.writeNext(new String[]{line[0], line[1]});
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            if (createOutputFiles)
            {
                ctx.getFileManager().addSequenceOutput(forLoupe, rs.getName() + ": Cell Hashing Calls", "10x GEX Cell Hashing Calls (Loupe)", rs.getReadsetId(), null, genomeId, null);
            }
            else
            {
                ctx.getLogger().debug("Output file creation will be skipped");
            }
        }

        return cellToHto;
    }
}