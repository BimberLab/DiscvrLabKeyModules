package org.labkey.sequenceanalysis.run.preprocessing;

import htsjdk.samtools.util.FastqQualityFormat;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepCtx;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.sequenceanalysis.model.AdapterModel;
import org.labkey.sequenceanalysis.pipeline.IlluminaFastqSplitter;
import org.labkey.sequenceanalysis.pipeline.IlluminaReadHeader;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * User: bimber
 * Date: 10/28/12
 * Time: 11:06 PM
 */
public class TrimmomaticWrapper extends AbstractCommandWrapper
{
    private Double _threshold = null;
    private static final String MIN_PCT_RETAINED = "minPctRetained";

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

        public List<String> getAdditionalParams(PipelineContext ctx, PreprocessingOutputImpl output, int stepIdx) throws PipelineJobException
        {
            List<String> params = new ArrayList<>();
            for (ToolParameterDescriptor desc : getParameters())
            {
                if (desc.getName().equals(MIN_PCT_RETAINED))
                {
                    continue;
                }

                params.add(desc.extractValue(ctx.getJob(), this, stepIdx));
            }

            return Collections.singletonList(_stepName + ":" + StringUtils.join(params, ":"));
        }

        public Double getThreshold(PipelineContext ctx, int stepIdx)
        {
            return getParameterByName(MIN_PCT_RETAINED).extractValue(ctx.getJob(), this, stepIdx, Double.class);
        }

        @Override
        public PipelineStepProvider<StepType> combineSteps(int existingStepIdx, PipelineStepCtx toCombine)
        {
            if (toCombine.getProvider() instanceof AbstractTrimmomaticProvider)
            {
                MultiStepTrimmomaticProvider multi = new MultiStepTrimmomaticProvider();
                multi.addProvider(this, existingStepIdx);
                multi.addProvider((AbstractTrimmomaticProvider)toCombine.getProvider(), toCombine.getStepIdx());

                return multi;
            }

            return null;
        }

