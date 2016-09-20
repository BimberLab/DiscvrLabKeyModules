package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingOutputImpl;
import org.labkey.api.util.FileUtil;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.run.util.CleanSamWrapper;

import java.io.File;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:45 PM
 */
public class CleanSamStep extends AbstractCommandPipelineStep<CleanSamWrapper> implements BamProcessingStep
{
    public CleanSamStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new CleanSamWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<CleanSamStep>
    {
        public Provider()
        {
            super("CleanSam", "Clean SAM Reads", "Picard", "This runs the Picard tools CleanSam tool, which performs various fix up steps", null, null, "http://picard.sourceforge.net/command-line-overview.shtml");
        }

        @Override
        public CleanSamStep create(PipelineContext ctx)
        {
            return new CleanSamStep(this, ctx);
        }
    }

    @Override
    public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".cleaned.bam");
        output.addIntermediateFile(outputBam);
        output.setBAM(getWrapper().executeCommand(inputBam, outputBam));

        return output;
    }
}
