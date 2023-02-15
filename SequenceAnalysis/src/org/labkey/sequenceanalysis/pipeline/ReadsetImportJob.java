package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
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
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.view.NotFoundException;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.util.NucleotideSequenceFileType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // Default constructor for serialization
    protected ReadsetImportJob()
    {
    }

    public static List<ReadsetImportJob> create(Container c, User u, String jobName, String description, JSONObject params, List<File> inputFiles) throws PipelineJobException, IOException, PipelineValidationException
    {
        Map<Container, PipeRoot> containerToPipeRootMap = new HashMap<>();

        //NOTE: for simple file import, where the input files will be unaltered and there is no possiblity of overlap between data across readsets, split this into one job per readset.  this allows us to more cleanly delete jobs later, and also open the potential to target by container
        boolean potentiallySplit = true;
        if (params.optBoolean("inputfile.merge", false))
        {
            potentiallySplit = false;
        }
        else if (params.optBoolean("inputfile.barcode", false))
        {
            potentiallySplit = false;
        }


        if (!potentiallySplit)
        {
            PipeRoot pr = getPipeRoot(containerToPipeRootMap, c);

            ReadsetImportJob job = new ReadsetImportJob(c, u, jobName, pr, params);
            job.setDescription(description);
            job.setInputFiles(inputFiles);

            return Arrays.asList(job);
        }
        else
        {
            List<ReadsetImportJob> ret = new ArrayList<>();

            Map<String, JSONObject> readsetKeys = new HashMap<>();
            Map<String, JSONObject> fileGroupKeys = new HashMap<>();
            Set<String> keysToRemove = new HashSet<>();
            keysToRemove.add("inputFiles");

            params.keySet().forEach(x -> {
                if (x.startsWith("readset_"))
                {
                    readsetKeys.put(x.split("_")[1], new JSONObject(params.get(x).toString()));
                    keysToRemove.add(x);
                }
                else if (x.startsWith("fileGroup_"))
                {
                    JSONObject json = new JSONObject(params.get(x).toString());
                    fileGroupKeys.put(json.getString("name"), json);
                    keysToRemove.add(x);
                }
            });

            for (JSONObject rs : readsetKeys.values())
            {
                JSONObject rsParams = new JSONObject(params.toString());
                keysToRemove.forEach(rsParams::remove);

                rsParams.put("readset_1", rs.toString());

                JSONObject fileGroup = fileGroupKeys.get(rs.getString("fileGroupId"));
                if (fileGroup == null)
                {
                    throw new IllegalArgumentException("Unable to find file group with ID: " + rs.getString("fileGroupId"));
                }

                rsParams.put("fileGroup_1", fileGroup.toString());

                Container targetContainer = c;
                boolean usedAlternateFolder = false;
                if (rs.get("readset") != null && StringUtils.trimToNull(rs.get("readset").toString()) != null)
                {
                    int readsetId = ConvertHelper.convert(rs.getInt("readset"), Integer.class);
                    Readset readset = SequenceAnalysisService.get().getReadset(readsetId, u);
                    targetContainer = ContainerManager.getForId(readset.getContainer());
                    usedAlternateFolder = true;
                }

                PipeRoot pr = getPipeRoot(containerToPipeRootMap, targetContainer);
                ReadsetImportJob job = new ReadsetImportJob(targetContainer, u, jobName, pr, rsParams);
                job.setDescription(description);
                if (usedAlternateFolder)
                {
                    job.getLogger().debug("job was submitted to an alternate folder: " + targetContainer.getPath());
                }

                List<File> inputFilesSubset = new ArrayList<>();
                JSONArray files = fileGroup.getJSONArray("files");
                for (int i = 0; i < files.length(); i++)
                {
                    JSONObject file1 = files.getJSONObject(i).getJSONObject("file1");
                    inputFilesSubset.add(findFile(file1, inputFiles));

                    if (files.getJSONObject(i).has("file2") && !files.getJSONObject(i).isNull("file2"))
                    {
                        JSONObject file2 = files.getJSONObject(i).getJSONObject("file2");
                        inputFilesSubset.add(findFile(file2, inputFiles));
                    }
                }

                if (inputFilesSubset.isEmpty())
                {
                    throw new IllegalArgumentException("Unable to find input files");
                }

                job.setInputFiles(inputFilesSubset);

                ret.add(job);
            }

            return ret;
        }
    }

    private static File findFile(JSONObject file, List<File> inputFiles)
    {
        if (!file.isNull("dataId") && StringUtils.trimToNull(file.get("dataId").toString()) != null)
        {
            try
            {
                int dataId = ConvertHelper.convert(file.get("dataId"), Integer.class);
                ExpData d = ExperimentService.get().getExpData(dataId);
                if (d == null)
                {
                    throw new IllegalArgumentException("Unable to find file with ID: " + file.getInt("dataId"));
                }

                return d.getFile();
            }
            catch (ConversionException | NullPointerException e)
            {
                throw new IllegalArgumentException("dataId is not an integer: " + "[" + file.get("dataId") + "]");
            }
        }
        else if (!file.isNull("fileName") && StringUtils.trimToNull(file.getString("fileName")) != null)
        {
            List<File> hits = new ArrayList<>();
            inputFiles.forEach(x -> {
                if (x.getName().equals(file.getString("fileName")))
                {
                    hits.add(x);
                }
            });

            if (hits.size() > 1)
            {
                throw new IllegalArgumentException("Ambiguous filename: " + file.get("fileName"));
            }
            else if (hits.isEmpty())
            {
                throw new IllegalArgumentException("No matching files: " + file.get("fileName"));
            }

            return hits.get(0);
        }

        throw new IllegalArgumentException("Unable to find file");
    }

    public static PipeRoot getPipeRoot(Map<Container, PipeRoot> containerToPipeRootMap, Container targetContainer)
    {
        PipeRoot pr = containerToPipeRootMap.get(targetContainer);
        if (pr == null)
        {
            pr = PipelineService.get().findPipelineRoot(targetContainer);
            if (pr == null || !pr.isValid())
                throw new NotFoundException();

            containerToPipeRootMap.put(targetContainer, pr);
        }

        return pr;
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
