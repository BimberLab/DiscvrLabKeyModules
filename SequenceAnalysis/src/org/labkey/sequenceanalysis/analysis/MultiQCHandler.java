package org.labkey.sequenceanalysis.analysis;

import org.json.JSONObject;
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
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.MultiQcRunner;
import org.labkey.sequenceanalysis.run.util.FastqcRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiQCHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor>
{
    public MultiQCHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "MultiQC", "This will run MultiQC to aggregate FASTQC data for these readsets", null, List.of(
                ToolParameterDescriptor.create("reportTitle", "Report Name", null, "textfield", new JSONObject()
                {{
                    put("allowBlank", false);
                }}, null)
        ));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return false;
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
    public SequenceReadsetProcessor getProcessor()
    {
        return new Processor();
    }

    public class Processor implements SequenceReadsetProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<Readset> readsets, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<Readset> readsets, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            try
            {
                FastqcRunner fastqc = new FastqcRunner(ctx.getLogger());
                Integer threads = SequencePipelineService.get().getMaxThreads(ctx.getLogger());
                if (threads != null)
                {
                    fastqc.setThreads(threads);
                }

                List<File> fastqcZip = new ArrayList<>();
                for (Readset rs : readsets)
                {
                    for (ReadData rd : rs.getReadData())
                    {
                        List<File> files = new ArrayList<>();
                        files.add(rd.getFile1());
                        if (rd.getFileId2() != null)
                            files.add(rd.getFile2());

                        for (File fq : files)
                        {
                            File zip = fastqc.getExpectedZipFile(fq);
                            fastqcZip.add(zip);
                            action.addInput(zip, "FASTQC Data");

                            if (!zip.exists())
                            {
                                Map<File, String> labelMap = new HashMap<>();
                                labelMap.put(fq, "Readset " + rs.getName());

                                fastqc.execute(Collections.singletonList(fq), labelMap);
                            }
                        }
                    }
                }

                MultiQcRunner runner = new MultiQcRunner(ctx.getLogger());
                runner.setWorkingDir(ctx.getOutputDir());
                runner.setOutputDir(ctx.getOutputDir());

                File report = runner.runForFiles(fastqcZip, ctx.getOutputDir(), null);
                action.addOutput(report, "MultiQC Report", false);

                SequenceOutputFile so = new SequenceOutputFile();
                so.setFile(report);
                so.setCategory("MultiQC Report");
                so.setName(ctx.getParams().optString("reportTitle", "MultiQC Report"));
                so.setDescription("Created from " + readsets.size() + " readsets using FastQC data");

                ctx.addSequenceOutput(so);

                action.setEndTime(new Date());
                ctx.addActions(action);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }
}
