package org.labkey.singlecell.run;

import org.apache.commons.io.FileUtils;
import org.json.old.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.singlecell.SingleCellModule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CellBenderLoupeHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public CellBenderLoupeHandler()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "Run CellBender (RNA-seq)", "This will run cellbender on the input cellranger folder and create a subset matrix with background/ambient noise removed.", null, CellBenderCiteSeqHandler.getParams(0.01, true, true));
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return CellRangerGexCountStep.LOUPE_CATEGORY.equals(o.getCategory()) & o.getFile().getName().endsWith("cloupe.cloupe");
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new CellBenderLoupeHandler.Processor();
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            if (inputFiles.size() > 1)
            {
                throw new PipelineJobException("Expected a single input");
            }

            File h5 = getH5(inputFiles.get(0).getFile());
            if (!h5.exists())
            {
                throw new PipelineJobException("Unable to find file: " + h5.getPath());
            }
        }

        private File getH5(File loupe)
        {
            return new File(loupe.getParentFile(), "raw_feature_bc_matrix.h5");
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            File filteredH5 = runCellBender(inputFiles.get(0).getFile(), ctx);
            if (ctx.getParams().optBoolean("copyH5", false))
            {
                ctx.getLogger().info("Copying h5 file to cellranger dir");
                List<File> toMove = Arrays.asList(
                        filteredH5,
                        new File(filteredH5.getParentFile(), filteredH5.getName().replaceAll("_filtered.h5", ".pdf")),
                        new File(filteredH5.getParentFile(), filteredH5.getName().replaceAll("_filtered.h5", "_cell_barcodes.csv")),
                        new File(filteredH5.getParentFile(), filteredH5.getName().replaceAll("_filtered.h5", ".log"))
                );

                try
                {
                    File destDir = getH5(inputFiles.get(0).getFile()).getParentFile();
                    for (File f : toMove)
                    {
                        File dest = new File(destDir, f.getName());
                        if (dest.exists())
                        {
                            dest.delete();
                        }

                        ctx.getLogger().debug("Copying file to: " + dest.getPath());
                        FileUtils.copyFile(f, dest);
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            SequenceOutputFile so = new SequenceOutputFile();
            so.setReadset(inputFiles.get(0).getReadset());
            so.setLibrary_id(inputFiles.get(0).getLibrary_id());
            so.setFile(filteredH5);
            if (so.getReadset() != null)
            {
                so.setName(ctx.getSequenceSupport().getCachedReadset(so.getReadset()).getName() + ": CellBender Filtered RNA");
            }
            else
            {
                so.setName(inputFiles.get(0).getName() + ": CellBender Filtered RNA");
            }
            so.setCategory("CellBender Filtered RNA-Seq Counts");
            ctx.addSequenceOutput(so);
        }

        public File runCellBender(File input, JobContext ctx) throws PipelineJobException
        {
            File inputH5 = getH5(input);
            File outputH5 = new File(ctx.getOutputDir(), FileUtil.getBaseName(inputH5.getName()) + ".cellbender.h5");

            String exe = SequencePipelineService.get().getExeForPackage("CELLBENDERPATH", "cellbender").getPath();
            List<String> args = new ArrayList<>(Arrays.asList(
                    exe, "remove-background",
                    "--input", inputH5.getPath(),
                    "--output", outputH5.getPath()
            ));
            args.addAll(getClientCommandArgs(ctx.getParams()));

            new SimpleScriptWrapper(ctx.getLogger()).execute(args);
            if (!outputH5.exists())
            {
                throw new PipelineJobException("Missing file: " + outputH5.getPath());
            }

            File filteredH5 = new File(outputH5.getPath().replaceAll(".h5$", "_filtered.h5"));
            if (!filteredH5.exists())
            {
                throw new PipelineJobException("Missing file: " + filteredH5.getPath());
            }

            return filteredH5;
        }
    }
}