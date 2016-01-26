package org.labkey.galaxyintegration.pipeline;

import com.github.jmchilton.blend4j.galaxy.GalaxyInstance;
import com.github.jmchilton.blend4j.galaxy.GalaxyInstanceFactory;
import com.github.jmchilton.blend4j.galaxy.beans.Dataset;
import com.github.jmchilton.blend4j.galaxy.beans.HistoryContentsProvenance;
import com.github.jmchilton.blend4j.galaxy.beans.Job;
import com.github.jmchilton.blend4j.galaxy.beans.JobDetails;
import com.github.jmchilton.blend4j.galaxy.beans.JobInputOutput;
import com.github.jmchilton.blend4j.galaxy.beans.Tool;
import com.github.jmchilton.blend4j.galaxy.beans.ToolSection;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.security.User;
import org.labkey.galaxyintegration.GalaxyIntegrationManager;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 6/5/2015.
 */
public class GalaxyProvenanceImporterTask
{
    private static final Logger _log = Logger.getLogger(GalaxyProvenanceImporterTask.class);

    private GalaxyInstance _gi = null;
    private Map<String, Tool> _toolMap = null;
    private Set<String> _encounteredJobs = new HashSet<>();

    private User _user;
    private Container _container;
    private String _galaxyHost;
    private String _apiKey;
    private String _historyId;
    private String _datasetId;
    private String _runName;
    private File _analysisDir;

    public GalaxyProvenanceImporterTask(User u, Container c, String hostName, String apiKey, String historyId, String datasetId, String runName)
    {
        _user = u;
        _container = c;
        _galaxyHost = hostName;
        _apiKey = apiKey;
        _historyId = historyId;
        _datasetId = datasetId;
        _runName = runName;
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        _log.info("using galaxy host: [" + _galaxyHost + "]");
        JSONObject json = GalaxyIntegrationManager.get().getServerSettings(_user, _galaxyHost);
        if (json == null)
        {
            throw new PipelineJobException("Unable to find saved information for host: " +  _galaxyHost + " for user: " + _user.getDisplayName(_user));
        }

        String url = json.getString("url");
        if (StringUtils.trimToNull(url) == null)
        {
            throw new PipelineJobException("No url saved for host: " +  _galaxyHost + " for user: " + _user.getDisplayName(_user));
        }

        _log.info("generating server with baseUrl: " + url);

        _gi = GalaxyInstanceFactory.get(url, _apiKey);

        LinkedHashSet<JobDetails> jobs = getJobsToImport();
        List<RecordedAction> actions = convertJobsToActions(jobs);

        return new RecordedActionSet(actions);
    }

    public GalaxyInstance getGalaxyInstance()
    {
        return _gi;
    }

    private String getHistoryId()
    {
        return _historyId;
    }

    private String getDatasetId()
    {
        return _datasetId;
    }

    private Tool resolveTool(String toolId)
    {
        if (_toolMap == null)
        {
            Map<String, Tool> m = new HashMap<>();
            for (ToolSection ts : getGalaxyInstance().getToolsClient().getTools())
            {
                if (ts.getElems() != null)
                {
                    for (Tool t : ts.getElems())
                    {
                        m.put(t.getId(), t);
                    }
                }
            }

            _toolMap = m;
        }

        return _toolMap.get(toolId);
    }

    private LinkedHashSet<JobDetails> getJobsToImport() throws PipelineJobException
    {
        LinkedHashSet<JobDetails> ret = new LinkedHashSet<>();

        Dataset ds = getGalaxyInstance().getHistoriesClient().showDataset(getHistoryId(), getDatasetId());

        appendJob(ds, ret);

        return ret;
    }

