package org.labkey.sequenceanalysis.run.preprocessing;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.PreprocessingStep;
import org.labkey.sequenceanalysis.api.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.api.run.AbstractCommandWrapper;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.run.util.DownsampleSamWrapper;
import org.labkey.sequenceanalysis.run.util.FastqToSamWrapper;
import org.labkey.sequenceanalysis.run.util.SamToFastqWrapper;
import org.labkey.sequenceanalysis.util.FastqUtils;

import java.io.File;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 6/14/2014
 * Time: 3:39 PM
 */
public class DownsampleFastqWrapper extends AbstractCommandWrapper
{
    private static String DownsampleReadNumber = "downsampleReadNumber";

    public DownsampleFastqWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class DownsampleFastqPipelineStep extends AbstractCommandPipelineStep<DownsampleFastqWrapper> implements PreprocessingStep
    {
        public DownsampleFastqPipelineStep(PipelineStepProvider provider, PipelineContext ctx, DownsampleFastqWrapper wrapper)
        {
            super(provider, ctx, wrapper);
        }

        @Override
        public PreprocessingOutputImpl processInputFile(File inputFile, @Nullable File inputFile2, File outputDir) throws PipelineJobException
        {
            PreprocessingOutputImpl output = new PreprocessingOutputImpl(inputFile, inputFile2);

            Integer readNumber = extractParamValue(DownsampleReadNumber, Integer.class);
            getPipelineCtx().getLogger().info("Downsampling file to ~" + readNumber + " random reads: " + inputFile.getName());

            int total = FastqUtils.getSequenceCount(inputFile);
            double pctRetained = readNumber / (double)total;
            pctRetained = Math.min(pctRetained, 1.0);
            getPipelineCtx().getLogger().info("\tTotal reads: " + total + ". Desired number: " + readNumber);
            getPipelineCtx().getLogger().info("\tRetaining " + (pctRetained * 100.0) + "% of reads");

            if (pctRetained == 1)
            {
                getPipelineCtx().getLogger().info("\tNothing to do");
            }
            else
            {
                getWrapper().setOutputDir(outputDir);
                output.setProcessedFastq(getWrapper().downsampleFile(inputFile, inputFile2, pctRetained));

                return output;
            }

            return output;
        }
    }

    public static class Provider extends AbstractPipelineStepProvider<PreprocessingStep>
    {
        public Provider()
        {
            super("DownsampleReads", "Downsample Reads", "If selected, up to the specified number of reads will be randomly selected from each input file.  It can be useful for debugging or trying new settings, as fewer reads will run faster.  Note: this will occur prior to barcode separation, but after merging.", Arrays.asList(
                    ToolParameterDescriptor.create(DownsampleReadNumber, "Total Reads", "For each input file, up to the this number of reads will be randomly retained", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 200)
            ), null, "http://picard.sourceforge.net/");
        }

        public DownsampleFastqPipelineStep create(PipelineContext context)
        {
            return new DownsampleFastqPipelineStep(this, context, new DownsampleFastqWrapper(context.getLogger()));
        }
    }

    public Pair<File, File> downsampleFile(File inputFile, @Nullable File inputFile2, double pctRetained) throws PipelineJobException
    {
        FastqToSamWrapper fq2sam = new FastqToSamWrapper(getLogger());
        fq2sam.setOutputDir(getOutputDir(null));
        File sam = fq2sam.execute(inputFile, inputFile2);

        DownsampleSamWrapper downsample = new DownsampleSamWrapper(getLogger());
        downsample.setOutputDir(getOutputDir(null));
        File downsampledSam = downsample.execute(sam, pctRetained);
        getLogger().info("\tDeleting file: " + sam.getPath());
        if (!sam.delete() || sam.exists())
            throw new PipelineJobException("File exists: " + sam.getPath());

        SamToFastqWrapper sam2fq = new SamToFastqWrapper(getLogger());
        sam2fq.setOutputDir(getOutputDir(null));
        String fn1 = FileUtil.getBaseName(inputFile) + ".downsampled.fastq";
        String fn2 = null;
        if (inputFile2 != null)
            fn2 = FileUtil.getBaseName(inputFile2) + ".downsampled.fastq";

        Pair<File, File> currentFiles = sam2fq.executeCommand(downsampledSam, fn1, fn2);
        getLogger().info("\tDeleting file: " + downsampledSam.getPath());
        if (!downsampledSam.delete() || downsampledSam.exists())
            throw new PipelineJobException("File exists: " + downsampledSam.getPath());

        return currentFiles;
    }
}
