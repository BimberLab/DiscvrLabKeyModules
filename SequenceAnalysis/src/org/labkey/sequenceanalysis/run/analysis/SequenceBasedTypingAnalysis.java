package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.filter.DuplicateReadFilter;
import htsjdk.samtools.filter.SamRecordFilter;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
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

    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta) throws PipelineJobException
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
            AvgBaseQualityAggregator avgBaseQualityAggregator = new AvgBaseQualityAggregator(getPipelineCtx().getLogger(), inputBam, referenceFasta, Arrays.<SamRecordFilter>asList(
                    new DuplicateReadFilter()
            ));
            avgBaseQualityAggregator.calculateAvgQuals();
            getPipelineCtx().getLogger().info("\tCalculation complete");

            getPipelineCtx().getLogger().info("Inspecting alignments in BAM");
            BamIterator bi = new BamIterator(inputBam, referenceFasta, getPipelineCtx().getLogger());

            List<AlignmentAggregator> aggregators = new ArrayList<>();
            SequenceBasedTypingAlignmentAggregator agg = new SequenceBasedTypingAlignmentAggregator(getPipelineCtx().getLogger(), referenceFasta, avgBaseQualityAggregator, toolParams);
            if (getProvider().getParameterByName("writeLog").extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, false))
            {
                File workDir = new File(getPipelineCtx().getSourceDirectory(), FileUtil.getBaseName(inputBam));
                if (!workDir.exists())
                {
                    workDir.mkdirs();
                }
                File outputLog = new File(workDir, FileUtil.getBaseName(inputBam) + ".sbt.txt");
                agg.setOutputLog(outputLog);
            }

            aggregators.add(agg);

            bi.addAggregators(aggregators);
            bi.iterateReads();
            getPipelineCtx().getLogger().info("Inspection complete");

            for (AlignmentAggregator a : aggregators)
            {
                a.writeOutput(getPipelineCtx().getJob().getUser(), getPipelineCtx().getJob().getContainer(), model);
            }

            bi.saveSynopsis(getPipelineCtx().getJob().getUser(), model);

            return null;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        return null;
    }
}
