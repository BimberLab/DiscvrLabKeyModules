package org.labkey.sequenceanalysis.run.preprocessing;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CutadaptCropWrapper extends AbstractCommandWrapper
{
    public CutadaptCropWrapper(Logger log)
    {
        super(log);
    }

    public static class Provider extends AbstractPipelineStepProvider<PreprocessingStep>
    {
        public Provider()
        {
            super("ReadCropping", "Read Cropping (Cutadapt)", "Cutadapt", "This provides the ability to crop reads to the defined length, potentially trimming forward and reverse to different lengths.", Arrays.asList(
                    ToolParameterDescriptor.create("bothReads", "Crop Length (both)", "The crop length (to retain), which is applied to both reads of the pair", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, null),
                    ToolParameterDescriptor.create("read1", "Crop Length (read 1)", "The crop length (to retain), which is applied to read 1", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, null),
                    ToolParameterDescriptor.create("read2", "Crop Length (read 2)", "The crop length (to retain), which is applied to read 2", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, null)
            ), null, "https://code.google.com/p/cutadapt/");
        }

        @Override
        public CutadaptCropWrapper.CutadaptCropPipelineStep create(PipelineContext context)
        {
            return new CutadaptCropWrapper.CutadaptCropPipelineStep(this, context, new CutadaptCropWrapper(context.getLogger()));
        }
    }

    public static class CutadaptCropPipelineStep extends AbstractCommandPipelineStep<CutadaptCropWrapper> implements PreprocessingStep
    {
        public CutadaptCropPipelineStep(PipelineStepProvider provider, PipelineContext ctx, CutadaptCropWrapper wrapper)
        {
            super(provider, ctx, wrapper);
        }

        @Override
        public PreprocessingOutputImpl processInputFile(File inputFile, @Nullable File inputFile2, File outputDir) throws PipelineJobException
        {
            PreprocessingOutputImpl output = new PreprocessingOutputImpl(inputFile, inputFile2);

            String extension = getExtension(inputFile);
            File output1 = new File(outputDir, SequenceTaskHelper.getUnzippedBaseName(inputFile.getName()) + ".crop" + extension);
            File output2 = inputFile2 == null ? null : new File(outputDir, SequenceTaskHelper.getUnzippedBaseName(inputFile2.getName()) + ".crop" + extension);

            Integer both = getProvider().getParameterByName("bothReads").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
            Integer r1 = getProvider().getParameterByName("read1").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);

            Integer r2 = getProvider().getParameterByName("read2").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
            if (r2 != null && inputFile2 == null)
            {
                throw new PipelineJobException("Cannot specify read 2 cropping when there is no read 2");
            }

            if (both != null)
            {
                getPipelineCtx().getLogger().info("Cropping both reads to " + both);
                getWrapper().cropReads(inputFile, inputFile2, output1, output2, both);
                output.setProcessedFastq(Pair.of(output1, output2));
            }
            else
            {
                try
                {
                    if (r1 != null)
                    {
                        getPipelineCtx().getLogger().info("Cropping Read 1 to " + r1);
                        getWrapper().cropReads(inputFile, null, output1, null, r1);
                    }
                    else
                    {
                        FileUtils.copyFile(inputFile, output1);
                    }

                    if (r2 != null)
                    {
                        getPipelineCtx().getLogger().info("Cropping Read 2 to " + r2);
                        getWrapper().cropReads(inputFile2, null, output2, null, r2);
                    }
                    else if (inputFile2 != null)
                    {
                        FileUtils.copyFile(inputFile2, output2);
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                output.setProcessedFastq(Pair.of(output1, output2));
            }

            output.addCommandsExecuted(getWrapper().getCommandsExecuted());

            return output;
        }

        private static String getExtension(File input)
        {
            return FileUtil.getExtension(input).endsWith("gz") ? ".fastq.gz" : ".fastq";
        }
    }

    public void cropReads(File inputFile1, @Nullable File inputFile2, File outputFile1, @Nullable File outputFile2, int length) throws PipelineJobException
    {
        getLogger().info("cropping reads using cutadapt:");

        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("--length");
        args.add(String.valueOf(length));

        args.add("-o");
        args.add(outputFile1.getPath());

        if (outputFile2 != null)
        {
            args.add("-p");
            args.add(outputFile2.getPath());
        }

        args.add(inputFile1.getPath());

        if (inputFile2 != null)
        {
            args.add(inputFile2.getPath());
        }

        execute(args);
    }

    public File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("CUTADAPTPATH", "cutadapt.py");
    }
}
