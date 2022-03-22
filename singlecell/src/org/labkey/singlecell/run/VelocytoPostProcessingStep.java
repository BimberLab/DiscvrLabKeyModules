package org.labkey.singlecell.run;

import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class VelocytoPostProcessingStep extends AbstractCommandPipelineStep<VelocytoAlignmentStep.VelocytoWrapper> implements BamProcessingStep
{
    public VelocytoPostProcessingStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new VelocytoAlignmentStep.VelocytoWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<VelocytoPostProcessingStep>
    {
        public Provider()
        {
            super("velocytoPostProcess", "Velocyto", "velocyto", "This will run velocyto to generate a supplemental feature count matrix", Arrays.asList(
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
            ), new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js")), null);
        }

        @Override
        public VelocytoPostProcessingStep create(PipelineContext ctx)
        {
            return new VelocytoPostProcessingStep(this, ctx);
        }
    }

    @Override
    public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
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

        File loom = getWrapper().runVelocytoFor10x(inputBam, gtf, outputDirectory, mask);
        output.addSequenceOutput(loom, rs.getName() + ": velocyto", "Velocyto Counts", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);

        return output;
    }

    @Override
    public boolean expectToCreateNewBam()
    {
        return false;
    }
}
