package org.labkey.htcondorconnector.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 10/31/2015.
 */
public class HTCondorExecutionEngineConfig implements PipelineJobService.RemoteExecutionEngineConfig
{
    protected String _submitCommand = "ssh ${user}@exacloud.ohsu.edu -c '/opt/condor/bin/condor_submit ${submitScript}'";
    protected String _statusCommand = "ssh ${user}@exacloud.ohsu.edu -c '/opt/condor/bin/condor_q owner ${user}'";
    protected String _removeCommand = "ssh ${user}@exacloud.ohsu.edu -c '/opt/condor/bin/condor_rm ${condorId}'";
    protected String _user = "labkey";
    protected PathMapper _pathMapper = null;
    protected String _remoteExecutable = "/usr/local/java_current/bin/java";
    protected Integer _requestCpus = 24;
    protected String _requestMemory = "48 GB";
    protected String _labKeyDir = "/labkey";

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

    public String getUser()
    {
        return _user;
    }

    @NotNull
    @Override
    public String getLocation()
    {
        return null;
    }

    @NotNull
    @Override
    public String getType()
    {
        return null;
    }

    @NotNull
    @Override
    public Set<String> getAvailableQueues()
    {
        return null;
    }

    @Override
    public PathMapper getPathMapper()
    {
        return _pathMapper;
    }

    public String getRemoteExecutable()
    {
        return _remoteExecutable;
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

    public List<String> getJobArgs(File localPipelineDir, File localSerializedJobFile)
    {
        return Arrays.asList(
            //"-Xdebug",
            //"-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",
            "-cp",
            getLabKeyDir() + "/labkeyBootstrap.jar",
            "org.labkey.bootstrap.ClusterBootstrap",
            "-modulesdir=" + getLabKeyDir() + "/modules",
            "-webappdir=" + getLabKeyDir() + "/labkeywebapp",
            "-configdir=" + getLabKeyDir() + "/config",
            HTCondorExecutionEngine.getClusterPath(localSerializedJobFile, true)
        );
    }
}
