package org.labkey.sequenceanalysis.run.preprocessing;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PreprocessingStep;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.model.AdapterModel;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 6/29/2014
 * Time: 1:57 PM
 */
public class CutadaptWrapper extends AbstractCommandWrapper
{
    private static final String ADAPTERS = "adapters";

    public CutadaptWrapper(Logger log)
    {
        super(log);
    }

    public static class Provider extends AbstractPipelineStepProvider<PreprocessingStep>
    {
        public Provider()
        {
            super("AdapterTrimming", "Adapter Trimming (Cutadapt)", "Cutadapt", "This provides the ability to trim adapters from the 5' and/or 3' end of the reads.  5' trimming will always be performed first, followed by a second round if 3' adapter trimming was selected.", Arrays.asList(
                    ToolParameterDescriptor.create(ADAPTERS, "Adapters", "", "sequenceanalysis-adapterpanel", null, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-O"), "overlapLength", "Overlap Length", "Minimum overlap length. If the overlap between the read and the adapter is shorter than LENGTH, the read is not modified.This reduces the no. of bases trimmed purely due to short random adapter matches", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 5),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-e"), "errorRate", "Error Rate", "Maximum allowed error rate (no. of errors divided by the length of the matching region)", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 0.1),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("-m"), "minLength", "Min Length", "Discard trimmed reads that are shorter than the provided value.  Will be ignored if blank.", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, null)
            ), Collections.singleton("SequenceAnalysis/panel/AdapterPanel.js"), "https://code.google.com/p/cutadapt/");
        }

        public CutadaptPipelineStep create(PipelineContext context)
        {
            return new CutadaptPipelineStep(this, context, new CutadaptWrapper(context.getLogger()));
        }
    }

    public static class CutadaptPipelineStep extends AbstractCommandPipelineStep<CutadaptWrapper> implements PreprocessingStep
    {
        public CutadaptPipelineStep(PipelineStepProvider provider, PipelineContext ctx, CutadaptWrapper wrapper)
        {
            super(provider, ctx, wrapper);
        }

        @Override
        public PreprocessingOutputImpl processInputFile(File inputFile, @Nullable File inputFile2, File outputDir) throws PipelineJobException
        {
            PreprocessingOutputImpl output = new PreprocessingOutputImpl(inputFile, inputFile2);

            String extention = getExtension(inputFile);
            File output1 = new File(outputDir, SequenceTaskHelper.getUnzippedBaseName(inputFile.getName()) + ".adaptertrimmed" + extention);
            File output2 = inputFile2 == null ? null : new File(outputDir, SequenceTaskHelper.getUnzippedBaseName(inputFile2.getName()) + ".adaptertrimmed" + extention);

            List<AdapterModel> adapters = TrimmomaticWrapper.AdapterTrimmingProvider.getAdapters(getPipelineCtx().getJob(), getProvider().getName());
            if (adapters == null || adapters.isEmpty())
            {
                throw new PipelineJobException("No adapters provided, will not run");
            }

            getWrapper().trimAdapters(inputFile, inputFile2, output1, output2, adapters, getClientCommandArgs());
            output.setProcessedFastq(Pair.of(output1, output2));
            output.addCommandsExecuted(getWrapper().getCommandsExecuted());

            return output;
        }
    }

    private static String getExtension(File input)
    {
        return FileUtil.getExtension(input).endsWith("gz") ? ".fastq.gz" : ".fastq";
    }

    public void trimAdapters(File inputFile1, @Nullable File inputFile2, File outputFile1, @Nullable File outputFile2, List<AdapterModel> adapters, List<String> params) throws PipelineJobException
    {
        getLogger().info("trimming the following adapters using cutadapt:");
        for (AdapterModel m : adapters)
        {
            getLogger().info("\t" + m.getName() + ": " + m.getSequence() + (m.isTrim5() ? ", 5'" : "") + (m.isTrim3() ? ", 3'" : ""));
        }

        //handle paired vs. non differently:
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        if (params != null)
        {
            args.addAll(params);
        }

        //force a min read length in order to avoid empty sequences
        if (!args.contains("-m"))
        {
            args.add("-m");
            args.add("1");
        }

        List<String> fivePrimeAdapters = new ArrayList<>();
        List<String> threePrimeAdapters = new ArrayList<>();
        for (AdapterModel m : adapters)
        {
            if (m.isTrim5())
            {
                fivePrimeAdapters.add("-b");
                fivePrimeAdapters.add(m.getSequence());
            }

            if (m.isTrim3())
            {
                threePrimeAdapters.add("-a");
                threePrimeAdapters.add(m.getSequence());
            }
        }

        if (inputFile2 == null)
        {
            File input = inputFile1;
            File output = null;
            List<File> tempFiles = new ArrayList<>();
            if (!fivePrimeAdapters.isEmpty())
            {
                getLogger().info("\tTrimming 5' adapters");
                output = new File(outputFile1.getParentFile(), SequenceTaskHelper.getUnzippedBaseName(inputFile1) + ".trim5" + getExtension(inputFile1));
                trimSE(input, output, args, fivePrimeAdapters);
                tempFiles.add(output);
                input = output;
            }

            if (!threePrimeAdapters.isEmpty())
            {
                getLogger().info("\tTrimming 3' adapters");
                output = new File(outputFile1.getParentFile(), SequenceTaskHelper.getUnzippedBaseName(inputFile1) + ".trim3" + getExtension(inputFile1));
                tempFiles.add(output);
                trimSE(input, output, args, threePrimeAdapters);
            }

            if (output != null && !outputFile1.equals(output))
            {
                try
                {
                    FileUtils.moveFile(output, outputFile1);

                    for (File tmp : tempFiles)
                    {
                        if (tmp.exists())
                        {
                            tmp.delete();
                        }
                    }
                }
               catch (IOException e)
               {
                   throw new PipelineJobException(e);
               }
            }
        }
        else
        {
            Pair<File, File> inputs = Pair.of(inputFile1, inputFile2);
            Pair<File, File> outputs = null;
            List<File> intermediateFiles = new ArrayList<>();
            if (!fivePrimeAdapters.isEmpty())
            {
                getLogger().info("\tTrimming 5' adapters");
                outputs = Pair.of(new File(outputFile1.getParentFile(), SequenceTaskHelper.getUnzippedBaseName(inputFile1) + ".trim5" + getExtension(inputFile1)), new File(outputFile2.getParentFile(), SequenceTaskHelper.getUnzippedBaseName(inputFile2) + ".trim5" + getExtension(inputFile2)));
                intermediateFiles.add(outputs.first);
                intermediateFiles.add(outputs.second);
                trimPE(inputs, outputs, args, fivePrimeAdapters);
                inputs = outputs;
            }

            if (!threePrimeAdapters.isEmpty())
            {
                getLogger().info("\tTrimming 3' adapters");
                outputs = Pair.of(new File(outputFile1.getParentFile(), SequenceTaskHelper.getUnzippedBaseName(inputFile1) + ".trim3" + getExtension(inputFile1)), new File(outputFile2.getParentFile(), SequenceTaskHelper.getUnzippedBaseName(inputFile2) + ".trim3" + getExtension(inputFile2)));
                intermediateFiles.add(outputs.first);
                intermediateFiles.add(outputs.second);
                trimPE(inputs, outputs, args, threePrimeAdapters);
            }

            if (outputs != null)
            {
                try
                {
                    FileUtils.moveFile(outputs.first, outputFile1);
                    FileUtils.moveFile(outputs.second, outputFile2);

                    for (File tmp : intermediateFiles)
                    {
                        if (tmp.exists())
                        {
                            tmp.delete();
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }

    }

    private File trimSE(File input, File output, List<String> baseArgs, List<String> adapterArgs) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.addAll(baseArgs);
        args.addAll(adapterArgs);

        args.add("-o");
        args.add(output.getPath());
        args.add(input.getPath());
        execute(args);

        return output;
    }

    private void trimPE(Pair<File, File> inputs, Pair<File, File> outputs, List<String> baseArgs, List<String> adapterArgs) throws PipelineJobException
    {
        //trim forward reads
        File tmpForward1 = new File(outputs.first.getParentFile(), SequenceTaskHelper.getUnzippedBaseName(outputs.first) + ".tmpForward" + getExtension(outputs.first));
        File tmpForward2 = new File(outputs.first.getParentFile(), SequenceTaskHelper.getUnzippedBaseName(outputs.second) + ".tmpForward" + getExtension(outputs.second));

        List<String> args = new ArrayList<>();
        args.addAll(baseArgs);
        args.addAll(adapterArgs);
        args.add("--paired-output");
        args.add(tmpForward2.getPath());
        args.add("-o");
        args.add(tmpForward1.getPath());
        args.add(inputs.first.getPath());
        args.add(inputs.second.getPath());
        execute(args);

        if (!tmpForward2.exists())
        {
            tmpForward2 = null;
        }

        //then reverse reads
        File tmpReverse1 = new File(outputs.first.getParentFile(), SequenceTaskHelper.getUnzippedBaseName(outputs.first) + ".tmpReverse" + getExtension(outputs.first));
        File tmpReverse2 = new File(outputs.first.getParentFile(), SequenceTaskHelper.getUnzippedBaseName(outputs.second) + ".tmpReverse" + getExtension(outputs.second));

        args = new ArrayList<>();
        args.addAll(baseArgs);
        args.addAll(adapterArgs);
        args.add("--paired-output");
        args.add(tmpReverse1.getPath());
        args.add("-o");
        args.add(tmpReverse2.getPath());
        args.add(tmpForward2 == null ? inputs.second.getPath() : tmpForward2.getPath());
        args.add(tmpForward1.getPath());
        execute(args);

        if (!tmpReverse1.exists())
        {
            tmpReverse1 = tmpForward1;
        }

        try
        {
            FileUtils.moveFile(tmpReverse1, outputs.first);
            FileUtils.moveFile(tmpReverse2, outputs.second);

            if (tmpForward1 != null && tmpForward1.exists())
            {
                tmpForward1.delete();
            }

            if (tmpForward2 != null && tmpForward2.exists())
            {
                tmpForward2.delete();
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("CUTADAPTPATH", "cutadapt.py");
    }
}
