package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.util.FileUtil;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.run.util.FixMateInformationWrapper;

import java.io.File;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:44 PM
 */
public class FixMateInformationStep extends AbstractCommandPipelineStep<FixMateInformationWrapper> implements BamProcessingStep
{
    public FixMateInformationStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new FixMateInformationWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<FixMateInformationStep>
    {
        public Provider()
        {
            super("FixMateInformation", "Fix Mate Information", "Picard", "Runs the Picard tools FixMateInformation command, which ensures that information is synced between each read and its mate pair", null, null, "http://picard.sourceforge.net/command-line-overview.shtml");
        }

        @Override
        public FixMateInformationStep create(PipelineContext ctx)
        {
            return new FixMateInformationStep(this, ctx);
        }
    }

    @Override
    public BamProcessingStep.Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().getOutputDir(outputDirectory);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".fixmate.bam");
        output.addIntermediateFile(outputBam);
        output.setBAM(getWrapper().executeCommand(inputBam, outputBam));

        return output;
    }
}
