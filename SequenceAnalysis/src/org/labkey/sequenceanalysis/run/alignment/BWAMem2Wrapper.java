package org.labkey.sequenceanalysis.run.alignment;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;

import java.io.File;

/**
 * User: bimber
 * Date: 6/14/2014
 * Time: 8:35 AM
 */
public class BWAMem2Wrapper extends BWAMemWrapper
{
    public BWAMem2Wrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class BWAMem2AlignmentStep extends BWAMemAlignmentStep
    {
        public BWAMem2AlignmentStep(AlignmentStepProvider<?> provider, PipelineContext ctx)
        {
            super(provider, ctx, new BWAMem2Wrapper(ctx.getLogger()));

            _addBtwswArg = false;
        }

        @Override
        public String getIndexCachedDirName(PipelineJob job)
        {
            return "bwamem2";
        }
    }

    @Override
    protected String getIndexDirName()
    {
        return("bwamem2");
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("BWA-Mem2", null, getToolParams(), null, "https://github.com/bwa-mem2/bwa-mem2", true, true);

            setAlwaysCacheIndex(true);
        }

        @Override
        public BWAMem2AlignmentStep create(PipelineContext context)
        {
            return new BWAMem2AlignmentStep(this, context);
        }
    }

    @Override
    public File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("BWAPATH", "bwa-mem2");
    }
}
