package org.labkey.sequenceanalysis.run.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.filter.DuplicateReadFilter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User: bimber
 * Date: 7/1/2014
 * Time: 11:10 AM
 */
public class SequenceBasedTypingAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public SequenceBasedTypingAnalysis(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    private static final String EXPORT_UNMAPPED = "EXPORT_UNMAPPED";
    public static final String MIN_EXPORT_LENGTH = "MIN_EXPORT_LENGTH";
    public static class Provider extends AbstractAnalysisStepProvider<SequenceBasedTypingAnalysis>
    {
        public Provider()
        {
            super("SBT", "Sequence Based Genotyping", null, "If selected, each alignment will be inspected, and those alignments lacking any high quality SNPs will be retained.  A report will be generated summarizing these matches, per read.", getDefaultParams(true), null, null);
        }

        @Override
        public SequenceBasedTypingAnalysis create(PipelineContext ctx)
        {
            return new SequenceBasedTypingAnalysis(this, ctx);
        }
    }

    public static List<ToolParameterDescriptor> getDefaultParams(boolean includeExport)
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>();
            ret.addAll(Arrays.asList(ToolParameterDescriptor.create("minSnpQual", "Minimum SNP Qual", "Only SNPs with a quality score above this threshold will be included.", "ldk-integerfield", new JSONObject()
            {{
                put("minValue", 0);
            }}, 17),
            ToolParameterDescriptor.create("minSnpAvgQual", "Minimum SNP Avg Qual", "If provided, the average quality score of all SNPs of a give base at each position must be above this value.", "ldk-integerfield", new JSONObject()
            {{
                put("minValue", 0);
            }}, 17),
            ToolParameterDescriptor.create("minDnpQual", "Minimum DIP Qual", "Only DIPs (deletion/indel polymorphisms) with a quality score above this threshold will be included.", "ldk-integerfield", new JSONObject()
            {{
                put("minValue", 0);
            }}, 17),
            ToolParameterDescriptor.create("minDnpAvgQual", "Minimum DIP Avg Qual", "If provided, the average quality score of all DIPs (deletion/indel polymorphisms) of a give base at each position must be above this value.", "ldk-integerfield", new JSONObject()
            {{
                put("minValue", 0);
            }}, 17),
            ToolParameterDescriptor.create("minMapQual", "Minimum Mapping Qual", "If provided, any alignment with a mapping quality lower than this value will be discarded", "ldk-integerfield", new JSONObject()
            {{
                put("minValue", 0);
            }}, 0),
            ToolParameterDescriptor.create("onlyImportValidPairs", "Only Import Valid Pairs", "If selected, only alignments consisting of valid forward/reverse pairs will be imported.  Do not check this unless you are using paired-end sequence.", "checkbox", new JSONObject()
            {{
                put("checked", true);
            }}, null),
            ToolParameterDescriptor.create("minCountForRef", "Min Read # Per Reference", "If a value is provided, for a reference to be considered an allowable hit, it must be present in at least this many reads across each sample.  This can be a way to reduce ambiguity among allele calls.", "ldk-integerfield", new JSONObject()
            {{
                put("minValue", 0);
            }}, 5),
            ToolParameterDescriptor.create("minPctForRef", "Min Read Pct Per Reference", "If a value is provided, for a reference to be considered an allowable hit, it must be present in at least this percent of total from each sample.  This can be a way to reduce ambiguity among allele calls.  Value should between 0-100.", "ldk-numberfield", new JSONObject()
            {{
                put("minValue", 0);
                put("maxValue", 100);
            }}, 0.05),
            ToolParameterDescriptor.create("minPctWithinGroup", "Min Read Pct Within Group", "If a value is provided, for a reference to be considered an allowable hit, it must be present in at least this percent of total reads within a set of hits.  For example, says 30 reads matched alleles A, B and C.  Within the whole sample, 300 reads aligned to allele B, 200 to allele B and only 30 aligned to C.  The latter represent 10% (30 / 300) of hits for that group.  If you set this filter above this, allele C would be discarded.  Value should between 0-100.", "ldk-numberfield", new JSONObject()
            {{
                put("minValue", 0);
                put("maxValue", 100);
            }}, 25),
            ToolParameterDescriptor.create("minPctForLineageFiltering", "Min Pct For Lineage Filtering", "If a value is provided, each group of allele hits will be categorized by lineage.  Any groupings representing more than the specified percent of reads from that lineage will be included.  Per lineage, we will also find the intersect of all groups.  If a set of alleles is common to all groups, only these alleles will be kept and the others discarded.", "ldk-numberfield", new JSONObject()
            {{
                put("minValue", 0);
                put("maxValue", 1);
                put("decimalPrecision", 4);
            }}, 0.025),
            ToolParameterDescriptor.create("minAlignmentLength", "Min Alignment Length", "If a value is provided, any alignment with a length less than this value will be discarded.", "ldk-integerfield", new JSONObject()
            {{
                put("minValue", 0);
            }}, 40),
            ToolParameterDescriptor.create("writeLog", "Write Detailed Log", "If checked, the analysis will write a detailed log file of read mapping and calls.  This is intended for debugging purposes", "checkbox", new JSONObject()
            {{
                put("checked", false);
            }}, null)
        ));

        if (includeExport)
        {
            ret.add(ToolParameterDescriptor.create(EXPORT_UNMAPPED, "Export Unmapped Threshold", "If provided, reads that aligned to a reference, but were discarded/filtered will be exported if at least this fraction of all reads do not have passing hits.", "ldk-numberfield", new JSONObject()
            {{
                put("minValue", 0);
                put("maxValue", 1);
            }}, 0.10));
        }

        ret.add(ToolParameterDescriptor.create(MIN_EXPORT_LENGTH, "Min Length For Export", "If provided, only unmapped reads longer than this value will be exported.", "ldk-numberfield", new JSONObject()
        {{
            put("minValue", 0);
        }}, 60));

        return ret;
    }

    @Override
    public void init(SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        prepareLineageMapFiles(support, getPipelineCtx().getLogger(), getPipelineCtx().getSourceDirectory());
    }

    public static void prepareLineageMapFiles(SequenceAnalysisJobSupport support, Logger log, File sourceDirectory) throws PipelineJobException
    {
        log.debug("preparing lineage map files");
        for (ReferenceGenome genome : support.getCachedGenomes())
        {
            if (genome.isTemporaryGenome())
            {
                continue;
            }

            File lineageMapFile = new File(sourceDirectory, genome.getGenomeId() + "_lineageMap.txt");
            try (final CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(lineageMapFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                log.info("writing lineage map file");
                SQLFragment sql = new SQLFragment("SELECT r.name, r.lineage FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES + " r WHERE r.rowid IN (SELECT ref_nt_id FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS + " WHERE library_id = ?)", genome.getGenomeId());
                SqlSelector ss = new SqlSelector(DbScope.getLabKeyScope(), sql);
                ss.forEach(new Selector.ForEachBlock<ResultSet>()
                {
                    @Override
                    public void exec(ResultSet rs) throws SQLException
                    {
                        if (rs.getObject("lineage") != null)
                            writer.writeNext(new String[]{rs.getString("name"), rs.getString("lineage")});
                    }
                });
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        File expectedTxt = getSBTSummaryFile(outDir, inputBam);
        if (expectedTxt.exists())
        {
            getPipelineCtx().getLogger().info("Processing SBT output: " + expectedTxt.getPath());

            SequenceBasedTypingAlignmentAggregator.processSBTSummary(getPipelineCtx().getJob().getUser(), getPipelineCtx().getJob().getContainer(), model, expectedTxt, referenceFasta, getPipelineCtx().getLogger());

            File compressed = Compress.compressGzip(expectedTxt);
            if (compressed.exists() && expectedTxt.exists())
            {
                expectedTxt.delete();
            }

            // Perform second pass to collapse groups:
            new AlignmentGroupCompare(model.getAnalysisId(), getPipelineCtx().getJob().getContainer(), getPipelineCtx().getJob().getUser()).collapseGroups(getPipelineCtx().getLogger(), getPipelineCtx().getJob().getUser());
        }
        else
        {
            getPipelineCtx().getLogger().info("SBT output not found, skipping: " + expectedTxt.getPath());
        }

        //delete lineage files
        if (model.getLibraryId() != null)
        {
            ReferenceGenome referenceGenome = SequenceAnalysisService.get().getReferenceGenome(model.getLibraryId(), getPipelineCtx().getJob().getUser());
            if (referenceGenome == null)
            {
                throw new PipelineJobException("Genome not found: " + model.getLibraryId());
            }

            File lineageMapFile = new File(getPipelineCtx().getSourceDirectory(), referenceGenome.getGenomeId() + "_lineageMap.txt");
            if (lineageMapFile.exists())
            {
                getPipelineCtx().getLogger().debug("deleting lineage map file: " + lineageMapFile.getName());
                lineageMapFile.delete();
            }
        }

        return null;
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        try
        {
            AnalysisOutputImpl output = new AnalysisOutputImpl();

            Map<String, String> toolParams = new HashMap<>();
            List<ToolParameterDescriptor> params = getProvider().getParameters();
            for (ToolParameterDescriptor td : params)
            {
                toolParams.put(td.getName(), td.extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()));
            }

            //first calculate avg qualities at each position
            getPipelineCtx().getLogger().info("Calculating avg quality scores");
            AvgBaseQualityAggregator avgBaseQualityAggregator = new AvgBaseQualityAggregator(getPipelineCtx().getLogger(), inputBam, referenceGenome.getWorkingFastaFile(), List.of(
                    new DuplicateReadFilter()
            ));
            avgBaseQualityAggregator.calculateAvgQuals();
            getPipelineCtx().getLogger().info("\tCalculation complete");

            getPipelineCtx().getLogger().info("Inspecting alignments in BAM");
            BamIterator bi = new BamIterator(inputBam, referenceGenome.getWorkingFastaFile(), getPipelineCtx().getLogger());

            List<AlignmentAggregator> aggregators = new ArrayList<>();
            SequenceBasedTypingAlignmentAggregator agg = new SequenceBasedTypingAlignmentAggregator(getPipelineCtx().getLogger(), referenceGenome.getWorkingFastaFile(), avgBaseQualityAggregator, toolParams);
            if (getProvider().getParameterByName("writeLog").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false))
            {
                File workDir = new File(getPipelineCtx().getSourceDirectory(), FileUtil.getBaseName(inputBam));
                if (!workDir.exists())
                {
                    workDir.mkdirs();
                }
                File outputLog = new File(workDir, FileUtil.getBaseName(inputBam) + ".sbt.txt.gz");
                agg.setOutputLog(outputLog);
            }

            File lineageMapFile = new File(getPipelineCtx().getSourceDirectory(), referenceGenome.getGenomeId() + "_lineageMap.txt");
            if (lineageMapFile.exists())
            {
                getPipelineCtx().getLogger().debug("using lineage map: " + lineageMapFile.getName());
                agg.setLineageMapFile(lineageMapFile);

                Double minPctForLineageFiltering = getProvider().getParameterByName("minPctForLineageFiltering").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Double.class);
                if (minPctForLineageFiltering != null)
                {
                    agg.setMinPctForLineageFiltering(minPctForLineageFiltering);
                }
            }
            else
            {
                getPipelineCtx().getLogger().debug("lineage map not found, skipping");
            }

            agg.setDoTrackIntervals(true);

            aggregators.add(agg);

            bi.addAggregators(aggregators);
            bi.iterateReads();
            getPipelineCtx().getLogger().info("Inspection complete");

            //write output as TSV
            agg.writeTable(getSBTSummaryFile(outputDir, inputBam));

            //optionally output FASTQ of unmapped reads
            Double exportThreshold = getProvider().getParameterByName(EXPORT_UNMAPPED).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Double.class);
            if (exportThreshold != null)
            {
                double pctUnmapped = agg.getPctMappedWithoutHits();
                getPipelineCtx().getLogger().debug("fraction reads unmapped: " + pctUnmapped);
                if (pctUnmapped >= exportThreshold)
                {
                    getPipelineCtx().getLogger().info("exporting unmapped reads");
                    String prefix = "";
                    if (rs != null && rs.getSubjectId() != null)
                    {
                        prefix = rs.getSubjectId() + "|" + rs.getRowId() + "|";
                    }

                    List<Pair<File, File>> readData = new ArrayList<>();
                    for (ReadData d : rs.getReadData())
                    {
                        readData.add(Pair.of(d.getFile1(), d.getFile2()));
                    }

                    int minExportLength = getProvider().getParameterByName(MIN_EXPORT_LENGTH).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, 0);
                    Pair<File, File> unmapped = agg.outputUnmappedReads(inputBam, readData, outputDir, FileUtil.getBaseName(inputBam) + ".unmapped", prefix, minExportLength);
                    if (unmapped != null)
                    {
                        if (unmapped.first != null)
                        {
                            output.addOutput(unmapped.first, "Unmapped SBT Reads (FASTQ)");
                        }

                        if (unmapped.second != null)
                        {
                            output.addOutput(unmapped.second, "Unmapped SBT Reads (Collapsed)");
                        }
                    }

                    File referencesCovered = agg.outputReferencesCovered(outputDir, FileUtil.getBaseName(inputBam), referenceGenome.getWorkingFastaFile(), prefix);
                    if (referencesCovered.exists())
                    {
                        output.addOutput(referencesCovered, "Reference Sequence Coverage FASTA");
                    }
                    else
                    {
                        getPipelineCtx().getLogger().warn("unable to find expected FASTA: " + referencesCovered.getPath());
                    }
                }
                else
                {
                    getPipelineCtx().getLogger().debug("will not export unmapped");
                }
            }


            return output;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    protected File getSBTSummaryFile(File outputDir, File bam)
    {
        return new File(outputDir, FileUtil.getBaseName(bam) + ".sbt_hits.txt");
    }

    public static class AlignmentGroupCompare
    {
        private final int analysisId;
        private final List<AlignmentGroup> groups = new ArrayList<>();

        public AlignmentGroupCompare(final int analysisId, Container c, User u)
        {
            this.analysisId = analysisId;

            new TableSelector(QueryService.get().getUserSchema(u, c, "sequenceanalysis").getTable("alignment_summary_grouped"), PageFlowUtil.set("analysis_id", "alleles", "lineages", "totalLineages", "total_reads", "total_forward", "total_reverse", "valid_pairs", "rowids"), new SimpleFilter(FieldKey.fromString("analysis_id"), analysisId), null).forEachResults(rs -> {
                if (rs.getString(FieldKey.fromString("alleles")) == null)
                {
                    return;
                }

                AlignmentGroup g = new AlignmentGroup();
                g.analysisId = analysisId;
                g.alleles.addAll(Arrays.stream(rs.getString(FieldKey.fromString("alleles")).split("\n")).toList());
                g.lineages = rs.getString(FieldKey.fromString("lineages"));
                g.totalLineages = rs.getInt(FieldKey.fromString("totalLineages"));
                g.totalReads = rs.getInt(FieldKey.fromString("total_reads"));
                g.totalForward = rs.getInt(FieldKey.fromString("total_forward"));
                g.totalReverse = rs.getInt(FieldKey.fromString("total_reverse"));
                g.validPairs = rs.getInt(FieldKey.fromString("valid_pairs"));
                g.rowIds.addAll(Arrays.stream(rs.getString(FieldKey.fromString("rowids")).split(",")).map(Integer::parseInt).toList());

                groups.add(g);
            });

            sortGroups();
        }

        private void sortGroups()
        {
            groups.sort(Comparator.comparingInt(o -> o.alleles.size()));
            Collections.reverse(groups);
        }

        public Pair<Integer, Integer> collapseGroups(Logger log, User user)
        {
            final long initialCounts = groups.stream().map(x -> x.totalReads).mapToInt(Integer::intValue).sum();

            if (groups.isEmpty())
            {
                return null;
            }

            Pair<Integer, Integer> ret = Pair.of(0, 0);
            while (doCollapse(log))
            {
                //do work. each time we have any groups collapsed, we will restart. once there are no collapsed allele groups, we finish
                sortGroups();
            }

            final int endCounts = groups.stream().map(x -> x.totalReads).mapToInt(Integer::intValue).sum();
            if (initialCounts != endCounts)
            {
                throw new IllegalStateException("Starting/ending counts not equal: " + initialCounts + " / " + endCounts);
            }

            List<Integer> alignmentIdsToDelete = groups.stream().map(x -> x.rowIdsToDelete).flatMap(List::stream).toList();
            List<AlignmentGroup> alignmentGroupsToUpdate = groups.stream().filter(g -> !g.rowIdsToDelete.isEmpty()).toList();
            log.info("Alignment IDs to delete: " + alignmentIdsToDelete.size());
            log.info("Alignment groups to update counts: " + alignmentGroupsToUpdate.size());

            if (!alignmentGroupsToUpdate.isEmpty())
            {
                log.info("Updating counts in " + alignmentGroupsToUpdate.size() + " groups after collapse");
                TableInfo alignmentSummary = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("alignment_summary");

                alignmentGroupsToUpdate.forEach(ag -> {
                    Map<String, Object> toUpdate = new CaseInsensitiveHashMap<>();
                    toUpdate.put("rowId", ag.rowIds.get(0));
                    toUpdate.put("total", ag.totalReads);
                    toUpdate.put("total_forward", ag.totalForward);
                    toUpdate.put("total_reverse", ag.totalReverse);
                    toUpdate.put("valid_pairs", ag.validPairs);
                    Table.update(user, alignmentSummary, toUpdate, ag.rowIds.get(0));

                    if (ag.rowIds.size() > 1) {
                        log.info("The following IDs are redundant and will also be removed: " + ag.rowIds.subList(1, ag.rowIds.size()).stream().map(String::valueOf).collect(Collectors.joining(", ")));
                        alignmentIdsToDelete.addAll(ag.rowIds.subList(1, ag.rowIds.size()));
                    }
                });
            }

            if (!alignmentIdsToDelete.isEmpty())
            {
                log.info("Deleting " + alignmentIdsToDelete.size() + " alignment_summary records after collapse");

                TableInfo alignmentSummary = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("alignment_summary");
                TableInfo alignmentSummaryJunction = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("alignment_summary_junction");

                alignmentIdsToDelete.forEach(rowId -> {
                    Table.delete(alignmentSummary, rowId);
                });
                ret.first += alignmentIdsToDelete.size();

                // also junction records:
                SimpleFilter alignmentIdFilter = new SimpleFilter(FieldKey.fromString("analysis_id"), analysisId, CompareType.EQUAL);
                alignmentIdFilter.addCondition(FieldKey.fromString("alignment_id"), alignmentIdsToDelete, CompareType.IN);
                List<Integer> junctionRecordsToDelete = new TableSelector(alignmentSummaryJunction, PageFlowUtil.set("rowid"), alignmentIdFilter, null).getArrayList(Integer.class);
                log.info("Deleting " + junctionRecordsToDelete.size() + " alignment_summary_junction records");
                if (!junctionRecordsToDelete.isEmpty())
                {
                    junctionRecordsToDelete.forEach(rowId -> {
                        Table.delete(alignmentSummaryJunction, rowId);
                    });
                    ret.second += junctionRecordsToDelete.size();
                }
            }

            return ret;
        }

        private boolean doCollapse(Logger log)
        {
            ListIterator<AlignmentGroup> it = groups.listIterator();
            AlignmentGroup g1 = it.next();
            while (it.hasNext())
            {
                int orig = g1.alleles.size();
                if (compareGroupToOthers(g1))
                {
                    log.info("Collapsed: " + g1.lineages + ", from: " + orig + " to " + g1.alleles.size() + " alleles");
                    return true; // abort and restart the process with a new list iterator
                }

                g1 = it.next();
            }

            return false;
        }

        private boolean compareGroupToOthers(AlignmentGroup g1)
        {
            boolean didCollapse = false;
            int idx = groups.indexOf(g1);
            if (idx == groups.size() - 1)
            {
                return false;
            }

            List<AlignmentGroup> groupsClone = new ArrayList<>(groups.subList(idx + 1, groups.size()));
            ListIterator<AlignmentGroup> it = groupsClone.listIterator();
            while (it.hasNext())
            {
                AlignmentGroup g2 = it.next();
                if (g2.equals(g1))
                {
                    throw new IllegalStateException("Should not happen");
                }

                if (g1.canCombine(g2))
                {
                    AlignmentGroup combined = g1.combine(g2);
                    groups.remove(g1);
                    groups.add(idx, combined);
                    g1 = combined;
                    groups.remove(g2);

                    didCollapse = true;
                }
            }

            return didCollapse;
        }

        public static class AlignmentGroup
        {
            int analysisId;
            Set<String> alleles = new TreeSet<>();
            String lineages;
            int totalLineages;
            int totalReads;
            int totalForward;
            int totalReverse;
            int validPairs;
            List<Integer> rowIds = new ArrayList<>();

            List<Integer> rowIdsToDelete = new ArrayList<>();

            public boolean canCombine(AlignmentGroup g2)
            {
                if (this.totalLineages > 1 || g2.totalLineages > 1 || this.alleles.size() < 4 || g2.alleles.size() < 4)
                {
                    return false;
                }

                // Allow greater level of collapse with highly ambiguous results:
                // Require similar sizes, but disjoint allele sets (e.g., A/B/D and A/C/D, but not A/B/C and A/D/E)
                int setDiffThreshold;
                int sizeDiffThreshold;
                if (this.alleles.size() >= 16)
                {
                    setDiffThreshold = 6;
                    sizeDiffThreshold = 3;
                }
                else if (this.alleles.size() >= 8)
                {
                    setDiffThreshold = 4;
                    sizeDiffThreshold = 2;
                }
                else
                {
                    setDiffThreshold = 2;
                    sizeDiffThreshold = 1;
                }

                return Math.abs(this.alleles.size() - g2.alleles.size()) <= sizeDiffThreshold && CollectionUtils.disjunction(this.alleles, g2.alleles).size() <= setDiffThreshold;
            }

            public AlignmentGroup combine(AlignmentGroup g2)
            {
                // Take the union of the allele sets:
                TreeSet<String> allAlleles = Stream.of(this.alleles, g2.alleles).flatMap(Collection::stream).collect(Collectors.toCollection(TreeSet::new));
                if (g2.alleles.size() > this.alleles.size())
                {
                    g2.alleles = allAlleles;
                    g2.rowIdsToDelete.addAll(this.rowIds);
                    g2.rowIdsToDelete.addAll(this.rowIdsToDelete);
                    g2.totalReads = g2.totalReads + totalReads;
                    g2.totalForward = g2.totalForward + totalForward;
                    g2.totalReverse = g2.totalReverse + totalReverse;
                    g2.validPairs = g2.validPairs + validPairs;

                    return g2;
                }
                else
                {
                    this.alleles = allAlleles;
                    this.rowIdsToDelete.addAll(g2.rowIds);
                    this.rowIdsToDelete.addAll(g2.rowIdsToDelete);
                    this.totalReads = g2.totalReads + totalReads;
                    this.totalForward = g2.totalForward + totalForward;
                    this.totalReverse = g2.totalReverse + totalReverse;
                    this.validPairs = g2.validPairs + validPairs;

                    return this;
                }
            }
        }
    }
}