        @Override
        public TrimmomaticPipelineStep create(PipelineContext context)
        {
            return new TrimmomaticPipelineStep(this, context);
        }
    }

    //NOTE: for internal use only, this should not be registered
    public static class MultiStepTrimmomaticProvider extends AbstractTrimmomaticProvider<PreprocessingStep>
    {
        private List<Pair<AbstractTrimmomaticProvider, Integer>> _providers = new ArrayList<>();
        public static final String NAME = "Trimmomatic";

        public MultiStepTrimmomaticProvider()
        {
            super(NAME, NAME, NAME, "Combined Trimmomatic Steps", null, null);
        }

        public void addProvider(AbstractTrimmomaticProvider provider, int stepIdx)
        {
            _providers.add(Pair.of(provider, stepIdx));
        }

        @Override
        public String getName()
        {
            return NAME;
        }

        @Override
        public List<String> getAdditionalParams(PipelineContext ctx, PreprocessingOutputImpl output, int stepIdx) throws PipelineJobException
        {

            List<String> additionalParams = new ArrayList<>();
            for (Pair<AbstractTrimmomaticProvider, Integer> provider : _providers)
            {
                additionalParams.addAll(provider.first.getAdditionalParams(ctx, output, provider.second));
            }

            return additionalParams;
        }


        @Override
        public Double getThreshold(PipelineContext ctx, int stepIdx)
        {
            Double ret = 0.0;
            for (Pair<AbstractTrimmomaticProvider, Integer> provider : _providers)
            {
                Double val = provider.first.getParameterByName(MIN_PCT_RETAINED).extractValue(ctx.getJob(), this, stepIdx, Double.class);
                if (val != null && val > ret)
                {
                    ret = val;
                }
            }

            return ret;
        }

        @Override
        public PipelineStepProvider combineSteps(int existingStepIdx, PipelineStepCtx toCombine)
        {
            if (toCombine.getProvider() instanceof AbstractTrimmomaticProvider)
            {
                this.addProvider((AbstractTrimmomaticProvider)toCombine.getProvider(), toCombine.getStepIdx());

                return this;
            }

            return null;
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
            List<String> trimmomaticParams = getWrapper().getTrimmomaticParams(input, input2, provider.getName(), provider.getAdditionalParams(getPipelineCtx(), output, getStepIdx()));

            Double threshold = provider.getThreshold(getPipelineCtx(), getStepIdx());
            if (threshold != null && threshold > 0.0)
            {
                getWrapper().setThreshold(threshold);
            }

            getWrapper().doTrim(trimmomaticParams);
            output.addCommandExecuted(StringUtils.join(trimmomaticParams, " "));

            List<File> files = getWrapper().getExpectedOutputFilenames(input, input2, provider.getName());
            for (File f : files)
            {
                if (!f.exists())
                {
                    throw new PipelineJobException("Output file could not be found: " + f.getPath());
                }
                else if (!SequenceUtil.hasLineCount(f))
                {
                    getPipelineCtx().getLogger().info("\tdeleting empty file: " + f.getName());
                    f.delete();
                }
                else
                {
                    output.addIntermediateFile(f);
                }
            }

            //a single input file results in only 1 output
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

            output.addCommandsExecuted(getWrapper().getCommandsExecuted());

            return output;
        }
    }

    public void doTrim(List<String> params) throws PipelineJobException
    {
        ProcessBuilder pb = getProcessBuilder(params);
        getLogger().info(StringUtils.join(params, " "));
        pb.redirectErrorStream(false);
        Process p = null;
        try
        {
            p = pb.start();
            List<Double> results = new ArrayList<>();
            generateSummaryText(p, results);
            try (BufferedReader procReader = new BufferedReader(new InputStreamReader(p.getErrorStream(), StringUtilsLabKey.DEFAULT_CHARSET)))
            {
                String line;
                while ((line = procReader.readLine()) != null)
                {
                    getLogger().debug("\t" + line);
                }

                int lastReturnCode = p.waitFor();
                if (lastReturnCode != 0)
                {
                    throw new PipelineJobException("process exited with non-zero value: " + lastReturnCode);
                }
            }

            if (_threshold != null && !results.isEmpty())
            {
                Double pctRetained = results.get(0);
                if (pctRetained < _threshold)
                {
                    throw new PipelineJobException("The resulting file had few than " + NumberFormat.getPercentInstance().format(_threshold) + " percent of reads remaining, was: " + NumberFormat.getPercentInstance().format(pctRetained));
                }
            }
        }
        catch (IOException | InterruptedException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            if (p != null)
            {
                p.destroy();
            }
        }
    }

    public void setThreshold(Double threshold)
    {
        _threshold = threshold;
    }

    public static class ReadLengthFilterProvider extends AbstractTrimmomaticProvider<PreprocessingStep>
    {
        public ReadLengthFilterProvider()
        {
            super("MINLEN", "ReadLengthFilter", "Read Length Filter", "If selected, any reads shorter than this value will be discarded from analysis", Arrays.asList(
                    ToolParameterDescriptor.create("minLength", "Minimum Read Length", "Reads shorter than this value will be discarded", "ldk-integerfield", null, null),
                    getMinReadsParam()
            ), null);
        }
    }

    public static class AdapterTrimmingProvider extends AbstractTrimmomaticProvider<PreprocessingStep>
    {
        public AdapterTrimmingProvider()
        {
            super("ILLUMINACLIP", "IlluminaAdapterTrimming", "Adapter Clipping (Trimmomatic)", "This provides the ability to trim adapters from the 5' or 3' ends of reads (typically resulting from read-through).", Arrays.asList(
                    ToolParameterDescriptor.create("adapters", "Adapters", "", "sequenceanalysis-adapterpanel", new JSONObject()
                    {{
                        put("canSpecifyEnd", false);
                        }}, null),
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
                        }}, 30),
                    getMinReadsParam()
            ), Collections.singleton("SequenceAnalysis/panel/AdapterPanel.js"));
        }

        @Override
        public List<String> getAdditionalParams(PipelineContext ctx, PreprocessingOutputImpl output, int stepIdx) throws PipelineJobException
        {
            List<String> params = new ArrayList<>();

            //adaptersFilePath:
            File adapterFasta = createAdapterFasta(ctx);
            output.addIntermediateFile(adapterFasta, PreprocessingOutputImpl.FASTQ_PROCESSING_OUTPUT_ROLE);

            params.add(adapterFasta.getPath());

            for (String name : Arrays.asList("seedMismatches", "palindromeClipThreshold", "simpleClipThreshold"))
            {
                params.add(getParameterByName(name).extractValue(ctx.getJob(), this, stepIdx));
            }

            return Collections.singletonList(_stepName + ":" + StringUtils.join(params, ":"));
        }

        private File createAdapterFasta(PipelineContext ctx) throws PipelineJobException
        {
            File outputFile = new File(ctx.getWorkingDirectory(), "adapters.fasta");
            if (!outputFile.exists())
            {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StringUtilsLabKey.DEFAULT_CHARSET)))
                {
                    List<AdapterModel> adapters = getAdapters(ctx.getJob(), getName());
                    if (adapters == null || adapters.isEmpty())
                    {
                        throw new PipelineJobException("No adapters provided");
                    }

                    for (AdapterModel ad : adapters)
                    {
                        ad.setTrim3(false); //ILLUMINACLIP should handle this for us
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

        @Nullable
        public static List<AdapterModel> getAdapters(PipelineJob job, String providerName)
        {
            List<AdapterModel> adapters = new ArrayList<>();
            List<JSONArray> rawData = getAdapterInfo(job, providerName);
            if (rawData == null)
            {
                return null;
            }

            for (JSONArray adapter : rawData)
            {
                adapters.add(AdapterModel.fromJSON(adapter));
            }

            return adapters;
        }

        @Nullable
        private static List<JSONArray> getAdapterInfo(PipelineJob job, String providerName)
        {
            List<JSONArray> adapters = new ArrayList<>();
            if (job.getParameters().containsKey("fastqProcessing." + providerName + ".adapters"))
            {
                if (job.getParameters().get("fastqProcessing." + providerName + ".adapters") == null || job.getParameters().get("fastqProcessing." + providerName + ".adapters").isEmpty())
                {
                    return null;
                }

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
                        }}, 15),
                    getMinReadsParam()
            ), null);
        }
    }

    private static ToolParameterDescriptor getMinReadsParam()
    {
        return ToolParameterDescriptor.create(MIN_PCT_RETAINED, "Min Pct Retained", "If fewer than this fraction of reads remain after trimming, an error will be thrown.", "ldk-numberfield", new JSONObject()
        {{
            put("minValue", 0);
            put("maxValue", 1);
        }}, 0.75);
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
                    }}, 0.9),
                    getMinReadsParam()
            ), null);
        }
    }

    public static class AvgQualProvider extends AbstractTrimmomaticProvider<PreprocessingStep>
    {
        public AvgQualProvider()
        {
            super("AVGQUAL", "AvgQualFilter", "Average Quality Filter", "This step will discard any reads where the average of all base qualities is below the provided threshold.", Arrays.asList(
                    ToolParameterDescriptor.create("avgqual", "Avg. Qual", "Any read where the average quality of all bases is below this threshold will be discarded.", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                        put("allowBlank", false);
                    }}, 20)
            ), null);
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
                        }}, 250),
                    getMinReadsParam()
            ), null);
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
                        }}, null),
                    getMinReadsParam()
            ), null);
        }
    }

    private File getTrimlog(File workingDir)
    {
        return new File(workingDir, "trimLog.txt");
    }

    public List<String> getTrimmomaticParams(File input, @Nullable File input2, String actionName, List<String> additionalParams) throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add(SequencePipelineService.get().getJavaFilepath());
        params.addAll(SequencePipelineService.get().getJavaOpts());

        params.add("-jar");
        params.add(getJar().getPath());
        params.add((input2 != null ? "PE" : "SE"));
        params.add("-trimlog");

        //TODO: consider testing OS?
        params.add("/dev/stdout");

        FastqQualityFormat encoding = FastqUtils.inferFastqEncoding(input);
        if (encoding != null)
        {
            params.add("-phred" +  FastqUtils.getQualityOffset(encoding));
        }

        Integer threads = SequenceTaskHelper.getMaxThreads(getLogger());
        if (threads != null)
        {
            threads = Math.max(1, threads - 1); //account for reading of log
            params.add("-threads");
            params.add(threads.toString());
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
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("TRIMMOMATICPATH");
        if (path == null)
        {
            path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        }

        if (path == null)
        {
            path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        }

        return path == null ? new File("trimmomatic.jar") : new File(path, "trimmomatic.jar");
    }

    public List<File> getExpectedOutputFilenames(File input1, @Nullable File input2, String actionName)
    {
        List<File> fileNames = new ArrayList<>();
        String basename = SequenceTaskHelper.getUnzippedBaseName(input1);
        String extension = FileUtil.getExtension(input1).endsWith("gz") ? ".fastq.gz" : ".fastq";

        if (input2 != null)
        {
            String basename2 = SequenceTaskHelper.getUnzippedBaseName(input2);
            if (basename2.equalsIgnoreCase(basename))
                basename2 = basename2 + "-2";

            File workingDir = getOutputDir(input1);
            File outputPaired1 = new File(workingDir, basename + "." + actionName + extension);
            File outputUnpaired1 = new File(workingDir, basename + "." + actionName + ".unpaired1" + extension);
            File outputPaired2 = new File(workingDir, basename2 + "." + actionName + extension);
            File outputUnpaired2 = new File(workingDir, basename2 + "." + actionName + ".unpaired2" + extension);
            fileNames.add(outputPaired1);
            fileNames.add(outputUnpaired1);
            fileNames.add(outputPaired2);
            fileNames.add(outputUnpaired2);
        }
        else
        {
            File output1 = new File(getOutputDir(input1), basename + "." + actionName + extension);
            fileNames.add(output1);
        }

        return fileNames;
    }

    private void generateSummaryText(final Process p, final List<Double> results) throws PipelineJobException
    {
        getLogger().debug("generating trimmomatic summary stats based on stdout");
        JobRunner.getDefault().execute(new Runnable()
        {
            @Override
            public void run()
            {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StringUtilsLabKey.DEFAULT_CHARSET)))
                {
                    long totalInspected = 0;
                    long totalInspectedF = 0;
                    long totalInspectedR = 0;
                    long totalReadsRetained = 0;
                    long totalLength = 0;

                    long totalReadsTrimmed = 0;
                    long totalBasesTrimmed = 0;
                    long totalDiscarded = 0;

                    long totalReadsTrimmedF = 0;
                    long totalBasesTrimmedF = 0;
                    long totalDiscardedF = 0;
                    long totalReadsRetainedF = 0;
                    long totalLengthF = 0;
                    long totalReadsTrimmedR = 0;
                    long totalBasesTrimmedR = 0;
                    long totalDiscardedR = 0;
                    long totalReadsRetainedR = 0;
                    long totalLengthR = 0;
                    long unknownOrientation = 0;
                    boolean haveReportedInvalidHeader = false;

                    String line;
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
                        //attempt to parse illumina
                        IlluminaReadHeader header = IlluminaFastqSplitter.parseHeader(name);
                        if (!haveReportedInvalidHeader && header == null)
                        {
                            getLogger().info("header does not match expected format: " + name);
                            haveReportedInvalidHeader = true;
                        }

                        if ((header != null && header.getPairNumber() == 1 ) || name.endsWith("/1") || header == null)
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
                            totalInspectedF++;
                            if (basesTrimmed > 0)
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
                            totalInspectedR++;
                            if (basesTrimmed > 0)
                                totalReadsTrimmedR++;
                        }
                        else
                        {
                            unknownOrientation++;
                            if (!haveReportedInvalidHeader)
                            {
                                getLogger().info("unable to determine if read is forward or reverse: " + name);
                                haveReportedInvalidHeader = true;
                            }
                        }
                    }

                    Double avgBasesTrimmed = totalReadsTrimmed == 0 ? 0 : (double)totalBasesTrimmed / (double)totalReadsTrimmed;
                    Double avgReadLength = totalReadsTrimmed == 0 ? 0 : (double)totalLength / (double)totalReadsRetained;
                    Double pctRetained =(double)totalReadsRetained / totalInspected;
                    results.add(pctRetained);
                    StringBuilder summary = new StringBuilder();
                    summary.append("Trimming summary:\n");
                    summary.append("\tTotal reads inspected: " + NumberFormat.getInstance().format(totalInspected) + "\n");
                    summary.append("\tTotal reads discarded: " + NumberFormat.getInstance().format(totalDiscarded) + "\n");
                    summary.append("\tPercent reads retained: " + NumberFormat.getPercentInstance().format(pctRetained) + "\n");
                    summary.append("\tTotal reads trimmed (includes discarded): " +  NumberFormat.getInstance().format(totalReadsTrimmed) + "\n");
                    summary.append("\tAvg bases trimmed: " +  avgBasesTrimmed + "\n");
                    summary.append("\tTotal reads remaining: " + NumberFormat.getInstance().format(totalReadsRetained) + "\n");
                    summary.append("\tAvg read length after trimming (excludes discarded reads): " + avgReadLength + "\n");
                    if (unknownOrientation > 0)
                    {
                        summary.append("\tReads inspected with unknown orientation: " + unknownOrientation + "\n");
                    }

                    if (totalInspectedF > 0)
                    {
                        Double avgBasesTrimmedF = totalBasesTrimmedF == 0 ? 0 : (double)totalBasesTrimmedF / (double)totalReadsTrimmedF;
                        Double avgReadLengthF = (double)totalLengthF / (double)totalReadsRetainedF;
                        Double pctRetainedF =(double)totalReadsRetainedF / totalInspectedF;
                        summary.append("Forward read trimming summary: " + "\n");
                        summary.append("\tTotal forward reads inspected: " + NumberFormat.getInstance().format(totalInspectedF) + "\n");
                        summary.append("\tTotal forward reads discarded: " + NumberFormat.getInstance().format(totalDiscardedF) + "\n");
                        summary.append("\tPercent reads retained: " + NumberFormat.getPercentInstance().format(pctRetainedF) + "\n");
                        summary.append("\tTotal forward reads trimmed (includes discarded): " +  NumberFormat.getInstance().format(totalReadsTrimmedF) + "\n");
                        summary.append("\tAvg bases trimmed from forward reads: " +  NumberFormat.getInstance().format(avgBasesTrimmedF) + "\n");
                        summary.append("\tTotal forward reads remaining: " + NumberFormat.getInstance().format(totalReadsRetainedF) + "\n");
                        summary.append("\tAvg forward read length after trimming (excludes discarded reads): " + NumberFormat.getInstance().format(avgReadLengthF) + "\n");
                    }

                    if (totalInspectedR > 0)
                    {
                        Double avgBasesTrimmedR = totalBasesTrimmedR == 0 ? 0 : (double)totalBasesTrimmedR / (double)totalReadsTrimmedR;
                        Double avgReadLengthR = (double)totalLengthR / (double)totalReadsRetainedR;
                        Double pctRetainedR =(double)totalReadsRetainedR / totalInspectedR;
                        summary.append("Reverse read trimming summary: " + "\n");
                        summary.append("\tTotal reverse reads inspected: " + NumberFormat.getInstance().format(totalInspectedR) + "\n");
                        summary.append("\tTotal reverse reads discarded: " + NumberFormat.getInstance().format(totalDiscardedR) + "\n");
                        summary.append("\tPercent reads retained: " + NumberFormat.getPercentInstance().format(pctRetainedR) + "\n");
                        summary.append("\tTotal reverse reads trimmed (includes discarded): " +  NumberFormat.getInstance().format(totalReadsTrimmedR) + "\n");
                        summary.append("\tAvg bases trimmed from reverse reads: " +  NumberFormat.getInstance().format(avgBasesTrimmedR) + "\n");
                        summary.append("\tTotal reverse reads remaining: " + NumberFormat.getInstance().format(totalReadsRetainedR) + "\n");
                        summary.append("\tAvg reverse read length after trimming (excludes discarded reads): " + NumberFormat.getInstance().format(avgReadLengthR) + "\n");
                    }

                    getLogger().info(summary.toString());
                }
                catch (NumberFormatException | IOException e)
                {
                    getLogger().error(e);
                }
            }
        });
    }
}
