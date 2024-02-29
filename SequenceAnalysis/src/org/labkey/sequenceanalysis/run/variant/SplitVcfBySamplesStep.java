package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractDiscvrSeqWrapper;
import org.labkey.sequenceanalysis.pipeline.SequenceJob;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SplitVcfBySamplesStep extends AbstractCommandPipelineStep<SplitVcfBySamplesStep.Wrapper> implements VariantProcessingStep, VariantProcessingStep.SupportsScatterGather
{
    public SplitVcfBySamplesStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new Wrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<SplitVcfBySamplesStep> implements SupportsScatterGather
    {
        public Provider()
        {
            super("SplitVcfBySamples", "Split VCF By Sample", "DISCVRseq", "A VCF will be generated containing only the samples specified below.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--samplesPerVcf"), "samplesPerVcf", "Samples Per VCF", "The max number of samples to write per VCF", "ldk-integerfield", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--minAllowableInFinalVcf"), "minAllowableInFinalVcf", "Min Allowable in Final VCF", "If the final VCF in the split has fewer than this number of samples, it will be merged with the second to last VCF", "ldk-integerfield", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--discardNonVariantSites"), "discardNonVariantSites", "Discard Non-Variant Sites", "If selected, any site in a subset VCF lacking at least one genotype with a variant will be discarded", "checkbox", null, true)
            ), null, null);
        }

        @Override
        public SplitVcfBySamplesStep create(PipelineContext context)
        {
            return new SplitVcfBySamplesStep(this, context);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();
        getPipelineCtx().getLogger().info("Running SplitVcfBySamples");

        List<String> args = new ArrayList<>(getWrapper().getBaseArgs());
        args.add("SplitVcfBySamples");
        args.add("-V");
        args.add(inputVCF.getPath());
        args.add("-O");
        args.add(outputDirectory.getPath());

        args.addAll(getClientCommandArgs());

        getWrapper().execute(args);

        output.addInput(inputVCF, "Input VCF");

        return output;
    }

    private List<File> findProducedVcfs(File inputVCF, File outputDirectory)
    {
        List<File> ret = new ArrayList<>();
        String basename = SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName());
        for (File f : outputDirectory.listFiles())
        {
            if (!f.getName().equals(inputVCF.getName()) && f.getName().startsWith(basename) && SequenceUtil.FILETYPE.vcf.getFileType().isType(f))
            {
                ret.add(f);
            }
        }

        return ret;
    }

    @Override
    public void performAdditionalMergeTasks(SequenceOutputHandler.JobContext ctx, PipelineJob job, TaskFileManager manager, ReferenceGenome genome, List<File> orderedScatterOutputs, List<String> orderedJobDirs) throws PipelineJobException
    {
        job.getLogger().info("Merging additional track VCFs");
        File inputVCF = ((SequenceJob)getPipelineCtx().getJob()).getInputFiles().get(0);
        List<File> firstJobOutputs = findProducedVcfs(inputVCF, new File(ctx.getSourceDirectory(), orderedJobDirs.get(0)));
        job.getLogger().info("total VCFs found in job dir: " + firstJobOutputs.size());
        if (firstJobOutputs.isEmpty())
        {
            throw new PipelineJobException("No VCFs found in folder: " + new File(ctx.getSourceDirectory(), orderedJobDirs.get(0)));
        }

        for (File fn : firstJobOutputs)
        {
            List<File> toConcat = orderedJobDirs.stream().map(jobDir -> {
                File f = new File(new File(getPipelineCtx().getSourceDirectory(), jobDir), fn.getName());
                if (!f.exists())
                {
                    throw new IllegalStateException("Missing file: " + f.getPath());
                }

                ctx.getFileManager().addIntermediateFile(f);
                ctx.getFileManager().addIntermediateFile(new File(f.getPath() + ".tbi"));

                return f;
            }).toList();

            String basename = SequenceAnalysisService.get().getUnzippedBaseName(toConcat.get(0).getName());
            File combined = new File(ctx.getSourceDirectory(), basename + ".vcf.gz");
            File combinedIdx = new File(combined.getPath() + ".tbi");
            if (combinedIdx.exists())
            {
                job.getLogger().info("VCF exists, will not recreate: " + combined.getPath());
            }
            else
            {
                combined = SequenceAnalysisService.get().combineVcfs(toConcat, combined, genome, job.getLogger(), true, null);
            }

            SequenceOutputFile so = new SequenceOutputFile();
            so.setName("Subset VCF: " + fn);
            so.setFile(combined);
            so.setCategory("VCF File");
            so.setLibrary_id(genome.getGenomeId());
            manager.addSequenceOutput(so);
        }
    }

    public static class Wrapper extends AbstractDiscvrSeqWrapper
    {
        public Wrapper(Logger log)
        {
            super(log);
        }
    }
}
