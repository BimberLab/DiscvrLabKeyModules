package org.labkey.sequenceanalysis.run.alignment;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 6/14/2014
 * Time: 8:35 AM
 */
public class BWAMemWrapper extends BWAWrapper
{
    public BWAMemWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class BWAMemAlignmentStep extends BWAAlignmentStep<BWAMemWrapper>
    {
        public BWAMemAlignmentStep(AlignmentStepProvider<?> provider, PipelineContext ctx, BWAMemWrapper wrapper)
        {
            super(provider, ctx, wrapper);
        }

        public BWAMemAlignmentStep(AlignmentStepProvider<?> provider, PipelineContext ctx)
        {
            this(provider, ctx, new BWAMemWrapper(ctx.getLogger()));
        }

        @Override
        public boolean doAddReadGroups()
        {
            return false;
        }

        @Override
        protected void doPerformAlignment(AlignmentOutputImpl output, File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename, Readset rs, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            List<String> extraArgs = getClientCommandArgs();

            extraArgs.add("-R");

            List<String> rg = new ArrayList<>();
            rg.add("@RG");
            rg.add("ID:" + readGroupId);
            rg.add("LB:" + rs.getReadsetId().toString());
            rg.add("PL:" + (rs.getPlatform() == null ? "ILLUMINA" : rs.getPlatform()));
            rg.add("PU:" + (platformUnit == null ? rs.getReadsetId().toString() : platformUnit));
            rg.add("SM:" + SequenceUtil.getLegalReadGroupName(rs));
            extraArgs.add("'" + StringUtils.join(rg, "\\t") + "'");

            double threadsForSamtools = getProvider().getParameterByName("threadsForSamtools").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Double.class, 0.0);

            int threadsForSamtoolsInt;
            if (threadsForSamtools < 1.0)
            {
                Integer threads = SequenceTaskHelper.getMaxThreads(getPipelineCtx().getJob());
                if (threads == null)
                {
                    threadsForSamtoolsInt = 0;  // will be ignored anyway
                }
                else
                {
                    threadsForSamtoolsInt = (int)Math.floor((double)threads * threadsForSamtools);
                    getPipelineCtx().getLogger().debug("Out of %s threads, %s will be reserved for samtools".formatted(threads, threadsForSamtoolsInt));
                }
            }
            else
            {
                threadsForSamtoolsInt = (int)threadsForSamtools;
            }

            getWrapper().performMemAlignment(getPipelineCtx().getJob(), output, inputFastq1, inputFastq2, outputDirectory, referenceGenome, basename, extraArgs, threadsForSamtoolsInt);
        }
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("BWA-Mem", null, getToolParams(), null, "http://bio-bwa.sourceforge.net/", true, true);
        }

        @Override
        public BWAMemAlignmentStep create(PipelineContext context)
        {
            return new BWAMemAlignmentStep(this, context);
        }
    }

    protected static List<ToolParameterDescriptor> getToolParams()
    {
        return Arrays.asList(
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-a"), "outputAll", "Output All Hits", "Output all found alignments for single-end or unpaired paired-end reads. These alignments will be flagged as secondary alignments.", "checkbox", new JSONObject(){{
                    put("checked", false);
                }}, true),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-M"), "markSplit", "Mark Shorter Hits As Secondary", "Mark shorter split hits as secondary (for Picard compatibility).", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-k"), "minSeedLength", "Min Seed Length", "Matches shorter than this value will be missed. The alignment speed is usually insensitive to this value unless it significantly deviates 20.  Default value: 19", "ldk-integerfield", new JSONObject(){{

                }}, null),
                ToolParameterDescriptor.create("threadsForSamtools", "Threads For Samtools View", "If provided, the threads for BWA will be reduced by this value, and these will be given to samtools view. If this value is <1, it will be treated as a fraction of cores (rounded down). If above 1, it will be treated as total cores. We recommend about 0.20 of threads", "ldk-numberfield", new JSONObject(){{
                    put("minValue", 0);
                }}, 0.2)
        );
    }

    public void performMemAlignment(PipelineJob job, AlignmentOutputImpl output, File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename, List<String> additionalArgs, int threadsForSamtools) throws PipelineJobException
    {
        setOutputDir(outputDirectory);

        getLogger().info("Running BWA-Mem");
        getLogger().debug("will write BAM to: " + outputDirectory);

        List<String> bwaArgs = new ArrayList<>();
        bwaArgs.add(getExe().getPath());
        bwaArgs.add("mem");
        bwaArgs.add("-v");
        bwaArgs.add("1");
        if (additionalArgs != null)
        {
            bwaArgs.addAll(additionalArgs);
        }

        appendThreads(job, bwaArgs, threadsForSamtools);

        bwaArgs.add("'" + FileUtil.appendName(referenceGenome.getAlignerIndexDir(getIndexDirName()), FileUtil.getBaseName(referenceGenome.getWorkingFastaFile().getName()) + ".bwa.index").getPath() + "'");
        bwaArgs.add("'" + inputFastq1.getPath() + "'");

        if (inputFastq2 != null)
        {
            bwaArgs.add("'" + inputFastq2.getPath() + "'");
        }

        //run BWA and pipe directly to samtools to make BAM
        File bam = FileUtil.appendName(outputDirectory, basename + ".bam");
        output.addCommandExecuted(StringUtils.join(bwaArgs, " "));

        SamtoolsRunner sr = new SamtoolsRunner(getLogger());
        List<String> samtoolsArgs = new ArrayList<>(Arrays.asList(sr.getSamtoolsPath().getPath(), "view"));
        if (threadsForSamtools > 0)
        {
            samtoolsArgs.add("-@");
            samtoolsArgs.add(String.valueOf(threadsForSamtools));
        }

        samtoolsArgs.addAll(Arrays.asList("-b", "-h", "-S", "-T", "'" + referenceGenome.getWorkingFastaFile().getPath() + "'", "-o", "'" + bam.getPath() + "'", "-"));
        output.addCommandExecuted(StringUtils.join(samtoolsArgs, " "));

        List<String> bashArgs = new ArrayList<>();
        bashArgs.add("/bin/bash");
        bashArgs.add("-c");
        bashArgs.add(StringUtils.join(bwaArgs, " ") + " | " + StringUtils.join(samtoolsArgs, " "));

        execute(bashArgs);

        if (!bam.exists())
        {
            throw new PipelineJobException("Unable to find output file: " + bam.getPath());
        }

        output.addOutput(bam, AlignmentOutputImpl.BAM_ROLE);
    }
}
