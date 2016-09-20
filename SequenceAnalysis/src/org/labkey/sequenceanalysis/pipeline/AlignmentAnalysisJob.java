package org.labkey.sequenceanalysis.pipeline;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipelineSettings;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisServiceImpl;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 4/27/14
 * Time: 5:41 AM
 */
public class AlignmentAnalysisJob extends SequenceJob
{
    private int _analyisId;
    private int _readsetId;

    private AlignmentAnalysisJob(Container c, User u, String jobName, PipeRoot root, JSONObject params, AnalysisModelImpl model) throws IOException
    {
        super(c, u, jobName, root, params, new TaskId(FileAnalysisTaskPipeline.class, NAME), "sequenceAnalysis");

        _analyisId = model.getAnalysisId();
        _readsetId = model.getReadset();

        getSequenceSupport().cacheAnalysis(model);
        if (model.getReadset() != null)
            getSequenceSupport().cacheReadset(SequenceAnalysisServiceImpl.get().getReadset(model.getAnalysisId(), u));
    }

    public static List<AlignmentAnalysisJob> createForAnalyses(Container c, User u, String jobName, String description, JSONObject params, JSONArray analysisIds) throws ClassNotFoundException, IOException, PipelineValidationException
    {
        PipeRoot pr = PipelineService.get().findPipelineRoot(c);
        if (pr == null || !pr.isValid())
            throw new NotFoundException();

        List<AlignmentAnalysisJob> ret = new ArrayList<>();
        for (int i=0;i<analysisIds.length();i++)
        {
            Integer analysisId = analysisIds.getInt(i);
            AnalysisModelImpl model = AnalysisModelImpl.getFromDb(analysisId, u);
            if (model == null)
            {
                throw new PipelineValidationException("Unable to find analysis: " + analysisId);
            }

            if (model.getAlignmentFile() == null)
            {
                throw new PipelineValidationException("Analysis lacks a BAM file: " + analysisId);
            }

            AlignmentAnalysisJob j = new AlignmentAnalysisJob(c, u, jobName, pr, params, model);
            j.setDescription(description);
            j.setInputFiles(Arrays.asList(model.getAlignmentFileObject()));

            ret.add(j);
        }

        return ret;
    }

    public static String NAME = "AlignmentAnalysisPipeline";

    public static void register() throws CloneNotSupportedException
    {
        FileAnalysisTaskPipelineSettings settings = new FileAnalysisTaskPipelineSettings(NAME);
        settings.setAnalyzeURL("/sequenceAnalysis/alignmentAnalysis.view");
        settings.setDescription("Analyze Alignment(s)");
        settings.setProtocolName("Analyze Alignment");
        settings.setProtocolFactoryName("analyzeAlignment");
        settings.setInitialInputExt(SequenceUtil.FILETYPE.bam.getFileType());
        settings.setTaskProgressionSpec(new Object[]{
                new TaskId(AlignmentAnalysisInitTask.class),
                new TaskId(AlignmentAnalysisRemoteWorkTask.class),
                getXarGenerator().getId(),
                new TaskId(AlignmentAnalysisWorkTask.class),
                new TaskId(AlignmentAnalysisCleanupTask.class)
        });
        settings.setDeclaringModule(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class));
        settings.setDefaultDisplayState(PipelineActionConfig.displayState.disabled);

        PipelineJobService.get().addTaskPipeline(settings);
    }

    public int getAnalyisId()
    {
        return _analyisId;
    }

    public void setAnalyisId(int analyisId)
    {
        _analyisId = analyisId;
    }

    public int getReadsetId()
    {
        return _readsetId;
    }

    public void setReadsetId(int readsetId)
    {
        _readsetId = readsetId;
    }
}
