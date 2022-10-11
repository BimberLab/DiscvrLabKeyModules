package org.labkey.sequenceanalysis.run.variant;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.json.old.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlinkPcaStep extends AbstractCommandPipelineStep<PlinkPcaStep.PlinkWrapper> implements VariantProcessingStep
{
    public PlinkPcaStep(PipelineStepProvider<?> provider, PipelineContext ctx)
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
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--keep-autoconv"), "constFid", "Keep Autoconversion Products", "If checked, the plink intermediate files are temporarily retained. This might be helpful to debug failures.", "checkbox", new JSONObject(){{
                        put("checked", false);
                    }}, false),
                    ToolParameterDescriptor.create("splitByApplication", "Split by Application", "If checked, one iteration of PCA will be performed for each application (defined by the readset).", "checkbox", null, false),
                    ToolParameterDescriptor.create("allowableApplications", "Allowable Applications", "If Split By Application is used, then it will search readsets to find those where the VCF sample matches the readset name. This is an option extra filter that can be added, to limit to search to a specific set of applications.", "ldk-simplelabkeycombo", new JSONObject(){{
                        put("schemaName", "sequenceanalysis");
                        put("queryName", "sequence_applications");
                        put("multiSelect", true);
                        put("joinReturnValue", true);
                        put("displayField", "application");
                        put("valueField", "application");
                        put("sortField", "application");
                    }}, null),
                    ToolParameterDescriptor.create("allowMissingSamples", "Allow Missing Samples", "When using split by application, this controls whether or not the job should fail if a matching readset cannot be found for specific samples.", "checkbox", null, false),
                    ToolParameterDescriptor.create(SelectSamplesStep.SAMPLE_INCLUDE, "Sample(s) Include", "Only the following samples will be included in the analysis.", "sequenceanalysis-trimmingtextarea", null, null),
                    ToolParameterDescriptor.create(SelectSamplesStep.SAMPLE_EXCLUDE, "Samples(s) To Exclude", "The following samples will be excluded from the analysis.", "sequenceanalysis-trimmingtextarea", null, null)
            ), Arrays.asList("sequenceanalysis/field/TrimmingTextArea.js"), "https://zzz.bwh.harvard.edu/plink/");
        }

        @Override
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
                    writer.writeNext(new String[]{x});
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

        boolean splitByApplication = getProvider().getParameterByName("splitByApplication").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, true);
        if (splitByApplication)
        {
            Map<String, List<String>> applicationToSample = new HashMap<>();
            try (BufferedReader reader = Readers.getReader(getSampleMapFile()))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    String[] tokens = line.split("\t");
                    if (!applicationToSample.containsKey(tokens[1]))
                    {
                        applicationToSample.put(tokens[1], new ArrayList<>());
                    }

                    applicationToSample.get(tokens[1]).add(tokens[0]);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            for (String application : applicationToSample.keySet())
            {
                getPipelineCtx().getLogger().info("Running PCA for: " + application);
                runBatch(inputVCF, outputDirectory, output, genome, applicationToSample.get(application), application);
            }
        }
        else
        {
            runBatch(inputVCF, outputDirectory, output, genome, null, null);
        }

        output.addInput(inputVCF, "Input VCF");
        output.addInput(genome.getWorkingFastaFile(), "Reference Genome");

        return output;
    }

    private void runBatch(File inputVCF, File outputDirectory, VariantProcessingStepOutputImpl output, ReferenceGenome genome, @Nullable List<String> sampleList, @Nullable String setName) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getWrapper().getExe().getPath());
        args.add("--pca");
        args.add("--allow-extra-chr");
        args.add("--const-fid");
        args.add("0");

        String samplesToInclude = getProvider().getParameterByName(SelectSamplesStep.SAMPLE_INCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        addSubjectSelectOptions(sampleList != null ? StringUtils.join(sampleList, ";") : samplesToInclude, args, "--keep", new File(outputDirectory, "samplesToKeep.txt"), output);

        String samplesToExclude = getProvider().getParameterByName(SelectSamplesStep.SAMPLE_EXCLUDE).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        addSubjectSelectOptions(samplesToExclude, args, "--exclude", new File(outputDirectory, "samplesToExclude.txt"), output);

        args.add("--vcf");
        args.add(inputVCF.getPath());

        File outPrefix;
        if (setName != null)
        {
            outPrefix = new File(outputDirectory, "plink." + FileUtil.makeLegalName(setName).replaceAll(" ", "_"));
        }
        else
        {
            outPrefix = new File(outputDirectory, "plink");
        }

        args.add("--out");
        args.add(outPrefix.getPath());

        // These are only written if --keep-autoconv is used:
        output.addIntermediateFile(new File(outPrefix.getPath() + ".pgen"));
        output.addIntermediateFile(new File(outPrefix.getPath() + ".pvar"));
        output.addIntermediateFile(new File(outPrefix.getPath() + ".psam"));
        output.addIntermediateFile(new File(outPrefix.getPath() + ".afreq"));
        output.addIntermediateFile(new File(outPrefix.getPath() + ".log"));

        if (SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger()) != null)
        {
            args.add("--threads");
            args.add(SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger()).toString());
        }

        args.addAll(getClientCommandArgs());

        getWrapper().execute(args);

        File outputFile = new File(outPrefix.getPath() + ".eigenvec");
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Unable to find expected file: " + outputFile);
        }

        output.addOutput(outputFile, "PLink PCA");
        output.addSequenceOutput(outputFile, "PLink PCA for: " + inputVCF.getName() + (setName == null ? "" : ", for: " + setName), "PLink PCA", null, null, genome.getGenomeId(), null);
    }

    @Override
    public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {
        boolean splitByApplication = getProvider().getParameterByName("splitByApplication").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, true);
        List<String> allowableApplications = null;
        String allowableApplicationsRaw = StringUtils.trimToNull(getProvider().getParameterByName("allowableApplications").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()));
        if (allowableApplicationsRaw != null)
        {
            allowableApplications = Arrays.asList(allowableApplicationsRaw.split(";"));
            getPipelineCtx().getLogger().debug("Will limit to the following applications: " + StringUtils.join(allowableApplications, "; "));
        }

        if (splitByApplication)
        {
            try (PrintWriter writer = PrintWriters.getPrintWriter(getSampleMapFile()))
            {
                getPipelineCtx().getLogger().info("Writing Sample Map");
                for (SequenceOutputFile so : inputFiles)
                {
                    Set<String> duplicates = new HashSet<>();
                    Set<String> missing = new HashSet<>();
                    try (VCFFileReader reader = new VCFFileReader(so.getFile()))
                    {
                        VCFHeader header = reader.getFileHeader();
                        if (header.getSampleNamesInOrder().isEmpty())
                        {
                            throw new PipelineJobException("Expected VCF to have samples: " + so.getFile().getPath());
                        }

                        for (String sample : header.getSampleNamesInOrder())
                        {
                            // Find readset:
                            Container targetContainer = getPipelineCtx().getJob().getContainer().isWorkbook() ? getPipelineCtx().getJob().getContainer().getParent() : getPipelineCtx().getJob().getContainer();
                            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("name"), sample).addCondition(FieldKey.fromString("status"), null, CompareType.ISBLANK);
                            if (allowableApplications != null)
                            {
                                filter.addCondition(FieldKey.fromString("application"), allowableApplications, CompareType.IN);
                            }

                            Set<String> applications = new HashSet<>(new TableSelector(QueryService.get().getUserSchema(getPipelineCtx().getJob().getUser(), targetContainer, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_READSETS), PageFlowUtil.set("application"), filter, null).getArrayList(String.class));
                            if (applications.size() == 1)
                            {
                                writer.println(sample + "\t" + applications.iterator().next());
                            }
                            else if (applications.size() > 1)
                            {
                                duplicates.add(sample);
                            }
                            else
                            {
                                missing.add(sample);
                            }
                        }
                    }

                    if (!duplicates.isEmpty())
                    {
                        throw new PipelineJobException("More than one readset with the given name found for the following samples: " + StringUtils.join(duplicates, ","));
                    }

                    if (!missing.isEmpty())
                    {
                        String msg = "No matching readsets with the given name found for the following samples: " + StringUtils.join(missing, ",");
                        boolean allowMissingSamples = getProvider().getParameterByName("allowMissingSamples").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
                        if (allowMissingSamples)
                        {
                            getPipelineCtx().getLogger().warn(msg);
                        }
                        else
                        {
                            throw new PipelineJobException(msg);
                        }
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    private File getSampleMapFile()
    {
        return new File(getPipelineCtx().getJob().isSplitJob() ? getPipelineCtx().getSourceDirectory().getParentFile() : getPipelineCtx().getSourceDirectory(), "sampleMap.txt");
    }

    public static class PlinkWrapper extends AbstractCommandWrapper
    {
        public PlinkWrapper(@Nullable Logger logger)
        {
            super(logger);
        }

        public File getExe()
        {
            return SequencePipelineService.get().getExeForPackage("PLINK2PATH", "plink2");
        }
    }
}
