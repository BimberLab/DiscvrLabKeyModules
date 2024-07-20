package org.labkey.singlecell.pipeline.singlecell;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.singlecell.analysis.AbstractSingleCellHandler;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.labkey.singlecell.analysis.AbstractSingleCellHandler.SEURAT_PROTOTYPE;

public class UpdateSeuratPrototype extends AbstractRDiscvrStep
{
    public UpdateSeuratPrototype(PipelineContext ctx, UpdateSeuratPrototype.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("UpdateSeuratPrototype", "Update Seurat Prototype", "CellMembrane/Rdiscvr", "This will re-process an existing seurat prototype object and overwrite the original", Arrays.asList(
                    SeuratToolParameter.create("reapplyMetadata", "Reapply Metadata", "If checked, metadata will be re-applied", "checkbox", null, true),
                    SeuratToolParameter.create("runRira", "Run RIRA", "If checked, RIRA classification will be re-run", "checkbox", null, true),
                    SeuratToolParameter.create("runTNKClassification", "Run T/NK Classification", "If checked, T/NK expression-based classification will be re-run", "checkbox", null, true),
                    SeuratToolParameter.create("applyTCR", "Append TCR Data", "If checked, TCR data will be applied. This will fail if", "checkbox", null, true),
                    SeuratToolParameter.create("allowMissingTcr", "Allow Missing TCR Data", "Unless checked, an error will be thrown if any sample lacks TCR data", "checkbox", new JSONObject()
                    {{
                        put("checked", false);
                    }}, false),
                    SeuratToolParameter.create("keepOriginal", "Keep Copy of Original File", "If checked, the original file will be copied with the file extension '.bk'", "checkbox", new JSONObject()
                    {{
                        put("checked", false);
                    }}, false)
            ), null, null);
        }

        @Override
        public UpdateSeuratPrototype create(PipelineContext ctx)
        {
            return new UpdateSeuratPrototype(ctx, this);
        }
    }

    @Override
    public void init(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputFiles) throws PipelineJobException
    {
        if (inputFiles.size() > 1)
        {
            throw new PipelineJobException("Seurat prototype step expects this job to have a single input. Consider selecting the option to run jobs individually instead of merged");
        }

        if (inputFiles.get(0).getReadset() == null)
        {
            throw new PipelineJobException("Seurat prototype step expects all inputs to have a readset ID.");
        }

        if (!SEURAT_PROTOTYPE.equals(inputFiles.get(0).getCategory()))
        {
            throw new PipelineJobException("Expected the input to be a seurat prototype, found: " + inputFiles.get(0).getCategory());
        }

        if (ctx.getSequenceSupport().getCachedGenomes().size() > 1)
        {
            throw new PipelineJobException("Expected seurat prototype step to use a single genome");
        }

        Readset rs = ctx.getSequenceSupport().getCachedReadset(inputFiles.get(0).getReadset());
        if (!ctx.getJob().getContainer().getId().equalsIgnoreCase(rs.getContainer()))
        {
            throw new PipelineJobException("Seurat prototype jobs must be submitted to the same folder as the source readset");
        }
    }

    @Override
    public Output execute(SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Output output = super.execute(ctx, inputObjects, outputPrefix);

        if (ctx.getSequenceSupport().getCachedGenomes().size() > 1)
        {
            throw new PipelineJobException("Expected seurat prototype step to use a single genome");
        }

        if (output.getSeuratObjects().size() != 1)
        {
            throw new PipelineJobException("Expected a single output object, found: " + output.getSeuratObjects().size());
        }

        SeuratObjectWrapper inputRDS = inputObjects.get(0);
        SeuratObjectWrapper wrapper = output.getSeuratObjects().get(0);
        if (wrapper.getReadsetId() == null)
        {
            throw new PipelineJobException("Missing readset Id: " + wrapper.getDatasetId());
        }

        File toReplace = inputRDS.getSequenceOutputFile().getFile();
        if (!toReplace.exists())
        {
            throw new PipelineJobException("Missing file: " + toReplace);
        }
        try
        {
            ctx.getLogger().info("Replacing existing prototype: " + toReplace.getPath());

            if (ctx.getParams().optBoolean("keepOriginal", false))
            {
                File backup = new File(toReplace.getPath() + ".orig");
                if (backup.exists())
                {
                    backup.delete();
                }

                FileUtils.moveFile(toReplace, backup);
            }

            if (toReplace.exists())
            {
                toReplace.delete();
            }

            FileUtils.moveFile(wrapper.getFile(), toReplace);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return output;
    }
}
