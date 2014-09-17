package org.labkey.sequenceanalysis.run.preprocessing;

import htsjdk.samtools.util.FastqQualityFormat;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequence.IlluminaReadHeader;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.PreprocessingStep;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.api.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.api.run.AbstractCommandWrapper;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.model.AdapterModel;
import org.labkey.sequenceanalysis.pipeline.IlluminaFastqSplitter;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: bimber
 * Date: 10/28/12
 * Time: 11:06 PM
 */
public class TrimmomaticWrapper extends AbstractCommandWrapper
{
    public TrimmomaticWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    abstract private static class AbstractTrimmomaticProvider<StepType extends PipelineStep> extends AbstractPipelineStepProvider<StepType>
    {
        protected String _stepName;

        public AbstractTrimmomaticProvider(String stepName, String name, String label, String description, @Nullable List<ToolParameterDescriptor> parameters, @Nullable Collection<String> clientDependencyPaths)
        {
            super(name, label, "Trimmomatic", description, parameters, clientDependencyPaths, "http://www.usadellab.org/cms/?page=trimmomatic");
            _stepName = stepName;
        }

        public String getAdditionalParams(PipelineContext ctx, PreprocessingOutputImpl output) throws PipelineJobException
        {
            List<String> params = new ArrayList<>();
            for (ToolParameterDescriptor desc : getParameters())
            {
                params.add(desc.extractValue(ctx.getJob(), this));
            }

            return _stepName + ":" + StringUtils.join(params, ":");
        }
    }

    public static class TrimmomaticPipelineStep extends AbstractCommandPipelineStep<TrimmomaticWrapper> implements PreprocessingStep
    {
        public TrimmomaticPipelineStep(PipelineStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx, new TrimmomaticWrapper(ctx.getLogger()));
        }

