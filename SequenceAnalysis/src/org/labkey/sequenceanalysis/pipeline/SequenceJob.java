package org.labkey.sequenceanalysis.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import htsjdk.samtools.util.IOUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.pipeline.XarGeneratorFactorySettings;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.HasJobParams;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputTracker;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/31/2016.
 */
public class SequenceJob extends PipelineJob implements FileAnalysisJobSupport, HasJobParams, SequenceOutputTracker
{
    private TaskId _taskPipelineId;
    private Integer _experimentRunRowId;
    private String _jobName;
    private String _description;
    private File _webserverJobDir;
    private File _parentWebserverJobDir;
    private String _folderPrefix;
    private List<File> _inputFiles;
    private List<SequenceOutputFile> _outputsToCreate = new ArrayList<>();
    private PipeRoot _folderFileRoot;

    transient private JSONObject _params;

    // NOTE: this allows optional deserializing of job JSON with this property,
    // to support pre-existing JSON before this change
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    transient private SequenceJobSupportImpl _support;

    // Default constructor for serialization
    protected SequenceJob()
    {
    }

    protected SequenceJob(SequenceJob parentJob, String jobName, String subdirectory) throws IOException
    {
        super(parentJob);
        _taskPipelineId = parentJob._taskPipelineId;
        _experimentRunRowId = parentJob._experimentRunRowId;
        _jobName = jobName;
        _description = parentJob._description;
        _support = parentJob.getSequenceSupport();
        _parentWebserverJobDir = parentJob._webserverJobDir;
        _webserverJobDir = new File(parentJob._webserverJobDir, subdirectory);
        if (!_webserverJobDir.exists())
        {
            _webserverJobDir.mkdirs();
        }

        _folderPrefix = parentJob._folderPrefix;
        _inputFiles = parentJob._inputFiles;
        _folderFileRoot = parentJob._folderFileRoot;

        _params = parentJob.getParameterJson();

        setLogFile(_getLogFile());
        writeSupportToDisk();
    }

    public SequenceJob(String providerName, Container c, User u, @Nullable String jobName, PipeRoot pipeRoot, JSONObject params, TaskId taskPipelineId, String folderPrefix) throws IOException
    {
        super(providerName, new ViewBackgroundInfo(c, u, null), pipeRoot);

        _support = new SequenceJobSupportImpl();

        _jobName = jobName;
        _taskPipelineId = taskPipelineId;
        _folderPrefix = folderPrefix;
        _webserverJobDir = createLocalDirectory(pipeRoot);

        addCustomParams(params);
        _params = params;

        writeParameters(params);

        _folderFileRoot = c.isWorkbook() ? PipelineService.get().findPipelineRoot(c.getParent()) : pipeRoot;

        setLogFile(_getLogFile());
        writeSupportToDisk();
    }

    @Override
    public boolean setActiveTaskStatus(@NotNull TaskStatus activeTaskStatus)
    {
        if (TaskStatus.complete == activeTaskStatus)
        {
            writeSupportToDiskIfNeeded();
        }

        return super.setActiveTaskStatus(activeTaskStatus);
    }

    protected void addCustomParams(JSONObject params)
    {
        params.put("serverBaseUrl", AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath());
        params.put("labkeyFolderPath", getContainer().isWorkbook() ? getContainer().getParent().getPath() : getContainer().getPath());
    }

    private Path _getLogFile()
    {
        return AssayFileWriter.findUniqueFileName((FileUtil.makeLegalName(_jobName) + ".log"), getDataDirectory().toPath());
    }

