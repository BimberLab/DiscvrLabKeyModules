package org.labkey.sequenceanalysis.pipeline;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipelineSettings;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * User: bimber
 * Date: 4/27/14
 * Time: 5:41 AM
 */
public class AlignmentImportJob extends SequenceJob
{
    public static final String FOLDER_NAME = "sequenceImport";

    // Default constructor for serialization
    protected AlignmentImportJob()
    {
    }

    private AlignmentImportJob(Container c, User u, String jobName, PipeRoot root, JSONObject params) throws IOException
    {
        super(SequencePipelineProvider.NAME, c, u, jobName, root, params, new TaskId(FileAnalysisTaskPipeline.class, NAME), FOLDER_NAME);
    }

    public static List<AlignmentImportJob> create(Container c, User u, String jobName, String description, JSONObject params, Collection<File> inputFiles) throws IOException, PipelineValidationException
    {
        PipeRoot pr = PipelineService.get().findPipelineRoot(c);
        if (pr == null || !pr.isValid())
            throw new NotFoundException();

        AlignmentImportJob job = new AlignmentImportJob(c, u, jobName, pr, params);
        job.setDescription(description);
        job.setInputFiles(inputFiles);

        return Arrays.asList(job);
    }

    public static String NAME = "AlignmentImportPipeline";

    public static void register() throws CloneNotSupportedException
    {
        FileAnalysisTaskPipelineSettings settings = new FileAnalysisTaskPipelineSettings(NAME);
        settings.setAnalyzeURL("/sequenceAnalysis/alignmentImport.view");
        settings.setDescription("Import Alignment(s)");
        settings.setProtocolName("Alignment Import");
        settings.setProtocolFactoryName("alignmentImport");
        settings.setInitialInputExt(SequenceUtil.FILETYPE.bam.getFileType());
        settings.setTaskProgressionSpec(new Object[]{
                new TaskId(AlignmentImportInitTask.class),
                new TaskId(AlignmentNormalizationTask.class),
                getXarGenerator().getId(),
                new TaskId(AlignmentImportTask.class)
        });
        settings.setDeclaringModule(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class));
        //settings.setDefaultDisplayState(PipelineActionConfig.displayState.disabled);

        PipelineJobService.get().addTaskPipeline(settings);
    }
}
