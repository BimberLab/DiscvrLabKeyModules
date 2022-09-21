package org.labkey.singlecell.pipeline.singlecell;

import org.json.old.JSONArray;
import org.json.old.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppendNimble extends AbstractRDiscvrStep
{
    public AppendNimble(PipelineContext ctx, AppendNimble.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("AppendNimble", "Append Nimble Data", "Nimble/Rdiscvr", "The seurat object will be subset based on the expression below, which is passed directly to Seurat's subset(subset = X).", Arrays.asList(
                    ToolParameterDescriptor.create("nimbleGenomes", "Genomes", "Genomes to include", "singlecell-nimbleappendpanel", new JSONObject(){{
                        put("allowBlank", false);
                    }}, null)
            ), Arrays.asList("sequenceanalysis/field/GenomeField.js", "/singlecell/panel/NimbleAppendPanel.js"), null);
        }


        @Override
        public AppendNimble create(PipelineContext ctx)
        {
            return new AppendNimble(ctx, this);
        }
    }

    @Override
    public Collection<String> getRLibraries()
    {
        Set<String> ret = new HashSet<>();
        ret.add("Seurat");
        ret.addAll(super.getRLibraries());

        return ret;
    }

    @Override
    protected Chunk createParamChunk(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Chunk ret = super.createParamChunk(ctx, inputObjects, outputPrefix);

        ret.bodyLines.add("serverBaseUrl <- '" + getPipelineCtx().getJob().getParameters().get("serverBaseUrl") + "'");
        ret.bodyLines.add("defaultLabKeyFolder <- '" + getPipelineCtx().getJob().getParameters().get("labkeyFolderPath") + "'");

        ret.bodyLines.add("nimbleGenomes <- list(");
        String genomeStr = getProvider().getParameterByName("nimbleGenomes").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class);
        JSONArray json = new JSONArray(genomeStr);
        String delim = "";
        for (int i = 0; i < json.length(); i++)
        {
            JSONArray arr = json.getJSONArray(i);
            if (arr.length() != 2)
            {
                throw new PipelineJobException("Unexpected value: " + json.getString(i));
            }

            int genomeId = arr.getInt(0);
            String targetAssay = arr.getString(1);
            ret.bodyLines.add("\t" + delim + "'" + genomeId + "' = '" + targetAssay + "'");
            delim = ",";
        }
        ret.bodyLines.add(")");

        return ret;
    }

    @Override
    public String getFileSuffix()
    {
        return "nimble";
    }
}
