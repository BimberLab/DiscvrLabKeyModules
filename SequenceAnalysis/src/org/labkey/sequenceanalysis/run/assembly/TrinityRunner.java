package org.labkey.sequenceanalysis.run.assembly;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AssemblyStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.analysis.AssemblyOutputImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:16 PM
 */
public class TrinityRunner extends AbstractCommandWrapper
{
    public TrinityRunner(Logger log)
    {
        super(log);
    }

    public static class Provider extends AbstractPipelineStepProvider<AssemblyStep>
    {
        public Provider()
        {
            super("TrinityAssembly", "Trinity", "Trinity", "Trinity is a de novo assembler.  It will generate contigs using the input FASTQ reads after processing.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--min_contig_length"), "min_contig_length", "Min Contig Length", "Any contigs below this value will be discarded", "ldk-integerfield", new JSONObject()
                    {{

                    }}, 100)
            ), null, "https://github.com/trinityrnaseq/trinityrnaseq/wiki");
        }

        public TrinityPipelineStep create(PipelineContext context)
        {
            return new TrinityPipelineStep(this, context, new TrinityRunner(context.getLogger()));
        }
    }

    public static class TrinityPipelineStep extends AbstractCommandPipelineStep<TrinityRunner> implements AssemblyStep
    {
        public TrinityPipelineStep(PipelineStepProvider provider, PipelineContext ctx, TrinityRunner wrapper)
        {
            super(provider, ctx, wrapper);
        }

        public AssemblyStep.Output performAssembly(Readset rs, File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename) throws PipelineJobException
        {
            AssemblyOutputImpl ret = new AssemblyOutputImpl();

            //TODO:
            //--max_memory 20G
            //--genome_guided_bam <BAM>
            //--full_cleanup

            List<String> extraParams = new ArrayList<>();
            extraParams.addAll(getClientCommandArgs());

            if (SequenceTaskHelper.getMaxThreads(getPipelineCtx().getJob()) != null)
            {
                extraParams.add("--CPU");
                extraParams.add(SequenceTaskHelper.getMaxThreads(getPipelineCtx().getJob()).toString());
            }

            File fasta = getWrapper().performAssembly(inputFastq1, inputFastq2, outputDirectory, basename, extraParams, false);
            ret.addOutput(fasta, "Assembled Contigs");

            return ret;
        }

    }

    public File performAssembly(File inputFastq1, @Nullable File inputFastq2, File outputDirectory, String basename, @Nullable List<String> extraParams, boolean doCleanup) throws PipelineJobException
    {
        return performAssembly(inputFastq1, inputFastq2, outputDirectory, basename, extraParams, doCleanup, false);
    }

    public File performAssembly(File inputFastq1, @Nullable File inputFastq2, File outputDirectory, String basename, @Nullable List<String> extraParams, boolean doCleanup, boolean allowMissingOutput) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        if (extraParams != null)
        {
            args.addAll(extraParams);
        }

        args.add("--seqType");
        args.add("fq");

        if (inputFastq2 != null)
        {
            args.add("--left");
            args.add(inputFastq1.getPath());

            args.add("--right");
            args.add(inputFastq2.getPath());
        }
        else
        {
            args.add("--single");
            args.add(inputFastq1.getPath());
        }

        if (doCleanup)
        {
            args.add("--full_cleanup");
        }

        //NOTE: for some odd reason Trinity requires this to contain 'trinity'
        if (!basename.toLowerCase().contains("trinity"))
        {
            basename = "trinity-" + basename;
        }

        File output = new File(outputDirectory, basename);
        args.add("--output");
        args.add(output.getPath());

        execute(args);

        File fasta;
        if (!doCleanup)
        {
            fasta = new File(output, "Trinity.fasta");
        }
        else
        {
            fasta = new File(output.getParentFile().getPath(), output.getName() + ".Trinity.fasta");
        }

        if (!fasta.exists())
        {
            if (allowMissingOutput)
            {
                getLogger().info("no output created.  expected: " + fasta.getPath());
                return null;
            }

            if (output.exists())
            {
                String[] files = output.list();
                getLogger().debug("files in output directory: " + output.getPath());
                for (String fn : files)
                {
                    getLogger().debug(fn);
                }
            }
            else
            {
                String[] files = outputDirectory.list();
                getLogger().debug("output directory not found: " + output.getPath());
                getLogger().debug("files in: " + outputDirectory.getPath());
                for (String fn : files)
                {
                    getLogger().debug(fn);
                }
            }

            throw new PipelineJobException("Unable to find expected output: " + fasta.getPath());
        }

        return fasta;
    }

    public File getExe()
    {
        File ret = SequencePipelineService.get().getExeForPackage("TRINITYPATH", "trinity");

        return new File(ret, "Trinity");
    }
}
