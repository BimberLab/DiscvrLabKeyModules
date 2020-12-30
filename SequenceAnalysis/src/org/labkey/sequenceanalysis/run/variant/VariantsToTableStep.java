package org.labkey.sequenceanalysis.run.variant;

import htsjdk.samtools.util.Interval;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;
import org.labkey.api.util.Compress;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.pipeline.VariantProcessingJob;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("-EMD"), "emd", "Error on Missing Data", "If checked, the tool will error is any variants are missing the requested fields", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, false),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--show-filtered"), "showFiltered", "Show Filtered Data", "If checked, rows that are filtered will be included", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, false),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--split-multi-allelic"), "splitMultiAllelic", "Split Multi Allelic", "If checked, sites with multiple alleles will be split into separate rows in the output table", "checkbox", new JSONObject(){{
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
                    }}, ""),
                    ToolParameterDescriptor.create("intervals", "Intervals", "The intervals over which to merge the data.  They should be in the form: chr01:102-20394", "sequenceanalysis-intervalfield", null, null)
            ), PageFlowUtil.set("sequenceanalysis/field/VariantFieldSelector.js", "/sequenceanalysis/field/IntervalField.js"), "https://software.broadinstitute.org/gatk/gatkdocs/org_broadinstitute_gatk_tools_walkers_variantutils_VariantsToTable.php");
        }

        public VariantsToTableStep create(PipelineContext ctx)
        {
            return new VariantsToTableStep(this, ctx);
        }
    }

    @Override
    public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {
        String intervalText = StringUtils.trimToNull(getProvider().getParameterByName("intervals").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class));
        if (intervalText != null)
        {
            if (job instanceof VariantProcessingJob && ((VariantProcessingJob)job).isScatterJob())
            {
                throw new PipelineJobException("Splitting jobs per chromosome is not supported when custom intervals are provided for VariantsToTable");
            }
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        List<String> args = new ArrayList<>();
        args.addAll(getClientCommandArgs());

        //site fields
        String fieldText = StringUtils.trimToNull(getProvider().getParameterByName("fields").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class));
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
        String genotypeFieldText = StringUtils.trimToNull(getProvider().getParameterByName("genotypeFields").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class));
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

        String intervalText = StringUtils.trimToNull(getProvider().getParameterByName("intervals").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class));
        List<Interval> il = parseAndSortIntervals(intervalText);
        if (il != null)
        {
            for (Interval i : il)
            {
                args.add("-L");
                args.add(i.getContig() + ":" + i.getStart() + "-" + i.getEnd());
            }
        }

        if (intervals != null)
        {
            throw new PipelineJobException("Splitting jobs per chromosome is not supported when custom intervals are provided");
        }

        File outputFile = new File(outputDirectory, SequenceTaskHelper.getUnzippedBaseName(inputVCF) + ".txt");
        getWrapper().generateTable(inputVCF, outputFile, genome.getWorkingFastaFile(), args);

        if (getProvider().getParameterByName("gzipOutput").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false))
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

        boolean createOutputFile = getProvider().getParameterByName("createOutputFile").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
        if (createOutputFile)
        {
            String description = getProvider().getParameterByName("outputFileDescription").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
            output.addSequenceOutput(outputFile, outputFile.getName(), "Table of Variants", null, null, genome.getGenomeId(), description);
        }
        else
        {
            getPipelineCtx().getLogger().debug("no output file will be created from VariantsToTable");
        }

        return output;
    }

    public static class Wrapper extends AbstractGatk4Wrapper
    {
        public Wrapper(Logger log)
        {
            super(log);
        }

        public void generateTable(File inputVcf, File outputFile, File referenceFasta, List<String> arguments) throws PipelineJobException
        {
            getLogger().info("Running GATK VariantsToTable");

            ensureDictionary(referenceFasta);

            List<String> args = new ArrayList<>(getBaseArgs());
            args.add("VariantsToTable");
            args.add("-R");
            args.add(referenceFasta.getPath());
            args.add("-V");
            args.add(inputVcf.getPath());
            args.add("-O");
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

    private List<Interval> parseAndSortIntervals(String intervalString) throws PipelineJobException
    {
        intervalString = StringUtils.trimToNull(intervalString);
        if (intervalString == null)
        {
            return null;
        }

        intervalString = intervalString.replaceAll("(\\n|\\r|;)+", ";");
        List<Interval> intervals = new ArrayList<>();
        for (String i : intervalString.split(";"))
        {
            String[] tokens = i.split(":|-");
            if (tokens.length != 3)
            {
                throw new PipelineJobException("Invalid interval: " + i);
            }

            intervals.add(new Interval(tokens[0], Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2])));
        }


        Collections.sort(intervals);

        return intervals;
    }
}
