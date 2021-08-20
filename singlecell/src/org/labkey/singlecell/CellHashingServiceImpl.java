package org.labkey.singlecell;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections4.ComparatorUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
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
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
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
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.run.CellRangerFeatureBarcodeHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
    public static final String READSET_TO_COUNTS_MAP = "readsetToCountsMap";

    public static final String CALL_EXTENSION = ".calls.txt";

    private CellHashingServiceImpl()
    {

    }

    public static CellHashingServiceImpl get()
    {
        return _instance;
    }

    @Override
    public void prepareHashingIfNeeded(File sourceDir, PipelineJob job, SequenceAnalysisJobSupport support, String filterField, final boolean failIfNoHashing) throws PipelineJobException
    {
        prepareHashingAndCiteSeqFilesIfNeeded(sourceDir, job, support, filterField, failIfNoHashing, false, true, true, false);
    }

    @Override
    public void prepareHashingAndCiteSeqFilesIfNeeded(File sourceDir, PipelineJob job, SequenceAnalysisJobSupport support, String filterField, final boolean failIfNoHashing, final boolean failIfNoCiteSeq) throws PipelineJobException
    {
        prepareHashingAndCiteSeqFilesIfNeeded(sourceDir, job, support, filterField, failIfNoHashing, failIfNoCiteSeq, true, true, true);
    }

    public void prepareHashingAndCiteSeqFilesIfNeeded(File sourceDir, PipelineJob job, SequenceAnalysisJobSupport support, String filterField, final boolean failIfNoHashing, final boolean failIfNoCiteSeq, final boolean cacheCountMatrixFiles, boolean requireValidHashingIfPresent, boolean requireValidCiteSeqIfPresent) throws PipelineJobException
    {
        Container target = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
        UserSchema sequenceAnalysis = QueryService.get().getUserSchema(job.getUser(), target, SingleCellSchema.SEQUENCE_SCHEMA_NAME);
        UserSchema singleCell = QueryService.get().getUserSchema(job.getUser(), target, SingleCellSchema.NAME);
        TableInfo cDNAs = singleCell.getTable(SingleCellSchema.TABLE_CDNAS, null);
        TableInfo sequenceOutputs = sequenceAnalysis.getTable("outputfiles");

        job.getLogger().debug("preparing cDNA and cell hashing files");

        Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(cDNAs, PageFlowUtil.set(
                FieldKey.fromString("rowid"),
                FieldKey.fromString("sortId/sampleId/subjectId"),
                FieldKey.fromString("sortId/sampleId/stim"),
                FieldKey.fromString("sortId/population"),
                FieldKey.fromString("sortId/hto"),
                FieldKey.fromString("sortId/hto/adaptersequence"),
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
            AtomicInteger totalWritten = new AtomicInteger(0);
            for (Readset rs : cachedReadsets)
            {
                AtomicBoolean hasError = new AtomicBoolean(false);
                //find cDNA records using this readset
                new TableSelector(cDNAs, colMap.values(), new SimpleFilter(FieldKey.fromString(filterField), rs.getRowId()), null).forEachResults(results -> {
                    // NOTE: removed b/c newer callers probably dont need this:
                    //if (skipFailedCdna && results.getObject(FieldKey.fromString("status")) != null)
                    //{
                    //    job.getLogger().info("skipping cDNA with non-null status: " + results.getString(FieldKey.fromString("rowid")));
                    //    return;
                    //}

                    writer.writeNext(new String[]{
                            String.valueOf(rs.getRowId()),
                            results.getString(FieldKey.fromString("rowid")),
                            results.getString(FieldKey.fromString("sortId/sampleId/subjectId")),
                            results.getString(FieldKey.fromString("sortId/sampleId/stim")),
                            results.getString(FieldKey.fromString("sortId/population")),
                            String.valueOf(results.getObject(FieldKey.fromString("hashingReadsetId")) == null ? "" : results.getInt(FieldKey.fromString("hashingReadsetId"))),
                            String.valueOf(results.getObject(FieldKey.fromString("hashingReadsetId/totalFiles")) != null && results.getInt(FieldKey.fromString("hashingReadsetId/totalFiles")) > 0),
                            results.getString(FieldKey.fromString("sortId/hto")),
                            results.getString(FieldKey.fromString("sortId/hto/adaptersequence")),
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

                            String hto = results.getString(FieldKey.fromString("sortId/hto")) + "<>" + results.getString(FieldKey.fromString("sortId/hto/adaptersequence"));
                            if (!distinctHTOs.contains(hto) && !StringUtils.isEmpty(results.getString(FieldKey.fromString("sortId/hto/adaptersequence"))))
                            {
                                distinctHTOs.add(hto);
                                bcWriter.writeNext(new String[]{results.getString(FieldKey.fromString("sortId/hto/adaptersequence")), results.getString(FieldKey.fromString("sortId/hto"))});
                            }

                            if (results.getObject(FieldKey.fromString("sortId/hto/adaptersequence")) == null)
                            {
                                job.getLogger().error("Unable to find sequence for HTO: " + results.getString(FieldKey.fromString("sortId/hto")));
                                hasError.set(true);
                            }
                        }
                    }

                    boolean useCiteSeq = results.getObject(FieldKey.fromString("citeseqPanel")) != null;
                    if (useCiteSeq)
                    {
                        if (results.getObject(FieldKey.fromString("citeseqReadsetId")) == null)
                        {
                            job.getLogger().error("cDNA specifies cite-seq panel, but cite-seq readset is empty: " + results.getString(FieldKey.fromString("rowid")));
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
            HashMap<Integer, File> readsetToCountMap = new HashMap<>();
            if (distinctHTOs.size() > 1)
            {
                Set<Integer> hashingToRemove = new HashSet<>();
                readsetToHashingMap.forEach((readsetId, hashingReadsetId) -> {
                    if (cacheCountMatrixFiles)
                    {
                        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("category"), CellRangerFeatureBarcodeHandler.HASHING_CATEGORY);
                        filter.addCondition(FieldKey.fromString("readset"), hashingReadsetId, CompareType.EQUAL);

                        TableSelector ts = new TableSelector(sequenceOutputs, filter, new org.labkey.api.data.Sort("-rowid"));
                        if (!ts.exists())
                        {
                            if (requireValidHashingIfPresent)
                            {
                                throw new IllegalArgumentException("Unable to find existing count matrix for hashing readset: " + hashingReadsetId);
                            }
                            else
                            {
                                job.getLogger().warn("Unable to find existing count matrix for hashing readset: " + hashingReadsetId + ", skipping");
                                hashingToRemove.add(readsetId);
                            }
                        }
                        else
                        {
                            List<SequenceOutputFile> sos = ts.getArrayList(SequenceOutputFile.class);
                            if (sos.size() > 1)
                            {
                                job.getLogger().info("Multiple hashing count matrices found, using most recent: " + sos.get(0).getRowid());
                            }

                            SequenceOutputFile so = ts.getArrayList(SequenceOutputFile.class).get(0);
                            readsetToCountMap.put(hashingReadsetId, so.getFile().getParentFile());  //this is the umi_counts dir
                        }
                    }

                    support.cacheReadset(hashingReadsetId, job.getUser());

                });

                hashingToRemove.forEach(readsetToHashingMap::remove);
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

            Set<Integer> citeToRemove = new HashSet<>();
            readsetToCiteSeqMap.forEach((readsetId, citeseqReadsetId) -> {
                if (cacheCountMatrixFiles)
                {
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("category"), CellRangerFeatureBarcodeHandler.CITESEQ_CATEGORY);
                    filter.addCondition(FieldKey.fromString("readset"), citeseqReadsetId, CompareType.EQUAL);

                    TableSelector ts = new TableSelector(sequenceOutputs, filter, new org.labkey.api.data.Sort("-rowid"));
                    if (!ts.exists())
                    {
                        if (requireValidCiteSeqIfPresent)
                        {
                            throw new IllegalArgumentException("Unable to find existing count matrix for CITE-seq readset: " + citeseqReadsetId);
                        }
                        else
                        {
                            job.getLogger().warn("Unable to find existing count matrix for CITE-seq readset: " + citeseqReadsetId + ", skipping");
                            citeToRemove.add(readsetId);
                        }
                    }
                    else
                    {
                        List<SequenceOutputFile> sos = ts.getArrayList(SequenceOutputFile.class);
                        if (sos.size() > 1)
                        {
                            job.getLogger().info("Multiple CITE-seq count matrices found, using most recent: " + sos.get(0).getRowid());
                        }
                        SequenceOutputFile so = sos.get(0);
                        readsetToCountMap.put(citeseqReadsetId, so.getFile().getParentFile());  //this is the umi_count dir
                    }
                }

                support.cacheReadset(citeseqReadsetId, job.getUser());
            });

            citeToRemove.forEach(readsetToCiteSeqMap::remove);

            support.cacheObject(READSET_TO_HASHING_MAP, readsetToHashingMap);
            support.cacheObject(READSET_TO_CITESEQ_MAP, readsetToCiteSeqMap);
            support.cacheObject(READSET_TO_COUNTS_MAP, readsetToCountMap);

            //infer groups:
            TableInfo hashtagOligos = QueryService.get().getUserSchema(job.getUser(), target, SingleCellSchema.NAME).getTable(SingleCellSchema.TABLE_HASHING_LABELS);
            Set<String> uniqueHashtagGroups = new HashSet<>();
            for (String hto : distinctHTOs)
            {
                String[] tokens = hto.split("<>");
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("name"), tokens[0]);
                filter.addCondition(FieldKey.fromString("adaptersequence"), tokens[1]);

                TableSelector ts = new TableSelector(hashtagOligos, PageFlowUtil.set("groupName"), filter, null);
                if (ts.exists())
                {
                    uniqueHashtagGroups.addAll(ts.getArrayList(String.class));
                }
                else
                {
                    throw new PipelineJobException("Unable to find group for HTO: " + hto);
                }
            }

            if (!distinctHTOs.isEmpty() && uniqueHashtagGroups.isEmpty())
            {

                throw new PipelineJobException("No hashing groups found!");
            }

            if (!uniqueHashtagGroups.isEmpty())
            {
                writeAllHashingBarcodes(uniqueHashtagGroups, job.getUser(), job.getContainer(), sourceDir);
            }
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
                FieldKey.fromString("antibody/barcodePattern")
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
                    metaWriter.writeNext(new String[]{results.getString(FieldKey.fromString("antibody")), results.getString(FieldKey.fromString("antibody/adaptersequence")), name, label, results.getString(FieldKey.fromString("antibody/barcodePattern"))});
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

    @Override
    public File generateHashingCallsForRawMatrix(Readset parentReadset, PipelineOutputTracker output, SequenceOutputHandler.JobContext ctx, CellHashingParameters parameters, File rawCountMatrixDir) throws PipelineJobException
    {
        if (parameters.type != BARCODE_TYPE.hashing)
        {
            throw new PipelineJobException("This is only intended for cell hashing data");
        }

        parameters.validate(true);
        Map<Integer, Integer> readsetToHashing = getCachedHashingReadsetMap(ctx.getSequenceSupport());
        if (readsetToHashing.isEmpty())
        {
            ctx.getLogger().info("No cached " + parameters.type.name() + " readsets, skipping");
            return null;
        }

        //prepare whitelist of barcodes, based on cDNA records
        File htoBarcodeWhitelist = parameters.getHtoBarcodeFile();
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

        ctx.getLogger().debug("total cached readset/" + parameters.type.name() + " readset pairs: " + readsetToHashing.size());
        ctx.getLogger().debug("unique indexes: " + lineCount);

        Readset htoReadset = ctx.getSequenceSupport().getCachedReadset(readsetToHashing.get(parentReadset.getReadsetId()));
        if (htoReadset == null)
        {
            throw new PipelineJobException("Unable to find HTO readset for readset: " + parentReadset.getRowId());
        }
        parameters.htoReadset = htoReadset;

        parameters.validate();

        String outputBasename = parameters.getBasename() + "." + parameters.type.name();
        File callsFile = getExpectedCallsFile(ctx.getOutputDir(), outputBasename);
        File doneFile = new File(callsFile.getPath() + ".done");
        if (!doneFile.exists())
        {
            callsFile = generateCellHashingCalls(rawCountMatrixDir, ctx.getOutputDir(), outputBasename, ctx.getLogger(), ctx.getSourceDirectory(), parameters);

            try
            {
                FileUtils.touch(doneFile);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            ctx.getLogger().debug("Calling has been performed, skipping");
        }
        ctx.getFileManager().addIntermediateFile(doneFile);

        if (!callsFile.exists())
        {
            throw new PipelineJobException("No hashing calls generated");
        }

        Map<String, Object> callMap = parseOutputTable(callsFile);

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

        File rawCounts = (File) callMap.get("rawCounts");
        if (rawCounts == null)
        {
            throw new PipelineJobException("rawCounts file was null");
        }

        if (!parameters.retainRawCountFile)
        {
            ctx.getFileManager().addIntermediateFile(rawCounts);
        }
        else
        {
            ctx.getLogger().debug("Raw counts export will be retained");
        }

        StringBuilder description = new StringBuilder();
        String methods = parameters.methods.stream().map(CALLING_METHOD::name).collect(Collectors.joining(","));
        description.append(String.format("Min Reads/Cell: %,d\nTotal Singlet: %,d\nDoublet: %,d\nDiscordant: %,d\nNegative: %,d\nUnique HTOs: %s\nMethods: %s", parameters.minCountPerCell, callMap.get("singlet"), callMap.get("doublet"), callMap.get("discordant"), callMap.get("negative"), callMap.get("UniqueHtos"), methods));
        for (CALLING_METHOD x : CALLING_METHOD.values())
        {
            String value = "singlet." + x.name();
            if (callMap.containsKey(value))
            {
                description.append(",\n").append(callMap.get(value));
            }
        }

        if (parameters.createOutputFiles)
        {
            output.addSequenceOutput(htoCalls, parameters.getBasename() + ": Cell Hashing Calls", parameters.outputCategory, parameters.getEffectiveReadsetId(), null, parameters.genomeId, description.toString());
            output.addSequenceOutput(html, parameters.getBasename() + ": Cell Hashing Report", parameters.outputCategory + ": Report", parameters.getEffectiveReadsetId(), null, parameters.genomeId, description.toString());
        }
        else
        {
            ctx.getLogger().debug("Output files will not be created");
        }

        return callsFile;
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

    public Map<Integer, File> getCachedReadsetToCountMatrixMap(SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        return support.getCachedObject(READSET_TO_COUNTS_MAP, PipelineJob.createObjectMapper().getTypeFactory().constructParametricType(Map.class, Integer.class, File.class));
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

    public File getMetricsFile(File callFile)
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
                    List<String> metricNames = new ArrayList<>(Arrays.asList("PassingCellBarcodes", "TotalLowCounts", "TotalSinglet", "FractionCalled", "FractionSinglet", "FractionDoublet", "FractionDiscordant", "UniqueHtos"));
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
    public List<ToolParameterDescriptor> getHashingCallingParams()
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>(Arrays.asList(
            ToolParameterDescriptor.create("minCountPerCell", "Min Reads/Cell", null, "ldk-integerfield", null, 5),
            ToolParameterDescriptor.create("skipNormalizationQc", "Skip Normalization QC", null, "checkbox", null, true),
            ToolParameterDescriptor.create("retainRawCountFile", "Retain Raw Counts File", null, "checkbox", null, true)

        ));

        ret.add(ToolParameterDescriptor.create("methods", "Calling Methods", "The set of methods to use in calling.", "ldk-simplecombo", new JSONObject()
        {{
            put("multiSelect", true);
            put("allowBlank", false);
            put("storeValues", StringUtils.join(Arrays.stream(CALLING_METHOD.values()).map(Enum::name).collect(Collectors.toList()), ";"));
            put("initialValues", StringUtils.join(CALLING_METHOD.getDefaultMethodNames(), ";"));
            put("delimiter", ";");
            put("joinReturnValue", true);
        }}, null));

        return ret;
    }

    public File getAllHashingBarcodesFile(File webserverDir)
    {
        return new File(webserverDir, BARCODE_TYPE.hashing.getAllBarcodeFileName());
    }

    private void writeAllHashingBarcodes(Collection<String> groupNames, User u, Container c, File webserverDir) throws PipelineJobException
    {
        File output = getAllHashingBarcodesFile(webserverDir);
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(output), ',', CSVWriter.NO_QUOTE_CHARACTER))
        {
            Container target = c.isWorkbook() ? c.getParent() : c;
            TableInfo ti = QueryService.get().getUserSchema(u, target, SingleCellSchema.NAME).getTable(SingleCellSchema.TABLE_HASHING_LABELS, null);
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("adaptersequence", "name", "groupName", "barcodePattern"), new SimpleFilter(FieldKey.fromString("groupName"), groupNames, CompareType.IN), new org.labkey.api.data.Sort("name"));
            ts.forEachResults(rs -> {
                writer.writeNext(new String[]{rs.getString(FieldKey.fromString("adaptersequence")), rs.getString(FieldKey.fromString("name")), rs.getString(FieldKey.fromString("groupName")), rs.getString(FieldKey.fromString("barcodePattern"))});
            });
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public Map<String, Object> parseOutputTable(File htoCalls) throws PipelineJobException
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

            ret.put("htoCalls", htoCalls);
            File html = new File(htoCalls.getPath().replaceAll(CALL_EXTENSION, ".html"));
            if (!html.exists())
            {
                throw new PipelineJobException("Unable to find expected HTML file: " + html.getPath());
            }
            ret.put("html", html);

            File rawCounts = new File(htoCalls.getPath().replaceAll(CALL_EXTENSION, ".rawCounts.rds"));
            if (!rawCounts.exists())
            {
                throw new PipelineJobException("Unable to find expected counts file: " + rawCounts.getPath());
            }
            ret.put("rawCounts", rawCounts);

            return ret;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public File ensureLocalCopy(File input, File outputDir, Logger log, Set<File> toDelete) throws PipelineJobException
    {
        if (!outputDir.equals(input.getParentFile()))
        {
            try
            {
                //needed for docker currently
                log.debug("Copying file to working directory: " + input.getPath());
                File dest = new File(outputDir, input.getName());
                if (dest.exists())
                {
                    log.debug("deleting existing folder: " + dest.getPath());
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

    private File getExpectedCallsFile(File outputDir, String basename)
    {
        return new File(outputDir, basename + CALL_EXTENSION);
    }

    public File generateCellHashingCalls(File citeSeqCountOutDir, File outputDir, String basename, Logger log, File localPipelineDir, CellHashingService.CellHashingParameters parameters) throws PipelineJobException
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
        File localHtml = new File(localPipelineDir, htmlFile.getName());

        File countFile = new File(outputDir, basename + ".rawCounts.rds");
        File localCounts = new File(localPipelineDir, countFile.getName());

        // Note: if this job fails and then is resumed, having that pre-existing copy of the HTML can pose a problem
        if (localHtml.exists())
        {
            log.debug("Deleting pre-existing HTML file: " + localHtml.getPath());
        }

        if (localCounts.exists())
        {
            log.debug("Deleting pre-existing raw count file: " + localCounts.getPath());
        }

        File callsFile = getExpectedCallsFile(outputDir, basename);
        File metricsFile = getMetricsFile(callsFile);
        if (metricsFile.exists())
        {
            metricsFile.delete();
        }

        File localRScript = new File(outputDir, "generateCallsWrapper.R");
        try (PrintWriter writer = PrintWriters.getPrintWriter(localRScript))
        {
            List<String> methodNames = parameters.methods.stream().map(Enum::name).collect(Collectors.toList());
            String cellbarcodeWhitelist = cellBarcodeWhitelistFile != null ? "'/work/" + cellBarcodeWhitelistFile.getName() + "'" : "NULL";

            Set<String> allowableBarcodes = parameters.getAllowableBarcodeNames();
            String allowableBarcodeParam = allowableBarcodes != null ? "c('" + StringUtils.join(allowableBarcodes, "','") + "')" : "NULL";

            String skipNormalizationQcString = parameters.skipNormalizationQc ? "TRUE" : "FALSE";
            String keepMarkdown = parameters.keepMarkdown ? "TRUE" : "FALSE";
            writer.println("f <- cellhashR::CallAndGenerateReport(rawCountData = '/work/" + citeSeqCountOutDir.getName() + "', reportFile = '/work/" + htmlFile.getName() + "', callFile = '/work/" + callsFile.getName() + "', metricsFile = '/work/" + metricsFile.getName() + "', rawCountsExport = '/work/" + countFile.getName() + "', cellbarcodeWhitelist  = " + cellbarcodeWhitelist + ", barcodeWhitelist = " + allowableBarcodeParam + ", title = '" + parameters.getReportTitle() + "', skipNormalizationQc = " + skipNormalizationQcString + ", methods = c('" + StringUtils.join(methodNames, "','") + "'), keepMarkdown = " + keepMarkdown + ")");
            writer.println("print('Rmarkdown complete')");

        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
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

        boolean callFileValid = callsFile.exists();
        if (callFileValid)
        {
            try (BufferedReader reader = Readers.getReader(callsFile))
            {
                int lineIdx = 0;
                String line;
                while ((line = reader.readLine()) != null)
                {
                    lineIdx++;
                    line = StringUtils.trimToNull(line);
                    if (line == null)
                    {
                        callFileValid = false;
                        break;
                    }

                    if (lineIdx == 1 && !line.startsWith("cellbarcode"))
                    {
                        callFileValid = false;
                        break;
                    }

                    if (lineIdx > 1)
                    {
                        break;
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        if (!callFileValid)
        {
            //copy HTML locally to make debugging easier:
            if (localPipelineDir != null)
            {
                try
                {
                    log.info("copying HTML and counts files locally for easier debugging: " + localHtml.getPath() + " and " + localCounts.getPath());
                    if (localHtml.exists())
                    {
                        localHtml.delete();
                    }
                    FileUtils.copyFile(htmlFile, localHtml);

                    if (localCounts.exists())
                    {
                        localCounts.delete();
                    }
                    FileUtils.copyFile(countFile, localCounts);
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

    @Override
    public File getExistingFeatureBarcodeCountDir(Readset parentReadset, BARCODE_TYPE type, SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        Integer childId = type == BARCODE_TYPE.hashing ? getCachedHashingReadsetMap(support).get(parentReadset.getReadsetId()) : getCachedCiteSeqReadsetMap(support).get(parentReadset.getReadsetId());
        if (childId == null)
        {
            throw new PipelineJobException("Unable to find cached readset of type " + type.name() + " for parent: " + parentReadset.getReadsetId());
        }

        File ret = getCachedReadsetToCountMatrixMap(support).get(childId);
        if (ret == null)
        {
            throw new PipelineJobException("Unable to find cached count matrix of type " + type.name() + " for parent: " + parentReadset.getReadsetId());
        }

        return ret;
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

    public File subsetBarcodes(File allCellBarcodes, @Nullable String barcodePrefix) throws PipelineJobException
    {
        //Subset barcodes by dataset:
        File output = new File(allCellBarcodes.getParentFile(), "cellBarcodeWhitelist." + (barcodePrefix == null ? "all" : barcodePrefix ) + ".txt");
        try (CSVReader reader = new CSVReader(Readers.getReader(allCellBarcodes), '\t'); CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(output), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            String[] line;
            while ((line = reader.readNext()) != null)
            {
                String barcode = line[0];
                if (barcodePrefix == null || barcode.startsWith(barcodePrefix + "_"))
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

        return output;
    }

    public File getCellBarcodesFromSeurat(File seuratObj)
    {
        File barcodes = new File(seuratObj.getParentFile(), seuratObj.getName().replaceAll("seurat.rds", "cellBarcodes.csv"));
        if (!barcodes.exists())
        {
            throw new IllegalArgumentException("Unable to find expected cell barcodes file.  This might indicate the seurat object was created with an older version of the pipeline.  Expected: " + barcodes.getPath());
        }

        return barcodes;
    }

    public File getMetaTableFromSeurat(File seuratObj)
    {
        File barcodes = new File(seuratObj.getParentFile(), seuratObj.getName().replaceAll("seurat.rds", "seurat.meta.txt"));
        if (!barcodes.exists())
        {
            throw new IllegalArgumentException("Unable to find expected metadata file.  This might indicate the seurat object was created with an older version of the pipeline.  Expected: " + barcodes.getPath());
        }

        return barcodes;
    }
}
