package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.json.old.JSONArray;
import org.json.old.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.VariantFiltrationWrapper;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class GenotypeFiltrationStep extends AbstractCommandPipelineStep<VariantFiltrationWrapper> implements VariantProcessingStep
{
    public GenotypeFiltrationStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new VariantFiltrationWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<GenotypeFiltrationStep> implements VariantProcessingStep.SupportsScatterGather
    {
        public Provider()
        {
            super("GenotypeFiltrationStep", "GATK VariantFiltration for Genotypes", "GATK", "Filter genotypes using GATK VariantFiltration", Arrays.asList(
                    ToolParameterDescriptor.create("filters", "Filters", "Filters that will be applied to the variants.", "sequenceanalysis-genotypefilterpanel", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--set-filtered-genotype-to-no-call"), "setFilteredGtToNocall", "Set Filtered Genotypes to No-Call", "If selected, any filtered genotypes will be converted to no-call.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true)
            ), Arrays.asList("sequenceanalysis/panel/GenotypeFilterPanel.js"), "");
        }

        public GenotypeFiltrationStep create(PipelineContext ctx)
        {
            return new GenotypeFiltrationStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();
        File outputVcf = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".gfiltered.vcf.gz");

        List<String> params = new ArrayList<>();
        params.addAll(getClientCommandArgs());

        //filters
        String filterText = getProvider().getParameterByName("filters").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class, null);
        if (filterText != null)
        {
            JSONArray filterArr = new JSONArray(filterText);
            for (int i = 0; i < filterArr.length(); i++)
            {
                JSONArray arr = filterArr.getJSONArray(i);
                if (arr.length() != 2)
                {
                    throw new PipelineJobException("Improper filter: " + filterArr.getString(i));
                }

                params.add("-G-filter-name");
                params.add(arr.getString(0));
                params.add("-G-filter");
                params.add(arr.getString(1));
            }
        }

        if (intervals != null)
        {
            intervals.forEach(interval -> {
                params.add("-L");
                params.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        getWrapper().execute(genome.getWorkingFastaFile(), inputVCF, outputVcf, params);
        if (!outputVcf.exists())
        {
            throw new PipelineJobException("unable to find output: " + outputVcf.getPath());
        }

        output.setVcf(outputVcf);

        return output;
    }
}
