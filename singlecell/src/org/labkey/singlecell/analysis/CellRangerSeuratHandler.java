package org.labkey.singlecell.analysis;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.IOUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.CellHashingServiceImpl;
import org.labkey.singlecell.SingleCellModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CellRangerSeuratHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _fileType = new FileType("cloupe", false);
    public static final String SEURAT_MAX_THREADS = "seuratMaxThreads";
    private static final String GTF_FILE_ID = "gtfFileId";

    public CellRangerSeuratHandler()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "Run Seurat", "This will run a standard seurat-based pipeline on the selected 10x/cellranger data and save the resulting Seurat object as an rds file for external use.", new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js")), getDefaultParams());
    }

    private static List<ToolParameterDescriptor> getDefaultParams()
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>(Arrays.asList(
                ToolParameterDescriptor.create("projectName", "Output Name", "This will be used as the final sample/file name.  If blank, the readset name will be used.  The latter cannot be used when merging multiple inputs.", "textfield", new JSONObject(){{

                }}, null),
                ToolParameterDescriptor.create("doSplitJobs", "Run Separately", "If checked, each input dataset will be run separately.  Otherwise they will be merged", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, false),
                ToolParameterDescriptor.create("skipProcessing", "Skip Processing", "If checked, the initial merge and EmptyDrops processing will be run, but PCA, DimRux, etc. will be skipped.  The primary use of this is to created a merged seurat object for manual downstream processing", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, false),
                ToolParameterDescriptor.create("dimsToUse", "PCs To Use", "If non-blank, this is the number of PCs that seurat will use for dim reduction steps.", "ldk-integerfield", new JSONObject(){{

                }}, null),
                ToolParameterDescriptor.create("minDimsToUse", "Minimum PCs To Use", "If non-blank, the pipeline will attempt to infer the number of PCs to use for dim reduction, but will not use fewer than this value.", "ldk-integerfield", new JSONObject(){{

                }}, 12),
                ToolParameterDescriptor.create("doCellFilter", "Perform Cell Filtering", "If selected, cells will be filtered on pct.mito and number of unique genes.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true),
                ToolParameterDescriptor.create("doCellCycle", "Perform Cell Cycle Correction", "If selected, the pipeline will attempt to correct for cell cycle.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true),
                ToolParameterDescriptor.create("useSCTransform", "Use SCTransform", "If selected, the pipeline will use the SCtransform method instead of the standard Seurat pipeline.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("runSingleR", "Run SingleR", "If selected, SingleR will be run after Seurat processing.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true),
                ToolParameterDescriptor.create("mergeMethod", "Merge Method", "This determines whether any batch correction will be applied when merging datasets.", "ldk-simplecombo", new JSONObject(){{
                    put("storeValues", "simple;cca");
                }}, "simple"),
                ToolParameterDescriptor.create(SEURAT_MAX_THREADS, "Seurat Max Threads", "Because seurat can behave badly with multiple threads, this allows a separate cap to be used from the main job.  This will allow CITE-Seq-Count and other tools to run with more threads.", "ldk-integerfield", null, 1),
                ToolParameterDescriptor.createExpDataParam(GTF_FILE_ID, "Gene File", "This is the ID of a GTF file containing genes from this genome.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                {{
                    put("extensions", Arrays.asList("gtf"));
                    put("width", 400);
                    put("allowBlank", true);
                }}, null)
        ));

        ret.addAll(CellHashingService.get().getDefaultHashingParams(false));

        return ret;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _fileType.isType(o.getFile());
    }

    @Override
    public List<String> validateParameters(List<SequenceOutputFile> outputFiles, JSONObject params)
    {
        if (!params.optBoolean("doSplitJobs", false) && StringUtils.trimToNull(params.optString("projectName")) == null && outputFiles.size() > 1)
        {
            return Collections.singletonList("Must provide the output name when merging multiple inputs");
        }

        return null;
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
    public boolean requiresSingleGenome()
    {
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new CellRangerSeuratHandler.Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile so : inputFiles)
            {
                if (so.getReadset() != null)
                {
                    support.cacheReadset(so.getReadset(), job.getUser());
                }
                else
                {
                    job.getLogger().error("Output file lacks a readset and will be skipped: " + so.getRowid());
                }
            }

            CellHashingService.get().prepareHashingAndCiteSeqFilesIfNeeded(outputDir, job, support,"readsetId", params.optBoolean("excludeFailedcDNA", true), false, false);

            if (params.get(GTF_FILE_ID) == null)
            {
                job.getLogger().info("attempting to infer GTF:");

                //TODO: collapse by filepath
                Map<File, Integer> gtfFileToId = new HashMap<>();
                for (SequenceOutputFile so : inputFiles)
                {
                    ExpData gtf = null;
                    ExpRun run = ExperimentService.get().getExpRun(so.getRunId());
                    if (run != null)
                    {
                        //Because existing runs didnt explicitly track GTF as an input, try to infer:
                        PipelineStatusFile sf = PipelineService.get().getStatusFile(run.getJobId());
                        if (sf != null)
                        {
                            File log = new File(sf.getFilePath());
                            File paramFile = new File(log.getParentFile(), "sequenceAnalysis.json");
                            if (paramFile.exists())
                            {
                                try (BufferedReader reader = Readers.getReader(paramFile))
                                {
                                    List<String> lines = IOUtils.readLines(reader);

                                    JSONObject json = lines.isEmpty() ? new JSONObject() : new JSONObject(StringUtils.join(lines, '\n'));
                                    Integer expData = json.optInt("alignment.CellRanger.gtfFile", -1);
                                    if (expData == -1)
                                    {

                                    }

                                    gtf = ExperimentService.get().getExpData(expData);
                                }
                                catch (IOException e)
                                {
                                    throw new PipelineJobException(e);
                                }
                            }
                        }
                    }

                    if (gtf == null)
                    {
                        throw new PipelineJobException("Unable to find GTF for output: " + so.getRowid());
                    }

                    gtfFileToId.put(gtf.getFile(), gtf.getRowId());
                }

                if (gtfFileToId.size() != 1)
                {
                    throw new PipelineJobException("All inputs must use the same GTF file, found: " + StringUtils.join(gtfFileToId.values(), ","));
                }

                int gtfId = gtfFileToId.get(gtfFileToId.keySet().iterator().next());
                support.cacheExpData(ExperimentService.get().getExpData(gtfId));
                support.cacheObject(GTF_FILE_ID, gtfId);
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            ctx.addActions(action);

            int gtfId = ctx.getParams().optInt(GTF_FILE_ID, -1);
            if (gtfId == -1)
            {
                ctx.getLogger().debug("GTF file was not specified, defaulting to inferred file");
                gtfId = ctx.getSequenceSupport().getCachedObject(GTF_FILE_ID, Integer.class);
            }

            File gtfFile = ctx.getSequenceSupport().getCachedData(gtfId);
            if (!gtfFile.exists())
            {
                throw new PipelineJobException("Unable to find GTF file: " + gtfFile.getPath());
            }
            ctx.getFileManager().addInput(action, "GTF File", gtfFile);

            Set<String> rsNames = new HashSet<>();
            for (SequenceOutputFile so : inputFiles)
            {
                ctx.getFileManager().addInput(action, "CellRanger Loupe", so.getFile());
                if (so.getReadset() != null)
                {
                    rsNames.add(ctx.getSequenceSupport().getCachedReadset(so.getReadset()).getName());
                }
            }

            String outPrefix = StringUtils.trimToNull(ctx.getParams().getString("projectName"));
            if (outPrefix == null)
            {
                if (rsNames.size() == 1)
                {
                    outPrefix = rsNames.iterator().next();
                }
                else
                {
                    throw new PipelineJobException("Must provide the output prefix when merging more than one output file");
                }
            }
            outPrefix = FileUtil.makeLegalName(outPrefix);

            File seuratObj = new File(ctx.getWorkingDirectory(), outPrefix + ".seurat.rds");
            File doneFile = new File(seuratObj.getPath() + ".done");
            boolean seuratHasRun = doneFile.exists();
            if (seuratHasRun)
            {
                ctx.getLogger().info("Seurat has already run, will not repeat");
            }

            Map<SequenceOutputFile, String> dataMap = new HashMap<>();

            File pr = ctx.getFolderPipeRoot().getRootPath().getParentFile();  //drop the @files or @pipeline
            for (SequenceOutputFile so : inputFiles)
            {
                //start with seurat 3
                File subDir = new File(so.getFile().getParentFile(), "raw_feature_bc_matrix");
                if (!subDir.exists())
                {
                    //try 2
                    subDir = new File(so.getFile().getParentFile(), "raw_gene_bc_matrices");
                    if (subDir.exists())
                    {
                        //now infer subdir:
                        for (File f : subDir.listFiles())
                        {
                            if (f.isDirectory())
                            {
                                subDir = f;
                                break;
                            }
                        }
                    }
                }

                if (!subDir.exists())
                {
                    throw new PipelineJobException("Unable to find raw data for input: " + so.getFile().getPath());
                }

                try
                {
                    String subDirRel = FileUtil.relativize(pr, subDir, true);
                    ctx.getLogger().debug("pipe root: " + pr.getPath());
                    ctx.getLogger().debug("file path: " + subDir.getPath());
                    ctx.getLogger().debug("relative path: " + subDirRel);

                    //Copy raw data directory locally to avoid docker permission issues
                    String dirName = so.getRowid() + "_RawData";
                    File copyDir = new File(ctx.getWorkingDirectory(), dirName);
                    if (!seuratHasRun)
                    {
                        if (copyDir.exists())
                        {
                            ctx.getLogger().debug("Deleting directory: " + copyDir.getPath());
                            FileUtils.deleteDirectory(copyDir);
                        }

                        ctx.getLogger().debug("Copying raw data directory: " + subDir.getPath());
                        ctx.getLogger().debug("To: " + copyDir.getPath());
                        FileUtils.copyDirectory(subDir, copyDir);
                    }
                    ctx.getFileManager().addIntermediateFile(copyDir);

                    dataMap.put(so, dirName);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            File rmdScript = new File(SequenceAnalysisService.get().getScriptPath(SingleCellModule.NAME, "external/scRNAseq/Seurat3.rmd"));
            if (!rmdScript.exists())
            {
                throw new PipelineJobException("Unable to find script: " + rmdScript.getPath());
            }

            File wrapperScript = new File(SequenceAnalysisService.get().getScriptPath(SingleCellModule.NAME, "external/scRNAseq/seuratWrapper.sh"));
            if (!wrapperScript.exists())
            {
                throw new PipelineJobException("Unable to find script: " + wrapperScript.getPath());
            }

            File tmpScript = new File(ctx.getWorkingDirectory(), "script.R");
            File outHtml = new File(ctx.getWorkingDirectory(), outPrefix + ".html");
            boolean skipProcessing = ctx.getParams().optBoolean("skipProcessing", false);

            try (PrintWriter writer = PrintWriters.getPrintWriter(tmpScript))
            {
                File scriptCopy = new File(ctx.getWorkingDirectory(), rmdScript.getName());
                if (scriptCopy.exists())
                {
                    scriptCopy.delete();
                }

                IOUtil.copyFile(rmdScript, scriptCopy);
                rmdScript = scriptCopy;
                ctx.getFileManager().addIntermediateFile(rmdScript);

                scriptCopy = new File(ctx.getWorkingDirectory(), wrapperScript.getName());
                if (scriptCopy.exists())
                {
                    scriptCopy.delete();
                }

                IOUtil.copyFile(wrapperScript, scriptCopy);
                ctx.getFileManager().addIntermediateFile(scriptCopy);

                writer.println("outPrefix <- '" + outPrefix + "'");
                writer.println("resolutionToUse <- 0.6");
                for (String v : new String[]{"dimsToUse", "minDimsToUse"})
                {
                    String val = StringUtils.trimToNull(ctx.getParams().optString(v));
                    val = val == null ? "NULL" : val;

                    writer.println(v + " <- " + val);
                }

                //GTF file:
                File gtfCopy = new File(ctx.getWorkingDirectory(), gtfId + ".gtf");
                if (gtfCopy.exists())
                {
                    gtfCopy.delete();
                }
                IOUtil.copyFile(gtfFile, gtfCopy);
                ctx.getFileManager().addIntermediateFile(gtfCopy);

                writer.println("gtfFile <- '" + gtfCopy.getName() + "'");

                String mergeMethod = StringUtils.trimToNull(ctx.getParams().optString("mergeMethod"));
                mergeMethod = mergeMethod == null ? "NULL" : "'" + mergeMethod + "'";
                writer.println("mergeMethod <- " + mergeMethod);

                boolean doCellFilter = ctx.getParams().optBoolean("doCellFilter", true);
                writer.println("doCellFilter <- " + String.valueOf(doCellFilter).toUpperCase());

                writer.println("skipProcessing <- " + String.valueOf(skipProcessing).toUpperCase());

                boolean runSingleR = ctx.getParams().optBoolean("runSingleR", true);
                writer.println("runSingleR <- " + String.valueOf(runSingleR).toUpperCase());

                boolean doCellCycle = ctx.getParams().optBoolean("doCellCycle", true);
                writer.println("doCellCycle <- " + String.valueOf(doCellCycle).toUpperCase());

                boolean useSCTransform = ctx.getParams().optBoolean("useSCTransform", false);
                writer.println("useSCTransform <- " + String.valueOf(useSCTransform).toUpperCase());

                writer.println("data <- list(");
                String delim = "";
                for (SequenceOutputFile so : dataMap.keySet())
                {
                    writer.println("\t" + delim + "'" + so.getRowid() + "'='" + dataMap.get(so) + "'");
                    delim = ",";
                }
                writer.println(")");
                writer.println();
                writer.println();
                writer.println("setwd('/work')");

                writer.println("rmarkdown::render('" + rmdScript.getName() + "', clean=TRUE, output_format = 'html_document', output_file='" + outHtml.getName() + "')");
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            if (!seuratHasRun)
            {
                SimpleScriptWrapper wrapper = new SimpleScriptWrapper(ctx.getLogger());
                wrapper.setWorkingDir(ctx.getWorkingDirectory());

                Integer maxThreads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
                if (maxThreads != null)
                {
                    if (ctx.getParams().get(SEURAT_MAX_THREADS) != null)
                    {
                        maxThreads = Math.min(ctx.getParams().getInt(SEURAT_MAX_THREADS), maxThreads);
                        wrapper.addToEnvironment("SEQUENCEANALYSIS_MAX_THREADS", maxThreads.toString());
                    }
                }

                wrapper.execute(Arrays.asList("/bin/bash", wrapperScript.getName(), pr.getPath()));

                try
                {
                    FileUtils.touch(doneFile);
                    ctx.getFileManager().addIntermediateFile(doneFile);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            if (!seuratObj.exists())
            {
                throw new PipelineJobException("Unable to find expected file: " + seuratObj.getPath());
            }

            String dimsToUse = StringUtils.trimToNull(ctx.getParams().optString("dimsToUse"));
            String minDimsToUse = StringUtils.trimToNull(ctx.getParams().optString("minDimsToUse"));
            String mergeMethod = StringUtils.trimToNull(ctx.getParams().optString("mergeMethod"));

            String description = StringUtils.join(new String[]{
                    "Correct Cell Cycle: " + ctx.getParams().optBoolean("doCellCycle", true),
                    "Perform Cell Filtering: " + ctx.getParams().optBoolean("doCellFilter", true),
                    "Min. Dims To Use: " + (minDimsToUse == null ? "NA" : minDimsToUse),
                    "Dims To Use: " + (dimsToUse == null ? "automatic" : dimsToUse),
                    "Use SCTransform: " + ctx.getParams().optBoolean("useSCTransform", false),
                    "Merge method: " + mergeMethod
            }, "\n");

            if (skipProcessing)
            {
                ctx.getFileManager().addSequenceOutput(seuratObj, "Seurat Raw Counts: " + outPrefix, "Seurat Unprocessed Data", (inputFiles.size() == 1 ? inputFiles.iterator().next().getReadset() : null), null, getGenomeId(inputFiles), "Unprocessed Data");
            }
            else
            {
                ctx.getFileManager().addSequenceOutput(seuratObj, "Seurat Object: " + outPrefix, "Seurat Data", (inputFiles.size() == 1 ? inputFiles.iterator().next().getReadset() : null), null, getGenomeId(inputFiles), description);
            }

            ctx.getFileManager().addOutput(action, "Seurat Object", seuratObj);

            if (!outHtml.exists())
            {
                throw new PipelineJobException("Unable to find summary report");
            }
            ctx.getFileManager().addOutput(action, "Seurat Report", outHtml);

            if (skipProcessing)
            {
                ctx.getFileManager().addSequenceOutput(outHtml, "Seurat Report: " + outPrefix, "Seurat Report", (inputFiles.size() == 1 ? inputFiles.iterator().next().getReadset() : null), null, getGenomeId(inputFiles), "Unprocessed Data");
            }
            else
            {
                ctx.getFileManager().addSequenceOutput(outHtml, "Seurat Report: " + outPrefix, "Seurat Report", (inputFiles.size() == 1 ? inputFiles.iterator().next().getReadset() : null), null, getGenomeId(inputFiles), description);
            }

            File seuratObjRaw = new File(ctx.getWorkingDirectory(), outPrefix + ".rawData.rds");
            if (seuratObjRaw.exists())
            {
                ctx.getFileManager().addIntermediateFile(seuratObjRaw);
            }

            if (CellHashingService.get().usesCellHashing(ctx.getSequenceSupport(), ctx.getSourceDirectory()))
            {
                runCellHashing(ctx, inputFiles, seuratObj, action);
            }
            else
            {
                ctx.getLogger().info("Cell hashing was not used");
            }

            if (CellHashingService.get().usesCiteSeq(ctx.getSequenceSupport(), inputFiles))
            {
                runCiteSeq(ctx, inputFiles, seuratObj, action, outPrefix);
            }
            else
            {
                ctx.getLogger().info("CITE-seq was not used");
            }
        }

        private File getAllCellBarcodesFile(File seuratObj) throws PipelineJobException
        {
            File allCellBarcodes = new File(seuratObj.getParentFile(), seuratObj.getName().replaceAll("seurat.rds", "cellBarcodes.csv"));
            if (!allCellBarcodes.exists())
            {
                throw new PipelineJobException("Unable to find expected cell barcodes file.  This might indicate the seurat object was created with an older version of the pipeline.  Expected: " + allCellBarcodes.getPath());
            }

            return allCellBarcodes;
        }

        private void runCiteSeq(JobContext ctx, List<SequenceOutputFile> inputFiles, File seuratObj, RecordedAction action, String outPrefix) throws PipelineJobException
        {
            ctx.getLogger().info("Adding CITE-seq");

            Map<String, File> citeSeqData = new HashMap<>();
            Map<String, File> markerMetadata = new HashMap<>();
            File allCellBarcodes = getAllCellBarcodesFile(seuratObj);

            for (SequenceOutputFile so : inputFiles)
            {
                //This is the loupe file at this point
                String barcodePrefix = so.getRowid().toString();
                Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                if (rs == null)
                {
                    throw new PipelineJobException("Unable to find readset for outputfile: " + so.getRowid());
                }
                else if (rs.getReadsetId() == null)
                {
                    throw new PipelineJobException("Readset lacks a rowId for outputfile: " + so.getRowid());
                }

                File barcodes = subsetBarcodes(allCellBarcodes, barcodePrefix);
                ctx.getFileManager().addIntermediateFile(barcodes);

                // write readset-specific HTO list
                Integer citeseqReadsetId = CellHashingServiceImpl.getCachedCiteSeqReadsetMap(ctx.getSequenceSupport()).get(rs.getReadsetId());
                if (citeseqReadsetId == null)
                {
                    ctx.getLogger().info("No cite-seq readset for: " + rs.getReadsetId() + ", this probably indicates either hashing is not used or the hashing data is not available.");
                    continue;
                }

                Readset citeseqReadset = ctx.getSequenceSupport().getCachedReadset(citeseqReadsetId);
                if (citeseqReadset == null)
                {
                    throw new PipelineJobException("Unable to find Cite-seq readset for GEX readset: " + rs.getReadsetId());
                }

                File perReadsetAdts = CellHashingServiceImpl.getValidCiteSeqBarcodeFile(ctx.getSourceDirectory(), rs.getReadsetId());
                long adtsForReadset = !perReadsetAdts.exists() ? 0 : SequencePipelineService.get().getLineCount(perReadsetAdts) - 1;

                if (adtsForReadset > 0)
                {
                    ctx.getLogger().info("Total ADTs for readset: " + adtsForReadset);
                    File countMatrix = CellRangerCellHashingHandler.processBarcodeFile(ctx, barcodes, rs, citeseqReadset, so.getLibrary_id(), action, getClientCommandArgs(ctx.getParams()), false, SeuratCiteSeqHandler.CATEGORY, true, perReadsetAdts, false);
                    citeSeqData.put(barcodePrefix, countMatrix.getParentFile());
                    File perReadsetAdtMetadata = CellHashingServiceImpl.getValidCiteSeqBarcodeMetadataFile(ctx.getSourceDirectory(), rs.getReadsetId());
                    markerMetadata.put(barcodePrefix, perReadsetAdtMetadata);
                }
                else
                {
                    ctx.getLogger().info("No ADTs found for readset: " + rs.getReadsetId());
                }
            }

            if (!citeSeqData.isEmpty())
            {
                ctx.getLogger().info("Storing cite-seq data in seurat object");
                File outHtml = appendCiteSeqToSeurat(ctx, seuratObj, citeSeqData, markerMetadata);

                ctx.getFileManager().addSequenceOutput(outHtml, "CITE-Seq Report: " + outPrefix, "CITE-Seq Report", (inputFiles.size() == 1 ? inputFiles.iterator().next().getReadset() : null), null, getGenomeId(inputFiles), null);
            }
            else
            {
                ctx.getLogger().info("CITE-seq was not used.  Will not append to seurat");
            }
        }

        private void runCellHashing(JobContext ctx, List<SequenceOutputFile> inputFiles, File seuratObj, RecordedAction action) throws PipelineJobException
        {
            ctx.getLogger().info("Adding cell hashing");

            Map<String, File> finalCalls = new HashMap<>();
            File allCellBarcodes = getAllCellBarcodesFile(seuratObj);

            for (SequenceOutputFile so : inputFiles)
            {
                //This is the loupe file at this point
                String barcodePrefix = so.getRowid().toString();
                Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                if (rs == null)
                {
                    throw new PipelineJobException("Unable to find readset for outputfile: " + so.getRowid());
                }
                else if (rs.getReadsetId() == null)
                {
                    throw new PipelineJobException("Readset lacks a rowId for outputfile: " + so.getRowid());
                }

                File barcodes = subsetBarcodes(allCellBarcodes, barcodePrefix);
                ctx.getFileManager().addIntermediateFile(barcodes);

                // write readset-specific HTO list
                Integer hashingReadsetId = CellHashingServiceImpl.get().getCachedHashingReadsetMap(ctx.getSequenceSupport()).get(rs.getReadsetId());
                if (hashingReadsetId == null)
                {
                    ctx.getLogger().info("No hashing readset for: " + rs.getReadsetId() + ", this probably indicates either hashing is not used or the hashing data is not available.");
                    continue;
                }

                Readset htoReadset = ctx.getSequenceSupport().getCachedReadset(hashingReadsetId);
                if (htoReadset == null)
                {
                    throw new PipelineJobException("Unable to find hashing readset for GEX readset: " + rs.getReadsetId());
                }

                File perReadsetHtos = new File(allCellBarcodes.getParentFile(), "allowableHtos." + barcodePrefix + ".txt");
                int htosForReadset = 0;
                try (CSVReader reader = new CSVReader(Readers.getReader(CellHashingServiceImpl.get().getCDNAInfoFile(ctx.getSourceDirectory())), '\t'); CSVWriter bcWriter = new CSVWriter(PrintWriters.getPrintWriter(perReadsetHtos), ',', CSVWriter.NO_QUOTE_CHARACTER))
                {
                    String[] line;
                    while ((line = reader.readNext()) != null)
                    {
                        if (hashingReadsetId.toString().equals(line[5]))
                        {
                            htosForReadset++;
                            bcWriter.writeNext(new String[]{line[8], line[7]});
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                if (htosForReadset > 1)
                {
                    ctx.getLogger().info("Total HTOs for readset: " + htosForReadset);
                    finalCalls.put(barcodePrefix, CellRangerCellHashingHandler.processBarcodeFile(ctx, barcodes, rs, htoReadset, so.getLibrary_id(), action, getClientCommandArgs(ctx.getParams()), false, SeuratCellHashingHandler.CATEGORY, true, perReadsetHtos, true));
                }
                else if (htosForReadset == 1)
                {
                    ctx.getLogger().info("Only single HTO used for lane, skipping cell hashing calling");
                }
                else
                {
                    ctx.getLogger().info("No HTOs found for readset");
                }
            }

            if (!finalCalls.isEmpty())
            {
                ctx.getLogger().info("Storing cell hashing calls in seurat object");
                appendHashingCallsToSeurat(ctx, seuratObj, finalCalls);
            }
            else
            {
                ctx.getLogger().info("Cell hashing was not used.  will not append to seurat");
            }
        }

        private File subsetBarcodes(File allCellBarcodes, String barcodePrefix) throws PipelineJobException
        {
            //Subset barcodes by dataset:
            File barcodes = new File(allCellBarcodes.getParentFile(), "cellBarcodeWhitelist." + barcodePrefix + ".txt");
            try (CSVReader reader = new CSVReader(Readers.getReader(allCellBarcodes), '\t'); CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(barcodes), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    String barcode = line[0];
                    if (barcode.startsWith(barcodePrefix + "_"))
                    {
                        barcode = barcode.split("_")[1];
                        writer.writeNext(new String[]{barcode});
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            return barcodes;
        }

        private File appendCiteSeqToSeurat(JobContext ctx, File seuratObj, Map<String, File> citeseqData, Map<String, File> perReadsetAdtMap) throws PipelineJobException
        {
            File rScript = new File(seuratObj.getParentFile(), "appendCiteSeq.Rmd");
            File bashScript = new File(seuratObj.getParentFile(), "runDockerForCiteSeq.sh");

            File localRoot = seuratObj.getParentFile();

            File outputHtml = new File(localRoot, FileUtil.getBaseName(seuratObj) + ".citeseq.html");

            Set<File> toDelete = new HashSet<>();
            try (PrintWriter rWriter = PrintWriters.getPrintWriter(rScript); PrintWriter bashWriter = PrintWriters.getPrintWriter(bashScript))
            {
                rWriter.println("---");
                rWriter.println("   title: 'CITE-seq'");
                rWriter.println("---");

                rWriter.println("```{r setup}");
                rWriter.println("library(OOSAP)");
                rWriter.println("```");
                rWriter.println("");

                rWriter.println("```{r citeseq}");
                rWriter.println("seuratObj <- readRDS('" + seuratObj.getName() + "')");
                rWriter.println("initialCells <- ncol(seuratObj)");
                rWriter.println("citeSeq <- list(");
                int idx = 0;
                for (String barcodePrefix : citeseqData.keySet()) {
                    idx++;
                    String localCopy = ensureLocalCopy(localRoot, toDelete, citeseqData.get(barcodePrefix), ctx.getLogger());
                    rWriter.println("'" + barcodePrefix + "' = '" + localCopy + "'" + (idx < citeseqData.size() ? "," : ""));
                }
                rWriter.println(")");
                rWriter.println("");

                rWriter.println("perReadsetAdtMap <- list(");
                idx = 0;
                for (String barcodePrefix : perReadsetAdtMap.keySet()) {
                    idx++;
                    String localCopy = ensureLocalCopy(localRoot, toDelete, perReadsetAdtMap.get(barcodePrefix), ctx.getLogger());
                    rWriter.println("'" + barcodePrefix + "' = '" + localCopy + "'" + (idx < citeseqData.size() ? "," : ""));
                }
                rWriter.println(")");
                rWriter.println("");

                rWriter.println("for (barcodePrefix in names(citeSeq)) {");
                rWriter.println("   seuratObj <- OOSAP:::AppendCiteSeq(seuratObj = seuratObj, countMatrixDir = citeSeq[[barcodePrefix]], barcodePrefix = barcodePrefix, featureLabelTable = perReadsetAdtMap[[barcodePrefix]])");
                rWriter.println("}");
                rWriter.println("if (ncol(seuratObj) != initialCells) { stop('Cell count not equal after appending cite-seq calls!') }");
                rWriter.println("saveRDS(seuratObj, file = '" + seuratObj.getName() + "')");
                rWriter.println("```");

                rWriter.println("```{r Plot}");
                rWriter.println("OOSAP:::.PlotCiteSeqCountData(seuratObj)");
                rWriter.println("```");

                rWriter.println("```{r SessionInfo}");
                rWriter.println("sessionInfo()");
                rWriter.println("```");


                bashWriter.println("#!/bin/bash");
                bashWriter.println("set -e");
                bashWriter.println("set -x");
                bashWriter.println("DOCKER=/opt/acc/sbin/exadocker");
                bashWriter.println("WD=`pwd`");
                bashWriter.println("HOME=`echo ~/`");

                Integer maxRam = SequencePipelineService.get().getMaxRam();
                String ramOpts = "";
                if (maxRam != null)
                {
                    ramOpts = " --memory=" +maxRam  +"g ";
                }

                bashWriter.println("sudo $DOCKER pull bimberlab/oosap");
                bashWriter.println("sudo $DOCKER run --rm=true " + ramOpts + "-v \"${WD}:/work\" -v \"${HOME}:/homeDir\" -u $UID -e USERID=$UID -w /work -e HOME=/homeDir bimberlab/oosap Rscript -e \"" + "rmarkdown::render('" + rScript.getName() + "', output_file = '" + outputHtml.getName() + "')\"");

            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            try
            {
                SimpleScriptWrapper wrapper = new SimpleScriptWrapper(ctx.getLogger());
                wrapper.setWorkingDir(seuratObj.getParentFile());
                wrapper.execute(Arrays.asList("/bin/bash", bashScript.getName()));

                for (File f : toDelete)
                {
                    ctx.getLogger().debug("deleting local copy: " + f.getPath());
                    if (f.isDirectory())
                    {
                        FileUtils.deleteDirectory(f);
                    }
                    else
                    {
                        f.delete();
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            return outputHtml;
        }

        private String ensureLocalCopy(File localRoot, Set<File> toDelete, File toCopy, Logger log) throws PipelineJobException
        {
            log.info("copying file locally: " + toCopy.getPath());

            if (toCopy.getPath().startsWith(localRoot.getPath()))
            {
                return FileUtil.relativePath(localRoot.getPath(), toCopy.getPath());
            }

            try
            {
                File localCopy;
                if (toCopy.isDirectory())
                {
                    localCopy = new File(localRoot, toCopy.getName());

                    File umiDir = new File(toCopy, "umi_count");
                    if (!umiDir.exists())
                    {
                        throw new PipelineJobException("Missing umi_count dir: " + umiDir.getPath());
                    }

                    if (localCopy.exists())
                    {
                        log.info("local copy exists, skipping: " + localCopy.getPath());
                    }
                    else
                    {
                        FileUtils.copyDirectory(umiDir, localCopy);
                    }
                }
                else
                {
                    localCopy = new File(localRoot, toCopy.getName());

                    if (localCopy.exists())
                    {
                        log.info("local copy exists, skipping: " + localCopy.getPath());
                    }
                    else
                    {
                        FileUtils.copyFile(toCopy, localCopy);
                    }
                }

                log.debug("destination: " + localCopy.getPath());
                toDelete.add(localCopy);

                return FileUtil.relativePath(localRoot.getPath(), localCopy.getPath());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private void appendHashingCallsToSeurat(JobContext ctx, File seuratObj, Map<String, File> finalCalls) throws PipelineJobException
        {
            File rScript = new File(seuratObj.getParentFile(), "appendHashing.R");
            File bashScript = new File(seuratObj.getParentFile(), "runDockerForHashing.sh");

            try (PrintWriter rWriter = PrintWriters.getPrintWriter(rScript); PrintWriter bashWriter = PrintWriters.getPrintWriter(bashScript))
            {
                rWriter.println("library(OOSAP)");
                rWriter.println("seuratObj <- readRDS('" + seuratObj.getName() + "')");
                rWriter.println("initialCells <- ncol(seuratObj)");
                rWriter.println("callsFiles <- list(");
                int idx = 0;
                for (String barcodePrefix : finalCalls.keySet())
                {
                    idx++;
                    rWriter.println("'" + barcodePrefix + "' = '" + finalCalls.get(barcodePrefix).getName() + "'" + (idx < finalCalls.size() ? "," : ""));
                }

                rWriter.println(")");
                rWriter.println("");
                rWriter.println("for (barcodePrefix in names(callsFiles)) {");
                rWriter.println("   seuratObj <- OOSAP:::AppendCellHashing(seuratObj = seuratObj, barcodeCallFile = callsFiles[[barcodePrefix]], barcodePrefix = barcodePrefix)");
                rWriter.println("}");
                rWriter.println("if (ncol(seuratObj) != initialCells) { stop('Cell count not equal after appending cell hashing calls!') }");
                rWriter.println("saveRDS(seuratObj, file = '" + seuratObj.getName() + "')");

                bashWriter.println("#!/bin/bash");
                bashWriter.println("set -e");
                bashWriter.println("set -x");
                bashWriter.println("DOCKER=/opt/acc/sbin/exadocker");
                bashWriter.println("WD=`pwd`");
                bashWriter.println("HOME=`echo ~/`");

                Integer maxRam = SequencePipelineService.get().getMaxRam();
                String ramOpts = "";
                if (maxRam != null)
                {
                    ramOpts = " --memory=" + maxRam + "g ";
                }

                bashWriter.println("sudo $DOCKER pull bimberlab/oosap");
                bashWriter.println("sudo $DOCKER run --rm=true " + ramOpts + "-v \"${WD}:/work\" -v \"${HOME}:/homeDir\" -u $UID -e USERID=$UID -w /work -e HOME=/homeDir bimberlab/oosap Rscript --vanilla " + rScript.getName());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            SimpleScriptWrapper wrapper = new SimpleScriptWrapper(ctx.getLogger());
            wrapper.setWorkingDir(seuratObj.getParentFile());
            wrapper.execute(Arrays.asList("/bin/bash", bashScript.getName()));
        }

        @Override
        public void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            for (SequenceOutputFile so : outputsCreated)
            {
                if (so.getFile() != null && so.getFile().getPath().endsWith(".seurat.rds"))
                {
                    File metrics = new File(so.getFile().getPath().replaceAll(".seurat.rds", ".summary.txt"));
                    if (metrics.exists())
                    {
                        processMetricsFile(job, metrics, so);
                    }
                    else
                    {
                        job.getLogger().warn("Unable to find metrics file: " + metrics.getPath());
                    }
                }
                else if (so.getFile() != null && so.getFile().getPath().endsWith(".calls.txt"))
                {
                    File metrics = new File(so.getFile().getPath().replaceAll(".calls.txt", ".metrics.txt"));
                    if (metrics.exists())
                    {
                        processMetricsFile(job, metrics, so);
                    }
                    else
                    {
                        job.getLogger().warn("Unable to find metrics file: " + metrics.getPath());
                    }
                }
            }
        }
    }

    private void processMetricsFile(PipelineJob job, File metrics, SequenceOutputFile so) throws PipelineJobException
    {
        job.getLogger().info("Loading metrics");
        TableInfo ti = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("quality_metrics");

        //NOTE: if this job errored and restarted, we may have duplicate records:
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("readset"), so.getReadset());
        filter.addCondition(FieldKey.fromString("analysis_id"), so.getAnalysis_id(), CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("dataid"), so.getDataId(), CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("container"), job.getContainer().getId(), CompareType.EQUAL);
        TableSelector ts = new TableSelector(ti, PageFlowUtil.set("rowid"), filter, null);
        if (ts.exists())
        {
            job.getLogger().info("Deleting existing QC metrics (probably from prior restarted job)");
            ts.getArrayList(Integer.class).forEach(rowid -> {
                Table.delete(ti, rowid);
            });
        }

        int total = 0;
        try (CSVReader reader = new CSVReader(Readers.getReader(metrics), '\t'))
        {
            String[] line;
            while ((line = reader.readNext()) != null)
            {
                if ("Category".equals(line[0]))
                {
                    continue;
                }

                Map<String, Object> r = new HashMap<>();
                r.put("category", line[0]);
                r.put("metricname", line[1]);

                String fieldName = NumberUtils.isCreatable(line[2]) ? "metricvalue" : "qualvalue";
                r.put(fieldName, line[2]);
                r.put("analysis_id", so.getAnalysis_id());
                r.put("dataid", so.getDataId());
                r.put("readset", so.getReadset());
                r.put("container", job.getContainer());
                r.put("createdby", job.getUser().getUserId());

                Table.insert(job.getUser(), ti, r);
                total++;
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        job.getLogger().info("total metrics: " + total);
    }

    private Integer getGenomeId(List<SequenceOutputFile> inputFiles)
    {
        Set<Integer> genomeIds = new HashSet<>();
        inputFiles.forEach(x -> {
            genomeIds.add(x.getLibrary_id());
        });

        return genomeIds.size() == 1 ? genomeIds.iterator().next() : null;
    }
}
