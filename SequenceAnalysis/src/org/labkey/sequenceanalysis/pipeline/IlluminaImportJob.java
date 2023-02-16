package org.labkey.sequenceanalysis.pipeline;

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
import org.labkey.api.util.FileType;
import org.labkey.api.view.NotFoundException;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 4/27/14
 * Time: 5:41 AM
 */
public class IlluminaImportJob extends SequenceJob
{
    public static final String FOLDER_NAME = "sequenceImport";

    // Default constructor for serialization
    protected IlluminaImportJob()
    {
    }

    private IlluminaImportJob(Container c, User u, String jobName, PipeRoot root, JSONObject params) throws IOException
    {
        super(SequencePipelineProvider.NAME, c, u, jobName, root, params, new TaskId(FileAnalysisTaskPipeline.class, NAME), FOLDER_NAME);
    }

    public static List<IlluminaImportJob> create(Container c, User u, String jobName, String description, JSONObject params, Collection<File> inputFiles) throws ClassNotFoundException, IOException, PipelineValidationException
    {
        PipeRoot pr = PipelineService.get().findPipelineRoot(c);
        if (pr == null || !pr.isValid())
            throw new NotFoundException();

        List<IlluminaImportJob> ret = new ArrayList<>();
        for (File csv : inputFiles)
        {
            IlluminaImportJob job = new IlluminaImportJob(c, u, jobName, pr, params);
            job.setDescription(description);
            job.setInputFiles(Collections.singletonList(csv));
            ret.add(job);
        }

        return ret;
    }

    public static String NAME = "IlluminaImportPipeline";

    public static void register() throws CloneNotSupportedException
    {
        FileType csvFileType = new FileType(".csv", FileType.gzSupportLevel.NO_GZ);

        FileAnalysisTaskPipelineSettings settings = new FileAnalysisTaskPipelineSettings(NAME);
        settings.setAnalyzeURL("/sequenceAnalysis/instrumentImport.view?platform=ILLUMINA");
        settings.setDescription("Import Illumina data");
        settings.setProtocolName("Illumina Import");
        settings.setProtocolFactoryName("illuminaImport");
        settings.setInitialInputExt(csvFileType);
        settings.setTaskProgressionSpec(new Object[]{
                new TaskId(IlluminaImportTask.class),
                getXarGenerator().getId(),
                new TaskId(IlluminaReadsetCreationTask.class)
        });
        settings.setDeclaringModule(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class));
        settings.setDefaultDisplayState(PipelineActionConfig.displayState.enabled);

        PipelineJobService.get().addTaskPipeline(settings);
    }
}
