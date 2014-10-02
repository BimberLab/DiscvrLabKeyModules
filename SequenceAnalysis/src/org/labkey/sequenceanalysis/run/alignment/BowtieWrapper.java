package org.labkey.sequenceanalysis.run.alignment;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.api.pipeline.AbstractAlignmentStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.AlignmentStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceGenome;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.api.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.api.run.AbstractCommandWrapper;
import org.labkey.sequenceanalysis.api.run.CommandLineParam;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.SamFormatConverterWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 12/16/12
 * Time: 1:01 PM
 */
public class BowtieWrapper extends AbstractCommandWrapper
{
    public BowtieWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class BowtieAlignmentStep extends AbstractCommandPipelineStep<BowtieWrapper> implements AlignmentStep
    {
        public BowtieAlignmentStep(PipelineStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new BowtieWrapper(ctx.getLogger()));
        }

        @Override
        public AlignmentOutput performAlignment(File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename) throws PipelineJobException
        {
            AlignmentOutputImpl output = new AlignmentOutputImpl();
            AlignerIndexUtil.copyIndexIfExists(this.getPipelineCtx(), output, getProvider().getName());
            BowtieWrapper wrapper = getWrapper();

            List<String> args = new ArrayList<>();
            args.add(wrapper.getExe().getPath());
            args.add("-q"); //input is FASTQ format
            Integer threads = SequenceTaskHelper.getMaxThreads(getPipelineCtx().getJob());
            if (threads != null)
            {
                args.add("-p"); //multi-threaded
                args.add(threads.toString());
            }
            args.add("-S"); //SAM output

            //unaligned reads:
            args.add("--un");
            args.add(new File(outputDirectory, SequenceTaskHelper.getMinimalBaseName(inputFastq1) + ".bowtie.unaligned.fastq").getPath());
            args.addAll(getClientCommandArgs());

            File indexFile = new File(referenceGenome.getWorkingFastaFile().getParentFile() + "/" + getProvider().getName(), getExpectedIndexName(referenceGenome.getWorkingFastaFile()));
            args.add(indexFile.getPath());

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

            File sam = new File(outputDirectory, basename + ".sam");
            args.add(sam.getPath());

            File bam = new File(outputDirectory, basename + ".bam");
            getWrapper().execute(args);

            //convert to BAM
            bam = new SamFormatConverterWrapper(getPipelineCtx().getLogger()).execute(sam, bam, true);
            if (!bam.exists())
            {
                throw new PipelineJobException("Unable to find output file: " + bam.getPath());
            }
            else
            {
                getPipelineCtx().getLogger().info("deleting intermediate SAM file");
                sam.delete();
            }

            output.addOutput(bam, AlignmentOutputImpl.BAM_ROLE);

            return output;
        }

        @Override
        public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            getPipelineCtx().getLogger().info("Creating Bowtie index");
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = new File(outputDir, getProvider().getName());
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getProvider().getName());
            if (!hasCachedIndex)
            {
                if (!indexDir.exists())
                {
                    indexDir.mkdirs();
                }

                List<String> args = new ArrayList<>();
                args.add(getWrapper().getBuildExe().getPath());
                args.add("-f");  //input is FASTA
                args.add("-q");  //quiet mode
                args.add(referenceGenome.getWorkingFastaFile().getPath());
                args.add(new File(indexDir, getExpectedIndexName(referenceGenome.getWorkingFastaFile())).getPath());

                getWrapper().execute(args);
            }

            output.appendOutputs(referenceGenome.getWorkingFastaFile(), indexDir);

            //recache if not already
            AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), indexDir, getProvider().getName(), output);

            return output;
        }

        private String getExpectedIndexName(File refFasta)
        {
            return FileUtil.getBaseName(refFasta) + ".bowtie.index";
        }
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("Bowtie", "Bowtie is a fast aligner often used for short reads. Disadvantages are that it does not perform gapped alignment. It will return a single hit for each read.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-l"), "seed_length", "Seed Length", null, "ldk-numberfield", null, 20),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-n"), "max_seed_mismatches", "Max Seed Mismatches", null, "ldk-numberfield", null, 3)
            ), null, "http://bowtie-bio.sourceforge.net/index.shtml", true);
        }

        public BowtieAlignmentStep create(PipelineContext context)
        {
            return new BowtieAlignmentStep(this, context);
        }
    }

    protected File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("BOWTIEPATH", "bowtie");
    }

    protected File getBuildExe()
    {
        return SequencePipelineService.get().getExeForPackage("BOWTIEPATH", "bowtie-build");
    }
}
