package org.labkey.singlecell.run;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class VelocytoAlignmentStep extends AbstractCellRangerDependentStep
{
    public VelocytoAlignmentStep(AlignmentStepProvider provider, PipelineContext ctx, CellRangerWrapper wrapper)
    {
        super(provider, ctx, wrapper);
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("velocyto", "This will run velocyto to generate a supplemental feature count matrix", getCellRangerGexParams(Arrays.asList(
                    ToolParameterDescriptor.createExpDataParam("gtf", "Gene File", "This is the ID of a GTF file containing genes from this genome.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                    {{
                        put("extensions", Arrays.asList("gtf"));
                        put("width", 400);
                        put("allowBlank", false);
                    }}, null),
                    ToolParameterDescriptor.createExpDataParam("mask", "Mask File", "This is the ID of an optional GTF file containing repetitive regions to mask.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                    {{
                        put("extensions", Arrays.asList("gtf"));
                        put("width", 400);
                        put("allowBlank", false);
                    }}, null)
            )), new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js")), null, true, false, ALIGNMENT_MODE.MERGE_THEN_ALIGN);
        }

        @Override
        public AlignmentStep create(PipelineContext context)
        {
            return new VelocytoAlignmentStep(this, context, new CellRangerWrapper(context.getLogger()));
        }
    }

    @Override
    public AlignmentOutput performAlignment(Readset rs, List<File> inputFastqs1, @Nullable List<File> inputFastqs2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
    {
        AlignmentOutputImpl output = new AlignmentOutputImpl();
        File localBam = runCellRanger(output, rs, inputFastqs1, inputFastqs2, outputDirectory, referenceGenome, basename, readGroupId, platformUnit);

        File gtf = getPipelineCtx().getSequenceSupport().getCachedData(getProvider().getParameterByName("gtf").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class));
        if (gtf == null)
        {
            throw new PipelineJobException("Missing GTF file param");
        }
        else if (!gtf.exists())
        {
            throw new PipelineJobException("File not found: " + gtf.getPath());
        }

        File mask = null;
        if (getProvider().getParameterByName("mask").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class) != null)
        {
            mask = getPipelineCtx().getSequenceSupport().getCachedData(getProvider().getParameterByName("mask").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class));
            if (!mask.exists())
            {
                throw new PipelineJobException("Missing file: " + mask.getPath());
            }
        }

        File loom = new VelocytoWrapper(getPipelineCtx().getLogger()).runVelocytoFor10x(localBam, gtf, outputDirectory, mask);
        output.addSequenceOutput(loom, rs.getName() + ": velocyto", "Velocyto Counts", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);

        return output;
    }

    @Override
    public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        return super.createIndex(referenceGenome, outputDir);
    }

    public static class VelocytoWrapper extends AbstractCommandWrapper
    {
        public VelocytoWrapper(Logger log)
        {
            super(log);
        }

        public File runVelocytoFor10x(File localBam, File gtf, File outputFolder, @Nullable File mask) throws PipelineJobException
        {
            // https://velocyto.org/velocyto.py/tutorial/cli.html#run10x-run-on-10x-chromium-samples
            // velocyto run10x -m repeat_msk.gtf mypath/sample01 somepath/refdata-cellranger-mm10-1.2.0/genes/genes.gtf

            SimpleScriptWrapper wrapper = new SimpleScriptWrapper(getLogger());
            List<String> args = new ArrayList<>();
            args.add(SequencePipelineService.get().getExeForPackage("VELOCYTOPATH", "velocyto").getPath());
            args.add("run10x");

            args.add("-o");
            args.add(outputFolder.getPath());

            Integer threads = SequencePipelineService.get().getMaxThreads(getLogger());
            if (threads != null && threads > 1)
            {
                args.add("--samtools-threads");
                args.add(String.valueOf(threads - 1));
            }

            if (mask != null)
            {
                args.add("-m");
                args.add(mask.getPath());
            }

            // Input 10x. This is the top-level project output
            args.add(localBam.getParentFile().getParentFile().getPath());

            args.add(gtf.getPath());

            wrapper.execute(args);

            File loom = new File(outputFolder, "sample.loom");
            if (!loom.exists())
            {
                throw new PipelineJobException("Missing expected file: " + loom.getPath());
            }

            return loom;
        }
    }
}
