package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KingInferenceStep extends AbstractCommandPipelineStep<KingInferenceStep.KingWrapper> implements VariantProcessingStep
{
    public KingInferenceStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new KingInferenceStep.KingWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<KingInferenceStep>
    {
        public Provider()
        {
            super("KingInferenceStep", "KING/Relatedness", "", "This will run KING to infer kinship from a VCF", Arrays.asList(
                    ToolParameterDescriptor.create("limitToChromosomes", "Limit to Chromosomes", "If checked, the analysis will include only the primary chromosomes", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true)
            ), null, "https://www.kingrelatedness.com/manual.shtml");
        }

        @Override
        public KingInferenceStep create(PipelineContext ctx)
        {
            return new KingInferenceStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        output.addInput(inputVCF, "Input VCF");
        output.addInput(genome.getWorkingFastaFile(), "Reference Genome");

        File plinkOut = new File(outputDirectory, "plink");
        output.addIntermediateFile(new File(plinkOut.getPath() + ".bed"));
        //output.addIntermediateFile(new File(plinkOut.getPath() + ".fam"));
        output.addIntermediateFile(new File(plinkOut.getPath() + ".bim"));
        output.addIntermediateFile(new File(plinkOut.getPath() + ".log"));
        output.addIntermediateFile(new File(plinkOut.getPath() + "-temporary.psam"));

        PlinkPcaStep.PlinkWrapper plink = new PlinkPcaStep.PlinkWrapper(getPipelineCtx().getLogger());
        List<String> plinkArgs = new ArrayList<>();
        plinkArgs.add(plink.getExe().getPath());
        plinkArgs.add("--vcf");
        plinkArgs.add(inputVCF.getPath());

        plinkArgs.add("--make-bed");

        boolean limitToChromosomes = getProvider().getParameterByName("limitToChromosomes").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, true);
        if (limitToChromosomes)
        {
            SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary().toPath());
            List<String> toKeep = dict.getSequences().stream().filter(s -> {
                String name = StringUtils.replaceIgnoreCase(s.getSequenceName(), "^chr", "");

                return NumberUtils.isCreatable(name) || "X".equalsIgnoreCase(name) || "Y".equalsIgnoreCase(name);
            }).map(SAMSequenceRecord::getSequenceName).toList();

            plinkArgs.add("--chr");
            plinkArgs.add(StringUtils.join(toKeep, ","));
        }

        plinkArgs.add("--allow-extra-chr");
        plinkArgs.add("--silent");

        plinkArgs.add("--max-alleles");
        plinkArgs.add("2");

        plinkArgs.add("--out");
        plinkArgs.add(plinkOut.getPath());

        Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (threads != null)
        {
            plinkArgs.add("--threads");
            plinkArgs.add(threads.toString());
        }

        //TODO: consider --memory (in MB)

        plink.execute(plinkArgs);

        File plinkOutBed = new File(plinkOut.getPath() + ".bed");
        if (!plinkOutBed.exists())
        {
            throw new PipelineJobException("Unable to find file: " + plinkOutBed.getPath());
        }

        KingWrapper wrapper = new KingWrapper(getPipelineCtx().getLogger());
        wrapper.setWorkingDir(outputDirectory);

        List<String> kingArgs = new ArrayList<>();
        kingArgs.add(wrapper.getExe().getPath());

        kingArgs.add("-b");
        kingArgs.add(plinkOutBed.getPath());

        kingArgs.add("--prefix");
        kingArgs.add(SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()));

        if (threads != null)
        {
            kingArgs.add("--cpus");
            kingArgs.add(threads.toString());
        }

        kingArgs.add("--related");

        File kinshipOutput = new File(outputDirectory, SequenceAnalysisService.get().getUnzippedBaseName(inputVCF.getName()) + ".kin");
        wrapper.execute(kingArgs);
        if (!kinshipOutput.exists())
        {
            throw new PipelineJobException("Unable to find file: " + kinshipOutput.getPath());
        }

        File kinshipOutputTxt = new File(kinshipOutput.getPath() + ".txt");
        if (kinshipOutputTxt.exists())
        {
            kinshipOutputTxt.delete();
        }

        try
        {
            FileUtils.moveFile(kinshipOutput, kinshipOutputTxt);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        output.addSequenceOutput(kinshipOutputTxt, "King Relatedness: " + inputVCF.getName(), "KING Relatedness", null, null, genome.getGenomeId(), null);

        return output;
    }

    public static class KingWrapper extends AbstractCommandWrapper
    {
        public KingWrapper(@Nullable Logger logger)
        {
            super(logger);
        }

        public File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("KINGPATH", "king");
        }
    }
}
