package org.labkey.singlecell.run;

import org.apache.commons.io.FileUtils;
import org.json.old.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.singlecell.SingleCellModule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CellBenderCiteSeqHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public CellBenderCiteSeqHandler()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "Run CellBender (CITE-seq)", "This will run cellbender on the input cellranger folder and create a subset matrix with background/ambient noise removed.", null, getParams(0.05, false, false));
    }

    protected static List<ToolParameterDescriptor> getParams(double fpr, boolean useGPU, boolean copyH5)
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>(Arrays.asList(
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--expected-cells"), "expectedCells", "Expected Cells", "Passed to CellBender --expected-cells", "ldk-integerfield", null, 5000),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--total-droplets-included"), "totalDropletsIncluded", "Total Droplets Included", "Passed to CellBender --total-droplets-included", "ldk-integerfield", null, 20000),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--fpr"), "fpr", "FPR", "Passed to CellBender --fpr", "ldk-numberfield", new JSONObject(){{
                    put("decimalPrecision", 3);
                }}, fpr),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--epochs"), "epochs", "Epochs", "Passed to CellBender --epochs", "ldk-integerfield", null, 150),
                ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--cuda"), "useGpus", "Use GPUs", "If checked, the --cuda argument will be set on cellbender", "checkbox", new JSONObject(){{
                    put("checked", useGPU);
                }}, useGPU)
        ));

        if (copyH5)
        {
            ret.add(ToolParameterDescriptor.create("copyH5", "Use Matrix Source Dir", "If checked, the filtered matrix and output will be deposited in the same folder as the input cellranger project. This is required to use the filtered matrix in the seurat pipeline", "checkbox", new JSONObject(){{
                put("checked", true);
            }}, true));
        }

        return ret;
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public boolean requiresGenome()
    {
        return false;
    }

    @Override
    public boolean requiresSingleGenome()
    {
        return false;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return "CITE-seq Counts".equals(o.getCategory()) & "matrix.mtx.gz".equals(o.getFile().getName());
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
        return new CellBenderCiteSeqHandler.Processor();
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

        private File getH5(File matrix)
        {
            return new File(matrix.getParentFile().getParentFile(), "raw_feature_bc_matrix.h5");
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            File inputH5 = getH5(inputFiles.get(0).getFile());
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

            SequenceOutputFile so = new SequenceOutputFile();
            so.setReadset(inputFiles.get(0).getReadset());
            so.setLibrary_id(inputFiles.get(0).getLibrary_id());
            so.setFile(filteredH5);
            if (so.getReadset() != null)
            {
                so.setName(ctx.getSequenceSupport().getCachedReadset(so.getReadset()).getName() + ": CellBender Filtered");
            }
            else
            {
                so.setName(inputFiles.get(0).getName() + ": CellBender Filtered");
            }
            so.setCategory("CellBender Filtered CITE-Seq Counts");
            ctx.addSequenceOutput(so);

            File aggregates = new File(inputH5.getParentFile(), "antibody_analysis/aggregate_barcodes.csv");
            if (!aggregates.exists())
            {
                throw new PipelineJobException("Missing file: " + outputH5.getPath());
            }

            try
            {
                File aggregatesCopy = new File(ctx.getOutputDir(), aggregates.getName());
                if (aggregatesCopy.exists())
                {
                    aggregatesCopy.delete();
                }

                FileUtils.copyFile(aggregates, aggregatesCopy);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }
}