package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class BaseQualityScoreRecalibrator extends AbstractGatk4Wrapper
{
    public BaseQualityScoreRecalibrator(Logger log)
    {
        super(log);
    }

    public File execute(File bam, File fasta, File output, @Nullable File knownVariants, @Nullable List<String> extraArgs) throws PipelineJobException
    {
        boolean deleteKnownVariantFile = false;
        if (knownVariants == null)
        {
            getLogger().info("Creating empty file for known variants");
            knownVariants = new File(output.getParentFile(), "knownVariants.vcf");
            try (PrintWriter writer = PrintWriters.getPrintWriter(knownVariants))
            {
                writer.println("##fileformat=VCFv4.0");
                writer.println("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO");
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            List<String> args1 = new ArrayList<>(getBaseArgs());
            args1.add("IndexFeatureFile");
            args1.add("-I");
            args1.add(knownVariants.getPath());
            execute(args1);

            deleteKnownVariantFile = true;
        }

        File recalTable = new File(output.getParentFile(), "recal_table.txt");
        List<String> argsRecal = new ArrayList<>(getBaseArgs());
        argsRecal.add("BaseRecalibrator");
        argsRecal.add("-I");
        argsRecal.add(bam.getPath());
        argsRecal.add("-R");
        argsRecal.add(fasta.getPath());
        argsRecal.add("--known-sites");
        argsRecal.add(knownVariants.getPath());
        argsRecal.add("-O");
        argsRecal.add(recalTable.getPath());

        if (extraArgs != null)
        {
            argsRecal.addAll(extraArgs);
        }

        execute(argsRecal);

        // If there is not recal possible, the output has 132 lines.
        long lineCount = SequenceUtil.getLineCount(recalTable);
        if (lineCount > 132)
        {
            List<String> argsApply = new ArrayList<>(getBaseArgs());
            argsApply.add("ApplyBQSR");
            argsApply.add("-I");
            argsApply.add(bam.getPath());
            argsApply.add("-R");
            argsApply.add(fasta.getPath());
            argsApply.add("--bqsr-recal-file");
            argsApply.add(recalTable.getPath());
            argsApply.add("-O");
            argsApply.add(output.getPath());
            execute(argsApply);

            if (!output.exists())
            {
                throw new PipelineJobException("Expected output not created: " + output.getPath());
            }
        }
        else
        {
            getLogger().info("No recalibration was possible, skipping ApplyBQSR");
            output = bam;
        }

        if (deleteKnownVariantFile)
        {
            knownVariants.delete();
            new File(knownVariants.getPath() + ".idx").delete();
        }

        return output;
    }

    public static class BaseQualityScoreRecalibratorStep extends AbstractCommandPipelineStep<BaseQualityScoreRecalibrator> implements BamProcessingStep
    {
        public BaseQualityScoreRecalibratorStep(PipelineStepProvider<?> provider, PipelineContext ctx)
        {
            super(provider, ctx, new BaseQualityScoreRecalibrator(ctx.getLogger()));
        }

        public static class Provider extends AbstractPipelineStepProvider<BaseQualityScoreRecalibratorStep>
        {
            public Provider()
            {
                super("BaseQualityScoreRecalibrator", "Base Quality Score Recalibrator", "GATK", "The step runs GATK's BaseQualityScoreRecalibrator tool.", Arrays.asList(
                        ToolParameterDescriptor.createExpDataParam("knownVariants", "Known Variants VCF", "This is typically the dbSNP variants file.  If working in a species where no suitable reference data exists, leave this blank and an empty VCF will be created.", "ldk-expdatafield", null, null),
                        ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--maximum-cycle-value"), "maximumCycleValue", "Maximum Cycle Value", "Passed directly to BaseQualityScoreRecalibrator", "ldk-integerfield", new JSONObject(){{
                            put("minValue", 0);
                        }}, null)
                ), new LinkedHashSet<>(List.of("ldk/field/ExpDataField.js")), "https://gatk.broadinstitute.org/hc/en-us/articles/360036363332-BaseRecalibrator");
            }

            @Override
            public BaseQualityScoreRecalibratorStep create(PipelineContext ctx)
            {
                return new BaseQualityScoreRecalibratorStep(this, ctx);
            }
        }

        @Override
        public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
        {
            BamProcessingOutputImpl output = new BamProcessingOutputImpl();
            getWrapper().setOutputDir(outputDirectory);
            getWrapper().setWorkingDir(outputDirectory);

            File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".bqsr.bam");
            File knownVariants = null;
            Integer knownVariantsId = getProvider().getParameterByName("knownVariants").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class, null);
            if (knownVariantsId != null)
            {
                knownVariants = getPipelineCtx().getSequenceSupport().getCachedData(knownVariantsId);
                if (!knownVariants.exists())
                {
                    throw new PipelineJobException("Unable to find known variants file: " + knownVariants.getPath());
                }
            }

            outputBam = getWrapper().execute(inputBam, referenceGenome.getWorkingFastaFile(), outputBam, knownVariants, getClientCommandArgs());

            output.setBAM(outputBam);

            return output;
        }
    }
}
