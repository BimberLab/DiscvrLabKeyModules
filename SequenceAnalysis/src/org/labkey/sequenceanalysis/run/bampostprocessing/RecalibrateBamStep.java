package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.apache.commons.lang3.NotImplementedException;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.ReadsetModel;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;

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
            super("RecalibrateBam", "Base Quality Score Recalibration", "GATK", "This will use GATK to perform base quality score recalibration (BQSR) on the BAM file.  This requires your input library to be associated with a set of know SNPs", null, null, "http");
        }

        @Override
        public BamProcessingStep create(PipelineContext ctx)
        {
            return new RecalibrateBamStep(this, ctx);
        }
    }

    @Override
    public Output processBam(ReadsetModel rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        //BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        //return output;

        throw new NotImplementedException("This step has not yet been implemented");
    }
}
