package org.labkey.sequenceanalysis.run.alignment;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.SamFormatConverterWrapper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 12/16/12
 * Time: 11:16 AM
 */
public class BWAWrapper extends AbstractCommandWrapper
{
    public BWAWrapper(@Nullable Logger logger)
    {
        super(logger);
    }


    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("BWA", "BWA is a commonly used aligner, optimized for shorter reads. It also supports paired-end reads.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-n"), "MaxMismatches", "Max Mismatches", "Maximum edit distance if the value is INT, or the fraction of missing alignments given 2% uniform base error rate if FLOAT. In the latter case, the maximum edit distance is automatically chosen for different read lengths. [0.04]", "ldk-numberfield", null, null)
                    //ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-n"), ),
                    //ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-n"), )
            ), null, "http://bio-bwa.sourceforge.net/", true, true);
        }

        public String getName()
        {
            return "BWA";
        }

        public String getDescription()
        {
            return null;
        }

        public AlignmentStep create(PipelineContext context)
        {
            return new BWAAlignmentStep(this, context);
        }
    }

    public static class BWAAlignmentStep extends AbstractCommandPipelineStep<BWAWrapper> implements AlignmentStep
    {
        public BWAAlignmentStep(PipelineStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new BWAWrapper(ctx.getLogger()));
        }

        @Override
        public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            getPipelineCtx().getLogger().info("Creating BWA index");
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = new File(outputDir, "bwa");
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), "bwa", referenceGenome);
            if (!hasCachedIndex)
            {
                List<String> args = new ArrayList<>();
                args.add(getWrapper().getExe().getPath());

                args.add("index");

                //necessary for DBs larger than 2gb
                args.add("-a");
                args.add("bwtsw");

                args.add("-p");

                String outPrefix = FileUtil.getBaseName(referenceGenome.getSourceFastaFile()) + ".bwa.index";
                if (!indexDir.exists())
                {
                    indexDir.mkdirs();
                }

                args.add(new File(indexDir, outPrefix).getPath());
                args.add(referenceGenome.getWorkingFastaFile().getPath());
                getWrapper().execute(args);
            }

            output.appendOutputs(referenceGenome.getWorkingFastaFile(), indexDir);

            //recache if not already
            AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), indexDir, "bwa", referenceGenome);

            return output;
        }

        @Override
        public final AlignmentOutput performAlignment(File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename) throws PipelineJobException
        {
            AlignmentOutputImpl output = new AlignmentOutputImpl();
            AlignerIndexUtil.copyIndexIfExists(this.getPipelineCtx(), output, "bwa", referenceGenome);

            return _performAlignment(output, inputFastq1, inputFastq2, outputDirectory, referenceGenome, basename);
        }

        @Override
        public boolean doAddReadGroups()
        {
            return true;
        }

        @Override
        public boolean doSortIndexBam()
        {
            return true;
        }


        protected AlignmentOutput _performAlignment(AlignmentOutputImpl output, File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename) throws PipelineJobException
        {
            getWrapper().setOutputDir(outputDirectory);

            getPipelineCtx().getLogger().info("Running BWA");
            File sai1 = getWrapper().runBWAAln(getPipelineCtx().getJob(), referenceGenome.getWorkingFastaFile(), inputFastq1);
            File sai2 = null;
            if (inputFastq2 != null)
            {
                sai2 = getWrapper().runBWAAln(getPipelineCtx().getJob(), referenceGenome.getWorkingFastaFile(), inputFastq2);
            }

            List<String> args = new ArrayList<>();
            args.add(getWrapper().getExe().getPath());
            args.add(inputFastq2 == null ? "samse" : "sampe");
            args.addAll(getClientCommandArgs());

            if (!referenceGenome.getWorkingFastaFile().exists())
            {
                throw new PipelineJobException("Reference FASTA does not exist: " + referenceGenome.getWorkingFastaFile().getPath());
            }

            File expectedIndex = new File(referenceGenome.getWorkingFastaFile().getParentFile() + "/bwa", FileUtil.getBaseName(referenceGenome.getWorkingFastaFile()) + ".bwa.index.sa");
            if (!expectedIndex.exists())
            {
                throw new PipelineJobException("Expected index does not exist: " + expectedIndex);
            }
            args.add(new File(referenceGenome.getWorkingFastaFile().getParentFile() + "/bwa", FileUtil.getBaseName(referenceGenome.getWorkingFastaFile().getName()) + ".bwa.index").getPath());

            //add SAI
            args.add(sai1.getPath());
            if (inputFastq2 != null)
            {
                args.add(sai2.getPath());
            }

            //add FASTQ
            args.add(inputFastq1.getPath());
            if (inputFastq2 != null)
            {
                args.add(inputFastq2.getPath());
            }

            File sam = new File(outputDirectory, basename + ".sam");
            getWrapper().execute(args, sam);
            if (!sam.exists() || SequenceUtil.getLineCount(sam) < 2)
            {
                throw new PipelineJobException("SAM file doesnt exist or has too few lines: " + sam.getPath());
            }

            //convert to BAM
            File bam = new File(outputDirectory, basename + ".bam");
            bam = new SamFormatConverterWrapper(getPipelineCtx().getLogger()).execute(sam, bam, true);
            if (!bam.exists())
            {
                throw new PipelineJobException("Unable to find output file: " + bam.getPath());
            }

            output.addOutput(bam, AlignmentOutputImpl.BAM_ROLE);

            return output;
        }
    }

    public File runBWAAln(PipelineJob job, File refFasta, File inputFile) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("aln");
        appendThreads(job, args);
        args.add(new File(refFasta.getParentFile() + "/bwa", FileUtil.getBaseName(refFasta.getName()) + ".bwa.index").getPath());
        args.add(inputFile.getPath());

        File output = new File(getOutputDir(inputFile), inputFile.getName() + ".sai");

        execute(args, output);

        return output;
    }

    protected void appendThreads(PipelineJob job, List<String> args)
    {
        Integer threads = SequenceTaskHelper.getMaxThreads(job);
        if (threads != null)
        {
            args.add("-t"); //multi-threaded
            args.add(threads.toString());
        }
    }

    public File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("BWAPATH", "bwa");
    }
}
