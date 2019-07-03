package org.labkey.cluster.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.cluster.ClusterResourceAllocator;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.pipeline.file.PathMapperImpl;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.cluster.ClusterServiceImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by bimber on 7/11/2017.
 */
abstract class AbstractClusterEngineConfig implements PipelineJobService.RemoteExecutionEngineConfig
{
    protected PathMapper _pathMapper = new PathMapperImpl();
    protected String _workingDir = "/pipeline";
    protected Integer _requestCpus = 24;
    protected Integer _requestMemory = 48;
    protected String _javaHome = null;
    protected List<String> _environmentVars = null;
    protected List<String> _javaOpts = null;
    protected String _labKeyDir = "/labkey";
    protected String _location = "cluster";
    protected String _remoteExecutable = "/usr/local/java_current/bin/java";
    protected List<String> _extraSubmitScriptLines = null;

    protected String _submitCommand;
    protected String _statusCommand;
    protected String _removeCommand;
    protected String _historyCommand;

    public void setRemoteExecutable(String remoteExecutable)
    {
        _remoteExecutable = remoteExecutable;
    }

    @NotNull
    @Override
    public String getLocation()
    {
        return _location;
    }

    public String getClusterUser(Container c)
    {
        return ClusterServiceImpl.get().getClusterUser(c);
    }

    @Override
    @NotNull
    public PathMapper getPathMapper()
    {
        return _pathMapper;
    }

    public String getWorkingDir()
    {
        return _workingDir;
    }

    public Integer getRequestCpus()
    {
        return _requestCpus;
    }

    public Integer getRequestMemory()
    {
        return _requestMemory;
    }

    public String getLabKeyDir()
    {
        return _labKeyDir == null || !_labKeyDir.endsWith("/") ? _labKeyDir : _labKeyDir.substring(0, _labKeyDir.length()-1);
    }

    public String getRemoteExecutable()
    {
        return _remoteExecutable;
    }

    protected List<String> getFinalJavaOpts(PipelineJob job, RemoteExecutionEngine engine)
    {
        List<String> javaOpts = new ArrayList<>();
        if (_javaOpts != null)
        {
            javaOpts.addAll(_javaOpts);
        }

        List<ClusterResourceAllocator.Factory> allocatorFactories = ClusterServiceImpl.get().getAllocators(job.getActiveTaskId());
        for (ClusterResourceAllocator.Factory allocatorFact : allocatorFactories)
        {
            ClusterResourceAllocator allocator = allocatorFact.getAllocator();
            job.getLogger().debug("using resource allocator: " + allocator.getClass().getName());
            allocator.processJavaOpts(job, engine, javaOpts);
        }

        return javaOpts;
    }

    public List<String> getJobArgs(File localPipelineDir, File localSerializedJobXmlFile, PipelineJob job, RemoteExecutionEngine engine)
    {
        List<String> ret = new ArrayList<>();
        ret.addAll(getFinalJavaOpts(job, engine));

        //TODO: support a _debug flag
        //"-Xdebug",
        //"-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",

        ret.addAll(Arrays.asList(
                "-cp",
                getLabKeyDir() + "/labkeyBootstrap.jar",
                "org.labkey.bootstrap.ClusterBootstrap",
                "-modulesdir=" + getLabKeyDir() + "/modules",
                "-webappdir=" + getLabKeyDir() + "/labkeywebapp",
                "-configdir=" + getLabKeyDir() + "/config",
                getClusterPath(localSerializedJobXmlFile, true)
        ));

        return ret;
    }

    public void setPathMapper(PathMapper pathMapper)
    {
        _pathMapper = pathMapper;
    }

    public void setRequestCpus(Integer requestCpus)
    {
        _requestCpus = requestCpus;
    }

    public void setRequestMemory(Integer requestMemory)
    {
        _requestMemory = requestMemory;
    }

    public void setLabKeyDir(String labKeyDir)
    {
        _labKeyDir = labKeyDir;
    }

    public void setLocation(String location)
    {
        _location = location;
    }

    public String getClusterPath(File localFile)
    {
        return getClusterPath(localFile, false);
    }

    public String getClusterPath(File localFile, boolean asURI)
    {
        //TODO: verify this
        // This PathMapper considers "local" from a cluster node's point of view.
        String ret = getPathMapper().remoteToLocal(localFile.getAbsoluteFile().toURI().toString());

        if (ret != null && !asURI)
        {
            ret = ret.replaceFirst("^file:/", "/");
            ret = ret.replaceAll("$/+", "/");
        }

        return ret;
    }

    public void setWorkingDir(String workingDir)
    {
        _workingDir = workingDir;
    }

    public String getJavaHome()
    {
        return _javaHome;
    }

    public void setJavaHome(String javaHome)
    {
        _javaHome = javaHome;
    }

    public void setJavaOpts(List<String> javaOpts)
    {
        _javaOpts = javaOpts;
    }

    public List<String> getEnvironmentVars()
    {
        return _environmentVars;
    }

    public void setEnvironmentVars(List<String> environmentVars)
    {
        _environmentVars = environmentVars;
    }

    public StringExpression getSubmitCommandExpr()
    {
        if (_submitCommand == null)
        {
            throw new IllegalArgumentException("submitCommand is null for type: " + getType());
        }

        return StringExpressionFactory.create(_submitCommand);
    }

    public StringExpression getStatusCommandExpr()
    {
        if (_statusCommand == null)
        {
            throw new IllegalArgumentException("statusCommand is null for type: " + getType());
        }

        return StringExpressionFactory.create(_statusCommand);
    }

    public StringExpression getRemoveCommandExpr()
    {
        if (_removeCommand == null)
        {
            throw new IllegalArgumentException("removeCommand is null for type: " + getType());
        }

        return StringExpressionFactory.create(_removeCommand);
    }

    public StringExpression getHistoryCommandExpr()
    {
        if (_historyCommand == null)
        {
            throw new IllegalArgumentException("historyCommand is null for type: " + getType());
        }

        return StringExpressionFactory.create(_historyCommand);
    }

    public void setSubmitCommand(String submitCommand)
    {
        _submitCommand = submitCommand;
    }

    public void setStatusCommand(String statusCommand)
    {
        _statusCommand = statusCommand;
    }

    public void setRemoveCommand(String removeCommand)
    {
        _removeCommand = removeCommand;
    }

    public void setHistoryCommand(String historyCommand)
    {
        _historyCommand = historyCommand;
    }

    public List<String> getExtraSubmitLines()
    {
        List<String> ret = new ArrayList<>();
        if (_extraSubmitScriptLines != null)
        {
            ret.addAll(_extraSubmitScriptLines);
        }

        return ret;
    }

    public void setExtraSubmitScriptLines(List<String> extraSubmitScriptLines)
    {
        _extraSubmitScriptLines = extraSubmitScriptLines;
    }
}