    private void appendJob(Dataset ds, LinkedHashSet<JobDetails> jobs) throws PipelineJobException
    {
        HistoryContentsProvenance prov = getGalaxyInstance().getHistoriesClient().showProvenance(getHistoryId(), ds.getId());
        JobDetails job = getGalaxyInstance().getJobsClient().showJob(prov.getJobId());

        if (_encounteredJobs.contains(job.getId()))
        {
            //_log.warn("already encountered job: " + job.getId());
            return;
        }
        _encounteredJobs.add(job.getId());

        //NOTE: galaxy seems to have odd handling of the initial upload step, and including this is resulting in run graphs with inappropriate inputs included
        if (job.getToolId().equals("upload1"))
        {
            _log.info("skipping upload file job");
            return;
        }

        jobs.add(job);

        Map<String, JobInputOutput> inputs = job.getInputs();
        for (String name : inputs.keySet())
        {
            JobInputOutput jio = inputs.get(name);
            try
            {
                Dataset dataset = getGalaxyInstance().getHistoriesClient().showDataset(getHistoryId(), jio.getId());
                if (dataset == null)
                {
                    _log.error("unable to find dataset for jobId: " + jio.getId() + ", with source: " + jio.getSource());
                    continue;
                }

                appendJob(dataset, jobs);
            }
            catch (Exception e)
            {
                _log.error("unable to retrieve dataset from job input/output: " + jio.getId() + ", url: [" + jio.getUrl() + "]");

                throw e;
            }
        }

        Map<String, JobInputOutput> outputs = job.getOutputs();
        for (String name : outputs.keySet())
        {
            JobInputOutput jio = outputs.get(name);
            Dataset dataset = getGalaxyInstance().getHistoriesClient().showDataset(getHistoryId(), jio.getId());
            if (dataset == null)
            {
                _log.error("unable to find dataset for jobId: " + jio.getId() + ", with source: " + jio.getSource());
                continue;
            }

            appendJob(dataset, jobs);
        }
    }

    private List<RecordedAction> convertJobsToActions(LinkedHashSet<JobDetails> jobs)
    {
        List<RecordedAction> ret = new ArrayList<>();
        for (JobDetails job : jobs)
        {
            ret.add(convertJobToAction(job));
        }

        return ret;
    }

    private RecordedAction convertJobToAction(JobDetails job)
    {
        Tool tool = resolveTool(job.getToolId());
        RecordedAction action = new RecordedAction(tool == null ? job.getToolId() : tool.getName());

        action.setDescription(action.getName());
        action.setStartTime(job.getCreated());
        action.setEndTime(job.getUpdated());

        if (job.getParams() != null)
        {
            for (String paramName : job.getParams().keySet())
            {
                RecordedAction.ParameterType paramType = new RecordedAction.ParameterType(paramName, PropertyType.STRING);
                action.addParameter(paramType, job.getParams().get(paramName).toString());
            }
        }

        if (job.getCommandLine() != null)
        {
            RecordedAction.ParameterType paramType = new RecordedAction.ParameterType("Galaxy Command Line", PropertyType.STRING);
            action.addParameter(paramType, job.getCommandLine());
        }

        if (job.getToolId() != null)
        {
            RecordedAction.ParameterType paramType = new RecordedAction.ParameterType("Galaxy Tool Id", PropertyType.STRING);
            action.addParameter(paramType, job.getToolId().toString());
        }

        if (tool != null && tool.getVersion() != null)
        {
            RecordedAction.ParameterType paramType = new RecordedAction.ParameterType("Galaxy Tool Version", PropertyType.STRING);
            action.addParameter(paramType, tool.getVersion());
        }

        //then add inputs/outputs
        Map<String, JobInputOutput> inputs = job.getInputs();
        for (String name : inputs.keySet())
        {
            JobInputOutput jio = inputs.get(name);
            Dataset dataset = getGalaxyInstance().getHistoriesClient().showDataset(getHistoryId(), jio.getId());
            if (dataset == null)
            {
                _log.error("unable to find dataset for jobId: " + jio.getId() + ", with source: " + jio.getSource());
                continue;
            }

            //NOTE: this could potentially use dataset.getName(); however, this is often exactly the same as job name
            action.addInput(getURI(dataset), name);
        }

        Map<String, JobInputOutput> outputs = job.getOutputs();
        for (String name : outputs.keySet())
        {
            JobInputOutput jio = outputs.get(name);
            Dataset dataset = getGalaxyInstance().getHistoriesClient().showDataset(getHistoryId(), jio.getId());
            if (dataset == null)
            {
                _log.error("unable to find dataset for jobId: " + jio.getId() + ", with source: " + jio.getSource());
                continue;
            }

            action.addOutput(getURI(dataset), name, true);
        }

        return action;
    }

    private URI getURI(Dataset ds)
    {
        try
        {
            return new URI(ds.getGalaxyUrl() + ds.getUrl());
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }
}