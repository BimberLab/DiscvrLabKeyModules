package org.labkey.sequenceanalysis.run.alignment;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.IndexOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractAlignmentPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Pbmm2Wrapper extends AbstractCommandWrapper
{
    public Pbmm2Wrapper(Logger log)
    {
        super(log);
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("Pbmm2", "pbmm2 is a version of minimap2, produced by PacBio, suited for long reads.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--preset"), "preset", "Preset", "The config preset, see pbmm2 docs.", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", "CCS;SUBREAD;HIFI;ISOSEQ");
                        put("multiSelect", false);
                    }}, "SUBREAD"),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--log-level"), "loglevel", "Log Level", "The verbosity of logging.", "ldk-simplecombo", new JSONObject(){{
                        put("storeValues", "DEBUG;INFO");
                        put("multiSelect", false);
                    }}, "INFO"),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--median-filter"), "medianFilter", "Median Filter", "Using --median-filter, only the subread closest to the median subread length per ZMW is being aligned. Preferably, full-length subreads flanked by adapters are chosen.", "checkbox", new JSONObject(){{

                    }}, false)
            ), null, "https://github.com/PacificBiosciences/pbmm2", true, false);

            setAlwaysCacheIndex(false);
        }

        @Override
        public String getDescription()
        {
            return null;
        }

        @Override
        public AlignmentStep create(PipelineContext context)
        {
            return new Pbmm2Wrapper.Pbmm2AlignmentStep(this, context, new Pbmm2Wrapper(context.getLogger()));
        }
    }

    public static class Pbmm2AlignmentStep extends AbstractAlignmentPipelineStep<Pbmm2Wrapper> implements AlignmentStep
    {
        public Pbmm2AlignmentStep(AlignmentStepProvider provider, PipelineContext ctx, Pbmm2Wrapper wrapper)
        {
            super(provider, ctx, wrapper);
        }

        @Override
        public boolean supportsGzipFastqs()
        {
            return true;
        }

        @Override
        public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            getPipelineCtx().getLogger().info("Creating pbmm2 index");
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = new File(outputDir, getIndexCachedDirName(getPipelineCtx().getJob()));
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
            if (!hasCachedIndex)
            {
                List<String> args = new ArrayList<>();
                args.add(getWrapper().getExe().getPath());

                args.add("index");

                args.add(referenceGenome.getWorkingFastaFile().getPath());

                String outPrefix = getIndexFileName(referenceGenome);
                if (!indexDir.exists())
                {
                    indexDir.mkdirs();
                }

                args.add(new File(indexDir, outPrefix).getPath());
                getWrapper().execute(args);
            }

            output.appendOutputs(referenceGenome.getWorkingFastaFile(), indexDir);

            //recache if not already
            AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), indexDir, getProvider().getName(), referenceGenome);

            return output;
        }

        private String getIndexFileName(ReferenceGenome genome)
        {
            return FileUtil.getBaseName(genome.getSourceFastaFile()) + ".mmi";
        }

        @Override
        public final AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            File inputFastq1 = assertSingleFile(inputFastqs1);

            if (inputFastqs2 != null)
            {
                throw new PipelineJobException("pbmm2 expects a single FASTQ input");
            }

            AlignmentOutputImpl output = new AlignmentOutputImpl();

            File indexDir = AlignerIndexUtil.getIndexDir(referenceGenome, getIndexCachedDirName(getPipelineCtx().getJob()));

            List<String> args = new ArrayList<>();
            args.add(getWrapper().getExe().getPath());
            args.add("align");
            args.add(new File(indexDir, getIndexFileName(referenceGenome)).getPath());
            args.add(inputFastq1.getPath());

            File outBam = new File(getPipelineCtx().getWorkingDirectory(), SequenceAnalysisService.get().getUnzippedBaseName(inputFastq1.getName()) + ".pbmm2.bam");
            args.add(outBam.getPath());
            args.add("--sort");

            args.add("--rg");
            args.add("@RG\\tID:" + rs.getReadsetId().toString() + "\\tSM:" + rs.getName().replaceAll(" ", "_"));

            args.add("--sample");
            args.add(rs.getName().replaceAll(" ", "_"));

            // By default, 25% of threads specified with -j, maximum 8, are used for sorting.
            Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
            if (maxThreads != null)
            {
                args.add("-j");
                args.add(maxThreads.toString());
            }

            args.addAll(getClientCommandArgs());

            getWrapper().execute(args);

            if (!outBam.exists())
            {
                throw new PipelineJobException("Unable to find BAM: " + outBam.getPath());
            }

            output.addCommandsExecuted(getWrapper().getCommandsExecuted());
            output.setBAM(outBam);

            return output;
        }

        @Override
        public boolean doAddReadGroups()
        {
            return false;
        }

        @Override
        public boolean doSortIndexBam()
        {
            return true;
        }

        @Override
        public boolean alwaysCopyIndexToWorkingDir()
        {
            return false;
        }
    }

    private File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("PBMM2PATH", "pbmm2");
    }
}
