package org.labkey.sequenceanalysis.run.variant;

import com.google.common.io.Files;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PedigreeToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.Compress;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class KingInferenceStep extends AbstractCommandPipelineStep<KingInferenceStep.KingWrapper> implements VariantProcessingStep
{
    public KingInferenceStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new KingInferenceStep.KingWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<KingInferenceStep> implements VariantProcessingStep.SupportsPedigree
    {
        public Provider()
        {
            super("KingInferenceStep", "KING/Relatedness", "", "This will run KING (via plink2) to infer kinship from a VCF", List.of(
                    ToolParameterDescriptor.create("limitToChromosomes", "Limit to Chromosomes", "If checked, the analysis will include only the primary chromosomes", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.create("excludedContigs", "Excluded Contigs", "A comma separated list of contigs to exclude, such as X,Y,MT.", "textfield", new JSONObject(){{

                    }}, "X,Y,MT"),
                    new PedigreeToolParameterDescriptor(false)
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

        boolean limitToChromosomes = getProvider().getParameterByName("limitToChromosomes").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, true);
        if (limitToChromosomes)
        {
            SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary().toPath());
            List<String> toKeep = dict.getSequences().stream().map(SAMSequenceRecord::getSequenceName).filter(sequenceName -> {
                String name = StringUtils.replaceIgnoreCase(sequenceName, "^chr", "");

                return NumberUtils.isCreatable(name) || "X".equalsIgnoreCase(name) || "Y".equalsIgnoreCase(name);
            }).toList();

            if (toKeep.isEmpty())
            {
                getPipelineCtx().getLogger().info("The option to limit to chromosomes was selected, but no contigs were found with numeric names or names beginning with chr. All contigs will be used.");
            }
            else
            {
                plinkArgs.add("--chr");
                plinkArgs.add(StringUtils.join(toKeep, ","));
            }
        }

        String excludedContigs = StringUtils.trimToNull(getProvider().getParameterByName("excludedContigs").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class));
        if (excludedContigs != null)
        {
            plinkArgs.add("--not-chr");
            plinkArgs.add(excludedContigs);
        }

        plinkArgs.add("--allow-extra-chr");
        plinkArgs.add("--silent");

        plinkArgs.add("--max-alleles");
        plinkArgs.add("2");

        // NOTE: tools like sawfish can report half-called genotypes, like 0/.. For now, be most conservative in PCA:
        plinkArgs.add("--vcf-half-call");
        plinkArgs.add("missing");

        Integer threads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
        if (threads != null)
        {
            plinkArgs.add("--threads");
            plinkArgs.add(threads.toString());
        }

        Integer maxRam = SequencePipelineService.get().getMaxRam();
        if (maxRam != null)
        {
            plinkArgs.add("--memory");

            maxRam = maxRam * 1000;
            plinkArgs.add(String.valueOf(maxRam));
        }

        List<String> plinkArgs1 = new ArrayList<>(plinkArgs);
        plinkArgs1.add("--make-bed");

        // Added since KING is designed for plink1.9. This avoids the "Too many first alleles as the major allele" error.
        plinkArgs1.add("--maj-ref");
        plinkArgs1.add("--out");
        plinkArgs1.add(plinkOut.getPath());

        File doneFile = new File (plinkOut.getPath() + ".done");
        output.addIntermediateFile(doneFile);
        if (doneFile.exists())
        {
            getPipelineCtx().getLogger().debug("plink has completed, will not repeat");
        }
        else {
            plink.execute(plinkArgs1);

            try
            {
                Files.touch(doneFile);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        File plinkOutBed = new File(plinkOut.getPath() + ".bed");
        if (!plinkOutBed.exists())
        {
            throw new PipelineJobException("Unable to find file: " + plinkOutBed.getPath());
        }

        // Compute kinship with plink2:
        List<String> plinkArgs2 = new ArrayList<>(plinkArgs);
        plinkArgs2.add("--make-king-table");
        File plinkOutKing = new File(outputDirectory, "plinkKinship");
        plinkArgs2.add("--out");
        plinkArgs2.add(plinkOutKing.getPath());

        doneFile = new File (plinkOutKing.getPath() + ".done");
        File plinkOutKingFile = new File(plinkOutKing.getPath() + ".kin0");
        File plinkOutKingFileGz = new File(plinkOutKingFile.getPath() + ".txt.gz");
        if (doneFile.exists())
        {
            getPipelineCtx().getLogger().debug("plink has completed, will not repeat");
        }
        else
        {
            plink.execute(plinkArgs2);

            if (!plinkOutKingFile.exists())
            {
                throw new PipelineJobException("Unable to find file: " + plinkOutKingFile.getPath());
            }

            if (plinkOutKingFileGz.exists())
            {
                plinkOutKingFileGz.delete();
            }

            try
            {
                Compress.compressGzip(plinkOutKingFile, plinkOutKingFileGz);
                FileUtils.delete(plinkOutKingFile);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            try
            {
                Files.touch(doneFile);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        long lineCount = SequencePipelineService.get().getLineCount(plinkOutKingFileGz)-1;
        output.addSequenceOutput(plinkOutKingFileGz, "PLINK2/KING Relatedness: " + inputVCF.getName(), "PLINK2/KING Kinship", null, null, genome.getGenomeId(), "Total lines: " + lineCount);

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
