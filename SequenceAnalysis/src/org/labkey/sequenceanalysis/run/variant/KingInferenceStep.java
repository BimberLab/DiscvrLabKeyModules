package org.labkey.sequenceanalysis.run.variant;

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
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
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
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.pipeline.ProcessVariantsHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            super("KingInferenceStep", "KING/Relatedness", "", "This will run KING to infer kinship from a VCF", List.of(
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

        plinkArgs.add("--make-bed");
        
        // Added since KING is designed for plink1.9. This avoids the "Too many first alleles as the major allele" error.
        plinkArgs.add("--maj-ref");        

        boolean limitToChromosomes = getProvider().getParameterByName("limitToChromosomes").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, true);
        if (limitToChromosomes)
        {
            SAMSequenceDictionary dict = SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary().toPath());
            List<String> toKeep = dict.getSequences().stream().filter(s -> {
                String name = StringUtils.replaceIgnoreCase(s.getSequenceName(), "^chr", "");

                return NumberUtils.isCreatable(name) || "X".equalsIgnoreCase(name) || "Y".equalsIgnoreCase(name);
            }).map(SAMSequenceRecord::getSequenceName).toList();

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

        // Update the pedigree / fam file:
        String demographicsProviderName = getProvider().getParameterByName(PedigreeToolParameterDescriptor.NAME).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx());
        if (demographicsProviderName != null)
        {
            File pedFile = ProcessVariantsHandler.getPedigreeFile(getPipelineCtx().getSourceDirectory(true), demographicsProviderName);
            if (!pedFile.exists())
            {
                throw new PipelineJobException("Unable to find pedigree file: " + pedFile.getPath());
            }

            File kingFam = createFamFile(pedFile, new File(plinkOutBed.getParentFile(), "plink.fam"));
            kingArgs.add("--ped");
            kingArgs.add(kingFam.getPath());

            output.addIntermediateFile(kingFam);
        }

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

        File kinshipOutputTxt = new File(kinshipOutput.getPath() + ".txt.gz");
        if (kinshipOutputTxt.exists())
        {
            kinshipOutputTxt.delete();
        }

        try
        {
            kinshipOutput = Compress.compressGzip(kinshipOutput);
            FileUtils.moveFile(kinshipOutput, kinshipOutputTxt);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        output.addSequenceOutput(kinshipOutputTxt, "King Relatedness: " + inputVCF.getName(), "KING Relatedness", null, null, genome.getGenomeId(), null);

        return output;
    }

    private File createFamFile(File pedFile, File famFile) throws PipelineJobException
    {
        File newFamFile = new File(famFile.getParentFile(), "king.fam");

        Map<String, String> pedMap = new CaseInsensitiveHashMap<>();
        try (BufferedReader reader = Readers.getReader(pedFile))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] tokens = line.split(" ");
                if (tokens.length != 6)
                {
                    throw new PipelineJobException("Improper ped line length: " + tokens.length);
                }

                pedMap.put(tokens[1], StringUtils.join(Arrays.asList("0", tokens[1], tokens[2], tokens[3], tokens[4], "-9"), "\t"));
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        try (BufferedReader reader = Readers.getReader(famFile);PrintWriter writer = PrintWriters.getPrintWriter(newFamFile))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] tokens = line.split("\t");
                if (tokens.length != 6)
                {
                    throw new PipelineJobException("Improper ped line length: " + tokens.length);
                }

                String newRow = pedMap.get(tokens[1]);
                if (newRow == null)
                {
                    getPipelineCtx().getLogger().warn("Unable to find pedigree entry for: " + tokens[1] + ", reusing original");
                    writer.println(line);
                }
                else
                {
                    writer.println(newRow);
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return newFamFile;
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
