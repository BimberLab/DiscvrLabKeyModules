package org.labkey.cluster.pipeline;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.RemoteExecutionEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bimber on 10/31/2015.
 */
public class DockerHTCondorExecutionEngineConfig extends HTCondorExecutionEngineConfig
{
    private String _dockerImageName = "bbimber/discvr-seq";
    private String _configDir = "";
    protected String _activeMqHost = "";

    public DockerHTCondorExecutionEngineConfig()
    {
        _remoteExecutable = "docker";
        _labKeyDir = "/labkey";
    }

    @Override
    public List<String> getExtraSubmitLines()
    {
        List<String> ret = super.getExtraSubmitLines();
        ret.add("requirements = (TARGET.IsDockerComputeNode =?= True)");

        return ret;
    }

    @Override
    public List<String> getJobArgs(File localPipelineDir, File localSerializedJobFile, PipelineJob job, RemoteExecutionEngine engine)
    {
        List<String> ret = new ArrayList<>();
        ret.add("run");
        ret.add("--rm=true");
        //TODO: add flag to force rebuild of image
        //ret.add("-e");
        //ret.add("ACTIVEMQ_HOST=X");
        //TODO: mount whole file root
        ret.add("-v");
        ret.add(getClusterPath(localPipelineDir) + ":/data");
        ret.add("-v");
        ret.add("/mnt/scratch:/work");
        ret.add("-v");
        ret.add(_configDir + ":/labkey/config");

        ret.add("--add-host=activeMqServer:" + _activeMqHost);
        //TODO: add -env for CPUs, memory?
        ret.add(_dockerImageName);
        ret.add("java");
        ret.addAll(getFinalJavaOpts(job, engine));

        //TODO: support as config param
        //ret.add("-Xdebug");
        //ret.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005");
        ret.add("-cp");
        ret.add("/labkey/labkeyBootstrap.jar");
        ret.add("org.labkey.bootstrap.ClusterBootstrap");
        ret.add("-modulesdir=" + "/labkey/modules");
        ret.add("-webappdir=" + "/labkey/labkeywebapp");
        ret.add("-configdir=" + "/labkey/config");
        ret.add(getClusterPath(localSerializedJobFile, true));

        return ret;
    }

    public String getDockerImageName()
    {
        return _dockerImageName;
    }

    public void setDockerImageName(String dockerImageName)
    {
        _dockerImageName = dockerImageName;
    }

    public String getConfigDir()
    {
        return _configDir;
    }

    public void setConfigDir(String configDir)
    {
        _configDir = configDir;
    }

    public String getActiveMqHost()
    {
        return _activeMqHost;
    }

    public void setActiveMqHost(String activeMqHost)
    {
        _activeMqHost = activeMqHost;
    }
}
