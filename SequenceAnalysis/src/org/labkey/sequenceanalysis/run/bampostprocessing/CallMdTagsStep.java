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
import org.labkey.sequenceanalysis.run.util.CallMdWrapper;

import java.io.File;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:44 PM
 */ 
public class CallMdTagsStep extends AbstractCommandPipelineStep<CallMdWrapper> implements BamProcessingStep
{
    public CallMdTagsStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new CallMdWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<CallMdTagsStep>
    {
        public Provider()
        {
            super("CallMdTags", "Call MD Tags", "Samtools", "This runs the samtools calmd command to populate MD tags in the BAM if not present.  Some tools require these, or will complain if the tag is missing or inaccurate.  Certain downstream steps may require these to be present", null, null, "http://samtools.sourceforge.net/samtools.shtml");
        }

        @Override
        public CallMdTagsStep create(PipelineContext ctx)
        {
            return new CallMdTagsStep(this, ctx);
        }
    }

    @Override
    public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();

        getWrapper().setOutputDir(outputDirectory);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".calmd.bam");
        output.addIntermediateFile(outputBam);
        File calMdBam = getWrapper().execute(inputBam, outputBam, referenceGenome.getWorkingFastaFile());
        output.setBAM(calMdBam);

        return output;
    }
}
