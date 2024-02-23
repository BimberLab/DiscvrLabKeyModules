package org.labkey.sequenceanalysis.pipeline;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineActionConfig;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipelineSettings;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceAnalysisServiceImpl;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 4/27/14
 * Time: 5:41 AM
 */
public class SequenceAlignmentJob extends SequenceJob
{
    public static final String FOLDER_NAME = "sequenceAnalysis";

    private int _readsetId;

    // Default constructor for serialization
    protected SequenceAlignmentJob()
    {
    }

    private SequenceAlignmentJob(Container c, User u, String jobName, PipeRoot root, JSONObject params, SequenceReadsetImpl readset) throws IOException
    {
        super(SequencePipelineProvider.NAME, c, u, jobName, root, params, new TaskId(FileAnalysisTaskPipeline.class, NAME), FOLDER_NAME);

        _readsetId = readset.getRowId();
        getSequenceSupport().cacheReadset(readset);
        writeSupportToDisk();
    }

    public static List<SequenceAlignmentJob> createForReadsets(Container c, User u, String jobName, String description, JSONObject params, JSONArray readsetIds, boolean submitJobToReadsetContainer) throws ClassNotFoundException, IOException, PipelineValidationException
    {
        Map<Container, PipeRoot> containerToPipeRootMap = new HashMap<>();

        List<SequenceAlignmentJob> ret = new ArrayList<>();
        for (int i=0;i<readsetIds.length();i++)
        {
            Integer readsetId = readsetIds.getInt(i);
            SequenceReadsetImpl readset = SequenceAnalysisServiceImpl.get().getReadset(readsetId, u);
            if (readset == null)
            {
                throw new PipelineValidationException("Unable to find readset: " + readsetId);
            }

            Container targetContainer = submitJobToReadsetContainer ? ContainerManager.getForId(readset.getContainer()) : c;

            PipeRoot pr = ReadsetImportJob.getPipeRoot(containerToPipeRootMap, targetContainer);
            SequenceAlignmentJob j = new SequenceAlignmentJob(targetContainer, u, jobName, pr, params, readset);
            j.setDescription(description);

            List<File> inputFiles = new ArrayList<>();
            for (ReadData rd : readset.getReadData())
            {
                if (rd.getFileId1() != null)
                {
                    ExpData d1 = ExperimentService.get().getExpData(rd.getFileId1());
                    if (d1 == null)
                    {
                        throw new PipelineValidationException("Missing FASTQ file: " + readsetId);
                    }
                    else
                    {
                        inputFiles.add(d1.getFile());
                    }
                }

                if (rd.getFileId2() != null)
                {
                    ExpData d2 = ExperimentService.get().getExpData(rd.getFileId2());
                    if (d2 == null)
                    {
                        throw new PipelineValidationException("Missing FASTQ file: " + readsetId);
                    }
                    else
                    {
                        inputFiles.add(d2.getFile());
                    }
                }
            }

            j.setInputFiles(inputFiles);

            ret.add(j);
        }

        return ret;
    }

    public int getReadsetId()
    {
        return _readsetId;
    }

    public SequenceReadsetImpl getReadset()
    {
        return getSequenceSupport().getCachedReadset(_readsetId);
    }

    public ReferenceGenome getTargetGenome()
    {
        return getSequenceSupport().getCachedGenomes().isEmpty() ? null : getSequenceSupport().getCachedGenomes().iterator().next();
    }

    public static final String NAME = "sequenceAnalysisPipeline";

    public static void register() throws CloneNotSupportedException
    {
        FileAnalysisTaskPipelineSettings settings = new FileAnalysisTaskPipelineSettings(NAME);
        settings.setAnalyzeURL("/sequenceAnalysis-sequenceAnalysis.view");
        settings.setDescription("Initiate analyses such as alignment or SNP calling on sequence data.");
        settings.setProtocolName("Sequence Analysis");
        settings.setProtocolFactoryName("sequenceAnalysis");
        settings.setInitialInputExt(SequenceUtil.FILETYPE.fastq.getFileType());
        settings.setTaskProgressionSpec(new Object[]{
                new TaskId(AlignmentInitTask.class),
                new TaskId(PrepareAlignerIndexesTask.class),
                new TaskId(SequenceAlignmentTask.class),
                getXarGenerator().getId(),
                new TaskId(SequenceAnalysisTask.class)
        });
        settings.setDeclaringModule(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class));
        settings.setDefaultDisplayState(PipelineActionConfig.displayState.disabled);

        PipelineJobService.get().addTaskPipeline(settings);
    }
}
