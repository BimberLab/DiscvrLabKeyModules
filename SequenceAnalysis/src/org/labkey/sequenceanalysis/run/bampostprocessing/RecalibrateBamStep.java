package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.BamProcessingStep;
import org.labkey.sequenceanalysis.api.pipeline.DefaultPipelineStepOutput;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepOutput;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;

import java.io.File;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:46 PM
 */
public class RecalibrateBamStep extends AbstractPipelineStep implements BamProcessingStep
{
    public RecalibrateBamStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<BamProcessingStep>
    {
        public Provider()
        {
            super("RecalibrateBam", "Variant Quality Score Recalibration", "This will use GATK to perform variant quality score recalibration (VQSR) on the BAM file.  This requires your input library to be associated with a set of know SNPs", null, null, "http");
        }

        @Override
        public BamProcessingStep create(PipelineContext ctx)
        {
            return new RecalibrateBamStep(this, ctx);
        }
    }

    @Override
    public Output processBam(ReadsetModel rs, File inputBam, File referenceFasta, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();


        return output;
    }
}
