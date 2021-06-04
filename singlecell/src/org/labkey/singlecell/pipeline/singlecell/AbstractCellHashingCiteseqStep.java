package org.labkey.singlecell.pipeline.singlecell;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.pipeline.AbstractSingleCellPipelineStep;
import org.labkey.api.singlecell.pipeline.SingleCellOutput;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

abstract public class AbstractCellHashingCiteseqStep extends AbstractSingleCellPipelineStep
{
    public AbstractCellHashingCiteseqStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
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
            markdown.chunks.add(createDataChunk(countData, ctx.getOutputDir()));
            markdown.chunks.addAll(addAdditionalChunks(ctx, inputObjects, countData));

            markdown.chunks.addAll(getChunks(ctx));
            markdown.chunks.add(createFinalChunk());

            markdown.print(out);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return outfile;
    }

    protected List<Chunk> addAdditionalChunks(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, Map<Integer, File> countData) throws PipelineJobException
    {
        return Collections.emptyList();
    }

    protected Chunk createDataChunk(Map<Integer, File> hashingData, File outputDir)
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
                lines.add("\t'" + key + "' = '" + getRelativePath(hashingData.get(key), outputDir) + "',");
            }
        }

        // Remove trailing comma:
        int lastIdx = lines.size() - 1;
        lines.set(lastIdx, lines.get(lastIdx).replaceAll(",$", ""));

        lines.add(")");
        lines.add("");

        return new Chunk("featureData", null, null, lines, "include=FALSE");
    }

    protected String getRelativePath(File target, File outputDir)
    {
        return FileUtil.relativePath(outputDir.getPath(), target.getPath());
    }

    abstract protected Map<Integer, File> prepareCountData(SingleCellOutput output, SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException;
}
