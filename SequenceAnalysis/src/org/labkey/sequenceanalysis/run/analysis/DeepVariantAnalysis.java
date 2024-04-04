package org.labkey.sequenceanalysis.run.analysis;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAnalysisStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineOutputTracker;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 7/3/2014
 * Time: 11:29 AM
 */
public class DeepVariantAnalysis extends AbstractCommandPipelineStep<DeepVariantAnalysis.DeepVariantWrapper> implements AnalysisStep
{
    public DeepVariantAnalysis(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new DeepVariantAnalysis.DeepVariantWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractAnalysisStepProvider<DeepVariantAnalysis>
    {
        public Provider()
        {
            super("DeepVariantAnalysis", "DeepVariant", "DeepVariant", "This will run DeepVariant on the selected data to generate a gVCF.", getToolDescriptors(), null, null);
        }

        @Override
        public DeepVariantAnalysis create(PipelineContext ctx)
        {
            return new DeepVariantAnalysis(this, ctx);
        }
    }

    public static List<ToolParameterDescriptor> getToolDescriptors()
    {
        return Arrays.asList(
                ToolParameterDescriptor.create("modelType", "Model Type", "", "ldk-simplecombo", new JSONObject(){{
                    put("storeValues", "AUTO;WGS;WES;PACBIO;ONT_R104;HYBRID_PACBIO_ILLUMINA");
                    put("multiSelect", false);
                    put("allowBlank", false);
                }}, "AUTO"),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--haploid_contigs"), "haploidContigs", "Haploid Contigs", "", "textfield", new JSONObject(){{

                }}, "X,Y"),
                ToolParameterDescriptor.create("binVersion", "DeepVariant Version", "The version of DeepVariant to run, which is passed to their docker container", "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, "1.6.0"),
                ToolParameterDescriptor.create("retainVcf", "Retain VCF", "If selected, the VCF with called genotypes will be retained", "checkbox", null, false)
        );
    }

    @Override
    public void init(SequenceAnalysisJobSupport support) throws PipelineJobException
    {
        String modelType = getProvider().getParameterByName("modelType").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        if (modelType == null)
        {
            throw new PipelineJobException("Missing model type");
        }

        inferModelType(modelType, getPipelineCtx());
    }

    public static void inferModelType(String modelType, PipelineContext ctx) throws PipelineJobException
    {
        if ("AUTO".equals(modelType))
        {
            ctx.getLogger().info("Inferring model type by readset type:");
            if (ctx.getSequenceSupport().getCachedReadsets().size() != 1)
            {
                throw new PipelineJobException("Expected a single cached readset, found: " + ctx.getSequenceSupport().getCachedReadsets().size());
            }

            Readset rs = ctx.getSequenceSupport().getCachedReadsets().get(0);
            if ("ILLUMINA".equals(rs.getPlatform()))
            {
                switch (rs.getApplication())
                {
                    case "Whole Genome: Deep Coverage":
                        modelType = "WGS";
                        break;
                    case "Whole Genome: Light Coverage":
                        modelType = "WGS";
                        break;
                    case "Whole Exome":
                        modelType = "WXS";
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown application: " + rs.getApplication());
                }
            }
            else if ("PACBIO".equals(rs.getPlatform()))
            {
                modelType = "PACBIO";
            }

            if ("AUTO".equals(modelType))
            {
                throw new PipelineJobException("Unable to infer modelType for: " + rs.getName());
            }

            ctx.getSequenceSupport().cacheObject("modelType", modelType);
        }
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();
        output.addInput(inputBam, "Input BAM File");

        File outputFile = new File(outputDir, FileUtil.getBaseName(inputBam) + ".g.vcf.gz");
        File idxFile = new File(outputDir, FileUtil.getBaseName(inputBam) + ".g.vcf.gz.idx");

        String inferredModelType = getPipelineCtx().getSequenceSupport().getCachedObject("modelType", String.class);
        String modelType = inferredModelType == null ? getProvider().getParameterByName("modelType").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class) : inferredModelType;
        if (modelType == null)
        {
            throw new PipelineJobException("Missing model type");
        }

        List<String> args = new ArrayList<>(getClientCommandArgs());
        args.add("--model_type=" + modelType);

        String binVersion = getProvider().getParameterByName("binVersion").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        if (binVersion == null)
        {
            throw new PipelineJobException("Missing binVersion");
        }

        boolean retainVcf = getProvider().getParameterByName("retainVcf").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);

        getWrapper().setOutputDir(outputDir);
        getWrapper().setWorkingDir(outputDir);
        getWrapper().execute(inputBam, referenceGenome.getWorkingFastaFile(), outputFile, retainVcf, output, binVersion, args);

        output.addOutput(outputFile, "gVCF File");
        output.addSequenceOutput(outputFile, outputFile.getName(), "DeepVariant gVCF File", rs.getReadsetId(), null, referenceGenome.getGenomeId(), "DeepVariant Version: " + binVersion);
        if (idxFile.exists())
        {
            output.addOutput(idxFile, "VCF Index");
        }

        if (retainVcf)
        {
            File outputFileVcf = new File(outputDir, FileUtil.getBaseName(inputBam) + ".vcf.gz");
            if (!outputFileVcf.exists())
            {
                throw new PipelineJobException("Missing expected file: " + outputFileVcf.getPath());
            }

            output.addSequenceOutput(outputFile, outputFileVcf.getName(), "DeepVariant VCF File", rs.getReadsetId(), null, referenceGenome.getGenomeId(), "DeepVariant Version: " + binVersion);
        }

        return output;
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }

    public static class DeepVariantWrapper extends AbstractCommandWrapper
    {
        public DeepVariantWrapper(Logger logger)
        {
            super(logger);
        }

        private File ensureLocalCopy(File input, File workingDirectory, PipelineOutputTracker output) throws PipelineJobException
        {
            try
            {
                if (workingDirectory.equals(input.getParentFile()))
                {
                    return input;
                }

                File local = new File(workingDirectory, input.getName());
                if (!local.exists())
                {
                    getLogger().debug("Copying file locally: " + input.getPath());
                    FileUtils.copyFile(input, local);
                }

                output.addIntermediateFile(local);

                return local;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        public void execute(File inputBam, File refFasta, File outputGvcf, boolean retainVcf, PipelineOutputTracker tracker, String binVersion, List<String> extraArgs) throws PipelineJobException
        {
            File workDir = outputGvcf.getParentFile();
            File outputVcf = new File(outputGvcf.getPath().replaceAll(".g.vcf", ".vcf"));
            if (!retainVcf)
            {
                tracker.addIntermediateFile(outputVcf);
                tracker.addIntermediateFile(new File(outputVcf.getPath() + ".tbi"));
            }

            File inputBamLocal = ensureLocalCopy(inputBam, workDir, tracker);
            ensureLocalCopy(SequenceUtil.getExpectedIndex(inputBam), workDir, tracker);

            File refFastaLocal = ensureLocalCopy(refFasta, workDir, tracker);
            ensureLocalCopy(new File(refFasta.getPath() + ".fai"), workDir, tracker);
            ensureLocalCopy(new File(FileUtil.getBaseName(refFasta.getPath()) + ".dict"), workDir, tracker);

            File localBashScript = new File(workDir, "docker.sh");
            File dockerBashScript = new File(workDir, "dockerRun.sh");
            tracker.addIntermediateFile(localBashScript);
            tracker.addIntermediateFile(dockerBashScript);

            List<String> bashArgs = new ArrayList<>(Arrays.asList("/opt/deepvariant/bin/run_deepvariant"));
            bashArgs.add("--make_examples_extra_args='normalize_reads=true'");
            bashArgs.add("--ref=/work/" + refFastaLocal.getName());
            bashArgs.add("--reads=/work/" + inputBamLocal.getName());
            bashArgs.add("--output_gvcf=/work/" + outputGvcf.getName());
            bashArgs.add("--output_vcf=/work/" + outputVcf.getName());
            Integer maxThreads = SequencePipelineService.get().getMaxThreads(getLogger());
            if (maxThreads != null)
            {
                bashArgs.add("--num_shards=" + maxThreads);
            }

            if (extraArgs != null)
            {
                bashArgs.addAll(extraArgs);
            }

            try (PrintWriter writer = PrintWriters.getPrintWriter(localBashScript); PrintWriter dockerWriter = PrintWriters.getPrintWriter(dockerBashScript))
            {
                writer.println("#!/bin/bash");
                writer.println("set -x");
                writer.println("WD=`pwd`");
                writer.println("HOME=`echo ~/`");
                writer.println("DOCKER='" + SequencePipelineService.get().getDockerCommand() + "'");
                writer.println("sudo $DOCKER pull google/deepvariant:" + binVersion);
                writer.println("sudo $DOCKER run --rm=true \\");
                writer.println("\t-v \"${WD}:/work\" \\");
                writer.println("\t-v \"${HOME}:/homeDir\" \\");
                if (!StringUtils.isEmpty(System.getenv("TMPDIR")))
                {
                    writer.println("\t-v \"${TMPDIR}:/tmp\" \\");
                }
                writer.println("\t-u $UID \\");
                writer.println("\t-e USERID=$UID \\");
                writer.println("\t--entrypoint /bin/bash \\");
                writer.println("\t-w /work \\");
                Integer maxRam = SequencePipelineService.get().getMaxRam();
                if (maxRam != null)
                {
                    writer.println("\t-e SEQUENCEANALYSIS_MAX_RAM=" + maxRam + " \\");
                    writer.println("\t--memory='" + maxRam + "g' \\");
                }
                writer.println("\tgoogle/deepvariant:" + binVersion + " \\");
                writer.println("\t/work/" + dockerBashScript.getName());
                writer.println("EXIT_CODE=$?");
                writer.println("echo 'Docker run exit code: '$EXIT_CODE");
                writer.println("exit $EXIT_CODE");

                dockerWriter.println("#!/bin/bash");
                dockerWriter.println("set -x");
                dockerWriter.println(StringUtils.join(bashArgs, " "));
                dockerWriter.println("EXIT_CODE=$?");
                dockerWriter.println("echo 'Exit code: '$?");
                dockerWriter.println("exit $EXIT_CODE");
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            setWorkingDir(workDir);
            execute(Arrays.asList("/bin/bash", localBashScript.getPath()));

            if (!outputGvcf.exists())
            {
                throw new PipelineJobException("File not found: " + outputGvcf.getPath());
            }

            File idxFile = new File(outputGvcf.getPath() + ".tbi");
            if (!idxFile.exists())
            {
                throw new PipelineJobException("Missing index: " + idxFile.getPath());
            }
        }
    }
}
