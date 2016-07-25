package org.labkey.sequenceanalysis.run.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.filter.DuplicateReadFilter;
import htsjdk.samtools.filter.SamRecordFilter;
import org.json.JSONObject;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.QueryService;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public static class Provider extends AbstractAnalysisStepProvider<SequenceBasedTypingAnalysis>
    {
        public Provider()
        {
            super("SBT", "Sequence Based Genotyping", null, "If selected, each alignment will be inspected, and those alignments lacking any high quality SNPs will be retained.  A report will be generated summarizing these matches, per read.", Arrays.asList(
                    ToolParameterDescriptor.create("minSnpQual", "Minimum SNP Qual", "Only SNPs with a quality score above this threshold will be included.", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 17),
                    ToolParameterDescriptor.create("minSnpAvgQual", "Minimum SNP Avg Qual", "If provided, the average quality score of all SNPs of a give base at each position must be above this value.", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 17),
                    ToolParameterDescriptor.create("minSnpQual", "Minimum DIP Qual", "Only DIPs (deletion/indel polymorphisms) with a quality score above this threshold will be included.", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 17),
                    ToolParameterDescriptor.create("minSnpAvgQual", "Minimum DIP Avg Qual", "If provided, the average quality score of all DIPs (deletion/indel polymorphisms) of a give base at each position must be above this value.", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 17),
                    ToolParameterDescriptor.create("onlyImportValidPairs", "Only Import Valid Pairs", "If selected, only alignments consisting of valid forward/reverse pairs will be imported.  Do not check this unless you are using paired-end sequence.", "checkbox", new JSONObject()
                    {{
                            put("checked", false);
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
                    }}, 0.05),
                    ToolParameterDescriptor.create("minAlignmentLength", "Min Alignment Length", "If a value is provided, any alignment with a length less than this value will be discarded.", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 40),
                    ToolParameterDescriptor.create("writeLog", "Write Detailed Log", "If checked, the analysis will write a detailed log file of read mapping and calls.  This is intended for debugging purposes", "checkbox", new JSONObject()
                    {{
                            put("checked", false);
                    }}, null)
            ), null, null);
        }

        @Override
        public SequenceBasedTypingAnalysis create(PipelineContext ctx)
        {
            return new SequenceBasedTypingAnalysis(this, ctx);
        }
    }

    @Override
    public void init(List<AnalysisModel> models) throws PipelineJobException
    {
        Set<Integer> distinctGenomeIds = new HashSet<>();
        for (AnalysisModel m : models)
        {
            if (m.getLibraryId() != null)
            {
                distinctGenomeIds.add(m.getLibraryId());
            }
        }

        for (Integer libraryId : distinctGenomeIds)
        {
            ReferenceGenome referenceGenome = SequenceAnalysisService.get().getReferenceGenome(libraryId, getPipelineCtx().getJob().getUser());
            if (referenceGenome == null)
            {
                throw new PipelineJobException("Genome not found: " + libraryId);
            }

            File lineageMapFile = new File(getPipelineCtx().getSourceDirectory(), referenceGenome.getGenomeId() + "_lineageMap.txt");
            try (final CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(lineageMapFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                SQLFragment sql = new SQLFragment("SELECT r.name, r.lineage FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES + " r WHERE r.rowid IN (SELECT ref_nt_id FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS + " WHERE library_id = ?)", libraryId);
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
        }
        else
        {
            getPipelineCtx().getLogger().info("SBT output not found, skipping: " + expectedTxt.getPath());
        }

        //delete lineage files
        ReferenceGenome referenceGenome = SequenceAnalysisService.get().getReferenceGenome(model.getLibraryId(), getPipelineCtx().getJob().getUser());
        if (referenceGenome == null)
        {
            throw new PipelineJobException("Genome not found: " + model.getLibraryId());
        }

        File lineageMapFile = new File(getPipelineCtx().getSourceDirectory(), referenceGenome.getGenomeId() + "_lineageMap.txt");
        if (lineageMapFile.exists())
        {
            lineageMapFile.delete();
        }

        return null;
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        try
        {
            Map<String, String> toolParams = new HashMap<>();
            List<ToolParameterDescriptor> params = getProvider().getParameters();
            for (ToolParameterDescriptor td : params)
            {
                toolParams.put(td.getName(), td.extractValue(getPipelineCtx().getJob(), getProvider()));
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
            if (getProvider().getParameterByName("writeLog").extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, false))
            {
                File workDir = new File(getPipelineCtx().getSourceDirectory(), FileUtil.getBaseName(inputBam));
                if (!workDir.exists())
                {
                    workDir.mkdirs();
                }
                File outputLog = new File(workDir, FileUtil.getBaseName(inputBam) + ".sbt.txt.gz");
                agg.setOutputLog(outputLog);

                File lineageMapFile = new File(getPipelineCtx().getSourceDirectory(), referenceGenome.getGenomeId() + "_lineageMap.txt");
                if (lineageMapFile.exists())
                {
                    getPipelineCtx().getLogger().debug("using lineage map: " + lineageMapFile.getName());
                    agg.setLineageMapFile(lineageMapFile);

                    Double minPctForLineageFiltering = getProvider().getParameterByName("minPctForLineageFiltering").extractValue(getPipelineCtx().getJob(), getProvider(), Double.class);
                    if (minPctForLineageFiltering != null)
                    {
                        agg.setMinPctForLineageFiltering(minPctForLineageFiltering);
                    }
                }
                else
                {
                    getPipelineCtx().getLogger().debug("lineage map not found, skipping");
                }
            }

            aggregators.add(agg);

            bi.addAggregators(aggregators);
            bi.iterateReads();
            getPipelineCtx().getLogger().info("Inspection complete");

            //write output as TSV
            agg.writeTable(getSBTSummaryFile(outputDir, inputBam));

            return null;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private File getSBTSummaryFile(File outputDir, File bam)
    {
        return new File(outputDir, FileUtil.getBaseName(bam) + ".sbt_hits.txt");
    }
}
