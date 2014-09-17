package org.labkey.sequenceanalysis.run.alignment;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
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
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 12/14/12
 * Time: 7:40 AM
 */
public class MosaikWrapper extends AbstractCommandWrapper
{
    public MosaikWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class MosaikAlignmentStep extends AbstractCommandPipelineStep<MosaikWrapper> implements AlignmentStep
    {
        public MosaikAlignmentStep(PipelineStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new MosaikWrapper(ctx.getLogger()));
        }

        @Override
        public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File outputFile = getWrapper().getExpectedMosaikRefFile(outputDir, referenceGenome.getFastaFile());
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getProvider().getName());
            if (!hasCachedIndex)
            {
                getWrapper().executeMosaikBuild(referenceGenome.getFastaFile(), null, outputFile, "-oa", getClientCommandArgs());
                if (!outputFile.exists())
                    throw new PipelineJobException("Unable to find file: " + outputFile.getPath());
            }

            output.addOutput(outputFile, IndexOutputImpl.PRIMARY_ALIGNER_INDEX_FILE);
            output.addDeferredDeleteIntermediateFile(outputFile);
            output.appendOutputs(referenceGenome.getFastaFile(), outputDir);

            //recache if not already
            AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), outputDir, getProvider().getName(), output);

            return output;
        }

        @Override
        public AlignmentOutput performAlignment(File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename) throws PipelineJobException
        {
            AlignmentOutputImpl output = new AlignmentOutputImpl();
            AlignerIndexUtil.copyIndexIfExists(this.getPipelineCtx(), output, referenceGenome.getFastaFile().getParentFile(), getProvider().getName());
            getWrapper().setOutputDir(outputDirectory);

            //TODO: can we infer the technology?
            File reads = getWrapper().buildFastqReads(outputDirectory, inputFastq1, inputFastq2, SequencingTechnology.illumina_long);
            output.addIntermediateFile(reads);

            File bam = getWrapper().executeMosaikAligner(referenceGenome.getFastaFile(), reads, outputDirectory, basename, getClientCommandArgs());
            if (!bam.exists())
            {
                throw new PipelineJobException("BAM not created, expected: " + bam.getPath());
            }

            output.setBAM(bam);
            output.addIntermediateFile(new File(outputDirectory, basename + ".stat"));

            return output;
        }
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
////            'ma|mode' => 'm',
////            'ma|hash_size' => 'hs',
////            'ma|processors' => 'p',
////            'ma|use_aligned_length' => 'mmal',
////            'ma|output_multiple' => 'om',

            super("Mosaik", "Mosaik is suitable for longer reads and has the option to retain multiple hits per read. The only downside is that it can be slower. When this pipeline was first written, this aligner was preferred for sequence-based genotyping and similar applications which require retaining multiple hits. It supports paired end reads. The aligner is still good; however, Lastz also seems to perform well for SBT.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-om"), "output_multiple", "Retain All Hits", "If selected, all hits above thresholds will be reported. If not, only a single hit will be retained", "checkbox", new JSONObject()
                    {{
                            put("checked", true);
                        }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-mmp"), "max_mismatch_pct", "Max Mismatch Pct", "The maximum percent of bases allowed to mismatch per alignment. Note: Ns are counted as mismatches", "ldk-numberfield", new JSONObject()
                    {{
                            put("minValue", 0);
                            put("maxValue", 1);
                        }}, 0.02),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-hs"), "hash_size", "Hash Size", "The hash size used in alignment (see Mosaik documentation). A large value is preferred for sequences expected to be highly similar to the reference", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                            put("maxValue", 32);
                        }}, 32),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-mhp"), "max_hash_positions", "Max Hash Positions", "The maximum number of hash matches that are passed to local alignment", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                            put("maxValue", 200);
                        }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-act"), "align_threshold", "Alignment Threshold", "The alignment score (length) required for an alignment to continue to local alignment. Because the latter is slow, a higher value can improve speed", "ldk-integerfield", null, 55)
            ), null, "https://code.google.com/p/mosaik-aligner/", true);
        }

        public MosaikAlignmentStep create(PipelineContext context)
        {
            return new MosaikAlignmentStep(this, context);
        }
    }

    protected File getBuildExe()
    {
        return SequencePipelineService.get().getExeForPackage("MOSAIKPATH", "MosaikBuild");
    }

    protected File getAlignExe()
    {
        return SequencePipelineService.get().getExeForPackage("MOSAIKPATH", "MosaikAligner");
    }

    public enum SequencingTechnology
    {
        roche454("454"),
        helicos("helicos"),
        illumina("illumina"),
        illumina_long("illumina_long"),
        sanger("sanger"),
        solid("solid");

        private String _paramValue;

        SequencingTechnology(String paramValue)
        {
            _paramValue = paramValue;
        }

        public String getParamValue()
        {
            return _paramValue;
        }
    }

    private File buildFastqReads(File outputDirectory, File input, @Nullable File input2, SequencingTechnology technology) throws PipelineJobException
    {
        File output = new File(outputDirectory, FileUtil.getBaseName(input) + ".mosaikreads");
        executeMosaikBuild(input, input2, output, "-out", Arrays.asList("-st", technology.getParamValue()));
        if (!output.exists())
            throw new PipelineJobException("Unable to find file: " + output.getPath());

        return output;
    }

    public File executeMosaikAligner(File refFasta, File reads, File outputDirectory, String basename, List<String> options) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getAlignExe().getPath());
        args.add("-ia");
        File mosaikRef = getExpectedMosaikRefFile(refFasta.getParentFile(), refFasta);
        if (!mosaikRef.exists())
        {
            throw new PipelineJobException("Unable to find reference file: " + mosaikRef.getPath());
        }
        args.add(mosaikRef.getPath());

        args.add("-in");
        args.add(reads.getPath());


        args.add("-out");
        args.add(new File(outputDirectory, basename).getPath());

        File networkDir = SequencePipelineService.get().getExeForPackage("MOSAIK_NETWORK_FILE", "mosaikNetworkFile");
        args.add("-annpe");
        args.add(new File(networkDir, "2.1.26.pe.100.0065.ann").getPath());
        args.add("-annse");
        args.add(new File(networkDir, "2.1.26.se.100.005.ann").getPath());
        args.add("-quiet");

        args.addAll(options);

        execute(args);

        return new File(outputDirectory, basename + ".bam");
    }

    public void executeMosaikBuild(File input1, @Nullable File input2, File output, String outParam, List<String> options) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getBuildExe().getPath());
        args.addAll(options);
        args.add("-quiet");

        SequenceUtil.FILETYPE type = SequenceUtil.inferType(input1);
        String paramName1;
        String paramName2;
        if (type.equals(SequenceUtil.FILETYPE.fasta))
        {
            paramName1 = "-fr";
            paramName2 = "-f2";
        }
        else if (type.equals(SequenceUtil.FILETYPE.fastq))
        {
            paramName1 = "-q";
            paramName2 = "-q2";
        }
        else
        {
            throw new IllegalArgumentException("Unknown input type: " + type.name());
        }

        CommandLineParam arg1 = CommandLineParam.create(paramName1);
        args.addAll(arg1.getArguments(input1.getPath()));

        if (input2 != null)
        {
            CommandLineParam arg2 = CommandLineParam.create(paramName2);
            args.addAll(arg2.getArguments(input2.getPath()));
        }

        CommandLineParam outArg = CommandLineParam.create(outParam);
        args.addAll(outArg.getArguments(output.getPath()));

        execute(args);
    }

    public File getExpectedMosaikRefFile(File outputDir, File refFasta)
    {
        return new File(outputDir, FileUtil.getBaseName(refFasta) + ".mosaik");
    }
}
