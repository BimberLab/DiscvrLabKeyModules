package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class RunCsCore extends AbstractCellMembraneStep
{
    public RunCsCore(PipelineContext ctx, Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunCsCore", "CS-CORE", "CS-CORE", "Run CS-CORE on the seurat object to identify gene modules.", Collections.emptyList(), null, null);
        }

        @Override
        public RunCsCore create(PipelineContext ctx)
        {
            return new RunCsCore(ctx, this);
        }
    }

    @Override
    public boolean createsSeuratObjects()
    {
        return false;
    }

    @Override
    public String getFileSuffix()
    {
        return "cscore";
    }

    @Override
    public Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Output output = super.execute(ctx, inputObjects, outputPrefix);

        // Add the RDS files:
        File[] outputs = ctx.getOutputDir().listFiles(f -> f.isDirectory() && f.getName().endsWith(".cscore.wgcna.rds"));
        if (outputs == null || outputs.length == 0)
        {
            return output;
        }

        for (File rds : outputs)
        {
            String sn = rds.getName().replaceAll(".cscore.wgcna.rds", "");

            output.addSequenceOutput(rds, "CS-CORE: " + sn, "CS-CORE Results", inputObjects.get(0).getReadsetId(), null, ctx.getSequenceSupport().getCachedGenomes().iterator().next().getGenomeId(), null);
        }

        return output;
    }
}