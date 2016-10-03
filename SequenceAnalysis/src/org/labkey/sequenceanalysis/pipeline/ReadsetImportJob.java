package org.labkey.sequenceanalysis.pipeline;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipelineSettings;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.view.NotFoundException;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.util.NucleotideSequenceFileType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 4/27/14
 * Time: 5:41 AM
 */
public class ReadsetImportJob extends SequenceJob
{
    public static final String FOLDER_NAME = "sequenceImport";

    private ReadsetImportJob(Container c, User u, String jobName, PipeRoot root, JSONObject params) throws IOException, PipelineJobException
    {
        super(SequencePipelineProvider.NAME, c, u, jobName, root, params, new TaskId(FileAnalysisTaskPipeline.class, NAME), FOLDER_NAME);
    }

    public static List<ReadsetImportJob> create(Container c, User u, String jobName, String description, JSONObject params, List<File> inputFiles) throws PipelineJobException, IOException, PipelineValidationException
    {
        PipeRoot pr = PipelineService.get().findPipelineRoot(c);
        if (pr == null || !pr.isValid())
            throw new NotFoundException();

        ReadsetImportJob job = new ReadsetImportJob(c, u, jobName, pr, params);
        job.setDescription(description);
        job.setInputFiles(inputFiles);

        return Arrays.asList(job);
    }

    public static final String NAME = "sequenceImportPipeline";

    public List<SequenceReadsetImpl> getCachedReadsetModels()
    {
        List<SequenceReadsetImpl> ret = new ArrayList<>();
        for (Readset r : getSequenceSupport().getCachedReadsets())
        {
            ret.add((SequenceReadsetImpl)r);
        }

        return ret;
    }

    public static void register() throws CloneNotSupportedException
    {
        FileAnalysisTaskPipelineSettings settings = new FileAnalysisTaskPipelineSettings(NAME);
        settings.setAnalyzeURL("/sequenceAnalysis/importReadset.view");
        settings.setDescription("Import sequence data");
        settings.setProtocolName("Sequence Import");
        settings.setProtocolFactoryName("sequenceImport");
        settings.setInitialInputExt(new NucleotideSequenceFileType());
        settings.setTaskProgressionSpec(new Object[]{
                new TaskId(ReadsetInitTask.class),
                new TaskId(SequenceNormalizationTask.class),
                getXarGenerator().getId(),
                new TaskId(ReadsetCreationTask.class)
        });
        settings.setDeclaringModule(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class));
        settings.setDefaultDisplayState(PipelineActionConfig.displayState.enabled);

        PipelineJobService.get().addTaskPipeline(settings);
    }
}
