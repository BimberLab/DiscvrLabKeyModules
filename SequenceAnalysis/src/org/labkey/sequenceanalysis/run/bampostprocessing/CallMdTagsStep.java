package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.BamProcessingStep;
import org.labkey.sequenceanalysis.api.pipeline.DefaultPipelineStepOutput;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepOutput;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.run.AbstractCommandPipelineStep;
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
            super("CallMdTags", "Call MD Tags", "This runs the samtools calmd command to populate MD tags in the BAM if not present.  Some tools require these, or will complain if the tag is missing or inaccurate.  Certain downstream steps may require these to be present", null, null, "http://samtools.sourceforge.net/samtools.shtml");
        }

        @Override
        public CallMdTagsStep create(PipelineContext ctx)
        {
            return new CallMdTagsStep(this, ctx);
        }
    }

    @Override
    public Output processBam(ReadsetModel rs, File inputBam, File referenceFasta, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();

        getWrapper().setOutputDir(outputDirectory);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".calmd.bam");
        output.addIntermediateFile(outputBam);
        File calMdBam = getWrapper().execute(inputBam, outputBam, referenceFasta);
        output.setBAM(calMdBam);

        return output;
    }
}
