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
import org.labkey.sequenceanalysis.api.pipeline.ReferenceGenome;
import org.labkey.sequenceanalysis.api.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.pipeline.SequencePipelineSettings;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.util.AddOrReplaceReadGroupsWrapper;

import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:45 PM
 */
public class AddOrReplaceReadGroupsStep extends AbstractCommandPipelineStep<AddOrReplaceReadGroupsWrapper> implements BamProcessingStep
{
    public AddOrReplaceReadGroupsStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new AddOrReplaceReadGroupsWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<AddOrReplaceReadGroupsStep>
    {
        public Provider()
        {
            super("AddOrReplaceReadGroups", "Add Or Replace Read Groups", "Picard", "This runs the Picard tools AddOrReplaceReadGroups tool, which uses the readset information to ensure we have the proper samplename, platform, etc. set in the BAM file", null, null, "http://picard.sourceforge.net/command-line-overview.shtml");
        }

        @Override
        public AddOrReplaceReadGroupsStep create(PipelineContext ctx)
        {
            return new AddOrReplaceReadGroupsStep(this, ctx);
        }
    }

    @Override
    public Output processBam(ReadsetModel rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".readgroups.bam");
        output.addIntermediateFile(outputBam);
        output.setBAM(getWrapper().executeCommand(inputBam, outputBam, rs.getRowId().toString(), rs.getPlatform(), rs.getRowId().toString(), rs.getName()));

        return output;
    }
}
