package org.labkey.sequenceanalysis.run.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.filter.DuplicateReadFilter;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.json.JSONObject;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.pipeline.PipelineJobException;
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
import org.labkey.api.util.Pair;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/1/2014
 * Time: 11:10 AM
 */
public class SequenceBasedTypingAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public SequenceBasedTypingAnalysis(PipelineStepProvider provider, PipelineContext ctx)
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
        //TODO: store pct of mapped matching MHC

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
            AvgBaseQualityAggregator avgBaseQualityAggregator = new AvgBaseQualityAggregator(getPipelineCtx().getLogger(), inputBam, referenceGenome.getWorkingFastaFile(), Arrays.asList(
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
}
