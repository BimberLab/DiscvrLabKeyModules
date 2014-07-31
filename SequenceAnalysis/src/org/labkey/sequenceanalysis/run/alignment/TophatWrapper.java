package org.labkey.sequenceanalysis.run.alignment;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.pipeline.AbstractAlignmentStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.AlignmentStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.api.run.AbstractCommandWrapper;
import org.labkey.sequenceanalysis.api.run.CommandLineParam;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;

import java.io.File;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 12/16/12
 * Time: 11:16 AM
 */
public class TophatWrapper extends AbstractCommandWrapper
{
    public TophatWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class TophatStep extends AbstractPipelineStep implements AlignmentStep
    {
        public TophatStep(PipelineStepProvider provider, PipelineContext ctx)
        {
            super(provider, ctx);
        }

        @Override
        public IndexOutput createIndex(File refFasta, File outputDir) throws PipelineJobException
        {
            IndexOutputImpl output = new IndexOutputImpl(refFasta);

            //TODO

            output.appendOutputs(refFasta, outputDir);

            return output;
        }

        @Override
        public AlignmentOutput performAlignment(File inputFastq1, @Nullable File inputFastq2, File outputDirectory, File refFasta, String basename) throws PipelineJobException
        {
            return new AlignmentOutputImpl();
        }
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("Tophat", "Description here", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("a"), "identity", "Min Pct Identity", "The minimum percent identity required per alignment for that match to be included", "ldk-numberfield", null, 98)
//                    ToolParameterDescriptor.create("continuity", "Percent Continuity", "Continuity is the percentage of alignment columns that are not gaps. Alignment blocks outside the given range are discarded.", "ldk-numberfield", 90)
            ), null, "http://ccb.jhu.edu/software/tophat/index.shtml", false);
        }

        public TophatStep create(PipelineContext context)
        {
            return new TophatStep(this, context);
        }
    }

    protected File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("TOPHATPATH", "tophat");
    }
}
