package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.singlecell.CellHashingServiceImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class CheckExpectations extends AbstractCellMembraneStep
{
    public CheckExpectations(PipelineContext ctx, CheckExpectations.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("CheckExpectations", "Check Expectations", "CellMembrane", "This will tag the output of this job as a seurat prototype, which is designed to be a building block for subsequent analyses.", Arrays.asList(
                    SeuratToolParameter.create("requireSingleDatasetInput", "Expect Single Datasets", "If checked, this will enforce that each input seurat object holds a single dataset. This is expected if the input is a seurat prototype. In contrast, if the input is a merged object this would test false", "checkbox", null, true),
                    SeuratToolParameter.create("requireHashing", "Require Hashing, If Used", "If this dataset uses cell hashing, hashing calls are required", "checkbox", null, true),
                    SeuratToolParameter.create("requireCiteSeq", "Require Cite-Seq, If Used", "If this dataset uses CITE-seq, cite-seq data are required", "checkbox", null, true),
                    SeuratToolParameter.create("requireSaturation", "Require Per-Cell Saturation", "If this dataset uses TCR sequencing, these data are required", "checkbox", null, true),
                    SeuratToolParameter.create("requireSingleR", "Require SingleR", "If checked, SingleR calls, including singleRConsensus are required to pass", "checkbox", null, true),
                    SeuratToolParameter.create("requireScGate", "Require scGate", "If checked, scGateConsensus calls are required to pass", "checkbox", null, true),
                    SeuratToolParameter.create("requireRiraImmune", "Require RIRA Immune V2", "If checked, RIRA_Immune_v2 calls (field RIRA_Immune_v2.cellclass) are required to pass", "checkbox", null, true)
            ), null, null);
        }

        @Override
        public CheckExpectations create(PipelineContext ctx)
        {
            return new CheckExpectations(ctx, this);
        }
    }

    @Override
    public void init(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {

    }

    @Override
    public boolean requiresHashing(SequenceOutputHandler.JobContext ctx)
    {
        return getProvider().getParameterByName("requireHashing").extractValue(ctx.getJob(), getProvider(), getStepIdx(), Boolean.class, false);
    }

    @Override
    public boolean requiresCiteSeq(SequenceOutputHandler.JobContext ctx)
    {
        return getProvider().getParameterByName("requireCiteSeq").extractValue(ctx.getJob(), getProvider(), getStepIdx(), Boolean.class, false);
    }

    @Override
    protected Chunk createParamChunk(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Chunk ret = super.createParamChunk(ctx, inputObjects, outputPrefix);

        ret.bodyLines.add("usesHashing <- list()");
        ret.bodyLines.add("usesCiteSeq <- list()");

        for (SeuratObjectWrapper so : inputObjects)
        {
            if (so.getSequenceOutputFile().getReadset() == null)
            {
                throw new PipelineJobException("Unable to find readset for outputfile: " + so.getSequenceOutputFileId() + ". This step requires single-dataset inputs. Removing this step may be a solution.");
            }

            Readset parentReadset = ctx.getSequenceSupport().getCachedReadset(so.getSequenceOutputFile().getReadset());
            if (parentReadset == null)
            {
                throw new PipelineJobException("Unable to find readset for outputfile: " + so.getSequenceOutputFileId() + ". This step requires single-dataset inputs. Removing this step may be a solution.");
            }

            Set<String> htosPerReadset = CellHashingServiceImpl.get().getHtosForParentReadset(parentReadset.getReadsetId(), ctx.getSourceDirectory(), ctx.getSequenceSupport(), false);
            ret.bodyLines.add("usesHashing[['" + so.getDatasetId() + "']] <- " + (htosPerReadset.size() > 1 ? "TRUE" : "FALSE"));

            boolean usesCiteseq = CellHashingService.get().usesCiteSeq(ctx.getSequenceSupport(), Collections.singletonList(so.getSequenceOutputFile()));
            ret.bodyLines.add("usesCiteSeq[['" + so.getDatasetId() + "']] <- " + (usesCiteseq ? "TRUE" : "FALSE"));
        }

        return ret;
    }
}
