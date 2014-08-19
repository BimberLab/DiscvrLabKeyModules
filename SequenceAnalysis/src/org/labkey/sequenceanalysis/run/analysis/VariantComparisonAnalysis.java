package org.labkey.sequenceanalysis.run.analysis;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.api.pipeline.AbstractAnalysisStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.AnalysisStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.run.CommandLineParam;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Created by bimber on 8/6/2014.
 */
public class VariantComparisonAnalysis extends AbstractPipelineStep implements AnalysisStep
{
    public VariantComparisonAnalysis(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractAnalysisStepProvider<VariantComparisonAnalysis>
    {
        public Provider()
        {
            super("VariantComparisonAnalysis", "Variant Comparison", null, "This will tools to compare all VCF files produced during this run to create summaries of the intersect and difference.  This is often useful when comparing variant calling using different parameters.", null, null, null);
        }

        @Override
        public VariantComparisonAnalysis create(PipelineContext ctx)
        {
            return new VariantComparisonAnalysis(this, ctx);
        }
    }

    @Override
    public void init(List<AnalysisModel> models) throws PipelineJobException
    {

    }

    @Override
    public Output performAnalysisPerSample(AnalysisModel model, File inputBam, File referenceFasta) throws PipelineJobException
    {
        return null;
    }

    @Override
    public void performAnalysisOnAll(List<Output> previousSteps)
    {

    }
}
