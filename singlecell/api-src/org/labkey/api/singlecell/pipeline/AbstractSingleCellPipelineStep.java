package org.labkey.api.singlecell.pipeline;

import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;

import java.io.File;
import java.io.PrintWriter;

abstract public class AbstractSingleCellPipelineStep extends AbstractPipelineStep implements SingleCellStep
{
    public AbstractSingleCellPipelineStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    protected void appendSetup(PrintWriter out)
    {
        out.println("");
        out.println("```{r setup}");
        getRLibraries().forEach(l -> {
            out.println("library(" + l + ")");
        });

        //TODO: chunk settings

        out.println("```");
    }

    protected void appendChunk(String header, String extraText, String chunkName, String body, PrintWriter out)
    {
        out.println("");
        out.println("##" + header);
        if (extraText != null)
        {
            out.println("");
            out.println(extraText);
        }
        out.println("");
        out.println("```{r " + chunkName + "}");
        out.println(body);
        out.println("");
        out.println("```");
    }
}
