package org.labkey.sequenceanalysis.run.variant;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
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
import org.labkey.api.sequenceanalysis.run.AbstractGatkWrapper;
import org.labkey.api.util.Compress;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
public class VariantsToTableStep extends AbstractCommandPipelineStep<VariantsToTableStep.Wrapper> implements VariantProcessingStep
{
    public VariantsToTableStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new Wrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<VariantsToTableStep>
    {
        public Provider()
        {
            super("VariantsToTableStep", "GATK Variants To Table", "GATK", "Generate a table using the selected fields from a VCF file.", Arrays.asList(
                    ToolParameterDescriptor.create(null, null, null, "panel", new JSONObject()
                    {{
                        put("html", "This tool is powerful, but requires some understanding of the data within a VCF file.  A VCF will have some fields always present (like CHROM and POS), some typically present is generated though GATK (like AF), and some may be unique to your particular VCF, depending on how it was generated.  For example, SNPEff uses the ANN field for annotations.  Please refer to the GATK documentation for more detail.  The Add Common fields buttons can be used to populate with a set of the commonly present fields");
                        put("width", "90%");
                        put("border", false);
                        put("style", "padding-bottom: 20px;");
                        put("isToolParam", false);
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-AMD"), "amd", "Allow Missing Data", "If checked, do not require every record to contain every field", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--showFiltered"), "showFiltered", "Show Filtered Data", "If checked, rows that are filtered will be included", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, false),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--splitMultiAllelic"), "splitMultiAllelic", "Split Multi Allelic", "If checked, sites with multiple alleles will be split into separate rows in the output table", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, false),
                    ToolParameterDescriptor.create("fields", "Fields", "Choose the fields to display.  These should match fields available in the VCF, which will vary file to file.", "sequenceanalysis-variantfieldselector", null, null),
                    ToolParameterDescriptor.create("genotypeFields", "Genotype Fields", "Choose the genotype fields to display.  These should match fields available in the VCF, which will vary file to file.  These will create one column per sample in the VCF.", "sequenceanalysis-variantfieldselector", new JSONObject(){{
                        put("mode", "genotype");
                    }}, null),
                    ToolParameterDescriptor.create("createOutputFile", "Publish Output", "If checked, the resulting table will be imported as an output, meaning that it is slightly more visible and will appear in the listing of other pipeline files, such as the VCF(s) you generate.  No matter what you select the table will be generated and downloadable.  The primary purpose for this option is to avoid polluting the table with files that are only needed for a short period of time.", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, false),
                    ToolParameterDescriptor.create("gzipOutput", "GZip Output", "If checked, the resulting table will be compressed to save space.", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, false),
                    ToolParameterDescriptor.create("outputFileDescription", "Table Description", "If publish output is checked, this will be used as the description for the created file.  It can help identify this in the future.", "textarea", new JSONObject(){{
                        put("width", 800);
                    }}, "")
            ), PageFlowUtil.set("sequenceanalysis/field/VariantFieldSelector.js"), "https://software.broadinstitute.org/gatk/gatkdocs/org_broadinstitute_gatk_tools_walkers_variantutils_VariantsToTable.php");
        }

        public VariantsToTableStep create(PipelineContext ctx)
        {
            return new VariantsToTableStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        List<String> args = new ArrayList<>();
        args.addAll(getClientCommandArgs());

        //site fields
        String fieldText = StringUtils.trimToNull(getProvider().getParameterByName("fields").extractValue(getPipelineCtx().getJob(), getProvider(), String.class));
        if (fieldText != null)
        {
            String[] fields = fieldText.split("\n");
            for (String field : fields)
            {
                field = StringUtils.trimToNull(field);
                if (field != null)
                {
                    args.add("-F");
                    args.add(field);
                }
            }
        }

        //genotype fields
        String genotypeFieldText = StringUtils.trimToNull(getProvider().getParameterByName("genotypeFields").extractValue(getPipelineCtx().getJob(), getProvider(), String.class));
        if (genotypeFieldText != null)
        {
            String[] fields = genotypeFieldText.split("\n");
            for (String field : fields)
            {
                field = StringUtils.trimToNull(field);
                if (field != null)
                {
                    args.add("-GF");
                    args.add(field);
                }
            }
        }

        File outputFile = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".txt");
        getWrapper().generateTable(inputVCF, outputFile, genome.getWorkingFastaFile(), args);

        if (getProvider().getParameterByName("gzipOutput").extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, false))
        {
            getPipelineCtx().getLogger().info("compressing output: " + outputFile.getName());
            File outputGz = Compress.compressGzip(outputFile);
            if (outputFile.exists())
            {
                outputFile.delete();
            }

            outputFile = outputGz;
        }

        output.addOutput(outputFile, "Variant Table");

        boolean createOutputFile = getProvider().getParameterByName("createOutputFile").extractValue(getPipelineCtx().getJob(), getProvider(), Boolean.class, false);
        getPipelineCtx().getLogger().debug("output file: [" + createOutputFile + "]");
        if (createOutputFile)
        {
            String description = getProvider().getParameterByName("outputFileDescription").extractValue(getPipelineCtx().getJob(), getProvider(), String.class);
            output.addSequenceOutput(outputFile, outputFile.getName(), "Table of Variants", null, null, genome.getGenomeId(), description);
        }
        else
        {
            getPipelineCtx().getLogger().debug("no output file will be created");
        }

        return output;
    }

    public static class Wrapper extends AbstractGatkWrapper
    {
        public Wrapper(Logger log)
        {
            super(log);
        }

        public void generateTable(File inputVcf, File outputFile, File referenceFasta, List<String> arguments) throws PipelineJobException
        {
            getLogger().info("Running GATK VariantsToTable");

            ensureDictionary(referenceFasta);

            List<String> args = new ArrayList<>();
            args.add(SequencePipelineService.get().getJavaFilepath());
            args.addAll(SequencePipelineService.get().getJavaOpts());
            args.add("-jar");
            args.add(getJAR().getPath());
            args.add("-T");
            args.add("VariantsToTable");
            args.add("-R");
            args.add(referenceFasta.getPath());
            args.add("-V");
            args.add(inputVcf.getPath());
            args.add("-o");
            args.add(outputFile.getPath());

            if (arguments != null)
            {
                args.addAll(arguments);
            }

            execute(args);

            if (!outputFile.exists())
            {
                throw new PipelineJobException("Output not found: " + outputFile.getPath());
            }
        }
    }
}
