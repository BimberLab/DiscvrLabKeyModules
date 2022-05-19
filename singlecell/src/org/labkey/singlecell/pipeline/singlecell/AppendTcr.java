package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class AppendTcr extends AbstractRDiscvrStep
{
    public AppendTcr(PipelineContext ctx, AppendTcr.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("AppendTcr", "Append TCR Data", "RDiscvr", "This uses Rdiscvr::DownloadAndAppendTcrClonotypes to append TCR data.", Arrays.asList(
                    SeuratToolParameter.create("allowMissing", "Allow Missing Data", "If checked, an error will be thrown if any sample lacks TCR data", "checkbox", new JSONObject(){{
                        put("checked", true);
                    }}, true)
            ), null, null);
        }


        @Override
        public AppendTcr create(PipelineContext ctx)
        {
            return new AppendTcr(ctx, this);
        }
    }

    @Override
    public String getFileSuffix()
    {
        return "tcr";
    }

    @Override
    protected Chunk createParamChunk(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Chunk ret = super.createParamChunk(ctx, inputObjects, outputPrefix);

        ret.bodyLines.add("serverBaseUrl <- '" + getPipelineCtx().getJob().getParameters().get("serverBaseUrl") + "'");
        ret.bodyLines.add("defaultLabKeyFolder <- '" + getPipelineCtx().getJob().getParameters().get("labkeyFolderPath") + "'");

        return ret;
    }
}
