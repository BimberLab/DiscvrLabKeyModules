package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.json.JSONArray;
import org.json.JSONObject;
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
public class VariantFiltrationStep extends AbstractCommandPipelineStep<VariantFiltrationWrapper> implements VariantProcessingStep
{
    public VariantFiltrationStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new VariantFiltrationWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<VariantFiltrationStep> implements VariantProcessingStep.SupportsScatterGather
    {
        public Provider()
        {
            super("VariantFiltrationStep", "GATK VariantFiltration", "GATK", "Filter variants using GATK VariantFiltration", Arrays.asList(
                    ToolParameterDescriptor.create("filters", "Filters", "Filters that will be applied to the variants.", "sequenceanalysis-variantfilterpanel", null, null),
                    new MaskParameterDescriptor(),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--cluster-size"), "clusterSize", "Cluster Size", "If both this and cluster window size are provided, and windows of the specified size with at least the specified number of SNPs will be filtered as SnpCluster.", "ldk-numberfield", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--cluster-window-size"), "clusterWindowSize", "Cluster Window Size", "If both this and cluster size are provided, and windows of the specified size with at least the specified number of SNPs will be filtered as SnpCluster.", "ldk-numberfield", null, null)
            ), Arrays.asList("sequenceanalysis/panel/VariantFilterPanel.js", "sequenceanalysis/panel/VariantMaskPanel.js", "sequenceanalysis/field/GenomeFileSelectorField.js", "ldk/field/ExpDataField.js"), "");
        }

        public VariantFiltrationStep create(PipelineContext ctx)
        {
            return new VariantFiltrationStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();
        File outputVcf = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".filtered.vcf.gz");

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
                if (arr.length() < 2)
                {
                    throw new PipelineJobException("Improper filter: " + filterArr.getString(i));
                }

                params.add("--filter-name");
                params.add(arr.getString(0));
                params.add("-filter");
                params.add(arr.getString(1));
                //if (arr.length() > 2 && arr.optBoolean(2))
                //{
                //    params.add("-invfilter");
                //}
            }
        }

        //snp cluster, handled by getClientCommandArgs()

        //masking
        String maskText = getProvider().getParameterByName("mask").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class, null);
        if (maskText != null)
        {
            JSONObject mask = new JSONObject(maskText);
            if (mask.optString("maskName") == null)
            {
                throw new PipelineJobException("no mask name provided");
            }

            params.add("--mask-name");
            params.add(mask.getString("maskName"));

            params.add("--mask");
            if (mask.opt("fileId") == null)
            {
                throw new PipelineJobException("no fileId provided");
            }

            File maskData = getPipelineCtx().getSequenceSupport().getCachedData(mask.getInt("fileId"));
            if (maskData == null || !maskData.exists())
            {
                throw new PipelineJobException("file not found for dataId: " + mask.opt("fileId"));
            }
            params.add(maskData.getPath());
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

    private static class MaskParameterDescriptor extends ToolParameterDescriptor implements ToolParameterDescriptor.CachableParam
    {
        public MaskParameterDescriptor()
        {
            super(null, "mask", "Masking", "A mask that can be used to filter variant.", "sequenceanalysis-variantmaskpanel", null, null);
        }

        @Override
        public void doCache(PipelineJob job, Object value, SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            job.getLogger().debug("caching param " + getName() + ": " + value);
            if (value != null)
            {
                JSONObject mask;
                if (!(value instanceof JSONObject))
                {
                    mask = new JSONObject(value.toString());
                }
                else
                {
                    mask = (JSONObject)value;
                }

                if (mask.get("fileId") != null)
                {
                    ExpData d = ExperimentService.get().getExpData(mask.getInt("fileId"));
                    if (d != null)
                    {
                        support.cacheExpData(d);
                    }
                }
                else
                {
                    job.getLogger().warn("mask filter step lacks a fileId: " + value);
                    job.getLogger().debug("type: " + value.getClass());
                    for (String key : mask.keySet())
                    {
                        job.getLogger().warn(key + ": " + mask.get(key));
                    }

                    throw new PipelineJobException("No fileId provided for mask step");
                }
            }
        }
    }
}
