package org.labkey.sequenceanalysis.run.alignment;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.IndexOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractAlignmentPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
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
                    //bwa aln
                    ToolParameterDescriptor.create("MaxMismatches", "Max Mismatches", "Maximum edit distance if the value is INT, or the fraction of missing alignments given 2% uniform base error rate if FLOAT. In the latter case, the maximum edit distance is automatically chosen for different read lengths. [0.04]", "ldk-numberfield", null, null),

                    //bwa samse/pe
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-n"), "maxHits", "Max Hits", "Maximum number of alignments to output in the XA tag for reads paired properly. If a read has more than INT hits, the XA tag will not be written.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 20),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-n"), "maxHitsDiscordant", "Max Hits For Pair", "Maximum number of alignments to output in the XA tag for discordant read pairs (excluding singletons). If a read has more than INT hits, the XA tag will not be written.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 20)
            ), null, "http://bio-bwa.sourceforge.net/", true, true);

            setAlwaysCacheIndex(true);
        }

        @Override
        public String getName()
        {
            return "BWA";
        }

        @Override
        public String getDescription()
        {
            return null;
        }

        @Override
        public AlignmentStep create(PipelineContext context)
        {
            return new BWAAlignmentStep(this, context, new BWAWrapper(context.getLogger()));
        }
    }

    public static class BWAAlignmentStep<WrapperType extends BWAWrapper> extends AbstractAlignmentPipelineStep<WrapperType> implements AlignmentStep
    {
        public BWAAlignmentStep(AlignmentStepProvider provider, PipelineContext ctx, WrapperType wrapper)
        {
            super(provider, ctx, wrapper);
        }

        @Override
        public boolean supportsGzipFastqs()
        {
            return true;
        }

        @Override
        public String getIndexCachedDirName(PipelineJob job)
        {
            return "bwa";
        }

        @Override
        public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            getPipelineCtx().getLogger().info("Creating BWA index");
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = new File(outputDir, getIndexCachedDirName(getPipelineCtx().getJob()));
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
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
        public final AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            File inputFastq1 = assertSingleFile(inputFastqs1);
            File inputFastq2 = assertSingleFile(inputFastqs2);

            AlignmentOutputImpl output = new AlignmentOutputImpl();
            AlignerIndexUtil.copyIndexIfExists(this.getPipelineCtx(), output, "bwa", referenceGenome);

            doPerformAlignment(output, inputFastq1, inputFastq2, outputDirectory, referenceGenome, basename, rs, readGroupId, platformUnit);
            output.addCommandsExecuted(getWrapper().getCommandsExecuted());

            return output;
        }

        protected void doPerformAlignment(AlignmentOutputImpl output, File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename, Readset rs, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            List<String> alnOptions = new ArrayList<>();
            if (getProvider().getParameterByName("MaxMismatches").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()) != null)
            {
                alnOptions.add("-n");
                alnOptions.add(getProvider().getParameterByName("MaxMismatches").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()));
            }

            getWrapper().performBwaAlignment(getPipelineCtx().getJob(), output, inputFastq1, inputFastq2, outputDirectory, referenceGenome, basename, getClientCommandArgs(), alnOptions);
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

        @Override
        public boolean alwaysCopyIndexToWorkingDir()
        {
            return false;
        }
    }

    private File runBWAAln(PipelineJob job, ReferenceGenome referenceGenome, File inputFile, List<String> bwaAlnArgs) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("aln");
        appendThreads(job, args);
        args.addAll(bwaAlnArgs);
        args.add(new File(referenceGenome.getAlignerIndexDir("bwa"), FileUtil.getBaseName(referenceGenome.getWorkingFastaFile().getName()) + ".bwa.index").getPath());
        args.add(inputFile.getPath());

        File output = new File(getOutputDir(inputFile), inputFile.getName() + ".sai");

        execute(args, output);

        return output;
    }

    protected void performBwaAlignment(PipelineJob job, AlignmentOutputImpl output, File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename, List<String> samPeArgs, List<String> bwaAlnArgs) throws PipelineJobException
    {
        setOutputDir(outputDirectory);

        getLogger().info("Running BWA");
        File sai1 = runBWAAln(job, referenceGenome, inputFastq1, bwaAlnArgs);
        File sai2 = null;
        if (inputFastq2 != null)
        {
            sai2 = runBWAAln(job, referenceGenome, inputFastq2, bwaAlnArgs);
        }

        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add(inputFastq2 == null ? "samse" : "sampe");
        if (samPeArgs != null)
            args.addAll(samPeArgs);

        if (!referenceGenome.getWorkingFastaFile().exists())
        {
            throw new PipelineJobException("Reference FASTA does not exist: " + referenceGenome.getWorkingFastaFile().getPath());
        }

        File expectedIndex = new File(referenceGenome.getAlignerIndexDir("bwa"), FileUtil.getBaseName(referenceGenome.getWorkingFastaFile()) + ".bwa.index.sa");
        if (!expectedIndex.exists())
        {
            throw new PipelineJobException("Expected index does not exist: " + expectedIndex);
        }
        args.add(new File(referenceGenome.getAlignerIndexDir("bwa"), FileUtil.getBaseName(referenceGenome.getWorkingFastaFile().getName()) + ".bwa.index").getPath());

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
        execute(args, sam);
        if (!sam.exists() || !SequenceUtil.hasMinLineCount(sam, 2))
        {
            throw new PipelineJobException("SAM file doesn't exist or has too few lines: " + sam.getPath());
        }

        //convert to BAM
        File bam = new File(outputDirectory, basename + ".bam");
        bam = new SamFormatConverterWrapper(getLogger()).execute(sam, bam, true);
        if (!bam.exists())
        {
            throw new PipelineJobException("Unable to find output file: " + bam.getPath());
        }

        output.addOutput(bam, AlignmentOutputImpl.BAM_ROLE);
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
