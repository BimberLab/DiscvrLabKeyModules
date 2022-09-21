package org.labkey.sequenceanalysis.analysis;

import org.json.old.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.pipeline.ReadsetImportJob;
import org.labkey.sequenceanalysis.pipeline.SequenceAlignmentJob;
import org.labkey.sequenceanalysis.run.MultiQcRunner;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiQCBamHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public MultiQCBamHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "MultiQC", "This will run MultiQC to aggregate QC data for these files", null, Arrays.asList(
                ToolParameterDescriptor.create("reportTitle", "Report Name", null, "textfield", new JSONObject(){{
                    put("allowBlank", false);
                }}, null)
        ));
    }

    private final static FileType HTML_TYPE = new FileType(".html");
    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && (SequenceUtil.FILETYPE.bam.getFileType().isType(o.getFile()) || (HTML_TYPE.isType(o.getFile()) && "RNA-SeQC Report".equals(o.getCategory())));
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
        return new Processor();
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
            action.setStartTime(new Date());

            Set<File> dirs = new HashSet<>();
            Set<Integer> readsets = new HashSet<>();
            Set<Integer> genomes = new HashSet<>();
            for (SequenceOutputFile so : inputFiles)
            {
                dirs.add(findPipelineRoot(so.getFile().getParentFile(), SequenceAlignmentJob.NAME));
                readsets.add(so.getReadset());

                if (so.getReadset() != null)
                {
                    Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                    if (rs != null && rs.getReadData() != null)
                    {
                        for (ReadData rd : rs.getReadData())
                        {
                            if (rd.getFile1() != null)
                            {
                                dirs.add(findPipelineRoot(rd.getFile1().getParentFile(), ReadsetImportJob.NAME));
                            }

                            if (rd.getFile2() != null)
                            {
                                dirs.add(findPipelineRoot(rd.getFile2().getParentFile(), ReadsetImportJob.NAME));
                            }
                        }
                    }
                }

                genomes.add(so.getLibrary_id());
            }

            ctx.getLogger().info("Total folders: " + dirs.size());

            MultiQcRunner runner = new MultiQcRunner(ctx.getLogger());
            runner.setWorkingDir(ctx.getOutputDir());
            runner.setOutputDir(ctx.getOutputDir());

            File report = runner.runForFiles(dirs, ctx.getOutputDir(), null);
            action.addOutput(report, "MultiQC Report", false);

            SequenceOutputFile so = new SequenceOutputFile();
            so.setFile(report);
            so.setCategory("MultiQC Report");
            so.setName(ctx.getParams().optString("reportTitle", "MultiQC Report"));
            so.setDescription("Created from " + inputFiles.size() + " inputs");

            if (genomes.size() == 1)
            {
                so.setLibrary_id(genomes.iterator().next());
            }

            if (readsets.size() == 1)
            {
                so.setReadset(readsets.iterator().next());
            }

            ctx.addSequenceOutput(so);

            action.setEndTime(new Date());
            ctx.addActions(action);
        }
        
        private File findPipelineRoot(File startDir, String dirName)
        {
            File ret = startDir;
            while (ret != null)
            {
                if (ret.getParentFile() != null && ret.getParentFile().getName().equals(dirName))
                {
                    return ret;
                }
                
                ret = ret.getParentFile();
            }

            //indicates pipeline root not found
            return startDir;
        }
    }
}