    @Override
    public TaskPipeline<?> getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(_taskPipelineId);
    }

    protected void setInputFiles(Collection<File> inputs)
    {
        _inputFiles = inputs == null ? Collections.emptyList() : new ArrayList<>(inputs);
    }

    @Override
    public List<File> getInputFiles()
    {
        return _inputFiles == null ? Collections.emptyList() : Collections.unmodifiableList(_inputFiles);
    }

    public void setFolderFileRoot(PipeRoot folderFileRoot)
    {
        _folderFileRoot = folderFileRoot;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    protected File createLocalDirectory(PipeRoot pipeRoot) throws IOException
    {
        File webserverOutDir = new File(pipeRoot.getRootPath(), _folderPrefix + "Pipeline");
        if (!webserverOutDir.exists())
        {
            webserverOutDir.mkdir();
        }

        String folderName = FileUtil.makeLegalName(StringUtils.capitalize(_folderPrefix) + "_" + FileUtil.getTimestamp());
        webserverOutDir = AssayFileWriter.findUniqueFileName(folderName, webserverOutDir);
        if (!webserverOutDir.exists())
        {
            webserverOutDir.mkdirs();
        }

        return webserverOutDir;
    }

    protected void writeParameters(JSONObject params) throws IOException
    {
        try (PrintWriter writer = PrintWriters.getPrintWriter(getParametersFile()))
        {
            writer.write(params.isEmpty() ? "" : params.toString(1));
        }
    }

    @Override
    public Map<String, String> getJobParams()
    {
        return getParameters();
    }

    @Override
    public JSONObject getParameterJson()
    {
        if (_params == null)
        {
            try
            {
                File paramFile = getParametersFile();
                if (paramFile.exists())
                {
                    try (BufferedReader reader = Readers.getReader(getParametersFile()))
                    {
                        List<String> lines = IOUtils.readLines(reader);

                        _params = lines.isEmpty() ? new JSONObject() : new JSONObject(StringUtils.join(lines, '\n'));
                    }
                }
                else
                {
                    getLogger().error("parameter file not found: " + paramFile.getPath());
                }
            }
            catch (IOException e)
            {
                getLogger().error("Unable to find file: " + getParametersFile().getPath(), e);
            }
        }

        return _params;
    }

    @Override
    public Map<String, String> getParameters()
    {
        Map<String, String> ret = new HashMap<>();
        JSONObject json = getParameterJson();
        if (json == null)
        {
            getLogger().error("getParameterJson() was null for job: " + getDescription());
            return null;
        }

        for (String key : json.keySet())
        {
            ret.put(key, json.opt(key) == null ? null : String.valueOf(json.get(key)));
        }

        return ret;
    }

    @Override
    public String getDescription()
    {
        return _jobName;
    }

    public String getJobName()
    {
        return _jobName;
    }

    @Override
    public String getProtocolName()
    {
        return _jobName;
    }

    @Override
    public String getJoinedBaseName()
    {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public List<String> getSplitBaseNames()
    {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public String getBaseName()
    {
        return _webserverJobDir.getName();
    }

    @Override
    public String getBaseNameForFileType(FileType fileType)
    {
        return getBaseName();
    }

    @Override
    public File getDataDirectory()
    {
        return _webserverJobDir;
    }

    public File getWebserverDir(boolean forceParent)
    {
        return forceParent && isSplitJob() ? _parentWebserverJobDir : _webserverJobDir;
    }

    @Override
    public File getAnalysisDirectory()
    {
        return _webserverJobDir;
    }

    @Override
    public File findOutputFile(String name)
    {
        return findFile(name);
    }

    @Override
    public File findInputFile(String name)
    {
        return findFile(name);
    }


    @Override
    public File findOutputFile(@NotNull String outputDir, @NotNull String fileName)
    {
        return AbstractFileAnalysisJob.getOutputFile(outputDir, fileName, getPipeRoot(), getLogger(), getAnalysisDirectory());
    }

    @Override
    public ParamParser createParamParser()
    {
        return PipelineJobService.get().createParamParser();
    }

    @Override
    public File getParametersFile()
    {
        return new File(_parentWebserverJobDir == null ? _webserverJobDir : _parentWebserverJobDir, _folderPrefix + ".json");
    }

    @Nullable
    @Override
    public File getJobInfoFile()
    {
        return new File(_webserverJobDir, FileUtil.makeLegalName(_jobName) + ".job.json");
    }

    @Override
    public FileType.gzSupportLevel getGZPreference()
    {
        return null;
    }

    public SequenceJobSupportImpl getSequenceSupport()
    {
        if (_support == null)
        {
            try
            {
                _support = readSupportFromDisk();
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("Error reading cached support from file: " + getCachedSupportFile().getPath());
            }
        }

        return _support;
    }

    protected File getCachedSupportFile()
    {
        return new File(getLogFile().getParentFile(), "sequenceSupport.json.gz");
    }

    private SequenceJobSupportImpl readSupportFromDisk() throws IOException
    {
        File json = getCachedSupportFile();
        if (json.exists())
        {
            try
            {
                if (!SequenceUtil.hasLineCount(json))
                {
                    getLogger().debug("serialized support JSON file is empty: " + json.getPath());
                    return null;
                }
            }
            catch (PipelineJobException e)
            {
                throw new IOException(e);
            }

            try (InputStream is = IOUtil.maybeBufferInputStream(IOUtil.openFileForReading(json)))
            {
                ObjectMapper objectMapper = createObjectMapper();
                SequenceJobSupportImpl ret = objectMapper.readValue(is, SequenceJobSupportImpl.class);
                getLogger().debug("read SequenceJobSupportImpl from file, total readsets: " + ret.getCachedReadsets().size());

                return ret;
            }
            catch (Exception e)
            {
                getLogger().error(e.getMessage(), e);
                getLogger().debug("contents of JSON file: " + json.getPath());
                try (BufferedReader reader = IOUtil.openFileForBufferedReading(json))
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        getLogger().debug(line);
                    }
                }
            }
        }
        else
        {
            getLogger().warn("Serialized support JSON file not found: " + json.getPath(), new Exception());
        }

        return null;
    }

    @Override
    public void writeToFile(File file) throws IOException
    {
        TaskFactory<?> factory = getActiveTaskFactory();
        if (factory != null && !factory.isJobComplete(this))
        {
            writeSupportToDiskIfNeeded();
        }

        super.writeToFile(file);
    }

    protected void writeSupportToDiskIfNeeded()
    {
        if (_support == null)
        {
            getLogger().debug("SequenceJobSupportImpl is null, will not write to disk");
            return;
        }

        File json = getCachedSupportFile();
        if (!json.exists() || _support.isModifiedSinceSerialize())
        {
            try
            {
                writeSupportToDisk();
            }
            catch (IOException e)
            {
                getLogger().error("Unable to serialize job support", e);
            }
        }
        else
        {
            getLogger().debug("SequenceSupport was not modified, will not re-serialize to disk");
        }
    }

    protected void writeSupportToDisk() throws IOException
    {
        if (_support != null)
        {
            getLogger().info("writing SequenceJobSupportImpl to JSON, with " + _support.getCachedReadsets().size() + " readsets, " + _support.getAllCachedData().size() + ", files");
            File json = getCachedSupportFile();
            _support.writeToDisk(json);
        }
        else
        {
            getLogger().debug("SequenceJobSupportImpl is null, will not write to disk");
        }
    }

    @Override
    public ActionURL getStatusHref()
    {
        if (_experimentRunRowId != null)
        {
            ExpRun run = ExperimentService.get().getExpRun(_experimentRunRowId.intValue());
            if (run != null)
                return PageFlowUtil.urlProvider(ExperimentUrls.class).getRunGraphDetailURL(run, null);
        }
        return null;
    }

    @Override
    public void clearActionSet(ExpRun run)
    {
        super.clearActionSet(run);

        _experimentRunRowId = run.getRowId();
    }

    public PipeRoot getFolderPipeRoot()
    {
        return _folderFileRoot;
    }

    public Integer getExperimentRunRowId()
    {
        return _experimentRunRowId;
    }

    public void setExperimentRunRowId(Integer experimentRunRowId)
    {
        _experimentRunRowId = experimentRunRowId;
    }

    public File findFile(String name)
    {
        return new File(getAnalysisDirectory(), name);
    }

    protected static XarGeneratorFactorySettings getXarGenerator() throws CloneNotSupportedException
    {
        XarGeneratorFactorySettings settings = new XarGeneratorFactorySettings("xarGeneratorJoin");
        settings.setJoin(true);

        TaskFactory<?> factory = PipelineJobService.get().getTaskFactory(settings.getCloneId());
        if (factory == null)
        {
            PipelineJobService.get().addTaskFactory(settings);
        }

        return settings;
    }

    public void addOutputToCreate(SequenceOutputFile o)
    {
        getLogger().debug("adding sequence output: " + (o.getFile() == null ? o.getName() : o.getFile().getPath()));
        _outputsToCreate.forEach(so -> {
            if (o.getFile().equals(so.getFile()) && o.getName().equals(so.getName()))
            {
                getLogger().warn("Adding sequence output, but another already exists for the same file: " + o.getFile().getPath());
            }
        });
        _outputsToCreate.add(o);
    }

    @Override
    public List<SequenceOutputFile> getOutputsToCreate()
    {
        if (_outputsToCreate == null)
        {
            getLogger().warn("job._outputsToCreate is null");
            _outputsToCreate = new ArrayList<>();
        }

        return _outputsToCreate;
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testSerializeSupport() throws Exception
        {
            ExpData d1 = ExperimentService.get().createData(ContainerManager.getHomeContainer(), new DataType("testCase"));
            d1.setDataFileURI(new File(FileUtil.getTempDirectory(), "foo.txt").toURI());

            SequenceJob job = new SequenceJob();
            job._support = new SequenceJobSupportImpl();
            job._support.cacheExpData(d1);
            job.setLogFile(new File(FileUtil.getTempDirectory(), "testJob.log").toPath());

            File testFile = new File(FileUtil.getTempDirectory(), "testJob.json.txt");
            File support = job.getCachedSupportFile();
            if (support.exists())
            {
                support.delete();
            }

            // This should re-create the cached file, even though support is not modified
            job.writeSupportToDiskIfNeeded();
            assertTrue("Missing support file", support.exists());
            long modified = support.lastModified();

            job.writeToFile(testFile);
            assertEquals("Serialized support should not have been modified", modified, support.lastModified());

            job._support = null;
            SequenceJobSupportImpl deserializedSupport = job.getSequenceSupport();
            assertEquals("Missing cached data", 1, deserializedSupport.getAllCachedData().size());

            testFile.delete();
            support.delete();

            job.writeToFile(testFile);
            SequenceJob deserializedJob = (SequenceJob)readFromFile(testFile);
            assertNull("Support not null after deserialize", deserializedJob._support);
        }
    }
}