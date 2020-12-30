package org.labkey.singlecell;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
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
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.singlecell.model.CDNA_Library;
import org.labkey.api.singlecell.model.Sample;
import org.labkey.api.singlecell.model.Sort;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.analysis.CellHashingHandler;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CellHashingServiceImpl extends CellHashingService
{
    private static CellHashingServiceImpl _instance = new CellHashingServiceImpl();

    public static final String READSET_TO_HASHING_MAP = "readsetToHashingMap";
    public static final String READSET_TO_CITESEQ_MAP = "readsetToCiteSeqMap";

    private final Logger _log = LogManager.getLogger(CellHashingServiceImpl.class);

    private CellHashingServiceImpl()
    {

    }

    public static CellHashingServiceImpl get()
    {
        return _instance;
    }

    private File writeAllCellHashingBarcodes(File webserverDir, User u, Container c) throws PipelineJobException
    {
        return CellHashingHandler.writeAllBarcodes(CellHashingHandler.BARCODE_TYPE.hashing, webserverDir, u, c);
    }

    private File writeAllCiteSeqBarcodes(File webserverDir, User u, Container c) throws PipelineJobException
    {
        return CellHashingHandler.writeAllBarcodes(CellHashingHandler.BARCODE_TYPE.citeseq, webserverDir, u, c);
    }

    @Override
    public File runCiteSeqCount(PipelineStepOutput output, @Nullable String outputCategory, Readset htoReadset, File htoList, File cellBarcodeList, File outputDir, String basename, Logger log, List<String> extraArgs, boolean doHtoFiltering, @Nullable Integer minCountPerCell, File localPipelineDir, @Nullable Integer editDistance, boolean scanEditDistances, Readset parentReadset, @Nullable Integer genomeId, boolean generateHtoCalls, boolean createOutputFiles, boolean useSeurat, boolean useMultiSeq) throws PipelineJobException
    {
        return new CellHashingHandler().runCiteSeqCount(output, outputCategory, htoReadset, htoList, cellBarcodeList, outputDir, basename, log, extraArgs, doHtoFiltering, minCountPerCell, localPipelineDir, editDistance, scanEditDistances, parentReadset, genomeId, generateHtoCalls ? CellHashingHandler.BARCODE_TYPE.hashing : CellHashingHandler.BARCODE_TYPE.citeseq, createOutputFiles, useSeurat, useMultiSeq);
    }

    @Override
    public void prepareHashingAndCiteSeqFilesIfNeeded(File sourceDir, PipelineJob job, SequenceAnalysisJobSupport support, String filterField, final boolean skipFailedCdna, boolean failIfNoHashing, boolean failIfNoCiteSeq) throws PipelineJobException
    {
        Container target = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
        UserSchema singleCell = QueryService.get().getUserSchema(job.getUser(), target, SingleCellSchema.NAME);
        TableInfo cDNAs = singleCell.getTable(SingleCellSchema.TABLE_CDNAS, null);

        _log.debug("preparing cDNA and cell hashing files");

        writeAllCellHashingBarcodes(sourceDir, job.getUser(), job.getContainer());
        writeAllCiteSeqBarcodes(sourceDir, job.getUser(), job.getContainer());

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
                        _log.info("skipping cDNA with non-null status: " + results.getString(FieldKey.fromString("rowid")));
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
                    throw new PipelineJobException("No cell hashing readset or HTO found for one or more cDNAs. see the file: " + output.getName());
                }

                if (hashingStatus.size() > 1)
                {
                    _log.info("The selected readsets/cDNA records use a mixture of cell hashing and non-hashing.");
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
                _log.info("distinct HTOs: " + distinctHTOs.size());
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

    public static File getValidCiteSeqBarcodeFile(File sourceDir, int gexReadsetId)
    {
        return new File(sourceDir, "validADTS." + gexReadsetId + ".csv");
    }

    public static File getValidCiteSeqBarcodeMetadataFile(File sourceDir, int gexReadsetId)
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
                FieldKey.fromString("antibody/adaptersequence")
        ));

        for (int gexReadsetId : gexToPanels.keySet())
        {
            job.getLogger().info("Writing all unique ADTs for readset: " + gexReadsetId);
            File barcodeOutput = getValidCiteSeqBarcodeFile(outputDir, gexReadsetId);
            File metadataOutput = getValidCiteSeqBarcodeMetadataFile(outputDir, gexReadsetId);
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(barcodeOutput), ',', CSVWriter.NO_QUOTE_CHARACTER);CSVWriter metaWriter = new CSVWriter(PrintWriters.getPrintWriter(metadataOutput), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                metaWriter.writeNext(new String[]{"tagname", "sequence", "markername", "markerlabel"});
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
    public File runRemoteVdjCellHashingTasks(PipelineStepOutput output, String outputCategory, File perCellTsv, Readset rs, SequenceAnalysisJobSupport support, List<String> extraParams, File workingDir, File sourceDir, Integer editDistance, boolean scanEditDistances, Integer genomeId, Integer minCountPerCell, boolean useSeurat, boolean useMultiSeq) throws PipelineJobException
    {
        Map<Integer, Integer> readsetToHashing = getCachedHashingReadsetMap(support);
        if (readsetToHashing.isEmpty())
        {
            _log.info("No cached hashing readsets, skipping");
            return null;
        }

        //prepare whitelist of barcodes, based on cDNA records
        File htoBarcodeWhitelist = getValidHashingBarcodeFile(sourceDir);
        if (!htoBarcodeWhitelist.exists())
        {
            throw new PipelineJobException("Unable to find file: " + htoBarcodeWhitelist.getPath());
        }

        long lineCount = SequencePipelineService.get().getLineCount(htoBarcodeWhitelist);
        if (lineCount == 1)
        {
            _log.info("Only one HTO is used, will not use hashing");
            return null;
        }

        _log.debug("total cached readset/hashing readset pairs: " + readsetToHashing.size());
        _log.debug("unique HTOs: " + lineCount);

        //prepare whitelist of cell indexes
        File cellBarcodeWhitelist = getValidCellIndexFile(sourceDir);
        Set<String> uniqueBarcodes = new HashSet<>();
        Set<String> uniqueBarcodesIncludingNoCDR3 = new HashSet<>();
        _log.debug("writing cell barcodes, using file: " + perCellTsv.getPath());
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(cellBarcodeWhitelist), ',', CSVWriter.NO_QUOTE_CHARACTER); CSVReader reader = new CSVReader(Readers.getReader(perCellTsv), ','))
        {
            int rowIdx = 0;
            int noCallRows = 0;
            int nonCell = 0;
            String[] row;
            while ((row = reader.readNext()) != null)
            {
                //skip header
                rowIdx++;
                if (rowIdx > 1)
                {
                    if ("False".equalsIgnoreCase(row[1]))
                    {
                        nonCell++;
                        continue;
                    }

                    //NOTE: allow these to pass for cell-hashing under some conditions
                    boolean hasCDR3 = !"None".equals(row[12]);
                    if (!hasCDR3)
                    {
                        noCallRows++;
                    }

                    //NOTE: 10x appends "-1" to barcodes
                    String barcode = row[0].split("-")[0];
                    if (hasCDR3 && !uniqueBarcodes.contains(barcode))
                    {
                        writer.writeNext(new String[]{barcode});
                        uniqueBarcodes.add(barcode);
                    }

                    uniqueBarcodesIncludingNoCDR3.add(barcode);
                }
            }

            _log.debug("rows inspected: " + (rowIdx - 1));
            _log.debug("rows without CDR3: " + noCallRows);
            _log.debug("rows not called as cells: " + nonCell);
            _log.debug("unique cell barcodes (with CDR3): " + uniqueBarcodes.size());
            _log.debug("unique cell barcodes (including no CDR3): " + uniqueBarcodesIncludingNoCDR3.size());
            output.addIntermediateFile(cellBarcodeWhitelist);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        if (uniqueBarcodes.size() < 500 && uniqueBarcodesIncludingNoCDR3.size() > uniqueBarcodes.size())
        {
            _log.info("Total cell barcodes with CDR3s is low, so cell hashing will be performing using an input that includes valid cells that lacked CDR3 data.");
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(cellBarcodeWhitelist), ',', CSVWriter.NO_QUOTE_CHARACTER))
            {
                for (String barcode : uniqueBarcodesIncludingNoCDR3)
                {
                    writer.writeNext(new String[]{barcode});
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        Readset htoReadset = support.getCachedReadset(readsetToHashing.get(rs.getReadsetId()));
        if (htoReadset == null)
        {
            throw new PipelineJobException("Unable to find HTO readset for readset: " + rs.getRowId());
        }

        //run CiteSeqCount.  this will use Multiseq to make calls per cell
        String basename = FileUtil.makeLegalName(rs.getName());
        File hashtagCalls = runCiteSeqCount(output, outputCategory, htoReadset, htoBarcodeWhitelist, cellBarcodeWhitelist, workingDir, basename, _log, extraParams, false, minCountPerCell, sourceDir, editDistance, scanEditDistances, rs, genomeId, true, true, useSeurat, useMultiSeq);
        if (!hashtagCalls.exists())
        {
            throw new PipelineJobException("Unable to find expected file: " + hashtagCalls.getPath());
        }
        output.addOutput(hashtagCalls, HASHING_CALLS);

        File html = new File(hashtagCalls.getParentFile(), FileUtil.getBaseName(FileUtil.getBaseName(hashtagCalls.getName())) + ".html");
        if (!html.exists())
        {
            throw new PipelineJobException("Unable to find HTML file: " + html.getPath());
        }

        output.addOutput(html, "Cell Hashing TCR Report");

        return hashtagCalls;
    }

    public static Map<Integer, Integer> getCachedCiteSeqReadsetMap(SequenceAnalysisJobSupport support) throws PipelineJobException
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

    @Override
    public Map<Integer, Integer> getCachedHashingReadsetMap(Object sequenceJobSupport) throws PipelineJobException
    {
        if (!(sequenceJobSupport instanceof SequenceAnalysisJobSupport))
        {
            throw new IllegalArgumentException("Object must be instanceof SequenceAnalysisJobSupport!");
        }

        return getCachedHashingReadsetMap((SequenceAnalysisJobSupport)sequenceJobSupport);
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
        return new File(callFile.getPath().replaceAll(".calls.txt", ".metrics.txt"));
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
                    for (String metricName : Arrays.asList("InputBarcodes", "TotalCalled", "TotalCounts", "TotalSinglet", "FractionOfInputCalled", "FractionOfInputSinglet", "FractionOfInputDoublet", "FractionOfInputDiscordant", "FractionCalledNotInInput", "SeuratNonNegative", "MultiSeqNonNegative", "UniqueHtos", "UnknownTagMatchingKnown"))
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
    public List<ToolParameterDescriptor> getDefaultHashingParams(boolean includeExcludeFailedcDNA)
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>(Arrays.asList(
                ToolParameterDescriptor.create("scanEditDistances", "Scan Edit Distances", "If checked, CITE-seq-count will be run using edit distances from 0-3 and the iteration with the highest singlets will be used.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, false),
                ToolParameterDescriptor.create("editDistance", "Edit Distance", null, "ldk-integerfield", null, 2),
                ToolParameterDescriptor.create("minCountPerCell", "Min Reads/Cell", null, "ldk-integerfield", null, 5),
                ToolParameterDescriptor.create("useSeurat", "Use Seurat Calling", "If checked, the seurat HTO calling algorithm will be used.", "checkbox", null, true),
                ToolParameterDescriptor.create("useMultiSeq", "Use MultiSeq Calling", "If checked, the MultiSeq HTO calling algorithm will be used.", "checkbox", null, true)
        ));

        if (includeExcludeFailedcDNA)
        {
            ret.add(ToolParameterDescriptor.create("excludeFailedcDNA", "Exclude Failed cDNA", "If selected, cDNAs with non-blank status fields will be omitted", "checkbox", null, true));
        }

        return ret;
    }
}
