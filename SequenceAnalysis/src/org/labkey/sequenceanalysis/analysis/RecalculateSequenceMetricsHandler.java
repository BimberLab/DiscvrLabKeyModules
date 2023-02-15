package org.labkey.sequenceanalysis.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import org.json.JSONObject;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.FieldKey;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.pipeline.ReadsetCreationTask;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 5/25/2017.
 */
public class RecalculateSequenceMetricsHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    public RecalculateSequenceMetricsHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Recalculate Sequence Metrics", "This will recalculate the quality metrics for the readsets for the selected BAM(s)", null, null);
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && o.getFile().exists() && SequenceUtil.FILETYPE.bam.getFileType().isType(o.getFile());
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
        return new Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    public static class Processor implements SequenceOutputProcessor
    {
        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            for (SequenceOutputFile so : inputFiles)
            {
                Readset rs = support.getCachedReadset(so.getReadset());
                job.getLogger().debug("processing readset: " + rs.getName());

                for (ReadData rd : rs.getReadData())
                {
                    for (Integer dataId : Arrays.asList(rd.getFileId1(), rd.getFileId2()))
                    {
                        if (dataId == null)
                        {
                            continue;
                        }

                        //first delete existing:
                        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("readset"), rs.getRowId());
                        filter.addCondition(FieldKey.fromString("container"), rs.getContainer());
                        filter.addCondition(FieldKey.fromString("dataId"), dataId);
                        int deleted = Table.delete(SequenceAnalysisManager.get().getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS), filter);
                        job.getLogger().debug("existing metrics deleted: " + deleted);

                        //then add:
                        ReadsetCreationTask.addQualityMetricsForReadset(rs, dataId, job);
                    }
                }
            }
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            int idx = 0;
            for (SequenceOutputFile so : inputFiles)
            {
                idx++;
                if (so.getReadset() == null)
                {
                    continue;
                }

                Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                if (rs == null)
                {
                    throw new PipelineJobException("unknown readset: " + so.getReadset());
                }

                ctx.getJob().setStatus(PipelineJob.TaskStatus.running, "CALCULATING QUALITY METRICS (" + idx + " of " + inputFiles.size() + ")");
                for (ReadData rd : rs.getReadData())
                {
                    for (File f : Arrays.asList(rd.getFile1(), rd.getFile2()))
                    {
                        if (f == null)
                        {
                            continue;
                        }

                        Map<String, Object> metricsMap = FastqUtils.getQualityMetrics(f, ctx.getJob().getLogger());
                        File cachedMetrics = new File(f.getPath() + ".metrics");
                        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(cachedMetrics), '\t', CSVWriter.NO_QUOTE_CHARACTER))
                        {
                            for (String key : metricsMap.keySet())
                            {
                                writer.writeNext(new String[]{key, String.valueOf(metricsMap.get(key))});
                            }
                        }
                        catch (IOException e)
                        {
                            throw new PipelineJobException(e);
                        }
                    }
                }
            }
        }
    }
}
