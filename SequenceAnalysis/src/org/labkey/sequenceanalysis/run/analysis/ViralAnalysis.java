package org.labkey.sequenceanalysis.run.analysis;

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
 * Time: 11:05 AM
 */
public class ViralAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public ViralAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractAnalysisStepProvider<ViralAnalysis>
    {
        public Provider()
        {
            super("ViralAnalysis", "Viral Analysis", null, "This analysis was originally designed for viral populations, but can be applied to non-viral sequence as well.  Unlike a diploid organism, a viral population typically contains an unknonw number of variants present at unknown and sometimes low frequencies.  This analysis performs NT SNP calling using base quality scores, using an inclusive approach designed to allow low frequency variants to be included.  If these reference sequences are annotated with open reading frames, it will also calculate AA translations in the context of each read.  This means it will use the true flanking bases for the translation, rather than the consensus.  This can also be important when working with low frequency variation.  Finally, it will populate coverage data in the database.", Arrays.asList(
                    ToolParameterDescriptor.create("minSnpQual", "Minimum SNP Qual", "Only SNPs with a quality score above this threshold will be included.", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 17),
                    ToolParameterDescriptor.create("minSnpAvgQual", "Minimum SNP Avg Qual", "If provided, the average quality score of all SNPs of a give base at each position must be above this value.", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 17),
                    ToolParameterDescriptor.create("minDipQual", "Minimum DIP Qual", "Only DIPs (deletion/indel polymorphisms) with a quality score above this threshold will be included.", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 17),
                    ToolParameterDescriptor.create("minDipAvgQual", "Minimum DIP Avg Qual", "If provided, the average quality score of all DIPs (deletion/indel polymorphisms) of a give base at each position must be above this value.", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 17),
                    ToolParameterDescriptor.create("minMapQual", "Minimum Mapping Qual", "If provided, any alignment with a mapping quality lower than this value will be discarded", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 30)
            ), null, null);
        }

        @Override
        public ViralAnalysis create(PipelineContext ctx)
        {
            return new ViralAnalysis(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        try
        {
            Map<String, String> toolParams = new HashMap<>();
            List<ToolParameterDescriptor> params = getProvider().getParameters();
            for (ToolParameterDescriptor td : params)
            {
                toolParams.put(td.getName(), td.extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()));
            }

            //first calculate avg qualities at each position
            getPipelineCtx().getLogger().info("Calculating avg quality scores");
            AvgBaseQualityAggregator avgBaseQualityAggregator = new AvgBaseQualityAggregator(getPipelineCtx().getLogger(), inputBam, referenceFasta);
            avgBaseQualityAggregator.calculateAvgQuals();
            getPipelineCtx().getLogger().info("\tCalculation complete");

            getPipelineCtx().getLogger().info("Inspecting alignments in BAM");
            BamIterator bi = new BamIterator(inputBam, referenceFasta, getPipelineCtx().getLogger());

            List<AlignmentAggregator> aggregators = new ArrayList<>();
            NtCoverageAggregator coverage = new NtCoverageAggregator(getPipelineCtx().getLogger(), referenceFasta, avgBaseQualityAggregator, toolParams);
            aggregators.add(coverage);

            NtSnpByPosAggregator ntSnp = new NtSnpByPosAggregator(getPipelineCtx().getLogger(), referenceFasta, avgBaseQualityAggregator, toolParams);
            ntSnp.setCoverageAggregator(coverage, true);
            aggregators.add(ntSnp);

            AASnpByCodonAggregator aaSnp = new AASnpByCodonAggregator(getPipelineCtx().getLogger(), referenceFasta, avgBaseQualityAggregator, toolParams);
            aaSnp.setCoverageAggregator(coverage, true);
            aggregators.add(aaSnp);

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
