package org.labkey.sequenceanalysis.analysis;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.io.Files;
import htsjdk.samtools.util.IOUtil;
import org.apache.commons.collections4.ComparatorUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.Readers;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SortHelpers;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.util.FastqMerger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class CellHashingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor>
{
    private static final String CALL_EXTENSION = ".calls.txt";
    private static final String DEFAULT_TAG_GROUP = "5p-HTOs";

    public CellHashingHandler()
    {
        this("Cell Hashing Calls", "This will run CITE-Seq Count to generate a table of features counts from CITE-Seq or cell hashing libraries. It will also run R code to generate a table of calls per cell", getDefaultParams());
    }

    protected CellHashingHandler(String name, String description, List<ToolParameterDescriptor> defaultParams)
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), name, description, null, defaultParams);
    }

    public static List<ToolParameterDescriptor> getDefaultParams()
    {
        return getDefaultParams(true, DEFAULT_TAG_GROUP);
    }

    public static List<ToolParameterDescriptor> getDefaultParams(boolean allowScanningEditDistance, String defaultTagGroup)
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>(Arrays.asList(
                ToolParameterDescriptor.create("outputFilePrefix", "Output File Basename", null, "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, "cellHashingCalls"),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-cbf"), "cbf", "Cell Barcode Start", null, "ldk-integerfield", null, 1),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-cbl"), "cbl", "Cell Barcode End", null, "ldk-integerfield", null, 16),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-umif"), "umif", "UMI Start", null, "ldk-integerfield", null, 17),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-umil"), "umil", "UMI End", null, "ldk-integerfield", null, 26)
        ));

        if (allowScanningEditDistance)
        {
            ret.add(ToolParameterDescriptor.create("scanEditDistances", "Scan Edit Distances", "If checked, CITE-seq-count will be run using edit distances from 0-3 and the iteration with the highest singlets will be used.", "checkbox", new JSONObject()
            {{
                put("checked", true);
            }}, true));
        }

        ret.addAll(Arrays.asList(
                ToolParameterDescriptor.create("editDistance", "Edit Distance", null, "ldk-integerfield", null, 3),
                ToolParameterDescriptor.create("excludeFailedcDNA", "Exclude Failed cDNA", "If selected, cDNAs with non-blank status fields will be omitted", "checkbox", null, true),
                ToolParameterDescriptor.create("minCountPerCell", "Min Reads/Cell", null, "ldk-integerfield", null, 5),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-cells"), "cells", "Expected Cells", null, "ldk-integerfield", null, 20000),
                ToolParameterDescriptor.create("tagGroup", "Tag List", null, "ldk-simplelabkeycombo", new JSONObject(){{
                    put("schemaName", "sequenceanalysis");
                    put("queryName", "barcode_groups");
                    put("displayField", "group_name");
                    put("valueField", "group_name");
                    put("allowBlank", false);
                }}, defaultTagGroup),
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true)
        ));

        return ret;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return false;
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
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public SequenceReadsetProcessor getProcessor()
    {
        return new Processor(true);
    }

    public class Processor implements SequenceReadsetProcessor
    {
        private final boolean _generateHtoCalls;

        public Processor(boolean generateHtoCalls)
        {
            _generateHtoCalls = generateHtoCalls;
        }

        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            String tagGroup = params.getString("tagGroup");

            writeAllBarcodes(outputDir, tagGroup, job.getUser(), job.getContainer());
        }

        @Override
        public void complete(PipelineJob job, List<Readset> readsets, List<SequenceOutputFile> outputsCreated) throws PipelineJobException
        {
            TableInfo ti = SequenceAnalysisSchema.getInstance().getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS);
            for (SequenceOutputFile so : outputsCreated)
            {
                job.getLogger().info("Saving quality metrics for: " + so.getName());
                if (so.getFile().getName().endsWith(CALL_EXTENSION))
                {
                    Map<String, Object> counts = parseOutputTable(job.getLogger(), so.getFile(), getCiteSeqCountUnknownOutput(so.getFile().getParentFile(), null), so.getFile().getParentFile(), null, false);
                    for (String name : counts.keySet())
                    {
                        String valueField = (counts.get(name) instanceof String) ? "qualvalue" : "metricvalue";

                        Map<String, Object> r = new HashMap<>();
                        r.put("category", "Cell Hashing");
                        r.put("metricname", StringUtils.capitalize(name));
                        r.put(valueField, counts.get(name));
                        r.put("dataid", so.getDataId());
                        r.put("readset", so.getReadset());

                        r.put("container", job.getContainer());
                        r.put("createdby", job.getUser().getUserId());

                        Table.insert(job.getUser(), ti, r);
                    }
                }
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<Readset> readsets, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            ctx.addActions(action);

            for (Readset rs : readsets)
            {
                HtoMergeResult htoFastqs = possiblyMergeHtoFastqs(rs, ctx.getOutputDir(), ctx.getLogger());
                if (!htoFastqs.intermediateFiles.isEmpty())
                {
                    ctx.getFileManager().addIntermediateFiles(htoFastqs.intermediateFiles);
                }

                ctx.getFileManager().addInput(action, "Input FASTQ", htoFastqs.files.getLeft());
                ctx.getFileManager().addInput(action, "Input FASTQ", htoFastqs.files.getRight());

                Set<Integer> editDistances = new TreeSet<>();
                Map<Integer, Map<String, Object>> results = new HashMap<>();

                int highestSinglet = 0;
                Integer bestEditDistance = null;

                Integer minCountPerCell = ctx.getParams().optInt("minCountPerCell", 5);
                boolean scanEditDistances = ctx.getParams().optBoolean("scanEditDistances", false);
                if (!_generateHtoCalls && scanEditDistances)
                {
                    throw new PipelineJobException("Scan edit distances should not be possible to use unless cell hashing is used");
                }

                if (scanEditDistances)
                {
                    //account for total length of barcode.  shorter barcode should allow fewer edits
                    Integer minLength = readAllBarcodes(ctx.getSourceDirectory()).keySet().stream().map(String::length).min(Integer::compareTo).get();
                    int maxEdit = Math.min(3, minLength - 6);
                    ctx.getLogger().debug("Scanning edit distances, up to: " + maxEdit);
                    int i = 0;
                    while (i <= maxEdit)
                    {
                        editDistances.add(i);
                        i++;
                    }
                }
                else
                {
                    Integer ed = ctx.getParams().optInt("editDistance", 1);
                    editDistances.add(ed);
                }

                for (Integer editDistance : editDistances)
                {
                    Map<String, Object> callMap = executeCiteSeqCount(ctx, action, rs, editDistance, minCountPerCell, _generateHtoCalls);
                    results.put(editDistance, callMap);

                    if (_generateHtoCalls)
                    {
                        int singlet = Integer.parseInt(callMap.get("singlet").toString());
                        ctx.getLogger().info("Edit distance: " + editDistance + ", singlet: " + singlet + ", doublet: " + callMap.get("doublet"));
                        if (singlet > highestSinglet)
                        {
                            highestSinglet = singlet;
                            bestEditDistance = editDistance;
                        }
                    }
                }

                if (editDistances.size() == 1)
                {
                    bestEditDistance = editDistances.iterator().next();
                }

                if (bestEditDistance != null)
                {
                    ctx.getLogger().info("Using edit distance: " + bestEditDistance + (_generateHtoCalls ? ", singlet: " + highestSinglet : ""));

                    Map<String, Object> callMap = results.get(bestEditDistance);
                    if (_generateHtoCalls)
                    {
                        String description = String.format("Edit Distance: %,d\nMin Reads/Cell: %,d\nTotal Singlet: %,d\nDoublet: %,d\nDiscordant: %,d\nSeurat Called: %,d\nNegative: %,d\nUnique HTOs: %s", bestEditDistance, minCountPerCell, callMap.get("singlet"), callMap.get("doublet"), callMap.get("discordant"), callMap.get("seuratSinglet"), callMap.get("negative"), callMap.get("UniqueHtos"));
                        File htoCalls = (File) callMap.get("htoCalls");
                        File html = (File) callMap.get("html");

                        ctx.getFileManager().addSequenceOutput(htoCalls, rs.getName() + ": Cell Hashing Calls","Cell Hashing Calls", rs.getReadsetId(), null, null, description);
                        ctx.getFileManager().addSequenceOutput(html, rs.getName() + ": Cell Hashing Report","Cell Hashing Report", rs.getReadsetId(), null, null, description);
                    }
                    else
                    {
                        ctx.getLogger().debug("HTO calls will not be generated");

                        File citeSeqCount = (File) callMap.get("citeSeqCountMatrix");
                        ctx.getFileManager().addSequenceOutput(citeSeqCount, rs.getName() + ": CITE-Seq Count Matrix","CITE-Seq Count Matrix", rs.getReadsetId(), null, null, null);

                        File outDir = (File) callMap.get("outputDir");
                        ctx.getFileManager().removeIntermediateFile(outDir);
                    }

                    File origUnknown = getCiteSeqCountUnknownOutput(ctx.getSourceDirectory(), bestEditDistance);
                    File movedUnknown = getCiteSeqCountUnknownOutput(ctx.getSourceDirectory(), null);
                    try
                    {
                        ctx.getLogger().debug("Copying unknown barcode file to: " + movedUnknown.getPath());
                        if (movedUnknown.exists())
                        {
                            movedUnknown.delete();
                        }

                        FileUtils.copyFile(origUnknown, movedUnknown);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
                else
                {
                    ctx.getLogger().warn("None of the edit distances produced results");
                }

                //clear info field
                ctx.getJob().setStatus(PipelineJob.TaskStatus.running);
            }
        }
    }

    private Map<String, Object> executeCiteSeqCount(JobContext ctx, RecordedAction action, Readset rs, int editDistance, int minCountPerCell, boolean generateHtoCalls) throws PipelineJobException
    {
        CiteSeqCountWrapper wrapper = new CiteSeqCountWrapper(ctx.getLogger());
        ReadData rd = rs.getReadData().get(0);

        ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Running CITE-seq-count with edit distance: " + editDistance);
        List<String> args = new ArrayList<>();

        args.addAll(getClientCommandArgs(ctx.getParams()));
        args.add("-t");
        args.add(getAllBarcodesFile(ctx.getSourceDirectory()).getPath());
        args.add("-u");
        File unknownBarcodes = getCiteSeqCountUnknownOutput(ctx.getSourceDirectory(), editDistance);
        args.add(unknownBarcodes.getPath());

        args.add("--max-error");
        args.add(String.valueOf((Integer)editDistance));

        Integer cores = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
        if (cores != null)
        {
            args.add("-T");
            args.add(cores.toString());
        }

        String outputBasename = FileUtil.makeLegalName(rs.getName() + "_" + ctx.getParams().getString("outputFilePrefix") + "." + editDistance);
        File outputDir = new File(ctx.getOutputDir(), FileUtil.makeLegalName(rs.getName() + "_cellHashingRawCounts." + editDistance));

        File doneFile = new File(outputDir, "citeSeqCount." + editDistance + ".done");
        if (!doneFile.exists())
        {
            wrapper.execute(args, rd.getFile1(), rd.getFile2(), outputDir);
        }
        else
        {
            ctx.getLogger().info("CITE-seq count has already run, skipping");
        }

        File outputMatrix = new File(outputDir, "umi_count/matrix.mtx.gz");
        if (!outputMatrix.exists())
        {
            throw new PipelineJobException("Unable to find expected file: " + outputMatrix.getPath());
        }

        try
        {
            FileUtils.touch(doneFile);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
        ctx.getFileManager().addIntermediateFile(doneFile);
        ctx.getFileManager().addOutput(action, "Unknown barcodes", unknownBarcodes);
        ctx.getFileManager().addOutput(action, "CITE-seq Raw Counts", outputMatrix);
        ctx.getFileManager().addIntermediateFile(unknownBarcodes);
        ctx.getFileManager().addIntermediateFile(outputDir);

        Map<String, Object> callMap = new HashMap<>();
        callMap.put("citeSeqCountMatrix", outputMatrix);
        callMap.put("outputDir", outputDir);

        if (generateHtoCalls)
        {
            ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "Generating HTO calls for edit distance: " + editDistance);
            File htoCalls = generateFinalCalls(outputMatrix.getParentFile(), ctx.getOutputDir(), outputBasename, ctx.getLogger(), null, true, minCountPerCell, ctx.getSourceDirectory());
            File html = new File(htoCalls.getParentFile(), outputBasename + ".html");

            if (!html.exists())
            {
                throw new PipelineJobException("Unable to find expected HTML file: " + html.getPath());
            }

            ctx.getFileManager().addOutput(action, "Cell Hashing Calls", htoCalls);
            ctx.getFileManager().addOutput(action, "Cell Hashing Report", html);

            callMap.putAll(parseOutputTable(ctx.getLogger(), htoCalls, unknownBarcodes, ctx.getSourceDirectory(), ctx.getWorkingDirectory(), true));
            callMap.put("htoCalls", htoCalls);
            callMap.put("html", html);
        }

        return callMap;
    }

    private File getCiteSeqCountUnknownOutput(File webserverDir, Integer editDistance)
    {
        return new File(webserverDir, "citeSeqUnknownBarcodes." + (editDistance == null ? "final." : editDistance + ".") + "txt");
    }

    private Map<String, Object> parseOutputTable(Logger log, File htoCalls, File unknownBarcodeFile, File localPipelineDir, @Nullable File workDir, boolean includeFiles) throws PipelineJobException
    {
        long singlet = 0L;
        long doublet = 0L;
        long discordant = 0L;
        long negative = 0L;
        long seuratSinglet = 0L;
        long multiSeqSinglet = 0L;
        Set<String> uniqueHTOs = new TreeSet<>();

        try (CSVReader reader = new CSVReader(Readers.getReader(htoCalls), '\t'))
        {
            String[] line;

            int htoClassIdx = -1;
            int htoIdx = -1;
            int seuratIdx = -1;
            int multiSeqIdx = -1;

            List<String> header = new ArrayList<>();
            while ((line = reader.readNext()) != null)
            {
                //skip header
                if (header.isEmpty())
                {
                    header.addAll(Arrays.asList(line));
                    htoClassIdx = header.indexOf("HTO_Classification");
                    htoIdx = header.indexOf("HTO");
                    seuratIdx = header.indexOf("Seurat");
                    multiSeqIdx = header.indexOf("MultiSeq");
                    continue;
                }

                if ("Singlet".equals(line[htoClassIdx]))
                {
                    singlet++;
                }
                else if ("Doublet".equals(line[htoClassIdx]))
                {
                    doublet++;
                }
                else if ("Discordant".equals(line[htoClassIdx]))
                {
                    discordant++;
                }
                else if ("Negative".equals(line[htoClassIdx]))
                {
                    negative++;
                }

                if ("Singlet".equals(line[htoClassIdx]))
                {
                    uniqueHTOs.add(line[htoIdx]);

                    if ("TRUE".equals(line[seuratIdx]))
                    {
                        seuratSinglet++;
                    }

                    if ("TRUE".equals(line[multiSeqIdx]))
                    {
                        multiSeqSinglet++;
                    }
                }
            }

            Map<String, Object> ret = new HashMap<>();
            ret.put("singlet", singlet);
            ret.put("doublet", doublet);
            ret.put("discordant", discordant);
            ret.put("negative", negative);
            ret.put("seuratSinglet", seuratSinglet);
            ret.put("multiSeqSinglet", multiSeqSinglet);
            List<String> uniqueHTOSorted = new ArrayList<>(uniqueHTOs);
            Collections.sort(uniqueHTOSorted, ComparatorUtils.naturalComparator());
            ret.put("UniqueHtos", StringUtils.join(uniqueHTOSorted, ","));

            if (includeFiles)
            {
                ret.put("htoCalls", htoCalls);
                File html = new File(htoCalls.getPath().replaceAll(CALL_EXTENSION, ".html"));
                if (!html.exists())
                {
                    throw new PipelineJobException("Unable to find expected HTML file: " + html.getPath());
                }
                ret.put("html", html);
            }

            File  metricsFile = getMetricsFile(htoCalls);
            ret.putAll(parseUnknownBarcodes(unknownBarcodeFile, localPipelineDir, log, workDir, metricsFile));

            return ret;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private Map<String, Object> parseUnknownBarcodes(File unknownBarcodeFile, File localPipelineDir, Logger log, @Nullable File workDir, File metricsFile) throws PipelineJobException
    {
        log.debug("parsing unknown barcodes file: " + unknownBarcodeFile.getPath());
        log.debug("using metrics file: " + metricsFile.getPath());

        Map<String, Object> ret = new HashMap<>();
        if (unknownBarcodeFile.exists())
        {
            Map<String, String> allBarcodes = readAllBarcodes(localPipelineDir);
            Map<String, Integer> topUnknown = logTopUnknownBarcodes(unknownBarcodeFile, log, allBarcodes);
            if (!topUnknown.isEmpty())
            {
                List<String> toAdd = new ArrayList<>();
                topUnknown.forEach((x, y) -> {
                    toAdd.add(x + ": " + y);
                });

                Collections.sort(toAdd, SortHelpers.getNaturalOrderStringComparator());
                ret.put("UnknownHtoMatchingKnown", StringUtils.join(toAdd, ","));

                if (!metricsFile.exists())
                {
                    log.debug("metrics file not found in webserver dir: " + metricsFile.getPath());
                    if (workDir != null)
                    {
                        metricsFile = new File(workDir, metricsFile.getName());
                        log.debug("trying local dir: " + metricsFile.getPath());
                    }
                }

                if (metricsFile.exists())
                {
                    try (BufferedWriter writer = IOUtil.openFileForBufferedWriting(metricsFile, true))
                    {
                        writer.write(StringUtils.join(new String[]{"Cell Hashing", "UnknownHtoMatchingKnown", (String)ret.get("UnknownHtoMatchingKnown")}, "\t") + "\n");
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
                else
                {
                    log.error("Metric file not found, expected: " + metricsFile.getPath());
                }
            }
        }
        else
        {
            log.error("Unable to find unknown barcode file: " + unknownBarcodeFile.getPath());
        }

        return ret;
    }

    private File ensureLocalCopy(File input, File outputDir, Logger log) throws PipelineJobException
    {
        if (!outputDir.equals(input.getParentFile()))
        {
            try
            {
                //needed for docker currently
                log.debug("Copying file to working directory: " + input.getName());
                File dest = new File(outputDir, input.getName());
                if (dest.exists())
                {
                    if (input.isDirectory())
                    {
                        FileUtils.deleteDirectory(dest);
                    }
                    else
                    {
                        dest.delete();
                    }
                }

                if (input.isDirectory())
                {
                    FileUtils.copyDirectory(input, dest);
                }
                else
                {
                    FileUtils.copyFile(input, dest);
                }
                return dest;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return input;
    }

    public File generateFinalCalls(File citeSeqCountOutDir, File outputDir, String basename, Logger log, @Nullable File cellBarcodeWhitelist, boolean doHtoFiltering, Integer minCountPerCell, File localPipelineDir) throws PipelineJobException
    {
        log.debug("generating final calls from folder: " + citeSeqCountOutDir.getPath());

        String scriptWrapper = getScriptPath("sequenceanalysis", "/external/scRNAseq/htoClassifier.sh");

        SimpleScriptWrapper rWrapper = new SimpleScriptWrapper(log);
        rWrapper.setWorkingDir(outputDir);

        citeSeqCountOutDir = ensureLocalCopy(citeSeqCountOutDir, outputDir, log);
        if (cellBarcodeWhitelist != null)
        {
            cellBarcodeWhitelist = ensureLocalCopy(cellBarcodeWhitelist, outputDir, log);
        }

        File rScript = new File(getScriptPath("sequenceanalysis", "/external/scRNAseq/htoClassifier.Rmd"));
        File localScript = new File(outputDir, rScript.getName());
        if (localScript.exists())
        {
            localScript.delete();
        }
        IOUtil.copyFile(rScript, localScript);

        File htmlFile = new File(outputDir, basename + ".html");
        File callsFile = new File(outputDir, basename + CALL_EXTENSION);
        File rawCallsFile = new File(outputDir, basename + ".raw.txt");
        File metricsFile = getMetricsFile(callsFile);
        List<String> args = new ArrayList<>(Arrays.asList("/bin/bash", scriptWrapper, citeSeqCountOutDir.getName(), htmlFile.getName(), callsFile.getName(), rawCallsFile.getName(), (doHtoFiltering ? "T" : "F"), (minCountPerCell == null ? "0" : minCountPerCell.toString()), metricsFile.getName()));
        if (cellBarcodeWhitelist != null)
        {
            args.add(cellBarcodeWhitelist.getName());
        }

        rWrapper.execute(args);
        if (!htmlFile.exists())
        {
            throw new PipelineJobException("Unable to find HTML file: " + htmlFile.getPath());
        }

        if (!callsFile.exists())
        {
            //copy HTML locally to make debugging easier:
            if (localPipelineDir != null)
            {
                try
                {
                    File localHtml = new File(localPipelineDir, htmlFile.getName());
                    log.info("copying HTML file locally for easier debugging: " + localHtml.getPath());
                    if (localHtml.exists())
                    {
                        localHtml.delete();
                    }
                    FileUtils.copyFile(htmlFile, localHtml);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            throw new PipelineJobException("Unable to find HTO calls file: " + callsFile.getPath());
        }

        return callsFile;
    }

    private File getMetricsFile(File callFile)
    {
        return new File(callFile.getPath().replaceAll(CALL_EXTENSION, ".metrics.txt"));
    }

    private String getScriptPath(String moduleName, String path) throws PipelineJobException
    {
        Module module = ModuleLoader.getInstance().getModule(moduleName);
        Resource script = module.getModuleResource(path);
        if (script == null || !script.exists())
            throw new PipelineJobException("Unable to find file: " + script.getPath() + " in module: " + moduleName);

        File f = ((FileResource) script).getFile();
        if (!f.exists())
            throw new PipelineJobException("Unable to find file: " + f.getPath());

        return f.getPath();
    }

    public static class CiteSeqCountWrapper extends AbstractCommandWrapper
    {
        public CiteSeqCountWrapper(Logger log)
        {
            super(log);
        }

        public void execute(List<String> params, File fq1, File fq2, File outputDir) throws PipelineJobException
        {
            List<String> args = new ArrayList<>();
            args.add(getExe().getPath());
            args.addAll(params);

            args.add("-R1");
            args.add(fq1.getPath());

            if (fq2 != null)
            {
                args.add("-R2");
                args.add(fq2.getPath());
            }

            args.add("-o");
            args.add(outputDir.getPath());

            String output = executeWithOutput(args);
            if (output.contains("format requires -2147483648 <= number"))
            {
                throw new PipelineJobException("Error running Cite-seq-count. Repeat using more cores");
            }
        }

        private File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("CITESEQCOUNTPATH", "CITE-seq-Count");
        }
    }

    private static class HtoMergeResult
    {
        Pair<File, File> files;
        List<File> intermediateFiles = new ArrayList<>();
    }

    private HtoMergeResult possiblyMergeHtoFastqs(Readset htoReadset, File outdir, Logger log) throws PipelineJobException
    {
        HtoMergeResult ret = new HtoMergeResult();
        File file1;
        File file2;

        if (htoReadset.getReadData().size() != 1)
        {
            log.info("Merging HTO data");
            file1 = new File(outdir, FileUtil.makeLegalName(htoReadset.getName() + "hto.R1.fastq.gz"));
            file2 = new File(outdir, FileUtil.makeLegalName(htoReadset.getName() + "hto.R2.fastq.gz"));

            File doneFile = new File(outdir, "merge.done");
            if (doneFile.exists())
            {
                log.info("Resuming from previous merge");
            }
            else
            {
                FastqMerger merger = new FastqMerger(log);
                List<File> files1 = htoReadset.getReadData().stream().map(ReadData::getFile1).collect(Collectors.toList());
                merger.mergeFiles(file1, files1);

                List<File> files2 = htoReadset.getReadData().stream().map(ReadData::getFile2).collect(Collectors.toList());
                if (files2.contains(null))
                {
                    throw new PipelineJobException("All HTO readsets are expected ot have forward and reverse reads");
                }
                merger.mergeFiles(file2, files2);

                ret.intermediateFiles.add(file1);
                ret.intermediateFiles.add(file2);

                try
                {
                    Files.touch(doneFile);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            ret.intermediateFiles.add(doneFile);
        }
        else
        {
            ReadData rd = htoReadset.getReadData().get(0);
            file1 = rd.getFile1();
            file2 = rd.getFile2();
        }

        ret.files = Pair.of(file1, file2);

        return ret;
    }

    public File runCiteSeqCount(PipelineStepOutput output, String category, Readset htoReadset, File htoList, File cellBarcodeList, File outputDir, String basename, Logger log, List<String> extraArgs, boolean doHtoFiltering, @Nullable Integer minCountPerCell, File localPipelineDir, @Nullable Integer editDistance, boolean scanEditDistances, Readset parentReadset, @Nullable Integer genomeId) throws PipelineJobException
    {
        HtoMergeResult htoFastqs = possiblyMergeHtoFastqs(htoReadset, outputDir, log);
        if (!htoFastqs.intermediateFiles.isEmpty())
        {
            htoFastqs.intermediateFiles.forEach(x -> output.addIntermediateFile(x));
        }

        List<String> baseArgs = new ArrayList<>();
        baseArgs.add("-t");
        baseArgs.add(htoList.getPath());

        if (cellBarcodeList != null)
        {
            baseArgs.add("-wl");
            baseArgs.add(cellBarcodeList.getPath());

            //Note: version 1.4.2 and greater requires this:
            //https://github.com/Hoohm/CITE-seq-Count/issues/56
            baseArgs.add("-cells");
            baseArgs.add("0");
        }

        Integer cores = SequencePipelineService.get().getMaxThreads(log);
        if (cores != null)
        {
            baseArgs.add("-T");
            baseArgs.add(cores.toString());
        }

        for (ToolParameterDescriptor param : CellHashingHandler.getDefaultParams())
        {
            if (cellBarcodeList != null && param.getName().equals("cells"))
            {
                continue;
            }

            if (param.getCommandLineParam() != null && param.getDefaultValue() != null)
            {
                //this should avoid double-adding if extraArgs contains the param
                if (baseArgs.contains(param.getCommandLineParam().getArgName()))
                {
                    log.debug("skipping default param because caller specified an alternate value: " + param.getName());
                    continue;
                }

                baseArgs.addAll(param.getCommandLineParam().getArguments(param.getDefaultValue().toString()));
            }
        }

        if (extraArgs != null)
        {
            baseArgs.addAll(extraArgs);
        }

        Set<Integer> editDistances = new TreeSet<>();
        Map<Integer, Map<String, Object>> results = new HashMap<>();

        Integer highestSinglet = 0;
        Integer bestEditDistance = null;

        if (scanEditDistances)
        {
            editDistances.add(0);
            editDistances.add(1);
            editDistances.add(2);
            editDistances.add(3);
        }
        else
        {
            editDistances.add(editDistance);
        }

        for (Integer ed : editDistances)
        {
            List<String> toolArgs = new ArrayList<>(baseArgs);
            toolArgs.add("-u");
            File unknownBarcodeFile = getCiteSeqCountUnknownOutput(localPipelineDir == null ? outputDir : localPipelineDir, ed);
            toolArgs.add(unknownBarcodeFile.getPath());

            File citeSeqCountOutDir = new File(outputDir, basename + ".citeSeqCounts." + ed);
            String outputBasename = basename + "." + ed;
            Map<String, Object> callMap = executeCiteSeqCountWithJobCtx(outputDir, outputBasename, citeSeqCountOutDir, htoFastqs.files.getLeft(), htoFastqs.files.getRight(), toolArgs, ed, log, cellBarcodeList, doHtoFiltering, minCountPerCell, localPipelineDir, unknownBarcodeFile);
            results.put(ed, callMap);

            int singlet = Integer.parseInt(callMap.get("singlet").toString());
            log.info("Edit distance: " + ed + ", singlet: " + singlet + ", doublet: " + callMap.get("doublet"));
            if (singlet > highestSinglet)
            {
                highestSinglet = singlet;
                bestEditDistance = ed;
            }

            output.addIntermediateFile(unknownBarcodeFile);
            output.addIntermediateFile(citeSeqCountOutDir);
        }

        if (bestEditDistance != null)
        {
            log.info("Using edit distance: " + bestEditDistance + ", singlet: " + highestSinglet);

            Map<String, Object> callMap = results.get(bestEditDistance);
            String description = String.format("Edit Distance: %,d\nMin Reads/Cell: %,d\nTotal Singlet: %,d\nDoublet: %,d\nDiscordant: %,d\nSeurat Singlet: %,d\nMultiSeq Singlet: %,d\nNegative: %,d\nUnique HTOs: %s", bestEditDistance, minCountPerCell, callMap.get("singlet"), callMap.get("doublet"), callMap.get("discordant"), callMap.get("seuratSinglet"), callMap.get("multiSeqSinglet"), callMap.get("negative"), callMap.get("UniqueHtos"));
            File htoCalls = (File) callMap.get("htoCalls");
            if (htoCalls == null)
            {
                throw new PipelineJobException("htoCalls file was null");
            }

            File html = (File) callMap.get("html");
            if (html == null)
            {
                throw new PipelineJobException("html file was null");
            }

            File origUnknown = getCiteSeqCountUnknownOutput(localPipelineDir, bestEditDistance);
            File movedUnknown = getCiteSeqCountUnknownOutput(localPipelineDir, null);

            try
            {
                log.debug("Copying unknown barcode file to: " + movedUnknown.getPath());
                if (movedUnknown.exists())
                {
                    movedUnknown.delete();
                }

                FileUtils.copyFile(origUnknown, movedUnknown);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            if (category != null)
            {
                output.addSequenceOutput(htoCalls, parentReadset.getName() + ": Cell Hashing Calls", category, parentReadset.getReadsetId(), null, genomeId, description);
                output.addSequenceOutput(html, parentReadset.getName() + ": Cell Hashing Report", category + ": Report", parentReadset.getReadsetId(), null, genomeId, description);
            }

            return htoCalls;
        }
        else
        {
            throw new PipelineJobException("None of the edit distances produced results");
        }
    }

    private Map<String, Object> executeCiteSeqCountWithJobCtx(File outputDir, String basename, File citeSeqCountOutDir, File fastq1, File fastq2, List<String> baseArgs, Integer ed, Logger log, File cellBarcodeList, boolean doHtoFiltering, Integer minCountPerCell, File localPipelineDir, File unknownBarcodeFile) throws PipelineJobException
    {
        CellHashingHandler.CiteSeqCountWrapper wrapper = new CellHashingHandler.CiteSeqCountWrapper(log);
        File doneFile = new File(citeSeqCountOutDir, "citeSeqCount.done");
        if (!doneFile.exists())
        {
            baseArgs.add("--max-error");
            baseArgs.add(ed.toString());

            wrapper.execute(baseArgs, fastq1, fastq2, citeSeqCountOutDir);
        }
        else
        {
            log.info("CITE-seq count has already run, skipping");
        }

        if (!citeSeqCountOutDir.exists())
        {
            throw new PipelineJobException("missing expected output: " + citeSeqCountOutDir.getPath());
        }

        File outputMatrix = new File(citeSeqCountOutDir, "umi_count/matrix.mtx.gz");
        if (!outputMatrix.exists())
        {
            throw new PipelineJobException("Unable to find expected file: " + outputMatrix.getPath());
        }

        try
        {
            FileUtils.touch(doneFile);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        File outputFile = generateFinalCalls(outputMatrix.getParentFile(), outputDir, basename, log, cellBarcodeList, doHtoFiltering, minCountPerCell, localPipelineDir);
        if (!outputFile.exists())
        {
            throw new PipelineJobException("missing expected file: " + outputFile.getPath());
        }

        //this will log results and append to metrics
        return parseOutputTable(log, outputFile, unknownBarcodeFile, localPipelineDir, outputDir, true);
    }

    private Map<String, Integer> logTopUnknownBarcodes(File citeSeqCountUnknownOutput, Logger log, Map<String, String> allBarcodes) throws PipelineJobException
    {
        Map<String, Integer> unknownMap = new HashMap<>();
        try (CSVReader reader = new CSVReader(Readers.getReader(citeSeqCountUnknownOutput), ','))
        {
            String[] line;
            int lineIdx = 0;
            log.info("Top unknown barcodes:");
            while ((line = reader.readNext()) != null)
            {
                lineIdx++;
                if (lineIdx == 1)
                {
                    continue;
                }

                String name = allBarcodes.get(line[0]);
                if (name == null)
                {
                    for (String bc : allBarcodes.keySet())
                    {
                        if (line[0].startsWith(bc))
                        {
                            name = allBarcodes.get(bc);
                            break;
                        }
                    }
                }

                if (name != null)
                {
                    Integer count = unknownMap.getOrDefault(name, 0);
                    count += Integer.parseInt(line[1]);

                    unknownMap.put(name, count);
                }

                if (lineIdx <= 7)
                {
                    log.info(line[0] + (name == null ? "" : " (" + name + ")") + ": " + line[1]);
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return unknownMap;
    }

    private static File getAllBarcodesFile(File webserverDir)
    {
        return new File(webserverDir, "allHTOBarcodes.txt");
    }

    public static File writeAllBarcodes(File webserverDir, User u, Container c) throws PipelineJobException
    {
        return writeAllBarcodes(webserverDir, DEFAULT_TAG_GROUP, u, c);
    }

    public static File writeAllBarcodes(File webserverDir, String groupName, User u, Container c) throws PipelineJobException
    {
        File out = getAllBarcodesFile(webserverDir);
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(out), ',', CSVWriter.NO_QUOTE_CHARACTER))
        {
            TableInfo ti = QueryService.get().getUserSchema(u, c, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_BARCODES, null);
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("sequence", "tag_name"), new SimpleFilter(FieldKey.fromString("group_name"), groupName), new Sort("tag_name"));
            ts.forEachResults(rs -> {
                writer.writeNext(new String[]{rs.getString(FieldKey.fromString("sequence")), rs.getString(FieldKey.fromString("tag_name"))});
            });
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return out;
    }

    private Map<String, String> readAllBarcodes(File webserverDir) throws PipelineJobException
    {
        File barcodes = getAllBarcodesFile(webserverDir);
        try (CSVReader reader = new CSVReader(Readers.getReader(barcodes), ','))
        {
            Map<String, String> ret = new HashMap<>();
            String[] line;
            while ((line = reader.readNext()) != null)
            {
                ret.put(line[0], line[1]);
            }

            return ret;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