        @Override
        public PreprocessingOutputImpl processInputFile(File input, @Nullable File input2, File outputDir) throws PipelineJobException
        {
            PreprocessingOutputImpl output = new PreprocessingOutputImpl(input, input2);
            getWrapper().setOutputDir(outputDir);

            getPipelineCtx().getLogger().info("Using Trimmomatic to preprocess files for: " + getProvider().getLabel());
            getPipelineCtx().getLogger().info("\t" + input.getPath());
            if (input2 != null)
                getPipelineCtx().getLogger().info("\t" + input2.getPath());

            AbstractTrimmomaticProvider provider = (AbstractTrimmomaticProvider)getProvider();
            getWrapper().execute(getWrapper().getParams(input, input2, provider.getName(), Arrays.asList(provider.getAdditionalParams(getPipelineCtx(), output))));

            List<File> files = getWrapper().getExpectedOutputFilenames(input, input2, provider.getName());
            for (File f : files)
            {
                if (!f.exists())
                {
                    throw new PipelineJobException("Output file could not be found: " + f.getPath());
                }
                else if (FileUtils.sizeOf(f) == 0)
                {
                    getPipelineCtx().getLogger().info("\tdeleting empty file: " + f.getName());
                    f.delete();
                }
                else
                {
                    output.addIntermediateFile(f);
                }
            }

            //a single input file reuslts in only 1 output
            if (files.size() == 1)
            {

                if (FileUtils.sizeOf(files.get(0)) > 0)
                {
                    output.setProcessedFastq(Pair.of(files.get(0), (File) null));
                }
                else
                {
                    getPipelineCtx().getLogger().warn("File had a size of zero: " + files.get(0).getPath());
                    if (!files.get((0)).delete() || files.get(0).exists())
                        throw new PipelineJobException("File exists: " + files.get(0).getPath());
                }
            }
            else
            {
                if (FileUtils.sizeOf(files.get(0)) == 0)
                {
                    getPipelineCtx().getLogger().warn("File had a size of zero, deleting: " + files.get(0).getPath());
                    if (!files.get((0)).delete() || files.get(0).exists())
                        throw new PipelineJobException("File exists: " + files.get(0).getPath());
                }
                else if (FileUtils.sizeOf(files.get(2)) == 0)
                {
                    getPipelineCtx().getLogger().warn("File had a size of zero, deleting: " + files.get(2).getPath());
                    if (!files.get((2)).delete() || files.get(2).exists())
                        throw new PipelineJobException("File exists: " + files.get(2).getPath());
                }
                else
                {
                    output.setProcessedFastq(Pair.of(files.get(0), files.get(2)));
                }

                //TODO: outputs

            }

            if (getPipelineCtx().getLogger() != null)
            {
                getWrapper().generateSummaryText(getWrapper().getTrimlog(getWrapper().getOutputDir(input)));
            }

            return output;
        }
    }

    public static class ReadLengthFilterProvider extends AbstractTrimmomaticProvider<PreprocessingStep>
    {
        public ReadLengthFilterProvider()
        {
            super("MINLEN", "ReadLengthFilter", "Read Length Filter", "If selected, any reads shorter than this value will be discarded from analysis", Arrays.asList(
                    ToolParameterDescriptor.create("minLength", "Minimum Read Length", "Reads shorter than this value will be discarded", "ldk-integerfield", null, null)
            ), null);
        }

        public TrimmomaticPipelineStep create(PipelineContext context)
        {
            return new TrimmomaticPipelineStep(this, context);
        }
    }

    public static class AdapterTrimmingProvider extends AbstractTrimmomaticProvider<PreprocessingStep>
    {
        public AdapterTrimmingProvider()
        {
            super("ILLUMINACLIP", "IlluminaAdapterTrimming", "Illumina Adapter Clipping", "This provides the ability to trim adapters from the 3' ends of reads, typically resulting from read-through.", Arrays.asList(
                    ToolParameterDescriptor.create("adapters", "Adapters", "", "sequenceanalysis-adapterpanel", null, null),
                    ToolParameterDescriptor.create("seedMismatches", "Seed Mismatches", "The seed mismatch parameter is used to make alignments more efficient, specifying the maximum base mismatch count in the 16-base seed. Typical values here are 1 or 2.", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 2),
                    ToolParameterDescriptor.create("simpleClipThreshold", "Simple Clip Threshold", "A full description of this parameter can be found on the trimmomatic homepage.  The following is adapted from their documentation: The thresholds used are a simplified log-likelihood approach. Each matching base adds just over 0.6, while each mismatch reduces the alignment score by Q/10. Therefore, a perfect match of a 20 base sequence will score just over 12, while 25 bases are needed to score 15. As such we recommend values between 12 - 15 for this parameter.", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 12),
                    ToolParameterDescriptor.create("palindromeClipThreshold", "Palindrome Clip Threshold", "A full description of this parameter can be found on the trimmomatic homepage.  The following is adapted from their documentation: For palindromic matches, the entire read sequence plus (partial) adapter sequences can be used - therefore this threshold can be higher, in the range of 30-40", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 30)
            ), Collections.singleton("SequenceAnalysis/panel/AdapterPanel.js"));
        }

        @Override
        public String getAdditionalParams(PipelineContext ctx, PreprocessingOutputImpl output) throws PipelineJobException
        {
            List<String> params = new ArrayList<>();

            //adaptersFilePath:
            File adapterFasta = createAdapterFasta(ctx);
            output.addIntermediateFile(adapterFasta, PreprocessingOutputImpl.FASTQ_PROCESSING_OUTPUT_ROLE);

            params.add(adapterFasta.getPath());

            for (String name : Arrays.asList("seedMismatches", "palindromeClipThreshold", "simpleClipThreshold"))
            {
                params.add(getParameterByName(name).extractValue(ctx.getJob(), this));
            }

            return _stepName + ":" + StringUtils.join(params, ":");
        }

        private File createAdapterFasta(PipelineContext ctx) throws PipelineJobException
        {
            File outputFile = new File(ctx.getWorkingDirectory(), "adapters.fasta");
            if (!outputFile.exists())
            {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile)))
                {
                    for (AdapterModel ad : getAdapters(ctx.getJob(), getName()))
                    {
                        writer.write(ad.getFastaLines());
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e.getMessage());
                }
            }

            return outputFile;
        }

        public static List<AdapterModel> getAdapters(PipelineJob job, String providerName)
        {
            List<AdapterModel> adapters = new ArrayList<>();
            List<JSONArray> rawData = getAdapterInfo(job, providerName);
            for (JSONArray adapter : rawData)
            {
                adapters.add(AdapterModel.fromJSON(adapter));
            }

            return adapters;
        }

        private static List<JSONArray> getAdapterInfo(PipelineJob job, String providerName)
        {
            List<JSONArray> adapters = new ArrayList<>();
            if (job.getParameters().containsKey("fastqProcessing." + providerName + ".adapters"))
            {
                JSONArray adapterArr = new JSONArray(job.getParameters().get("fastqProcessing." + providerName + ".adapters"));
                if (adapterArr != null)
                {
                    for (int i = 0; i < adapterArr.length(); i++)
                    {

                        adapters.add(adapterArr.getJSONArray(i));
                    }
                }
            }

            return adapters;
        }

        public TrimmomaticPipelineStep create(PipelineContext context)
        {
            return new TrimmomaticPipelineStep(this, context);
        }
    }

    public static class SlidingWindowTrimmingProvider extends AbstractTrimmomaticProvider<PreprocessingStep>
    {
        public SlidingWindowTrimmingProvider()
        {
            super("SLIDINGWINDOW", "SlidingWindowTrim", "Quality Trimming (Sliding Window)", "This uses a sliding window to trim from the 3' ends.  Starting at the 3' end, the algorithm takes a window of the last X bases.  If their average quality score does not pass the specified value, the algorithm moves one base forward and repeats.  This continues until the average of the window passes the minimal quality provided.", Arrays.asList(
                    ToolParameterDescriptor.create("windowSize", "Window Size", "Bases will be inspected using a sliding window of this length", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 4),
                    ToolParameterDescriptor.create("avgQual", "Avg Qual", "The average quality score for the window that must be obtained", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 15)
            ), null);
        }

        public TrimmomaticPipelineStep create(PipelineContext context)
        {
            return new TrimmomaticPipelineStep(this, context);
        }
    }

    public static class MaxInfoTrimmingProvider extends AbstractTrimmomaticProvider<PreprocessingStep>
    {
        public MaxInfoTrimmingProvider()
        {
            super("MAXINFO", "MaxInfoTrim", "Quality Trimming (Adaptive)", "Performs an adaptive quality trim, balancing the benefits of retaining longer reads against the costs of retaining bases with errors.", Arrays.asList(
                    ToolParameterDescriptor.create("targetLength:", "Target Length", "This specifies the read length which is likely to allow the location of the read within the target sequence to be determined", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 50),
                    ToolParameterDescriptor.create("strictness", "Strictness", "This value, which should be set between 0 and 1, specifies the balance between preserving as much read length as possible vs. removal of incorrect bases. A low value of this parameter (<0.2) favours longer reads, while a high value (>0.8) favours read correctness.", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                        put("maxValue", 1);
                    }}, 0.9)
            ), null);
        }

        public TrimmomaticPipelineStep create(PipelineContext context)
        {
            return new TrimmomaticPipelineStep(this, context);
        }
    }

    public static class CropReadsProvider extends AbstractTrimmomaticProvider<PreprocessingStep>
    {
        public CropReadsProvider()
        {
            super("CROP", "CropReads", "Crop Reads", "If selected, any reads above the selected length will be cropped.  This is sometimes useful for Illumina data when read quality drops beyond a given read cycle.", Arrays.asList(
                    ToolParameterDescriptor.create("cropLength", "Crop Length", "Reads will be cropped to this length", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, 250)
            ), null);
        }

        public TrimmomaticPipelineStep create(PipelineContext context)
        {
            return new TrimmomaticPipelineStep(this, context);
        }
    }

    public static class HeadCropReadsProvider extends AbstractTrimmomaticProvider<PreprocessingStep>
    {
        public HeadCropReadsProvider()
        {
            super("HEADCROP", "HeadCropReads", "Head Crop", "The selected number of bases will be cropped from the 5' end of each read.", Arrays.asList(
                    ToolParameterDescriptor.create("headcropLength", "5' Cropping", "If provided, this will crop the specified number of bases from the 5' end of each read", "ldk-integerfield", new JSONObject()
                    {{
                            put("minValue", 0);
                        }}, null)
            ), null);
        }

        public TrimmomaticPipelineStep create(PipelineContext context)
        {
            return new TrimmomaticPipelineStep(this, context);
        }
    }

    private File getTrimlog(File workingDir)
    {
        return new File(workingDir, "trimLog.txt");
    }

    private List<String> getParams(File input, @Nullable File input2, String actionName, List<String> additionalParams) throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add("java");

        params.add("-classpath");
        params.add(getJar().getPath());
        params.add("org.usadellab.trimmomatic.Trimmomatic" + (input2 != null ? "PE" : "SE"));
        File trimLog = getTrimlog(getOutputDir(input));
        params.add("-trimlog");
        params.add(trimLog.getPath());

        FastqQualityFormat encoding = FastqUtils.inferFastqEncoding(input);
        if (encoding != null)
        {
            params.add("-phred" +  FastqUtils.getQualityOffset(encoding));
        }

        params.add(input.getPath());
        if (input2 != null)
        {
            params.add(input2.getPath());
        }

        List<File> files = getExpectedOutputFilenames(input, input2, actionName);
        for (File f : files)
        {
            params.add(f.getPath());
        }

        for (String additionalParam : additionalParams)
        {
            params.add(additionalParam);
        }

        return params;
    }

    private File getJar()
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("PICARDPATH");
        if (path != null)
        {
            return new File(path);
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        return path == null ? new File("trimmomatic.jar") : new File(path, "trimmomatic.jar");
    }

    private List<File> getExpectedOutputFilenames(File input1, @Nullable File input2, String actionName)
    {
        List<File> fileNames = new ArrayList<>();
        String basename = FileUtil.getBaseName(input1);

        if (input2 != null)
        {
            String basename2 = FileUtil.getBaseName(input2);
            if (basename2.equalsIgnoreCase(basename))
                basename2 = basename2 + "-2";

            File workingDir = getOutputDir(input1);
            File outputPaired1 = new File(workingDir, basename + "." + actionName + ".fastq");
            File outputUnpaired1 = new File(workingDir, basename + "." + actionName + ".unpaired1.fastq");
            File outputPaired2 = new File(workingDir, basename2 + "." + actionName + ".fastq");
            File outputUnpaired2 = new File(workingDir, basename2 + "." + actionName + ".unpaired2.fastq");
            fileNames.add(outputPaired1);
            fileNames.add(outputUnpaired1);
            fileNames.add(outputPaired2);
            fileNames.add(outputUnpaired2);
        }
        else
        {
            File output1 = new File(input1.getParentFile(), basename + "." + actionName + ".fastq");
            fileNames.add(output1);
        }

        return fileNames;
    }

    private void generateSummaryText(File logFile) throws PipelineJobException
    {
        if (!logFile.exists())
        {
            getLogger().warn("Did not find expected logFile: " + logFile.getPath());
        }

        String line = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile)))
        {
            int totalInspected = 0;
            int totalReadsRetained = 0;
            int totalLength = 0;

            int totalReadsTrimmed = 0;
            int totalBasesTrimmed = 0;
            int totalDiscarded = 0;

            int totalReadsTrimmedF = 0;
            int totalBasesTrimmedF = 0;
            int totalDiscardedF = 0;
            int totalReadsRetainedF = 0;
            int totalLengthF = 0;
            int totalReadsTrimmedR = 0;
            int totalBasesTrimmedR = 0;
            int totalDiscardedR = 0;
            int totalReadsRetainedR = 0;
            int totalLengthR = 0;

            while ((line = reader.readLine()) != null)
            {
                String[] cells = line.split(" ");
                if (cells.length < 4)
                {
                    getLogger().error("log line too short: [" + line + "]");
                    continue;
                }

                //NOTE: if the readname has spaces, we need to rebuild it after the split
                StringBuilder nameBuilder = new StringBuilder();
                int i = 0;
                String delim = "";
                while (i < (cells.length - 4))
                {
                    nameBuilder.append(delim).append(cells[i]);
                    delim = " ";
                    i++;
                }
                String name = nameBuilder.toString();

                Integer survivingLength = Integer.parseInt(cells[cells.length - 4]);
                Integer basesTrimmed = Integer.parseInt(cells[cells.length - 1]);

                totalInspected++;
                if (survivingLength == 0)
                {
                    totalDiscarded ++;
                }
                else
                {
                    totalReadsRetained++;
                    totalLength += survivingLength;
                }

                if (basesTrimmed > 0)
                {
                    totalBasesTrimmed += basesTrimmed;
                    totalReadsTrimmed++;
                }

                //infer metrics for paired end data
                //aatempt to parse illumina
                IlluminaReadHeader header = IlluminaFastqSplitter.parseHeader(name);
                if ((header != null && header.getPairNumber() == 1 ) || name.endsWith("/1"))
                {
                    if (survivingLength == 0)
                    {
                        totalDiscardedF++;
                    }
                    else
                    {
                        totalLengthF += survivingLength;
                        totalReadsRetainedF++;
                    }

                    totalBasesTrimmedF += basesTrimmed;
                    totalReadsTrimmedF++;
                }
                else if ((header != null && header.getPairNumber() == 2) || name.endsWith("/2"))
                {
                    if (survivingLength == 0)
                    {
                        totalDiscardedR++;
                    }
                    else
                    {
                        totalLengthR += survivingLength;
                        totalReadsRetainedR++;
                    }

                    totalBasesTrimmedR += basesTrimmed;
                    totalReadsTrimmedR++;
                }
            }

            Double avgBasesTrimmed = totalReadsTrimmed == 0 ? 0 : (double)totalBasesTrimmed / (double)totalReadsTrimmed;
            Double avgReadLength = totalReadsTrimmed == 0 ? 0 : (double)totalLength / (double)totalReadsRetained;
            getLogger().info("Trimming summary:");
            getLogger().info("\tTotal reads inspected: " + totalInspected);
            getLogger().info("\tTotal reads discarded: " + totalDiscarded);
            getLogger().info("\tTotal reads trimmed (includes discarded): " +  totalReadsTrimmed);
            getLogger().info("\tAvg bases trimmed: " +  avgBasesTrimmed);
            getLogger().info("\tTotal reads remaining: " + totalReadsRetained);
            getLogger().info("\tAvg read length after trimming (excludes discarded reads): " + avgReadLength);

            if (totalBasesTrimmedF > 0)
            {
                Double avgBasesTrimmedF = (double)totalBasesTrimmedF / (double)totalReadsTrimmedF;
                Double avgReadLengthF = (double)totalLengthF / (double)totalReadsRetainedF;
                getLogger().info("Forward read trimming summary: ");
                getLogger().info("\tTotal forward reads discarded: " + totalDiscardedF);
                getLogger().info("\tTotal forward reads trimmed (includes discarded): " +  totalReadsTrimmedF);
                getLogger().info("\tAvg bases trimmed from forward reads: " +  avgBasesTrimmedF);
                getLogger().info("\tTotal forward reads remaining: " + totalReadsRetainedF);
                getLogger().info("\tAvg forward read length after trimming (excludes discarded reads): " + avgReadLengthF);
            }

            if (totalBasesTrimmedR > 0)
            {
                Double avgBasesTrimmedR = (double)totalBasesTrimmedR / (double)totalReadsTrimmedR;
                Double avgReadLengthR = (double)totalLengthR / (double)totalReadsRetainedR;
                getLogger().info("Reverse read trimming summary: ");
                getLogger().info("\tTotal reverse reads discarded: " + totalDiscardedR);
                getLogger().info("\tTotal reverse reads trimmed (includes discarded): " +  totalReadsTrimmedR);
                getLogger().info("\tAvg bases trimmed from reverse reads: " +  avgBasesTrimmedR);
                getLogger().info("\tTotal reverse reads remaining: " + totalReadsRetainedR);
                getLogger().info("\tAvg reverse read length after trimming (excludes discarded reads): " + avgReadLengthR);
            }

            //delete on success
            logFile.delete();
        }
        catch (NumberFormatException | IOException e)
        {
            getLogger().error("Problem with line: [" + line + "]");
            throw new PipelineJobException(e);
        }
    }
}
