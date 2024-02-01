package org.labkey.jbrowse.pipeline;

import htsjdk.samtools.util.Interval;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractVariantProcessingStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStepOutputImpl;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.SelectVariantsWrapper;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class IndexVariantsStep extends AbstractCommandPipelineStep<SelectVariantsWrapper> implements VariantProcessingStep
{
    public static final String CATEGORY = "VCF Lucene Index";

    public IndexVariantsStep(PipelineStepProvider<?> provider, PipelineContext ctx)
    {
        super(provider, ctx, new SelectVariantsWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractVariantProcessingStepProvider<IndexVariantsStep> implements VariantProcessingStep.SupportsScatterGather
    {
        public Provider()
        {
            super("IndexVariantsStep", "Index Variants", "DISCVR-seq", "Create a lucene index for the selected fields", Arrays.asList(
                    ToolParameterDescriptor.create("infoFieldsToIndex", "INFO fields to index", "A list of INFO fields to index", "sequenceanalysis-trimmingtextarea", null, null),
                    ToolParameterDescriptor.create("allowLenientProcessing", "Allow Lenient Processing", "If selected, many error types will be logged but ignored.", "checkbox", null, false)
            ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), "https://github.com/BimberLab/DISCVRSeq");
        }

        @Override
        public IndexVariantsStep create(PipelineContext ctx)
        {
            return new IndexVariantsStep(this, ctx);
        }
    }

    @Override
    public Output processVariants(File inputVCF, File outputDirectory, ReferenceGenome genome, @Nullable List<Interval> intervals) throws PipelineJobException
    {
        VariantProcessingStepOutputImpl output = new VariantProcessingStepOutputImpl();

        String infoFieldsRaw = StringUtils.trimToNull(getProvider().getParameterByName("infoFieldsToIndex").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class));
        if (infoFieldsRaw == null)
        {
            throw new PipelineJobException("Missing info fields to index");
        }

        List<String> infoFields = Arrays.stream(infoFieldsRaw.split(";")).sorted().toList();
        boolean allowLenientProcessing = getProvider().getParameterByName("allowLenientProcessing").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Boolean.class, false);

        File indexDir = new File(outputDirectory, "lucene");
        JBrowseLucenePipelineJob.prepareLuceneIndex(inputVCF, indexDir, getPipelineCtx().getLogger(), infoFields, allowLenientProcessing);

        File idx = new File(indexDir, "write.lock");
        if (!idx.exists())
        {
            throw new PipelineJobException("Unable to find file: " + idx.getPath());
        }

        output.addSequenceOutput(idx, "Lucene index: " + inputVCF.getName(), CATEGORY, null, null, genome.getGenomeId(), "Fields indexed: " + infoFieldsRaw);

        return output;
    }
}
