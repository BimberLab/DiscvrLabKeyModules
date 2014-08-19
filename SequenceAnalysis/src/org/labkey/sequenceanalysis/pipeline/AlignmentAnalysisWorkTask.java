package org.labkey.sequenceanalysis.pipeline;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;
import org.labkey.sequenceanalysis.api.pipeline.AnalysisStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/4/2014.
 */
public class AlignmentAnalysisWorkTask extends WorkDirectoryTask<AlignmentAnalysisWorkTask.Factory>
{
    private SequenceTaskHelper _taskHelper;

    protected AlignmentAnalysisWorkTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(AlignmentAnalysisWorkTask.class);
        }

        public String getStatusName()
        {
            return "PERFORMING ANALYSIS";
        }

        public List<String> getProtocolActionNames()
        {
            List<String> allowableNames = new ArrayList<>();
            for (PipelineStepProvider provider: SequencePipelineService.get().getProviders(AnalysisStep.class))
            {
                allowableNames.add(provider.getLabel());
            }

            return allowableNames;
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            AlignmentAnalysisWorkTask task = new AlignmentAnalysisWorkTask(this, job);
            return task;
        }

        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(new FileType(".bam"));
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        _taskHelper = new SequenceTaskHelper(getJob(), _wd);
        Helper analysisHelper = new Helper(_taskHelper);

        //TODO: this is debugging only
        Map<Integer, File> cachedFiles = getTaskHelper().getSequenceSupport().getAllCachedData();
        getJob().getLogger().debug("total ExpDatas cached: " + cachedFiles.size());
        for (Integer dataId : cachedFiles.keySet())
        {
            getJob().getLogger().debug("file was cached: " + dataId + " / " + cachedFiles.get(dataId).getPath());
        }

        List<RecordedAction> actions = new ArrayList<>();

        List<PipelineStepProvider<AnalysisStep>> providers = SequencePipelineService.get().getSteps(getJob(), AnalysisStep.class);
        if (providers.isEmpty())
        {
            throw new PipelineJobException("No analysis selected, nothing to do");
        }

        getJob().getLogger().info("Processing Alignments");
        Map<String, AnalysisModel> alignmentMap = analysisHelper.getAnalysisMap();
        for (File inputBam : getTaskHelper().getSupport().getInputFiles())
        {
            AnalysisModel m = alignmentMap.get(inputBam.getName());
            if (m == null)
            {
                throw new PipelineJobException("Unable to find analysis details for file: " + inputBam.getName());
            }

            File refFasta = m.getReferenceLibraryFile();
            if (PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer)
            {
                PathMapper mapper = PipelineJobService.get().getPathMapper();
                if (mapper != null)
                {
                    getJob().getLogger().debug("attempting to translate filepath");
                    getJob().getLogger().debug("original file: " + refFasta.getPath());
                    refFasta = new File(mapper.localToRemote(refFasta.toURI().toString()));
                    getJob().getLogger().debug("translated file: " + refFasta.getPath());
                }
            }

            SequenceAnalysisTask.runAnalyses(actions, m, inputBam, refFasta, providers, getTaskHelper());
        }

        return new RecordedActionSet(actions);
    }

    public SequenceTaskHelper getTaskHelper()
    {
        return _taskHelper;
    }

    public static class Helper
    {
        private SequenceTaskHelper _taskHelper;
        private Map<Integer, AnalysisModel> _cachedModels = new HashMap<>();

        public Helper(SequenceTaskHelper taskHelper)
        {
            _taskHelper = taskHelper;
        }

        public Map<String, AnalysisModel> getAnalysisMap() throws PipelineJobException
        {
            Map<String, AnalysisModel> ret = new HashMap<>();
            for (String key : _taskHelper.getJob().getParameters().keySet())
            {
                if (key.startsWith("sample_"))
                {
                    JSONObject o = new JSONObject(_taskHelper.getJob().getParameters().get(key));
                    Integer analysisId = o.getInt("analysisid");
                    if (PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.WebServer)
                    {
                        AnalysisModel m = AnalysisModelImpl.getFromDb(analysisId, _taskHelper.getJob().getUser());
                        ExpData d = m.getAlignmentData();
                        if (d == null)
                        {
                            _taskHelper.getLogger().error("Analysis lacks an alignment file: " + m.getRowId());
                            continue;
                        }

                        ret.put(d.getFile().getName(), m);
                    }
                    else
                    {
                        getCachedAnalysisModels();
                        if (_cachedModels.containsKey(analysisId))
                        {
                            AnalysisModel m = _cachedModels.get(analysisId);
                            File bam = m.getAlignmentFileObject();
                            if (bam != null)
                            {
                                ret.put(bam.getName(), m);
                            }
                            else
                            {
                                _taskHelper.getLogger().error("Unable to find BAM for analysis: " + m.getRowId());
                            }
                        }
                    }
                }
            }

            return ret;
        }

        private File getCachedAnalysesFile()
        {
            return new File(_taskHelper.getSupport().getAnalysisDirectory(), "analyses.json");
        }

        public void cacheAnalysisModels() throws PipelineJobException
        {
            try
            {
                Map<String, AnalysisModel> map = getAnalysisMap();
                JSONArray arr = new JSONArray();
                for (AnalysisModel m : map.values())
                {
                    arr.put(m.toJSON());

                    _taskHelper.getSequenceSupport().cacheExpData(m.getAlignmentData());
                    _taskHelper.getSequenceSupport().cacheExpData(m.getReferenceLibraryData());
                }

                _taskHelper.getLogger().info("saving analysis details to file");
                File output = getCachedAnalysesFile();
                if (!output.exists())
                {
                    output.createNewFile();
                }

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(output)))
                {
                    writer.write(arr.toString(1));
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        public Map<Integer, AnalysisModel> getCachedAnalysisModels() throws PipelineJobException
        {
            if (_cachedModels != null)
            {
                return _cachedModels;
            }

            _taskHelper.getLogger().info("retrieving saved analyses");
            File output = getCachedAnalysesFile();
            if (!output.exists())
            {
                throw new PipelineJobException("cached analyses file not found, expected: " + output.getPath());
            }

            try (BufferedReader reader = new BufferedReader( new FileReader(output)))
            {
                String line;
                StringBuilder stringBuilder = new StringBuilder();
                String ls = System.getProperty("line.separator");

                while ((line = reader.readLine()) != null)
                {
                    stringBuilder.append(line);
                    stringBuilder.append(ls);
                }

                Map<Integer, AnalysisModel> ret = new HashMap<>();
                JSONArray arr = new JSONArray(stringBuilder.toString());
                for (JSONObject o : arr.toJSONObjectArray())
                {
                    AnalysisModel m = AnalysisModelImpl.fromJson(o);
                    ret.put(m.getRowId(), m);
                }

                return ret;
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }
}
