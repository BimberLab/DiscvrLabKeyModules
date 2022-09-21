package org.labkey.sequenceanalysis.run.variant;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Interval;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.old.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
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

public class PlinkPcaStep extends AbstractCommandPipelineStep<PlinkPcaStep.PlinkWrapper> implements VariantProcessingStep
{
    public PlinkPcaStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new PlinkPcaStep.PlinkWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<PlinkPcaStep>
    {
        public Provider()
        {
            super("PlinkPcaStep", "Plink/PCA", "", "This will run plink to generate the data for MDS/PCA", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--not-chr"), "excludedContigs", "Excluded Contigs", "A comma separated list of contigs to exclude, such as X,Y,MT.", "textfield", new JSONObject(){{

                    }}, "X,Y,MT"),
                    ToolParameterDescriptor.create(SelectSamplesStep.SAMPLE_INCLUDE, "Sample(s) Include", "Only the following samples will be included in the analysis.", "sequenceanalysis-trimmingtextarea", null, null),
                    ToolParameterDescriptor.create(SelectSamplesStep.SAMPLE_EXCLUDE, "Samples(s) To Exclude", "The following samples will be excluded from the analysis.", "sequenceanalysis-trimmingtextarea", null, null)
            ), Arrays.asList("sequenceanalysis/field/TrimmingTextArea.js"), "https://zzz.bwh.harvard.edu/plink/");
        }

        public PlinkPcaStep create(PipelineContext ctx)
        {
            return new PlinkPcaStep(this, ctx);
        }
    }

    private void addSubjectSelectOptions(String text, List<String> args, String argName, File outputFile, VariantProcessingStepOutputImpl output) throws PipelineJobException
    {
        text = StringUtils.trimToNull(text);
        if (text != null)
        {
            String[] names = text.split(";");
            try (CSVWriter writer = new CSVWriter(IOUtil.openFileForBufferedUtf8Writing(outputFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                Arrays.stream(names).forEach(x -> {
                    writer.writeNext(new String[]{x, x});
                });
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            args.add(argName);
            args.add(outputFile.getPath());

            output.addIntermediateFile(outputFile);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        List<String> args = new ArrayList<>();
        args.add(getWrapper().getExe().getPath());
        args.add("--pca");
        args.add("--allow-extra-chr");

        String samplesToInclude = getProvider().getParameterByName(SelectSamplesStep.SAMPLE_INCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        addSubjectSelectOptions(samplesToInclude, args, "--keep", new File(outputDirectory, "samplesToKeep.txt"), output);

        String samplesToExclude = getProvider().getParameterByName(SelectSamplesStep.SAMPLE_EXCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        addSubjectSelectOptions(samplesToExclude, args, "--exclude", new File(outputDirectory, "samplesToExclude.txt"), output);

        args.add("--vcf");
        args.add(inputVCF.getPath());

        File outPrefix = new File(outputDirectory, "plink");
        args.add("--out");
        args.add(outPrefix.getPath());

        args.addAll(getClientCommandArgs());

        getWrapper().execute(args);

        output.addInput(inputVCF, "Input VCF");
        output.addInput(genome.getWorkingFastaFile(), "Reference Genome");

        File outputFile = new File(outPrefix.getPath() + ".eigenvec");
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Unable to find expected file: " + outputFile);
        }

        output.addOutput(outputFile, "PLink PCA");
        output.addSequenceOutput(outputFile, "PLink PCA for: " + inputVCF.getName(), "PLink PCA", null, null, genome.getGenomeId(), null);

        return output;
    }

    public static class PlinkWrapper extends AbstractCommandWrapper
    {
        public PlinkWrapper(@Nullable Logger logger)
        {
            super(logger);
        }

        public File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("PLINK2PATH", "plink");
        }
    }
}
