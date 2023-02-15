package org.labkey.singlecell.run;

import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AnalysisStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class VelocytoAnalysisStep extends AbstractCommandPipelineStep<VelocytoAlignmentStep.VelocytoWrapper> implements AnalysisStep
{
    public VelocytoAnalysisStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new VelocytoAlignmentStep.VelocytoWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<VelocytoAnalysisStep>
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
                        put("allowBlank", true);
                    }}, null),
                    ToolParameterDescriptor.createExpDataParam("samtoolsMem", "Samtools Mem To Sort (GB)", "The amount of ram to use to samtools sort", "ldk-integerfield", new JSONObject()
                    {{
                        put("minValue", 0);
                    }}, 10)
            ), new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js")), null);
        }

        @Override
        public VelocytoAnalysisStep create(PipelineContext ctx)
        {
            return new VelocytoAnalysisStep(this, ctx);
        }
    }

    @Override
    public Output performAnalysisPerSampleLocal(AnalysisModel model, File inputBam, File referenceFasta, File outDir) throws PipelineJobException
    {
        return null;
    }

    @Override
    public Output performAnalysisPerSampleRemote(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
    {
        AnalysisOutputImpl output = new AnalysisOutputImpl();
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

        Integer samtoolsMem = getProvider().getParameterByName("samtoolsMem").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
        File loom = getWrapper().runVelocytoFor10x(inputBam, gtf, outputDir, mask, rs, samtoolsMem);
        output.addSequenceOutput(loom, rs.getName() + ": velocyto", "Velocyto Counts", rs.getReadsetId(), null, referenceGenome.getGenomeId(), null);

        return output;
    }
}
