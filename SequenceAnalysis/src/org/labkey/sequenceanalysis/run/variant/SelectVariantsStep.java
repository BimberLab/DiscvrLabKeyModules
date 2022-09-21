package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.apache.commons.lang3.StringUtils;
import org.json.old.JSONArray;
import org.json.old.JSONObject;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.SelectVariantsWrapper;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import javax.annotation.Nullable;
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
    public static String INTERVALS = "intervals";

    public SelectVariantsStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new SelectVariantsWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<SelectVariantsStep> implements VariantProcessingStep.SupportsScatterGather
    {
        public Provider()
        {
            super("SelectVariantsStep", "GATK SelectVariants", "GATK", "Select variants using GATK SelectVariants", Arrays.asList(
                    ToolParameterDescriptor.create("selects", "Select Expressions", "Filter expressions that can be used to subset variants.", "sequenceanalysis-variantfilterpanel", new JSONObject(){{
                        put("mode", "SELECT");
                        put("showFilterName", false);
                        put("title", "Select Expressions");
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--exclude-filtered"), "excludeFiltered", "Exclude Filtered", "If selected, any filtered sites will be removed", "checkbox", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--exclude-non-variants"), "excludeNonVariant", "Exclude Non-Variant", "If selected, any non-variant sites will be removed", "checkbox", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--preserve-alleles"), "noTrim", "Preserve Original Alleles", "If selected, the all alleles from the input will be retained, even if not used by any remaining genotypes.", "checkbox", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--remove-unused-alternates"), "trimAlternates", "Remove Unused Alternates", "If selected, any alternate alleles not used in any genotypes will be trimmed.", "checkbox", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--sites-only-vcf-output"), "sitesOnly", "Sites Only VCF Output", "If selected, the resulting VCF will omit samples and include only the site information.", "checkbox", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--allow-nonoverlapping-command-line-samples"), "allowNnonoverlappingSamples", "Allow non-overlapping Samples", "Normally the job will fail is samples are selected that do not exist in the VCF.  If checked, this will be allowed.", "checkbox", null, null),
                    ToolParameterDescriptor.create(SELECT_TYPE_TO_INCLUDE, "Select Type(s) To Include", "Only variants of the selected type(s) will be included", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", SelectSNVsStep.getSelectTypes());
                        put("multiSelect", true);
                    }}, "SNV"),
                    ToolParameterDescriptor.create(SELECT_TYPE_TO_EXCLUDE, "Select Type(s) To Exclude", "Variants of the selected type(s) will be excluded", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", SelectSNVsStep.getSelectTypes());
                        put("multiSelect", true);
                    }}, null),
                    new IntervalParameterDescriptor(),
                    ToolParameterDescriptor.create(SelectSamplesStep.SAMPLE_INCLUDE, "Select Sample(s) Include", "Only variants of the selected type(s) will be included", "sequenceanalysis-trimmingtextarea", null, null),
                    ToolParameterDescriptor.create(SelectSamplesStep.SAMPLE_EXCLUDE, "Select Samples(s) To Exclude", "Variants of the selected type(s) will be excluded", "sequenceanalysis-trimmingtextarea", null, null)
            ), Arrays.asList("sequenceanalysis/panel/VariantFilterPanel.js", "sequenceanalysis/panel/IntervalPanel.js", "/sequenceanalysis/field/TrimmingTextArea.js"), "");
        }

        public SelectVariantsStep create(PipelineContext ctx)
        {
            return new SelectVariantsStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        List<String> options = new ArrayList<>();

        String toInclude = getProvider().getParameterByName(SELECT_TYPE_TO_INCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        addSelectTypeOptions(toInclude, options, "--select-type-to-include");

        String toExclude = getProvider().getParameterByName(SELECT_TYPE_TO_EXCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        addSelectTypeOptions(toExclude, options, "--select-type-to-exclude");

        String samplesToInclude = getProvider().getParameterByName(SelectSamplesStep.SAMPLE_INCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        SelectVariantsStep.addSubjectSelectOptions(samplesToInclude, options, "-sn");

        String samplesToExclude = getProvider().getParameterByName(SelectSamplesStep.SAMPLE_EXCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        SelectVariantsStep.addSubjectSelectOptions(samplesToExclude, options, "-xl-sn");

        //intervals:
        String intervalText = getProvider().getParameterByName(INTERVALS).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class, null);
        options.addAll(getIntervalOptions(intervalText, getPipelineCtx().getSequenceSupport()));

        //JEXL
        String selectText = getProvider().getParameterByName("selects").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class, null);
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

        if (intervals != null)
        {
            intervals.forEach(interval -> {
                options.add("-L");
                options.add(interval.getContig() + ":" + interval.getStart() + "-" + interval.getEnd());
            });
        }

        File outputVcf = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".selectVariants.vcf.gz");
        getWrapper().execute(genome.getWorkingFastaFile(), inputVCF, outputVcf, options);
        if (!outputVcf.exists())
        {
            throw new PipelineJobException("output not found: " + outputVcf);
        }

        output.setVcf(outputVcf);

        return output;
    }

    public static void addSubjectSelectOptions(String text, List<String> options, String argName)
    {
        text = StringUtils.trimToNull(text);
        if (text != null)
        {
            String[] names = text.split(";");
            for (String name : names)
            {
                options.add(argName);
                options.add(name);
            }
        }
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

    private static class IntervalParameterDescriptor extends ToolParameterDescriptor implements ToolParameterDescriptor.CachableParam
    {
        public IntervalParameterDescriptor()
        {
            super(null, INTERVALS, "Intervals", "Only variants spanning these intervals will be included", "sequenceanalysis-intervalpanel", null, null);
        }

        @Override
        public void doCache(PipelineJob job, Object value, SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            job.getLogger().debug("caching param " + getName() + ": " + value);
            if (value != null)
            {
                JSONObject json;
                if (!(value instanceof JSONObject))
                {
                    json = new JSONObject(value.toString());
                }
                else
                {
                    json = (JSONObject)value;
                }

                if (json.get("fileId") != null)
                {
                    ExpData d = ExperimentService.get().getExpData(json.getInt("fileId"));
                    if (d != null)
                    {
                        support.cacheExpData(d);
                    }
                }
            }
        }
    }

    public static List<String> getIntervalOptions(String intervalText, SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        List<String> options = new ArrayList<>();

        if (intervalText != null)
        {
            JSONObject intervalJson = new JSONObject(intervalText);
            if (intervalJson.get("source") == null)
            {
                throw new PipelineJobException("Improper interval data: " + intervalText);
            }

            if (intervalJson.get("fileId") != null)
            {
                File d = support.getCachedData(intervalJson.getInt("fileId"));
                if (d.exists())
                {
                    options.add("-L");
                    options.add(d.getPath());
                }
                else
                {
                    throw new PipelineJobException("Unable to find file: " + d.getPath());
                }
            }
            else if (intervalJson.get("intervals") != null)
            {
                String[] intervals = intervalJson.getString("intervals").split(";");
                for (String i : intervals)
                {
                    options.add("-L");
                    options.add(i);
                }
            }
        }

        return options;
    }
}
