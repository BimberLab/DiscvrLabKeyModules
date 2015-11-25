package org.labkey.htcondorconnector.pipeline;

import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 10/31/2015.
 */
public class DockerHTCondorExecutionEngineConfig extends HTCondorExecutionEngineConfig
{
    private String _dockerImageName = "labkeyRemotePipeline15.2";

    public DockerHTCondorExecutionEngineConfig()
    {
        _remoteExecutable = "docker";
        _labKeyDir = "/labkey";
    }

    @Override
    public List<String> getExtraSubmitLines()
    {
        return Arrays.asList("requirements = (TARGET.IsDockerComputeNode =?= True)");
    }

    @Override
    public List<String> getJobArgs(File localPipelineDir, File localSerializedJobFile)
    {
        List<String> ret = new ArrayList<>();
        ret.add("run");
        ret.add("--rm=true");
        ret.add("-v");
        ret.add(HTCondorExecutionEngine.getClusterPath(localPipelineDir) + ":/data");
        ret.add("-v");
        ret.add("/mnt/scratch:/work");
        //TODO: add -env for CPUs, memory
        ret.add(_dockerImageName);
        //ret.add("-Xdebug");
        //ret.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005");
        ret.add("java");
        ret.add("-cp");
        ret.add(getLabKeyDir() + "/labkeyBootstrap.jar");
        ret.add("org.labkey.bootstrap.ClusterBootstrap");
        ret.add("-modulesdir=" + getLabKeyDir() + "/modules");
        ret.add("-webappdir=" + getLabKeyDir() + "/labkeywebapp");
        ret.add("-configdir=" + getLabKeyDir() + "/config");
        ret.add(HTCondorExecutionEngine.getClusterPath(localSerializedJobFile, true));

        return ret;
    }
}
