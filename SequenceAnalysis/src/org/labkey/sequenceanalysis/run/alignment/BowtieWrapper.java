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
public class BowtieWrapper extends AbstractCommandWrapper
{
    public BowtieWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class BowtieAlignmentStep extends AbstractAlignmentPipelineStep<BowtieWrapper> implements AlignmentStep
    {
        public BowtieAlignmentStep(AlignmentStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new BowtieWrapper(ctx.getLogger()));
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
            args.add(new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputFastq1.getName()) + ".bowtie.unaligned.fastq").getPath());
            args.addAll(getClientCommandArgs());

            File indexFile = new File(referenceGenome.getAlignerIndexDir(getProvider().getName()), getExpectedIndexName(referenceGenome.getWorkingFastaFile()));
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
            getPipelineCtx().getLogger().info("Creating Bowtie index");
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
            return FileUtil.getBaseName(refFasta) + ".bowtie.index";
        }
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("Bowtie", "Bowtie is a fast aligner often used for short reads. Disadvantages are that it does not perform gapped alignment. It will return a single hit for each read.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-l"), "seed_length", "Seed Length", null, "ldk-numberfield", null, 20),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-k"), "max_hits", "Max Alignments", "Report up to this many valid alignments per read or pair (default: 1).", "ldk-numberfield", null, 20),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-n"), "max_seed_mismatches", "Max Seed Mismatches", "Maximum number of mismatches permitted in the 'seed', i.e. the first L base pairs of the read (where L is set with -l/--seedlen). This may be 0, 1, 2 or 3 and the default is 2. This option is mutually exclusive with the Max Mismatches (-v) option.", "ldk-integerfield", null, 3),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-v"), "max_mismatches", "Max Mismatches", "Report alignments with at most <int> mismatches. -e and -l options are ignored and quality values have no effect on what alignments are valid. -v is mutually exclusive with Max Seed Mismatches (-n).", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                        put("maxValue", 3);
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-m"), "supress_threshold", "Suppress All Alignments Above", "Suppress all alignments for a particular read or pair if more than <int> reportable alignments exist for it. Reportable alignments are those that would be reported given the -n, -v, -l, -e, -k, -a, --best, and --strata options. Default: no limit. Bowtie is designed to be very fast for small -m but bowtie can become significantly slower for larger values of -m. If you would like to use Bowtie for larger values of -k, consider building an index with a denser suffix-array sample, i.e. specify a smaller -o/--offrate when invoking bowtie-build for the relevant index (see the Performance tuning section for details).", "ldk-integerfield", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--best"), "best", "Report Best Alignment", "Make Bowtie guarantee that reported singleton alignments are 'best' in terms of stratum (i.e. number of mismatches, or mismatches in the seed in the case of -n mode) and in terms of the quality values at the mismatched position(s). Stratum always trumps quality; e.g. a 1-mismatch alignment where the mismatched position has Phred quality 40 is preferred over a 2-mismatch alignment where the mismatched positions both have Phred quality 10. When --best is not specified, Bowtie may report alignments that are sub-optimal in terms of stratum and/or quality (though an effort is made to report the best alignment). --best mode also removes all strand bias. Note that --best does not affect which alignments are considered 'valid' by bowtie, only which valid alignments are reported by bowtie. When --best is specified and multiple hits are allowed (via -k or -a), the alignments for a given read are guaranteed to appear in best-to-worst order in bowtie's output. bowtie is somewhat slower when --best is specified.", "checkbox", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--strata"), "strata", "Strata", "", "checkbox", null, null)
            ), null, "http://bowtie-bio.sourceforge.net/index.shtml", true, true);
        }

        @Override
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
