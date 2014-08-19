package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.BamProcessingStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.run.AbstractCommandPipelineStep;
import org.labkey.sequenceanalysis.api.run.CommandLineParam;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.run.util.MarkDuplicatesWrapper;

import java.io.File;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:44 PM
 */
public class MarkDuplicatesStep extends AbstractCommandPipelineStep<MarkDuplicatesWrapper> implements BamProcessingStep
{
    public MarkDuplicatesStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new MarkDuplicatesWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<MarkDuplicatesStep>
    {
        public Provider()
        {
            super("MarkDuplicates", "Mark Duplicates", "Picard", "This runs Picard tools MarkDuplicates command in order to mark and/or remove duplicate reads.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("REMOVE_DUPLICATES"), "removeDuplicates", "Remove Duplicates", "If selected, duplicate reads will be removed, as opposed to flagged as duplicates.", "checkbox", null, null)
            ), null, "http://picard.sourceforge.net/command-line-overview.shtml");
        }

        @Override
        public MarkDuplicatesStep create(PipelineContext ctx)
        {
            return new MarkDuplicatesStep(this, ctx);
        }
    }

    @Override
    public Output processBam(ReadsetModel rs, File inputBam, File referenceFasta, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".markduplicates.bam");
        output.addIntermediateFile(outputBam);
        output.addIntermediateFile(getWrapper().getMetricsFile(inputBam));

        output.setBAM(getWrapper().executeCommand(inputBam, outputBam, getClientCommandArgs("=")));

        return output;
    }
}
