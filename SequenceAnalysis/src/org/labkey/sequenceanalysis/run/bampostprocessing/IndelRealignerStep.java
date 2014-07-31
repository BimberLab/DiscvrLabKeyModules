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
import org.labkey.sequenceanalysis.run.util.IndelRealignerWrapper;

import java.io.File;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:59 PM
 */
public class IndelRealignerStep extends AbstractCommandPipelineStep<IndelRealignerWrapper> implements BamProcessingStep
{
    public IndelRealignerStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new IndelRealignerWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<IndelRealignerStep>
    {
        public Provider()
        {
            super("IndelRealigner", "Indel Realigner", "The step runs GATK's IndelRealigner tool.  This tools performs local realignment to minmize the number of mismatching bases across all the reads.", null, null, "http://www.broadinstitute.org/gatk/gatkdocs/org_broadinstitute_sting_gatk_walkers_indels_IndelRealigner.html");
        }

        @Override
        public IndelRealignerStep create(PipelineContext ctx)
        {
            return new IndelRealignerStep(this, ctx);
        }
    }

    @Override
    public Output processBam(ReadsetModel rs, File inputBam, File referenceFasta, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".realigned.bam");

        output.addIntermediateFile(outputBam);
        output.addIntermediateFile(new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".realigned.bai"));
        output.addIntermediateFile(getWrapper().getExpectedIntervalsFile(inputBam), "Realigner Intervals File");
        output.setBAM(getWrapper().execute(inputBam, outputBam, referenceFasta, null));

        return output;
    }
}
