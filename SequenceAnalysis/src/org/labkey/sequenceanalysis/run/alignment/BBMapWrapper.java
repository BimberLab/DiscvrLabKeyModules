package org.labkey.sequenceanalysis.run.alignment;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.IndexOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SamtoolsRunner;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractAlignmentPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 12/14/12
 * Time: 7:40 AM
 */
public class BBMapWrapper extends AbstractCommandWrapper
{
    public BBMapWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class BBMapAlignmentStep extends AbstractAlignmentPipelineStep<BBMapWrapper> implements AlignmentStep
    {
        public BBMapAlignmentStep(AlignmentStepProvider<?> provider, PipelineContext ctx)
        {
            super(provider, ctx, new BBMapWrapper(ctx.getLogger()));
        }

        @Override
        public boolean supportsGzipFastqs()
        {
            return true;
        }

        @Override
        public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = new File(outputDir, getProvider().getName());
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
            if (!hasCachedIndex)
            {
                getWrapper().buildIndex(referenceGenome.getWorkingFastaFile(), indexDir);
            }

            AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), indexDir, getProvider().getName(), referenceGenome);

            return output;
        }

        @Override
        public AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            File inputFastq1 = assertSingleFile(inputFastqs1);
            File inputFastq2 = assertSingleFile(inputFastqs2);

            AlignmentOutputImpl output = new AlignmentOutputImpl();
            AlignerIndexUtil.copyIndexIfExists(this.getPipelineCtx(), output, getProvider().getName(), getProvider().getName(), referenceGenome, true);
            File localIdx = new File(getPipelineCtx().getWorkingDirectory(), "Shared/" + getProvider().getName());
            if (!localIdx.exists())
            {
                throw new PipelineJobException("Index not copied: " + localIdx);
            }
            output.addIntermediateFile(new File(getPipelineCtx().getWorkingDirectory(), "Shared"));

            // NOTE: bbmap only supports the location ./ref for the index:
            localIdx = new File(localIdx, "ref");
            if (!localIdx.exists())
            {
                throw new PipelineJobException("ref dir not found: " + localIdx);
            }

            File refDir = new File(outputDirectory, "ref");
            try
            {
                if (refDir.exists())
                {
                    getPipelineCtx().getLogger().debug("Deleting existing ref dir: " + refDir);
                    FileUtils.deleteDirectory(refDir);
                }

                FileUtils.moveDirectory(localIdx, refDir);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            getWrapper().setOutputDir(outputDirectory);

            List<String> params = new ArrayList<>();

            String ambig = StringUtils.trimToNull(getProvider().getParameterByName("ambiguous").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class));
            if (ambig != null)
            {
                params.add("ambig=" + ambig);
                if ("all".equals(ambig))
                {
                    params.add("xmtag=t");
                }
            }

            for (String paramName : Arrays.asList("local", "semiperfectmode"))
            {
                if (getProvider().getParameterByName(paramName).hasValueInJson(getPipelineCtx().getJob(), getProvider(), getStepIdx()))
                {
                    boolean val = getProvider().getParameterByName(paramName).extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);
                    params.add(paramName + "=" + (val ? "t" : "f"));
                }
            }

            if (getProvider().getParameterByName("minid").hasValueInJson(getPipelineCtx().getJob(), getProvider(), getStepIdx()))
            {
                Double val = getProvider().getParameterByName("minid").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Double.class);
                params.add("minid=" + val);
            }

            File bam = getWrapper().doAlignment(inputFastq1, inputFastq2, outputDirectory, basename, params);
            if (!bam.exists())
            {
                throw new PipelineJobException("BAM not created, expected: " + bam.getPath());
            }

            output.setBAM(bam);
            output.addCommandsExecuted(getWrapper().getCommandsExecuted());

            return output;
        }

        @Override
        public boolean doAddReadGroups()
        {
            return true;
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

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("BBMap", "BBMap is suitable for longer reads and has the option to retain multiple hits per read. The only downside is that it can be slower. When this pipeline was first written, this aligner was preferred for sequence-based genotyping and similar applications which require retaining multiple hits.", Arrays.asList(
                    ToolParameterDescriptor.create("ambiguous", "Ambiguous Handing", "Set behavior on ambiguously-mapped reads (with multiple top-scoring mapping locations)", "ldk-simplecombo", new JSONObject()
                    {{
                        put("storeValues", "all;best;toss;random");
                        put("delimiter", ";");
                        put("multiSelect", false);
                    }}, "all"),
                    ToolParameterDescriptor.create("local", "Local Alignment", "Set to true to use local, rather than global, alignments. This will soft-clip ugly ends of poor alignments", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.create("semiperfectmode", "Semi-perfectmode", "Allow only perfect and semiperfect (perfect except for N's in the reference) mappings", "checkbox", new JSONObject()
                    {{
                        put("checked", true);
                    }}, true),
                    ToolParameterDescriptor.create("minid", "Minimum Identity", "Approximate minimum alignment identity to look for. Higher is faster and less sensitive", "ldk-numberfield", new JSONObject()
                    {{
                        put("minValue", 0);
                        put("maxValue", 1);
                        put("decimalPrecision", 2);
                    }}, 0.95)
               ), null, "https://prost.readthedocs.io/en/latest/bbmap.html", true, true);
        }

        @Override
        public BBMapAlignmentStep create(PipelineContext context)
        {
            return new BBMapAlignmentStep(this, context);
        }
    }

    protected File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("BBMAPPATH", "bbmap.sh");
    }

    public File doAlignment(File inputFastq1, @Nullable File inputFastq2, File outputDirectory, String basename, List<String> options) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("-in=" + inputFastq1.getPath());
        if (inputFastq2 != null)
        {
            args.add("-in2=" + inputFastq2.getPath());
        }

        args.add("-eoom");

        args.add("mdtag=t");
        args.add("nhtag=t");
        args.add("amtag=t");
        args.add("nmtag=t");
        args.add("printunmappedcount=t");
        args.add("overwrite=t");

        // Maximum number of total alignments to print per read. Only relevant when secondary=t.
        args.add("maxsites=50");

        // Only print secondary alignments for ambiguously-mapped reads.
        args.add("secondary=t");
        args.add("ssao=t");

        // CONSIDER: mappedonly=f If true, treats 'out' like 'outm'
        // CONSIDER: outu=<file> Write only unmapped reads to this file.  Does not include unmapped paired reads with a mapped mate.
        File outputSam = new File(outputDirectory, basename + ".bbmap.sam");
        if (outputSam.exists())
        {
            outputSam.delete();
        }

        args.add("outm=" + outputSam.getPath());

        Integer maxRam = SequencePipelineService.get().getMaxRam();
        if (maxRam != null)
        {
            args.add("-Xmx" + maxRam + "g");
        }

        Integer maxThreads = SequencePipelineService.get().getMaxThreads(getLogger());
        args.add(maxThreads == null ? "threads=1" : "threads=" + maxThreads);

        args.addAll(options);

        setWorkingDir(outputDirectory);
        execute(args);

        if (!outputSam.exists())
        {
            throw new PipelineJobException("File not found: " + outputSam.getPath());
        }

        File outputBam = new File(outputDirectory, basename + ".bbmap.bam");
        if (outputBam.exists())
        {
            outputBam.delete();
        }

        SamtoolsRunner samtoolsRunner = new SamtoolsRunner(getLogger());
        List<String> stArgs = new ArrayList<>();
        stArgs.add(samtoolsRunner.getSamtoolsPath().getPath());
        stArgs.add("view");
        stArgs.add("-o");
        stArgs.add(outputBam.getPath());
        stArgs.add(outputSam.getPath());
        samtoolsRunner.execute(stArgs);

        if (!outputBam.exists())
        {
            throw new PipelineJobException("File not found: " + outputBam.getPath());
        }

        outputSam.delete();

        return outputBam;
    }

    public File buildIndex(File inputFasta, File outDir) throws PipelineJobException
    {
        if (!outDir.exists())
        {
            outDir.mkdirs();
        }

        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("k=7");
        args.add("path=" + outDir.getPath());
        args.add("ref=" + inputFasta.getPath());

        setWorkingDir(outDir);
        execute(args);

        File output = new File(outDir, "ref");
        if (!output.exists())
        {
            throw new PipelineJobException("Unable to find file: " + output);
        }

        return output;
    }
}
