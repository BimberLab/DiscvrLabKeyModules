package org.labkey.singlecell;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.IOUtil;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections4.ComparatorUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.PipelineOutputTracker;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.singlecell.model.CDNA_Library;
import org.labkey.api.singlecell.model.Sample;
import org.labkey.api.singlecell.model.Sort;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SortHelpers;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.analysis.CellHashingHandler;
import org.labkey.singlecell.analysis.CiteSeqHandler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CellHashingServiceImpl extends CellHashingService
{
    private static CellHashingServiceImpl _instance = new CellHashingServiceImpl();

    public static final String READSET_TO_HASHING_MAP = "readsetToHashingMap";
    public static final String READSET_TO_CITESEQ_MAP = "readsetToCiteSeqMap";

    public static final String CALL_EXTENSION = ".calls.txt";

    private CellHashingServiceImpl()
    {

    }

    public static CellHashingServiceImpl get()
    {
        return _instance;
    }

    @Override
    public void prepareHashingAndCiteSeqFilesIfNeeded(File sourceDir, PipelineJob job, SequenceAnalysisJobSupport support, String filterField, final boolean skipFailedCdna, boolean failIfNoHashing, boolean failIfNoCiteSeq) throws PipelineJobException
    {
        Container target = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
        UserSchema singleCell = QueryService.get().getUserSchema(job.getUser(), target, SingleCellSchema.NAME);
        TableInfo cDNAs = singleCell.getTable(SingleCellSchema.TABLE_CDNAS, null);

        job.getLogger().debug("preparing cDNA and cell hashing files");

        writeAllBarcodes(BARCODE_TYPE.hashing, sourceDir, job.getUser(), job.getContainer(), null);
        writeAllBarcodes(BARCODE_TYPE.citeseq, sourceDir, job.getUser(), job.getContainer(), null);

        Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(cDNAs, PageFlowUtil.set(
                FieldKey.fromString("rowid"),
                FieldKey.fromString("sortId/sampleId/subjectId"),
                FieldKey.fromString("sortId/sampleId/stim"),
                FieldKey.fromString("sortId/population"),
                FieldKey.fromString("sortId/hto"),
                FieldKey.fromString("sortId/hto/sequence"),
                FieldKey.fromString("hashingReadsetId"),
                FieldKey.fromString("hashingReadsetId/totalFiles"),
                FieldKey.fromString("citeseqReadsetId"),
                FieldKey.fromString("citeseqReadsetId/totalFiles"),
                FieldKey.fromString("citeseqPanel"),
                FieldKey.fromString("status"))
        );

        File output = getCDNAInfoFile(sourceDir);
        File barcodeOutput = getValidHashingBarcodeFile(sourceDir);
        HashMap<Integer, Integer> readsetToHashingMap = new HashMap<>();
        HashMap<Integer, Integer> readsetToCiteSeqMap = new HashMap<>();
        HashMap<Integer, Set<String>> gexToPanels = new HashMap<>();

        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(output), '\t', CSVWriter.NO_QUOTE_CHARACTER); CSVWriter bcWriter = new CSVWriter(PrintWriters.getPrintWriter(barcodeOutput), ',', CSVWriter.NO_QUOTE_CHARACTER))
        {
            writer.writeNext(new String[]{"ReadsetId", "CDNA_ID", "SubjectId", "Stim", "Population", "HashingReadsetId", "HasHashingReads", "HTO_Name", "HTO_Seq", "CiteSeqReadsetId", "HasCiteSeqReads", "CiteSeqPanel"});
            List<Readset> cachedReadsets = support.getCachedReadsets();
            Set<String> distinctHTOs = new HashSet<>();
            Set<Boolean> hashingStatus = new HashSet<>();
            Set<Boolean> citeseqStatus = new HashSet<>();
            AtomicInteger totalWritten = new AtomicInteger(0);
            for (Readset rs : cachedReadsets)
            {
                AtomicBoolean hasError = new AtomicBoolean(false);
                //find cDNA records using this readset
                new TableSelector(cDNAs, colMap.values(), new SimpleFilter(FieldKey.fromString(filterField), rs.getRowId()), null).forEachResults(results -> {
                    if (skipFailedCdna && results.getObject(FieldKey.fromString("status")) != null)
                    {
                        job.getLogger().info("skipping cDNA with non-null status: " + results.getString(FieldKey.fromString("rowid")));
                        return;
                    }

                    writer.writeNext(new String[]{
                            String.valueOf(rs.getRowId()),
                            results.getString(FieldKey.fromString("rowid")),
                            results.getString(FieldKey.fromString("sortId/sampleId/subjectId")),
                            results.getString(FieldKey.fromString("sortId/sampleId/stim")),
                            results.getString(FieldKey.fromString("sortId/population")),
                            String.valueOf(results.getObject(FieldKey.fromString("hashingReadsetId")) == null ? "" : results.getInt(FieldKey.fromString("hashingReadsetId"))),
                            String.valueOf(results.getObject(FieldKey.fromString("hashingReadsetId/totalFiles")) != null && results.getInt(FieldKey.fromString("hashingReadsetId/totalFiles")) > 0),
                            results.getString(FieldKey.fromString("sortId/hto")),
                            results.getString(FieldKey.fromString("sortId/hto/sequence")),
                            String.valueOf(results.getObject(FieldKey.fromString("citeseqReadsetId")) == null ? "" : results.getInt(FieldKey.fromString("citeseqReadsetId"))),
                            String.valueOf(results.getObject(FieldKey.fromString("citeseqReadsetId/totalFiles")) != null && results.getInt(FieldKey.fromString("citeseqReadsetId/totalFiles")) > 0),
                            results.getString(FieldKey.fromString("citeseqPanel"))
                    });
                    totalWritten.getAndIncrement();

                    boolean useCellHashing = results.getObject(FieldKey.fromString("sortId/hto")) != null;
                    hashingStatus.add(useCellHashing);
                    if (useCellHashing)
                    {
                        if (results.getObject(FieldKey.fromString("hashingReadsetId")) == null)
                        {
                            job.getLogger().error("cDNA specifies HTO, but does not list a hashing readset: " + results.getString(FieldKey.fromString("rowid")));
                            hasError.set(true);
                        }
                        else
                        {
                            readsetToHashingMap.put(rs.getReadsetId(), results.getInt(FieldKey.fromString("hashingReadsetId")));

                            String hto = results.getString(FieldKey.fromString("sortId/hto")) + "<>" + results.getString(FieldKey.fromString("sortId/hto/sequence"));
                            if (!distinctHTOs.contains(hto) && !StringUtils.isEmpty(results.getString(FieldKey.fromString("sortId/hto/sequence"))))
                            {
                                distinctHTOs.add(hto);
                                bcWriter.writeNext(new String[]{results.getString(FieldKey.fromString("sortId/hto/sequence")), results.getString(FieldKey.fromString("sortId/hto"))});
                            }

                            if (results.getObject(FieldKey.fromString("sortId/hto/sequence")) == null)
                            {
                                job.getLogger().error("Unable to find sequence for HTO: " + results.getString(FieldKey.fromString("sortId/hto")));
                                hasError.set(true);
                            }
                        }
                    }

                    boolean useCiteSeq = results.getObject(FieldKey.fromString("citeseqPanel")) != null;
                    citeseqStatus.add(useCiteSeq);
                    if (useCiteSeq)
                    {
                        if (results.getObject(FieldKey.fromString("citeseqReadsetId")) == null)
                        {
                            job.getLogger().error("cDNA specifies cite-seq readset but does not list panel: " + results.getString(FieldKey.fromString("rowid")));
                            hasError.set(true);
                        }
                        else
                        {
                            Set<String> panels = gexToPanels.getOrDefault(rs.getRowId(), new HashSet<>());
                            panels.add(results.getString(FieldKey.fromString("citeseqPanel")));
                            gexToPanels.put(rs.getRowId(), panels);

                            readsetToCiteSeqMap.put(rs.getReadsetId(), results.getInt(FieldKey.fromString("citeseqReadsetId")));
                        }
                    }
                });

                if (hasError.get())
                {
                    throw new PipelineJobException("There is a problem with either cell hashing or CITE-seq. See the file: " + output.getName());
                }

                if (hashingStatus.size() > 1)
                {
                    job.getLogger().info("The selected readsets/cDNA records use a mixture of cell hashing and non-hashing.");
                }

                //NOTE: hashingStatus.isEmpty() indicates there are no cDNA records associated with the data
            }

            // if distinct HTOs is 1, no point in running hashing.  note: presence of hashing readsets is a trigger downstream
            if (distinctHTOs.size() > 1)
            {
                readsetToHashingMap.forEach((readsetId, hashingReadsetId) -> support.cacheReadset(hashingReadsetId, job.getUser()));
            }
            else if (distinctHTOs.size() == 1)
            {
                job.getLogger().info("There is only a single HTO in this pool, will not use hashing");
            }

            if (totalWritten.get() == 0)
            {
                throw new PipelineJobException("No matching cDNA records found");
            }

            boolean useCellHashing = hashingStatus.size() == 1 ? hashingStatus.iterator().next() : !hashingStatus.isEmpty();
            if (useCellHashing && distinctHTOs.isEmpty())
            {
                throw new PipelineJobException("Cell hashing was selected, but no HTOs were found");
            }
            else
            {
                job.getLogger().info("distinct HTOs: " + distinctHTOs.size());
            }

            support.cacheObject(READSET_TO_HASHING_MAP, readsetToHashingMap);
            support.cacheObject(READSET_TO_CITESEQ_MAP, readsetToCiteSeqMap);
            readsetToCiteSeqMap.forEach((readsetId, citeseqReadsetId) -> support.cacheReadset(citeseqReadsetId, job.getUser()));
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        writeCiteSeqBarcodes(job, gexToPanels, sourceDir);

        if (failIfNoHashing && readsetToHashingMap.isEmpty())
        {
            throw new PipelineJobException("Readsets do not use cell hashing");
        }

        if (failIfNoCiteSeq && readsetToCiteSeqMap.isEmpty())
        {
            throw new PipelineJobException("Readsets do not use CITE-seq");
        }
    }

    public File getValidCiteSeqBarcodeFile(File sourceDir, int gexReadsetId)
    {
        return new File(sourceDir, "validADTS." + gexReadsetId + ".csv");
    }

    public File getValidCiteSeqBarcodeMetadataFile(File sourceDir, int gexReadsetId)
    {
        return new File(sourceDir, "validADTS." + gexReadsetId + ".metadata.txt");
    }

    private void writeCiteSeqBarcodes(PipelineJob job, Map<Integer, Set<String>> gexToPanels, File outputDir) throws PipelineJobException
    {
        Container target = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
        UserSchema schema = QueryService.get().getUserSchema(job.getUser(), target, SingleCellSchema.NAME);
        TableInfo panels = schema.getTable(SingleCellSchema.TABLE_CITE_SEQ_PANELS, null);

        Map<FieldKey, ColumnInfo> barcodeColMap = QueryService.get().getColumns(panels, PageFlowUtil.set(
                FieldKey.fromString("antibody"),
                FieldKey.fromString("antibody/markerName"),
                FieldKey.fromString("antibody/markerLabel"),
                FieldKey.fromString("markerLabel"),
                FieldKey.fromString("antibody/adaptersequence"),
                FieldKey.fromString("antibody/pattern")
        ));

        for (int gexReadsetId : gexToPanels.keySet())
        {
            job.getLogger().info("Writing all unique ADTs for readset: " + gexReadsetId);
            File barcodeOutput = getValidCiteSeqBarcodeFile(outputDir, gexReadsetId);
            File metadataOutput = getValidCiteSeqBarcodeMetadataFile(outputDir, gexReadsetId);
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(barcodeOutput), ',', CSVWriter.NO_QUOTE_CHARACTER);CSVWriter metaWriter = new CSVWriter(PrintWriters.getPrintWriter(metadataOutput), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                metaWriter.writeNext(new String[]{"tagname", "sequence", "markername", "markerlabel", "pattern"});
                AtomicInteger barcodeCount = new AtomicInteger();
                Set<String> found = new HashSet<>();
                new TableSelector(panels, barcodeColMap.values(), new SimpleFilter(FieldKey.fromString("name"), gexToPanels.get(gexReadsetId), CompareType.IN), new org.labkey.api.data.Sort("antibody")).forEachResults(results -> {
                    if (found.contains(results.getString(FieldKey.fromString("antibody/adaptersequence"))))
                    {
                        return;
                    }

                    found.add(results.getString(FieldKey.fromString("antibody/adaptersequence")));
                    barcodeCount.getAndIncrement();

                    writer.writeNext(new String[]{results.getString(FieldKey.fromString("antibody/adaptersequence")), results.getString(FieldKey.fromString("antibody"))});

                    //allow aliasing based on DB
                    String label = StringUtils.trimToNull(results.getString(FieldKey.fromString("markerLabel"))) == null ? results.getString(FieldKey.fromString("antibody/markerLabel")) : results.getString(FieldKey.fromString("markerLabel"));
                    String name = StringUtils.trimToNull(results.getString(FieldKey.fromString("markerLabel"))) != null ? results.getString(FieldKey.fromString("markerLabel")) :
                            StringUtils.trimToNull(results.getString(FieldKey.fromString("antibody/markerName"))) != null ? results.getString(FieldKey.fromString("antibody/markerName")) : results.getString(FieldKey.fromString("antibody"));
                    metaWriter.writeNext(new String[]{results.getString(FieldKey.fromString("antibody")), results.getString(FieldKey.fromString("antibody/adaptersequence")), name, label});
                });

                job.getLogger().info("Total CITE-seq barcodes written: " + barcodeCount.get());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    public File getValidHashingBarcodeFile(File sourceDir)
    {
        return new File(sourceDir, "validHashingBarcodes.csv");
    }

    public File getValidCellIndexFile(File sourceDir)
    {
        return new File(sourceDir, "validCellIndexes.csv");
    }

    @Override
    public File processCellHashingOrCiteSeqForParent(Readset parentReadset, PipelineOutputTracker output, SequenceOutputHandler.JobContext ctx, CellHashingParameters parameters) throws PipelineJobException
    {
        parameters.validate(true);
        Map<Integer, Integer> readsetToHashingOrCite = parameters.type == BARCODE_TYPE.hashing ? getCachedHashingReadsetMap(ctx.getSequenceSupport()) : getCachedCiteSeqReadsetMap(ctx.getSequenceSupport());
        if (readsetToHashingOrCite.isEmpty())
        {
            ctx.getLogger().info("No cached " + parameters.type.name() + " readsets, skipping");
            return null;
        }

        //prepare whitelist of barcodes, based on cDNA records
        File htoBarcodeWhitelist = parameters.getHtoOrCiteSeqBarcodeFile();
        if (!htoBarcodeWhitelist.exists())
        {
            throw new PipelineJobException("Unable to find file: " + htoBarcodeWhitelist.getPath());
        }

        long lineCount = SequencePipelineService.get().getLineCount(htoBarcodeWhitelist);
        if (parameters.type == BARCODE_TYPE.hashing && lineCount == 1)
        {
            ctx.getLogger().info("Only one barcode is used, will not use cell hashing");
            return null;
        }

        ctx.getLogger().debug("total cached readset/" + parameters.type.name() + " readset pairs: " + readsetToHashingOrCite.size());
        ctx.getLogger().debug("unique indexes: " + lineCount);

        Readset htoOrCiteReadset = ctx.getSequenceSupport().getCachedReadset(readsetToHashingOrCite.get(parentReadset.getReadsetId()));
        if (htoOrCiteReadset == null)
        {
            throw new PipelineJobException("Unable to find HTO readset for readset: " + parentReadset.getRowId());
        }
        parameters.htoOrCiteseqReadset = htoOrCiteReadset;

        // either the HTO calls or count matrix for CITE-seq
        File processOutput = processCellHashingOrCiteSeq(output, ctx.getOutputDir(), ctx.getSourceDirectory(), ctx.getLogger(), parameters);
        if (!processOutput.exists())
        {
            throw new PipelineJobException("Unable to find expected file: " + processOutput.getPath());
        }

        if (parameters.type == BARCODE_TYPE.hashing)
        {
            File html = new File(processOutput.getParentFile(), FileUtil.getBaseName(FileUtil.getBaseName(processOutput.getName())) + ".html");
            if (!html.exists())
            {
                throw new PipelineJobException("Unable to find HTML file: " + html.getPath());
            }
        }

        return processOutput;
    }

    @Override
    public File processCellHashingOrCiteSeq(SequenceOutputHandler.JobContext ctx, CellHashingParameters parameters) throws PipelineJobException
    {
        return processCellHashingOrCiteSeq(ctx.getFileManager(), ctx.getOutputDir(), ctx.getSourceDirectory(), ctx.getLogger(), parameters);
    }

    @Override
    public File processCellHashingOrCiteSeq(PipelineOutputTracker output, File outputDir, File webserverPipelineDir, Logger log, CellHashingParameters parameters) throws PipelineJobException
    {
        parameters.validate();
        
        HtoMergeResult htoFastqs = possiblyMergeHtoFastqs(parameters.htoOrCiteseqReadset, outputDir, log);
        if (!htoFastqs.intermediateFiles.isEmpty())
        {
            htoFastqs.intermediateFiles.forEach(output::addIntermediateFile);
        }

        if (parameters.scanEditDistances && !parameters.type.isSupportsScan())
        {
            throw new PipelineJobException("Scan edit distances should not be possible to use unless cell hashing is used");
        }

        Set<Integer> editDistances = new TreeSet<>();
        Map<Integer, Map<String, Object>> results = new HashMap<>();

        int highestSinglet = 0;
        Integer bestEditDistance = null;

        if (parameters.scanEditDistances)
        {
            //account for total length of barcode.  shorter barcode should allow fewer edits
            Integer minLength = CellHashingServiceImpl.get().readAllBarcodes(webserverPipelineDir, parameters.type).keySet().stream().map(String::length).min(Integer::compareTo).get();
            int maxEdit = Math.min(3, minLength - 6);
            log.debug("Scanning edit distances, up to: " + maxEdit);
            int i = 0;
            while (i <= maxEdit)
            {
                editDistances.add(i);
                i++;
            }
        }
        else
        {
            editDistances.add(parameters.editDistance);
        }

        for (Integer ed : editDistances)
        {
            File citeSeqCountOutDir = new File(outputDir, parameters.getBasename() + ".rawCounts." + ed + "." + parameters.type.name());
            String outputBasename = parameters.getBasename() + "." + ed + "." + parameters.type.name();

            File unknownBarcodeFile = getCiteSeqCountUnknownOutput(webserverPipelineDir == null ? outputDir : webserverPipelineDir, parameters.type, ed);
            Map<String, Object> callMap = executeCiteSeqCount(outputDir, outputBasename, citeSeqCountOutDir, htoFastqs.files.getLeft(), htoFastqs.files.getRight(), ed, log, webserverPipelineDir, unknownBarcodeFile, parameters);
            results.put(ed, callMap);

            if (parameters.type.doGenerateCalls())
            {
                int singlet = Integer.parseInt(callMap.get("singlet").toString());
                log.info("Edit distance: " + ed + ", singlet: " + singlet + ", doublet: " + callMap.get("doublet"));
                if (singlet > highestSinglet)
                {
                    highestSinglet = singlet;
                    bestEditDistance = ed;
                }
            }

            output.addIntermediateFile(unknownBarcodeFile);
            output.addIntermediateFile(citeSeqCountOutDir);
        }

        if (results.size() == 1)
        {
            bestEditDistance = results.keySet().iterator().next();
        }

        if (bestEditDistance != null)
        {
            log.info("Using edit distance: " + bestEditDistance + (highestSinglet == 0 ? "" : ", singlet: " + highestSinglet));

            File origUnknown = getCiteSeqCountUnknownOutput(webserverPipelineDir, parameters.type, bestEditDistance);
            File movedUnknown = getCiteSeqCountUnknownOutput(webserverPipelineDir, parameters.type, null);

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

            Map<String, Object> callMap = results.get(bestEditDistance);
            if (parameters.type.doGenerateCalls())
            {
                String description = String.format("%% Mapped: %s\n%% Unmapped: %s\nEdit Distance: %,d\nMin Reads/Cell: %,d\nTotal Singlet: %,d\nDoublet: %,d\nDiscordant: %,d\nNegative: %,d\nUnique HTOs: %s", callMap.get("PercentageMapped"), callMap.get("PercentageUnmapped"), bestEditDistance, parameters.minCountPerCell, callMap.get("singlet"), callMap.get("doublet"), callMap.get("discordant"), callMap.get("negative"), callMap.get("UniqueHtos"));
                for (CALLING_METHOD x : CALLING_METHOD.values())
                {
                    String value = "singlet." + x.name();
                    if (callMap.containsKey(value))
                    {
                       description = description + ",\n" + callMap.get(value);
                    }
                }

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

                if (parameters.createOutputFiles)
                {
                    output.addSequenceOutput(htoCalls, parameters.getBasename() + ": Cell Hashing Calls", parameters.outputCategory, parameters.getEffectiveReadsetId(), null, parameters.genomeId, description);
                    output.addSequenceOutput(html, parameters.getBasename() + ": Cell Hashing Report", parameters.outputCategory + ": Report", parameters.getEffectiveReadsetId(), null, parameters.genomeId, description);
                }
                else
                {
                    log.debug("Output files will not be created");
                }

                return htoCalls;
            }
            else
            {
                log.debug("Hashing calls will not be generated");

                String description = String.format("%% Mapped: %s\n%% Unmapped: %s", callMap.get("PercentageMapped"), callMap.get("PercentageUnmapped"));
                File citeSeqCount = (File) callMap.get("citeSeqCountMatrix");

                if (parameters.createOutputFiles)
                {
                    output.addSequenceOutput(citeSeqCount, parameters.getEffectiveReadsetId() + ": CITE-Seq Count Matrix", (parameters.outputCategory == null ? "CITE-Seq Count Matrix" : parameters.outputCategory), parameters.getEffectiveReadsetId(), null, parameters.genomeId, description);
                }
                else
                {
                    log.debug("Output files will not be created");
                }

                File outDir = (File) callMap.get("outputDir");
                output.removeIntermediateFile(outDir);

                return citeSeqCount;
            }
        }
        else
        {
            throw new PipelineJobException("None of the edit distances produced results");
        }
    }

    public File getCiteSeqCountUnknownOutput(File webserverDir, BARCODE_TYPE type, Integer editDistance)
    {
        return new File(webserverDir, "citeSeqUnknownBarcodes." + type.name() + "." + (editDistance == null ? "final." : editDistance + ".") + "txt");
    }

    public static class HtoMergeResult
    {
        public Pair<File, File> files;
        public List<File> intermediateFiles = new ArrayList<>();
    }

    public HtoMergeResult possiblyMergeHtoFastqs(Readset htoReadset, File outdir, Logger log) throws PipelineJobException
    {
        HtoMergeResult ret = new HtoMergeResult();
        File file1;
        File file2;

        if (htoReadset.getReadData().isEmpty())
        {
            throw new PipelineJobException("No HTO fastqs exist for readset: " + htoReadset.getName());
        }
        else if (htoReadset.getReadData().size() != 1)
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
                List<File> files1 = htoReadset.getReadData().stream().map(ReadData::getFile1).collect(Collectors.toList());
                SequenceAnalysisService.get().mergeFastqFiles(file1, files1, log);

                List<File> files2 = htoReadset.getReadData().stream().map(ReadData::getFile2).collect(Collectors.toList());
                if (files2.contains(null))
                {
                    throw new PipelineJobException("All HTO readsets are expected to have forward and reverse reads");
                }
                SequenceAnalysisService.get().mergeFastqFiles(file2, files2, log);

                ret.intermediateFiles.add(file1);
                ret.intermediateFiles.add(file2);

                try
                {
                    FileUtils.touch(doneFile);
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

    private Map<Integer, Integer> getCachedCiteSeqReadsetMap(SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        return support.getCachedObject(READSET_TO_CITESEQ_MAP, PipelineJob.createObjectMapper().getTypeFactory().constructParametricType(Map.class, Integer.class, Integer.class));
    }

    @Override
    public boolean usesCellHashing(SequenceAnalysisJobSupport support, File sourceDir) throws PipelineJobException
    {
        Map<Integer, Integer> gexToHashingMap = getCachedHashingReadsetMap(support);
        if (gexToHashingMap == null || gexToHashingMap.isEmpty())
            return false;

        File htoBarcodeWhitelist = getValidHashingBarcodeFile(sourceDir);
        if (!htoBarcodeWhitelist.exists())
        {
            throw new PipelineJobException("Unable to find file: " + htoBarcodeWhitelist.getPath());
        }

        return SequencePipelineService.get().getLineCount(htoBarcodeWhitelist) > 1;
    }

    @Override
    public boolean usesCiteSeq(SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {
        Map<Integer, Integer> gexToCiteMap = getCachedCiteSeqReadsetMap(support);
        if (gexToCiteMap == null || gexToCiteMap.isEmpty())
            return false;

        for (SequenceOutputFile so : inputFiles)
        {
            if (gexToCiteMap.containsKey(so.getReadset()))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public File getCDNAInfoFile(File sourceDir)
    {
        return new File(sourceDir, "cDNAInfo.txt");
    }
    
    public Map<Integer, Integer> getCachedHashingReadsetMap(SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        return support.getCachedObject(READSET_TO_HASHING_MAP, PipelineJob.createObjectMapper().getTypeFactory().constructParametricType(Map.class, Integer.class, Integer.class));
    }

    @Override
    public Sample getSampleById(int rowId)
    {
        return new TableSelector(SingleCellSchema.getInstance().getSchema().getTable(SingleCellSchema.TABLE_SAMPLES)).getObject(rowId, Sample.class);
    }

    @Override
    public CDNA_Library getLibraryById(int rowId)
    {
        return new TableSelector(SingleCellSchema.getInstance().getSchema().getTable(SingleCellSchema.TABLE_CDNAS)).getObject(rowId, CDNA_Library.class);
    }

    @Override
    public Sort getSortById(int rowId)
    {
        return new TableSelector(SingleCellSchema.getInstance().getSchema().getTable(SingleCellSchema.TABLE_SORTS)).getObject(rowId, Sort.class);
    }

    private File getMetricsFile(File callFile)
    {
        return new File(callFile.getPath().replaceAll(CALL_EXTENSION, ".metrics.txt"));
    }

    @Override
    public void processMetrics(SequenceOutputFile so, PipelineJob job, boolean updateDescription) throws PipelineJobException
    {
        if (so.getFile() == null)
        {
            job.getLogger().warn("Unable to update metrics, file id is null: " + so.getName());
            return;
        }

        Map<String, String> valueMap = new HashMap<>();

        File metrics = getMetricsFile(so.getFile());
        if (metrics.exists())
        {
            job.getLogger().info("Loading metrics");
            int total = 0;
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

                    //NOTE: R saves NaN as NA.  This is fixed in the R code, but add this check here to let existing jobs import
                    String value = line[2];
                    if ("NA".equals(value))
                    {
                        value = "0";
                    }

                    String fieldName = NumberUtils.isCreatable(value) ? "metricvalue" : "qualvalue";
                    r.put(fieldName, value);

                    r.put("analysis_id", so.getAnalysis_id());
                    r.put("dataid", so.getDataId());
                    r.put("readset", so.getReadset());
                    r.put("container", job.getContainer());
                    r.put("createdby", job.getUser().getUserId());

                    Table.insert(job.getUser(), ti, r);
                    total++;

                    valueMap.put(line[1], value);
                }

                job.getLogger().info("total metrics: " + total);

                if (updateDescription)
                {
                    job.getLogger().debug("Updating description");
                    StringBuilder description = new StringBuilder();
                    if (StringUtils.trimToNull(so.getDescription()) != null)
                    {
                        description.append(StringUtils.trimToNull(so.getDescription()));
                    }

                    String delim = description.length() > 0 ? "\n" : "";

                    DecimalFormat fmt = new DecimalFormat("##.##%");
                    List<String> metricNames = new ArrayList<>(Arrays.asList("PassingCellBarcodes", "TotalLowCounts", "TotalSinglet", "FractionCalled", "FractionSinglet", "FractionDoublet", "FractionDiscordant", "UniqueHtos", "UnknownTagMatchingKnown"));
                    Arrays.stream(CALLING_METHOD.values()).forEach(x -> metricNames.add("Singlet." + x.name()));

                    for (String metricName : metricNames)
                    {
                        if (valueMap.get(metricName) != null)
                        {
                            Double d = null;
                            if (metricName.startsWith("Fraction"))
                            {
                                try
                                {
                                    d = ConvertHelper.convert(valueMap.get(metricName), Double.class);
                                }
                                catch (ConversionException | IllegalArgumentException e)
                                {
                                    job.getLogger().error("Unable to convert to double: " + valueMap.get(metricName));
                                    throw e;
                                }
                            }

                            description.append(delim).append(metricName).append(": ").append(d == null ? valueMap.get(metricName) : fmt.format(d));
                            delim = ",\n";
                        }
                    }

                    so.setDescription(description.toString());

                    TableInfo tableOutputs = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("outputfiles");
                    Table.update(job.getUser(), tableOutputs, so, so.getRowid());
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            job.getLogger().warn("Unable to find metrics file: " + metrics.getPath());
        }
    }

    @Override
    public List<ToolParameterDescriptor> getDefaultHashingParams(boolean includeExcludeFailedcDNA, BARCODE_TYPE type)
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>(Arrays.asList(
                ToolParameterDescriptor.create("scanEditDistances", "Scan Edit Distances", "If checked, CITE-seq-count will be run using edit distances from 0-3 and the iteration with the highest singlets will be used.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("editDistance", "Edit Distance", null, "ldk-integerfield", null, 2),
                ToolParameterDescriptor.create("minCountPerCell", "Min Reads/Cell", null, "ldk-integerfield", null, 5)
        ));

        if (type == BARCODE_TYPE.hashing)
        {
            ret.add(ToolParameterDescriptor.create("methods", "Calling Methods", "The set of methods to use in calling.", "ldk-simplecombo", new JSONObject()
            {{
                put("multiSelect", true);
                put("allowBlank", false);
                put("storeValues", StringUtils.join(Arrays.stream(CALLING_METHOD.values()).map(Enum::name).collect(Collectors.toList()), ";"));
                put("initialValues", StringUtils.join(CALLING_METHOD.getDefaultMethodNames(), ";"));
                put("delimiter", ";");
                put("joinReturnValue", true);
            }}, null));
        }

        if (includeExcludeFailedcDNA)
        {
            ret.add(ToolParameterDescriptor.create("excludeFailedcDNA", "Exclude Failed cDNA", "If selected, cDNAs with non-blank status fields will be omitted", "checkbox", null, false));
        }

        return ret;
    }

    public File createCellBarcodeWhitelistFromLoupe(SequenceOutputHandler.JobContext ctx, File inputFile) throws PipelineJobException
    {
        ctx.getLogger().debug("inspecting file: " + inputFile.getPath());

        //prepare whitelist of cell indexes
        File cellBarcodeWhitelist = CellHashingServiceImpl.get().getValidCellIndexFile(ctx.getSourceDirectory());
        Set<String> uniqueBarcodes = new HashSet<>();
        ctx.getLogger().debug("writing cell barcodes, using file: " + inputFile.getPath());
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(cellBarcodeWhitelist), ',', CSVWriter.NO_QUOTE_CHARACTER); CSVReader reader = new CSVReader(IOUtil.openFileForBufferedUtf8Reading(inputFile), '\t'))
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

                    //This format is written out by the OOSAP pipeline
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

        return cellBarcodeWhitelist;
    }

    public File getAllBarcodesFile(File webserverDir, BARCODE_TYPE type)
    {
        return new File(webserverDir, type.getAllBarcodeFileName());
    }

    public void writeAllBarcodes(CellHashingService.BARCODE_TYPE type, File webserverDir, User u, Container c, @Nullable String groupName) throws PipelineJobException
    {
        writeAllBarcodes(groupName == null ? type.getDefaultTagGroup() : groupName, u, c, getAllBarcodesFile(webserverDir, type));
    }

    private void writeAllBarcodes(String groupName, User u, Container c, File output) throws PipelineJobException
    {
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(output), ',', CSVWriter.NO_QUOTE_CHARACTER))
        {
            TableInfo ti = QueryService.get().getUserSchema(u, c, SingleCellSchema.SEQUENCE_SCHEMA_NAME).getTable(SingleCellSchema.TABLE_BARCODES, null);
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("sequence", "tag_name"), new SimpleFilter(FieldKey.fromString("group_name"), groupName), new org.labkey.api.data.Sort("tag_name"));
            ts.forEachResults(rs -> {
                writer.writeNext(new String[]{rs.getString(FieldKey.fromString("sequence")), rs.getString(FieldKey.fromString("tag_name"))});
            });
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private Map<String, String> readAllBarcodes(File webserverDir, BARCODE_TYPE type) throws PipelineJobException
    {
        File barcodes = getAllBarcodesFile(webserverDir, type);
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

    public Map<String, Object> parseOutputTable(Logger log, File htoCalls, File unknownBarcodeFile, File localPipelineDir, @Nullable File workDir, boolean includeFiles, CellHashingService.BARCODE_TYPE type) throws PipelineJobException
    {
        long singlet = 0L;
        long doublet = 0L;
        long discordant = 0L;
        long negative = 0L;
        Map<String, Long> singletByMethod = new HashMap<>();
        Set<String> uniqueHTOs = new TreeSet<>();

        try (CSVReader reader = new CSVReader(Readers.getReader(htoCalls), '\t'))
        {
            String[] line;

            int htoClassIdx = -1;
            int htoIdx = -1;
            Map<String, Integer> singletColIdx = new HashMap<>();

            List<String> header = new ArrayList<>();
            while ((line = reader.readNext()) != null)
            {
                //skip header
                if (header.isEmpty())
                {
                    header.addAll(Arrays.asList(line));
                    htoClassIdx = header.indexOf("consensuscall.global");
                    htoIdx = header.indexOf("consensuscall");
                    Arrays.stream(CALLING_METHOD.values()).forEach(x -> singletColIdx.put(x.name(), header.indexOf(x.name())));
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

                    for (String name : singletColIdx.keySet())
                    {
                        if (singletColIdx.get(name) > -1 && "Singlet".equals(line[singletColIdx.get(name)]))
                        {
                            singletByMethod.put(name, singletByMethod.getOrDefault(name, 0L) + 1);
                        }
                    }
                }
            }

            Map<String, Object> ret = new HashMap<>();
            ret.put("singlet", singlet);
            ret.put("doublet", doublet);
            ret.put("discordant", discordant);
            ret.put("negative", negative);
            for (String name : singletByMethod.keySet())
            {
                ret.put("singlet." + name, singletByMethod.get(name));
            }

            List<String> uniqueHTOSorted = new ArrayList<>(uniqueHTOs);
            uniqueHTOSorted.sort(ComparatorUtils.naturalComparator());
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
            ret.putAll(parseUnknownBarcodes(unknownBarcodeFile, localPipelineDir, log, workDir, metricsFile, type));

            return ret;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private Map<String, Object> parseUnknownBarcodes(File unknownBarcodeFile, File localPipelineDir, Logger log, @Nullable File workDir, File metricsFile, CellHashingService.BARCODE_TYPE type) throws PipelineJobException
    {
        log.debug("parsing unknown barcodes file: " + unknownBarcodeFile.getPath());
        log.debug("using metrics file: " + metricsFile.getPath());

        Map<String, Object> ret = new HashMap<>();
        if (unknownBarcodeFile.exists())
        {
            Map<String, String> allBarcodes = readAllBarcodes(localPipelineDir, type);
            Map<String, Integer> topUnknown = logTopUnknownBarcodes(unknownBarcodeFile, log, allBarcodes);
            if (!topUnknown.isEmpty())
            {
                List<String> toAdd = new ArrayList<>();
                topUnknown.forEach((x, y) -> {
                    toAdd.add(x + ": " + y);
                });

                toAdd.sort(SortHelpers.getNaturalOrderStringComparator());
                ret.put("UnknownTagMatchingKnown", StringUtils.join(toAdd, ","));

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
                        writer.write(StringUtils.join(new String[]{type.getLabel(), "UnknownTagMatchingKnown", (String)ret.get("UnknownTagMatchingKnown")}, "\t") + "\n");
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

    public File ensureLocalCopy(File input, File outputDir, Logger log, Set<File> toDelete) throws PipelineJobException
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

                toDelete.add(dest);

                return dest;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return input;
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

    private File generateCellHashingCalls(File citeSeqCountOutDir, File outputDir, String basename, Logger log, File localPipelineDir, CellHashingService.CellHashingParameters parameters) throws PipelineJobException
    {
        log.debug("generating final calls from folder: " + citeSeqCountOutDir.getPath());

        Set<File> toDelete = new HashSet<>();

        SimpleScriptWrapper rWrapper = new SimpleScriptWrapper(log);
        rWrapper.setWorkingDir(outputDir);

        citeSeqCountOutDir = ensureLocalCopy(citeSeqCountOutDir, outputDir, log, toDelete);

        File cellBarcodeWhitelistFile = parameters.cellBarcodeWhitelistFile;
        if (cellBarcodeWhitelistFile != null)
        {
            cellBarcodeWhitelistFile = ensureLocalCopy(cellBarcodeWhitelistFile, outputDir, log, toDelete);
        }
        else
        {
            log.debug("No cell barcode whitelist provided");
        }

        File htmlFile = new File(outputDir, basename + ".html");
        File callsFile = new File(outputDir, basename + CALL_EXTENSION);
        File metricsFile = getMetricsFile(callsFile);

        File localRScript = new File(outputDir, "generateCallsWrapper.R");
        if (!localRScript.exists())
        {
            try (PrintWriter writer = PrintWriters.getPrintWriter(localRScript))
            {
                List<String> methodNames = parameters.methods.stream().map(Enum::name).collect(Collectors.toList());

                String cellbarcodeWhitelist = "";
                if (cellBarcodeWhitelistFile != null)
                {
                    cellbarcodeWhitelist = "'/work/" + cellBarcodeWhitelistFile.getName() + "'";
                }

                Set<String> allowableBarcodes = parameters.getAllowableBarcodeNames();
                String allowableBarcodeParam = allowableBarcodes != null ? "c('" + StringUtils.join(allowableBarcodes, "','") + "')" : "NULL";

                writer.println("f <- cellhashR::CallAndGenerateReport(rawCountData = '/work/" + citeSeqCountOutDir.getName() + "', reportFile = '/work/" + htmlFile.getName() + "', callFile = '/work/" + callsFile.getName() + "', metricsFile = '/work/" + metricsFile.getName() + "', cellbarcodeWhitelist  = " + cellbarcodeWhitelist + ", barcodeWhitelist = " + allowableBarcodeParam + ", title = '" + parameters.getReportTitle() + "', methods = c('" + StringUtils.join(methodNames, "','") + "'))");
                writer.println("print('Rmarkdown complete')");

            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            log.info("script exists, re-using: " + localRScript.getPath());
        }

        File localBashScript = new File(outputDir, "generateCallsDockerWrapper.sh");
        try (PrintWriter writer = PrintWriters.getPrintWriter(localBashScript))
        {
            writer.println("#!/bin/bash");
            writer.println("set -x");
            writer.println("WD=`pwd`");
            writer.println("HOME=`echo ~/`");

            writer.println("DOCKER='" + SequencePipelineService.get().getDockerCommand() + "'");
            writer.println("sudo $DOCKER pull ghcr.io/bimberlab/cellhashr:latest");
            writer.println("sudo $DOCKER run --rm=true \\");
            if (SequencePipelineService.get().getMaxRam() != null)
            {
                writer.println("\t--memory=" + SequencePipelineService.get().getMaxRam() + "g \\");
                writer.println("\t-e SEQUENCEANALYSIS_MAX_RAM \\");
            }

            if (SequencePipelineService.get().getMaxThreads(log) != null)
            {
                writer.println("\t-e SEQUENCEANALYSIS_MAX_THREADS \\");
            }

            writer.println("\t-v \"${WD}:/work\" \\");
            writer.println("\t-v \"${HOME}:/homeDir\" \\");
            writer.println("\t-u $UID \\");
            writer.println("\t-e USERID=$UID \\");
            writer.println("\t-w /work \\");
            //writer.println("\t-e HOME=/homeDir \\");
            writer.println("\tghcr.io/bimberlab/cellhashr:latest \\");
            writer.println("\tRscript --vanilla " + localRScript.getName());
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        rWrapper.execute(Arrays.asList("/bin/bash", localBashScript.getName()));
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

        localBashScript.delete();
        localRScript.delete();

        try
        {
            for (File f : toDelete)
            {
                log.debug("deleting local copy: " + f.getPath());
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

        return callsFile;
    }

    public Map<String, Object> executeCiteSeqCount(File outputDir, String basename, File citeSeqCountOutDir, File fastq1, File fastq2, Integer ed, Logger log, File localPipelineDir, File unknownBarcodeFile, CellHashingService.CellHashingParameters parameters) throws PipelineJobException
    {
        CellHashingHandler.CiteSeqCountWrapper wrapper = new CellHashingHandler.CiteSeqCountWrapper(log);
        File doneFile = new File(citeSeqCountOutDir, "citeSeqCount." + parameters.type.name() + "." + ed + ".done");
        if (!doneFile.exists())
        {
            List<String> baseArgs = new ArrayList<>();
            baseArgs.add("-t");
            baseArgs.add(parameters.getHtoOrCiteSeqBarcodeFile().getPath());

            if (parameters.cellBarcodeWhitelistFile!= null)
            {
                baseArgs.add("-wl");
                baseArgs.add(parameters.cellBarcodeWhitelistFile.getPath());

                //Note: version 1.4.2 and greater requires this:
                //https://github.com/Hoohm/CITE-seq-Count/issues/56
                baseArgs.add("-cells");
                baseArgs.add(parameters.cells == null ? "0" : String.valueOf(parameters.cells));
            }
            else if (parameters.cells != null)
            {
                baseArgs.add("-cells");
                baseArgs.add(String.valueOf(parameters.cells));
            }

            Integer cores = SequencePipelineService.get().getMaxThreads(log);
            if (cores != null)
            {
                baseArgs.add("-T");
                baseArgs.add(cores.toString());
            }

            for (ToolParameterDescriptor param : CellHashingHandler.getDefaultParams(parameters.type))
            {
                if ((parameters.cellBarcodeWhitelistFile != null || parameters.cells != null) && param.getName().equals("cells"))
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

            baseArgs.add("--max-error");
            baseArgs.add(ed.toString());

            if (unknownBarcodeFile != null)
            {
                baseArgs.add("-u");
                baseArgs.add(unknownBarcodeFile.getPath());
            }

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

        Map<String, Object> callMap = new HashMap<>();

        File logFile = new File(citeSeqCountOutDir, "run_report.yaml");
        try (BufferedReader reader = Readers.getReader(logFile))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                log.info(line);

                if (line.startsWith("Percentage mapped"))
                {
                    callMap.put("PercentageMapped", line.split(": ")[1]);
                }
                else if (line.startsWith("Percentage unmapped"))
                {
                    callMap.put("PercentageUnmapped", line.split(": ")[1]);
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        callMap.put("citeSeqCountMatrix", outputMatrix);
        callMap.put("outputDir", citeSeqCountOutDir);
        callMap.put("logFile", logFile);

        if (parameters.type.doGenerateCalls())
        {
            File htoCalls = generateCellHashingCalls(outputMatrix.getParentFile(), outputDir, basename, log, localPipelineDir, parameters);
            if (!htoCalls.exists())
            {
                throw new PipelineJobException("missing expected file: " + htoCalls.getPath());
            }

            File html = new File(htoCalls.getParentFile(), basename + ".html");

            //this will log results and append to metrics
            callMap.putAll(parseOutputTable(log, htoCalls, unknownBarcodeFile, localPipelineDir, outputDir, true, parameters.type));
            callMap.put("htoCalls", htoCalls);
            callMap.put("html", html);
        }

        return callMap;
    }

    @Override
    public Set<String> getHtosForParentReadset(Integer parentReadsetId, File webserverJobDir, SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        return getHtosForParentReadset(parentReadsetId, webserverJobDir, support, true);
    }

    public Set<String> getHtosForParentReadset(Integer parentReadsetId, File webserverJobDir, SequenceAnalysisJobSupport support, boolean throwIfNotFound) throws PipelineJobException
    {
        Integer htoReadset = getCachedHashingReadsetMap(support).get(parentReadsetId);
        if (htoReadset == null)
        {
            if (throwIfNotFound)
            {
                throw new PipelineJobException("Unable to find hashing readset for parent id: " + parentReadsetId);
            }
            else
            {
                return (Collections.emptySet());
            }
        }

        return getHtosForReadset(htoReadset, webserverJobDir);
    }

    public Set<String> getHtosForReadset(Integer hashingReadsetId, File webserverJobDir) throws PipelineJobException
    {
        Set<String> htosPerReadset = new HashSet<>();
        try (CSVReader reader = new CSVReader(Readers.getReader(CellHashingServiceImpl.get().getCDNAInfoFile(webserverJobDir)), '\t'))
        {
            String[] line;
            while ((line = reader.readNext()) != null)
            {
                if (hashingReadsetId.toString().equals(line[5]))
                {
                    htosPerReadset.add(line[7]);
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return htosPerReadset;
    }
}
