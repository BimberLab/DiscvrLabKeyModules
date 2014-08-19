package org.labkey.sequenceanalysis.run.alignment;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.api.pipeline.AbstractAlignmentStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.AlignmentStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.api.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.api.run.AbstractCommandWrapper;
import org.labkey.sequenceanalysis.api.run.CommandLineParam;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.SamFormatConverterWrapper;
import org.labkey.sequenceanalysis.run.util.SamtoolsRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by bimber on 8/9/2014.
 */
public class BismarkWrapper extends AbstractCommandWrapper
{
    public BismarkWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class BismarkAlignmentStep extends AbstractCommandPipelineStep<BismarkWrapper> implements AlignmentStep
    {
        public BismarkAlignmentStep(PipelineStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new BismarkWrapper(ctx.getLogger()));
        }

        @Override
        public AlignmentOutput performAlignment(File inputFastq1, @Nullable File inputFastq2, File outputDirectory, File refFasta, String basename) throws PipelineJobException
        {
            AlignmentOutputImpl output = new AlignmentOutputImpl();
            BismarkWrapper wrapper = getWrapper();

            List<String> args = new ArrayList<>();
            args.add(wrapper.getExe().getPath());

            args.add("--samtools_path");
            args.add(new SamtoolsRunner(getPipelineCtx().getLogger()).getSamtoolsPath().getPath());
            args.add("--path_to_bowtie");
            args.add(new SamtoolsRunner(getPipelineCtx().getLogger()).getSamtoolsPath().getPath());

            if (getClientCommandArgs() != null)
            {
                args.addAll(getClientCommandArgs());
            }

            args.add("-q"); //input is FASTQ format
            Integer threads = SequenceTaskHelper.getMaxThreads(getPipelineCtx().getJob());
            if (threads != null)
            {
                args.add("-p"); //multi-threaded
                args.add(threads.toString());
            }
            args.add("--bam"); //BAM output


            if (inputFastq2 != null)
            {
                args.add("-1");
                args.add(inputFastq1.getPath());

                args.add("-2");
                args.add(inputFastq2.getPath());
            }
            else
            {
                args.add(inputFastq1.getPath());
            }

            File bam = new File(outputDirectory, basename + ".bam");
            getWrapper().execute(args);

            output.addOutput(bam, AlignmentOutputImpl.BAM_ROLE);

            getPipelineCtx().getLogger().info("running bismark_methylation_extractor");
            List<String> args2 = new ArrayList<>();
            args2.add(getWrapper().getMethylationExtractorExe().getPath());
            args2.add(inputFastq2 == null ? "-s" : "-p");
            args2.add("--samtools_path");
            args2.add(new SamtoolsRunner(getPipelineCtx().getLogger()).getSamtoolsPath().getPath());
            args2.add("--comprehensive");
            args2.add("--bedGraph");
            args2.add(bam.getPath());
            wrapper.execute(args2);

            return output;
        }

        @Override
        public IndexOutput createIndex(File refFasta, File outputDir) throws PipelineJobException
        {
            getPipelineCtx().getLogger().info("Preparing reference for bismark");
            IndexOutputImpl output = new IndexOutputImpl(refFasta);

            List<String> args = new ArrayList<>();
            args.add(getWrapper().getBuildExe().getPath());
            args.add("--path_to_bowtie");
            args.add(new BowtieWrapper(getPipelineCtx().getLogger()).getExe().getPath());

            args.add("--verbose");
            args.add(refFasta.getParentFile().getPath());

            getWrapper().execute(args);
            output.appendOutputs(refFasta, outputDir);

            //TODO: copy outputs??

            return output;
        }
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("Bismark", "Bismark is a tool to map bisulfite converted sequence reads and determine cytosine methylation states.  It will use bowtie for the alignment itself.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-l"), "seed_length", "Seed Length", null, "ldk-numberfield", null, 20),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-n"), "max_seed_mismatches", "Max Seed Mismatches", null, "ldk-numberfield", null, 3)
            ), null, "http://www.bioinformatics.babraham.ac.uk/projects/bismark/", true);
        }

        public BismarkAlignmentStep create(PipelineContext context)
        {
            return new BismarkAlignmentStep(this, context);
        }
    }

    protected File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("BISMARKPATH", "bismark");
    }

    protected File getBuildExe()
    {
        return SequencePipelineService.get().getExeForPackage("BISMARKPATH", "bismark_genome_preparation");
    }

    protected File getMethylationExtractorExe()
    {
        return SequencePipelineService.get().getExeForPackage("BISMARKPATH", "bismark_methylation_extractor");
    }
}
