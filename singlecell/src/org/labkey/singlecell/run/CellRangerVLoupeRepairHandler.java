package org.labkey.singlecell.run;

import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.FileType;
import org.labkey.singlecell.SingleCellModule;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class CellRangerVLoupeRepairHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private final FileType _vloupeFileType = new FileType("vloupe", false);
    public CellRangerVLoupeRepairHandler()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "CellRanger VDJ Repair", "This is a one-off pipeline to retroactively decode the gamma/delta segments in the filtered_contig_annotations.csv files.", null, null);
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _vloupeFileType.isType(o.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return true;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new CellRangerVLoupeRepairHandler.Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return false;
    }

    @Override
    public boolean requiresSingleGenome()
    {
        return false;
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            for (SequenceOutputFile so : inputFiles)
            {
                ctx.getLogger().info("processing file: " + so.getName());

                try
                {
                    File allContigs = new File(so.getFile().getParentFile(), "all_contig_annotations.csv");
                    if (!allContigs.exists())
                    {
                        throw new PipelineJobException("Missing file: " + allContigs.getPath());
                    }

                    if (new File(allContigs.getPath() + ".orig").exists())
                    {
                        CellRangerVDJWrapper.replaceGammaDeltaSuffix(allContigs, ctx.getLogger());
                    }
                    else
                    {
                        ctx.getLogger().info("converted file found, skipping: " + allContigs.getPath());
                    }

                    File filteredContigs = new File(so.getFile().getParentFile(), "filtered_contig_annotations.csv");
                    if (!allContigs.exists())
                    {
                        throw new PipelineJobException("Missing file: " + filteredContigs.getPath());
                    }

                    if (new File(filteredContigs.getPath() + ".orig").exists())
                    {
                        CellRangerVDJWrapper.replaceGammaDeltaSuffix(filteredContigs, ctx.getLogger());
                    }
                    else
                    {
                        ctx.getLogger().info("converted file found, skipping: " + filteredContigs.getPath());
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            ctx.addActions(action);
        }
    }
}