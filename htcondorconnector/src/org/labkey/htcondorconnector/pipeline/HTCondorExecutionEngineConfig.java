package org.labkey.htcondorconnector.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.pipeline.file.PathMapperImpl;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 10/31/2015.
 */
public class HTCondorExecutionEngineConfig implements PipelineJobService.RemoteExecutionEngineConfig
{
    //these are for example purposes only
    protected String _submitCommand = "ssh ${user}@myCluster.edu '/opt/condor/bin/condor_submit ${submitScript}'";
    protected String _statusCommand = "ssh ${user}@myCluster.edu '/opt/condor/bin/condor_q owner ${user}'";
    protected String _removeCommand = "ssh ${user}@myCluster.edu '/opt/condor/bin/condor_rm ${condorId}'";
    protected String _historyCommand = "ssh ${user}@myCluster.edu '/opt/condor/bin/condor_history ${condorId}'";
    protected String _condorUser = "labkey";
    protected PathMapper _pathMapper = new PathMapperImpl();
    protected String _remoteExecutable = "/usr/local/java_current/bin/java";
    protected String _workingDir = "/pipeline";
    protected Integer _requestCpus = 24;
    protected String _requestMemory = "48 GB";
    protected String _labKeyDir = "/labkey";
    protected String _location = "cluster";
    protected String _javaHome = null;
    protected List<String> _javaOpts = null;

    public StringExpression getSubmitCommandExpr()
    {
        return StringExpressionFactory.create(_submitCommand);
    }

    public StringExpression getStatusCommandExpr()
    {
        return StringExpressionFactory.create(_statusCommand);
    }

    public StringExpression getRemoveCommandExpr()
    {
        return StringExpressionFactory.create(_removeCommand);
    }

    public StringExpression getHistoryCommandExpr()
    {
        return StringExpressionFactory.create(_historyCommand);
    }

    public String getCondorUser()
    {
        return _condorUser;
    }

    @NotNull
    @Override
    public String getLocation()
    {
        return _location;
    }

    @NotNull
    @Override
    public String getType()
    {
        return HTCondorExecutionEngine.TYPE;
    }

    @NotNull
    @Override
    public Set<String> getAvailableQueues()
    {
        return Collections.emptySet();
    }

    @NotNull
    @Override
    public PathMapper getPathMapper()
    {
        return _pathMapper;
    }

    public String getRemoteExecutable()
    {
        return _remoteExecutable;
    }

    public String getWorkingDir()
    {
        return _workingDir;
    }

    public Integer getRequestCpus()
    {
        return _requestCpus;
    }

    public String getRequestMemory()
    {
        return _requestMemory;
    }

    public String getLabKeyDir()
    {
        return _labKeyDir == null || !_labKeyDir.endsWith("/") ? _labKeyDir : _labKeyDir.substring(0, _labKeyDir.length()-1);
    }

    public List<String> getExtraSubmitLines()
    {
        return Collections.emptyList();
    }

    public List<String> getJobArgs(File localPipelineDir, File localSerializedJobXmlFile)
    {
        List<String> ret = new ArrayList<>();
        if (_javaOpts != null)
        {
            ret.addAll(_javaOpts);
        }

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

    public void setPathMapper(PathMapper pathMapper)
    {
        _pathMapper = pathMapper;
    }

    public void setRemoteExecutable(String remoteExecutable)
    {
        _remoteExecutable = remoteExecutable;
    }

    public void setRequestCpus(Integer requestCpus)
    {
        _requestCpus = requestCpus;
    }

    public void setRequestMemory(String requestMemory)
    {
        _requestMemory = requestMemory;
    }

    public void setLabKeyDir(String labKeyDir)
    {
        _labKeyDir = labKeyDir;
    }

    public void setCondorUser(String condorUser)
    {
        _condorUser = condorUser;
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
            return ret.replaceFirst("^file:/", "/");
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
}
