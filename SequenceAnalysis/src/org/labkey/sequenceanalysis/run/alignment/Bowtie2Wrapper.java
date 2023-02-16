package org.labkey.sequenceanalysis.run.alignment;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 12/16/12
 * Time: 1:01 PM
 */
public class Bowtie2Wrapper extends AbstractCommandWrapper
{
    public Bowtie2Wrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class Bowtie2AlignmentStep extends AbstractAlignmentPipelineStep<Bowtie2Wrapper> implements AlignmentStep
    {
        public Bowtie2AlignmentStep(AlignmentStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new Bowtie2Wrapper(ctx.getLogger()));
        }

        @Override
        public boolean supportsGzipFastqs()
        {
            return false;
        }

        @Override
        public AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            File inputFastq1 = assertSingleFile(inputFastqs1);
            File inputFastq2 = assertSingleFile(inputFastqs2);

            AlignmentOutputImpl output = new AlignmentOutputImpl();
            AlignerIndexUtil.copyIndexIfExists(this.getPipelineCtx(), output, getProvider().getName(), referenceGenome);
            Bowtie2Wrapper wrapper = getWrapper();

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
            args.add("--un-gz");
            args.add(new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputFastq1.getName()) + ".bowtie2.unaligned.fastq.gz").getPath());
            args.addAll(getClientCommandArgs());

            File indexFile = new File(referenceGenome.getAlignerIndexDir(getProvider().getName()), getExpectedIndexName(referenceGenome.getWorkingFastaFile()));
            args.add("-x");
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
                args.add("-U");
                args.add(inputFastq1.getPath());
            }

            File sam = new File(outputDirectory, basename + ".sam");
            args.add("-S");
            args.add(sam.getPath());

            File bam = new File(outputDirectory, basename + ".bam");
            getWrapper().execute(args);

            //convert to BAM
            SamFormatConverterWrapper converterWrapper = new SamFormatConverterWrapper(getPipelineCtx().getLogger());
            bam = converterWrapper.execute(sam, bam, true);
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
            output.addCommandsExecuted(getWrapper().getCommandsExecuted());

            return output;
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

        @Override
        public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            getPipelineCtx().getLogger().info("Creating Bowtie2 index");
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = new File(outputDir, getProvider().getName());
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
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
            AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), indexDir, getProvider().getName(), referenceGenome);

            return output;
        }

        public static String getExpectedIndexName(File refFasta)
        {
            return FileUtil.getBaseName(refFasta) + ".bowtie2.index";
        }
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("Bowtie2", "Bowtie2 is a fast aligner often used for short reads. Disadvantages are that it does not perform gapped alignment. It will return a single hit for each read.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-L"), "seed_length", "Seed Length", "Sets the length of the seed substrings to align during multiseed alignment. Smaller values make alignment slower but more senstive. Default: the --sensitive preset is used by default, which sets -L to 20 both in --end-to-end mode and in --local mode.", "ldk-numberfield", null, 20),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-N"), "max_seed_mismatches", "Max Seed Mismatches", "Sets the number of mismatches to allowed in a seed alignment during multiseed alignment. Can be set to 0 or 1. Setting this higher makes alignment slower (often much slower) but increases sensitivity.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 1);
                    }}, 1),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--local"), "local", "Local Mode", "In this mode, Bowtie 2 does not require that the entire read align from one end to the other. Rather, some characters may be omitted ('soft clipped') from the ends in order to achieve the greatest possible alignment score. The match bonus --ma is used in this mode, and the best possible alignment score is equal to the match bonus (--ma) times the length of the read. Specifying --local and one of the presets (e.g. --local --very-fast) is equivalent to specifying the local version of the preset (--very-fast-local). This is mutually exclusive with --end-to-end. --end-to-end is the default mode.", "checkbox", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--end-to-end"), "end-to-end", "End to End Mode", "In this mode, Bowtie 2 requires that the entire read align from one end to the other, without any trimming (or 'soft clipping') of characters from either end. The match bonus --ma always equals 0 in this mode, so all alignment scores are less than or equal to 0, and the greatest possible alignment score is 0. This is mutually exclusive with --local. --end-to-end is the default mode.", "checkbox", null, null)
            ), null, "http://bowtie-bio.sourceforge.net/bowtie2/index.shtml", true, true);
        }

        @Override
        public Bowtie2AlignmentStep create(PipelineContext context)
        {
            return new Bowtie2AlignmentStep(this, context);
        }
    }

    protected File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("BOWTIE2PATH", "bowtie2");
    }

    protected File getBuildExe()
    {
        return SequencePipelineService.get().getExeForPackage("BOWTIE2PATH", "bowtie2-build");
    }
}
