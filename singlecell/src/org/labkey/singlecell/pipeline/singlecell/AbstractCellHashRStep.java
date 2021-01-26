package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.pipeline.AbstractSingleCellPipelineStep;
import org.labkey.api.singlecell.pipeline.SingleCellOutput;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

abstract public class AbstractCellHashRStep extends AbstractSingleCellPipelineStep
{
    public AbstractCellHashRStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    @Override
    public Collection<String> getRLibraries()
    {
        return PageFlowUtil.set("cellhashR");
    }

    @Override
    public String getDockerContainerName()
    {
        return "ghcr.io/bimberlab/cellhashr:latest";
    }

    @Override
    protected File createRmd(SingleCellOutput output, SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Map<Integer, File> countData = prepareCountData(output, ctx, inputObjects, outputPrefix);

        File outfile = new File(ctx.getOutputDir(), outputPrefix + ".rmd");
        try (PrintWriter out = PrintWriters.getPrintWriter(outfile))
        {
            Markdown markdown = new Markdown();
            markdown.headerYml = markdown.getDefaultHeader();
            markdown.setup = new SetupChunk(getRLibraries());
            markdown.chunks = new ArrayList<>();
            markdown.chunks.add(createParamChunk(inputObjects, outputPrefix));
            markdown.chunks.add(createDataChunk(countData));

            markdown.chunks.addAll(getChunks());
            markdown.chunks.add(createFinalChunk());

            markdown.print(out);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return outfile;
    }

    protected Chunk createDataChunk(Map<Integer, File> hashingData)
    {
        List<String> lines = new ArrayList<>();

        lines.add("featureData <- list(");
        for (Integer key : hashingData.keySet())
        {
            if (hashingData.get(key) == null)
            {
                lines.add("\t'" + key + "' = NULL,");
            }
            else
            {
                lines.add("\t'" + key + "' = '" + hashingData.get(key).getName() + "',");
            }
        }

        // Remove trailing comma:
        int lastIdx = lines.size() - 1;
        lines.add(lastIdx, lines.get(lastIdx).replaceAll(",$", ""));

        lines.add(")");
        lines.add("");

        return new Chunk("featureData", null, null, lines, "include=FALSE");
    }

    @Override
    public boolean requiresHashingOrCiteSeq()
    {
        return true;
    }

    abstract protected Map<Integer, File> prepareCountData(SingleCellOutput output, SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException;
}
