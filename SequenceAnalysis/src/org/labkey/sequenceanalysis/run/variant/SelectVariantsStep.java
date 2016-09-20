package org.labkey.sequenceanalysis.run.variant;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
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
import org.labkey.api.sequenceanalysis.run.SelectVariantsWrapper;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.labkey.sequenceanalysis.run.variant.SelectSNVsStep.SELECT_TYPE_TO_EXCLUDE;
import static org.labkey.sequenceanalysis.run.variant.SelectSNVsStep.SELECT_TYPE_TO_INCLUDE;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class SelectVariantsStep extends AbstractCommandPipelineStep<SelectVariantsWrapper> implements VariantProcessingStep
{
    public SelectVariantsStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new SelectVariantsWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<SelectVariantsStep>
    {
        public Provider()
        {
            super("SelectVariantsStep", "GATK SelectVariants", "GATK", "Select variants using GATK SelectVariants", Arrays.asList(
                    ToolParameterDescriptor.create("selects", "Select Expressions", "Filter expressions that can be used to subset variants.", "sequenceanalysis-variantfilterpanel", new JSONObject(){{
                        put("mode", "SELECT");
                        put("showFilterName", false);
                        put("title", "Select Expressions");
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-ef"), "excludeFiltered", "Exclude Filtered", "If selected, any filtered sites will be removed", "checkbox", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-env"), "excludeNonVariant", "Exclude Non-Variant", "If selected, any non-variant sites will be removed", "checkbox", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-noTrim"), "noTrim", "Preserve Original Alleles", "If selected, the all alleles from the input will be retained, even if not used by any remaining genotypes.", "checkbox", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-trimAlternates"), "trimAlternates", "Trim Unused Alternates", "If selected, any alternate alleles not used in any genotypes will be trimmed.", "checkbox", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-sites_only"), "sitesOnly", "Output Sites Only", "If selected, genotypes will be omitted and a VCF with only the first 8 columns will be produced.", "checkbox", null, null),
                    ToolParameterDescriptor.create(SELECT_TYPE_TO_INCLUDE, "Select Type(s) To Include", "Only variants of the selected type(s) will be included", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", SelectSNVsStep.getSelectTypes());
                        put("multiSelect", true);
                    }}, "SNV"),
                    ToolParameterDescriptor.create(SELECT_TYPE_TO_EXCLUDE, "Select Type(s) To Exclude", "Variants of the selected type(s) will be excluded", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", SelectSNVsStep.getSelectTypes());
                        put("multiSelect", true);
                    }}, null)
                    //TODO: select by IDs
            ), Arrays.asList("sequenceanalysis/panel/VariantFilterPanel.js"), "");
        }

        public SelectVariantsStep create(PipelineContext ctx)
        {
            return new SelectVariantsStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        List<String> options = new ArrayList<>();

        String toInclude = getProvider().getParameterByName(SELECT_TYPE_TO_INCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), String.class);
        addSelectTypeOptions(toInclude, options, "--selectTypeToInclude");

        String toExclude = getProvider().getParameterByName(SELECT_TYPE_TO_EXCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), String.class);
        addSelectTypeOptions(toExclude, options, "--selectTypeToExclude");

        //JEXL
        String selectText = getProvider().getParameterByName("selects").extractValue(getPipelineCtx().getJob(), getProvider(), String.class, null);
        if (selectText != null)
        {
            JSONArray filterArr = new JSONArray(selectText);
            for (int i = 0; i < filterArr.length(); i++)
            {
                JSONArray arr = filterArr.getJSONArray(i);
                if (arr.length() < 2)
                {
                    throw new PipelineJobException("Improper select expression: " + filterArr.getString(i));
                }

                options.add("-select");
                options.add(arr.getString(1));
                //if (arr.length() > 2 && arr.optBoolean(2))
                //{
                //    params.add("-invfilter");
                //}
            }
        }

        options.addAll(getClientCommandArgs());

        File outputVcf = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".selectVariants.vcf.gz");
        getWrapper().execute(genome.getWorkingFastaFile(), inputVCF, outputVcf, options);
        if (!outputVcf.exists())
        {
            throw new PipelineJobException("output not found: " + outputVcf);
        }

        output.setVcf(outputVcf);

        return output;
    }

    public static void addSelectTypeOptions(String text, List<String> options, String argName)
    {
        if (StringUtils.trimToNull(text) != null)
        {
            if (text.startsWith("["))
            {
                JSONArray arr = new JSONArray(text);
                for (Object o : arr.toArray())
                {
                    if (o == null)
                    {
                        continue;
                    }

                    options.add(argName);
                    options.add(o.toString());
                }
            }
            else
            {
                for (String s : StringUtils.split(text, ";"))
                {
                    options.add(argName);
                    options.add(s);
                }
            }
        }
    }
}
