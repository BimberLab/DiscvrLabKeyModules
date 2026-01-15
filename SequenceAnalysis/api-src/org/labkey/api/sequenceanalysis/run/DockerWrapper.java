package org.labkey.api.sequenceanalysis.run;

import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineOutputTracker;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DockerWrapper extends AbstractCommandWrapper
{
    private final String _containerName;
    private final PipelineContext _ctx;
    private File _tmpDir = null;
    private String _entryPoint = null;
    private boolean _runPrune = false;
    private boolean _useLocalContainerStorage;
    private String _alternateUserHome = null;
    private final Map<String, String> _dockerEnvironment = new HashMap<>();
    private int _maxRetries = 3;

    public DockerWrapper(String containerName, Logger log, PipelineContext ctx)
    {
        super(log);
        _containerName = containerName;
        _ctx = ctx;

        _useLocalContainerStorage = SequencePipelineService.get().useLocalDockerContainerStorage();
    }

    public void setAlternateUserHome(String alternateUserHome)
    {
        _alternateUserHome = alternateUserHome;
    }

    public void setTmpDir(File tmpDir)
    {
        _tmpDir = tmpDir;
    }

    public void setEntryPoint(String entryPoint)
    {
        _entryPoint = entryPoint;
    }

    public void setRunPrune(boolean runPrune)
    {
        _runPrune = runPrune;
    }

    public void setUseLocalContainerStorage(boolean useLocalContainerStorage)
    {
        _useLocalContainerStorage = useLocalContainerStorage;
    }

    public void executeWithDocker(List<String> containerArgs, File workDir, PipelineOutputTracker tracker) throws PipelineJobException
    {
        executeWithDocker(containerArgs, workDir, tracker, null);
    }

    public void executeWithDocker(List<String> containerArgs, File workDir, PipelineOutputTracker tracker, @Nullable Collection<File> inputFiles) throws PipelineJobException
    {
        File localBashScript = new File(workDir, "docker.sh");
        File dockerBashScript = new File(workDir, "dockerRun.sh");
        tracker.addIntermediateFile(localBashScript);
        tracker.addIntermediateFile(dockerBashScript);

        setWorkingDir(workDir);
        try (PrintWriter writer = PrintWriters.getPrintWriter(localBashScript); PrintWriter dockerWriter = PrintWriters.getPrintWriter(dockerBashScript))
        {
            writer.println("#!/bin/bash");
            writer.println("set -x");
            writer.println("set -e");

            writer.println("DOCKER='" + SequencePipelineService.get().getDockerCommand() + "'");

            writer.println("IMAGE_EXISTS=`$DOCKER images -q \"" + getEffectiveContainerName() + "\" | wc -l`");
            writer.println("LOCAL=not_present");
            writer.println("if [[ $IMAGE_EXISTS > 0 ]];then");
            writer.println("\tLOCAL=`docker inspect --format='{{.Digest}}' " + getEffectiveContainerName() + "`");
            writer.println("fi");
            writer.println("LATEST=`regctl image digest --list " + getEffectiveContainerName() + "`");
            writer.println("if [ $LOCAL != $LATEST ];then");
            writer.println("\t$DOCKER pull " + getLocalStorageArgs() + getEffectiveContainerName());
            writer.println("else");
            writer.println("\techo 'Image up to date'");
            writer.println("fi");

            if (_runPrune)
            {
                writer.println("$DOCKER image prune " + getLocalStorageArgs() + "-f");
            }

            writer.println("$DOCKER run --rm=true \\");
            writer.println("\t--group-add keep-groups \\");
            writer.println("\t--transient-store \\");

            if (_useLocalContainerStorage)
            {
                getLogger().debug("Using local container storage: " + getLocalContainerDir().getPath());
                prepareLocalStorage();
                writer.println("\t" + getLocalStorageArgs() + "\\");
            }

            // NOTE: getDockerVolumes() should be refactored to remove the -v and this logic should be updated accordingly:
            File homeDir = new File(System.getProperty("user.home"));
            if (homeDir.exists())
            {
                if (_ctx.getDockerVolumes().stream().noneMatch(homeDir.getPath()::startsWith))
                {
                    writer.println("\t-v '" + homeDir.getPath() + "':'" + homeDir.getPath() + "' \\");
                }
                else
                {
                    _ctx.getLogger().debug("homeDir already present in docker volumes, will not re-add");
                }

                _dockerEnvironment.put("USER_HOME", homeDir.getPath());
            }

            if (_alternateUserHome != null)
            {
                _dockerEnvironment.put("HOME", _alternateUserHome);
            }

            _ctx.getDockerVolumes().forEach(v -> writer.println("\t-v '" + v + "':'" + v + "' \\"));
            if (inputFiles != null)
            {
                inspectInputFiles(inputFiles).forEach(v -> writer.println("\t-v '" + v + "':'" + v + "' \\"));
            }

            if (_tmpDir != null)
            {
                // NOTE: getDockerVolumes() should be refactored to remove the -v and this logic should be updated accordingly:
                if (_ctx.getDockerVolumes().stream().noneMatch(_tmpDir.getPath()::startsWith))
                {
                    writer.println("\t-v '" + _tmpDir.getPath() + "':/tmp \\");
                }
                else
                {
                    _ctx.getLogger().debug("tmpDir already present in docker volumes, omitting");
                }

                addToDockerEnvironment("TMPDIR", _tmpDir.getPath());
            }

            if (_entryPoint != null)
            {
                writer.println("\t--entrypoint \"" + _entryPoint + "\"\\");
            }

            writer.println("\t-w " + workDir.getPath() + " \\");
            addToDockerEnvironment("WORK_DIR", workDir.getPath());

            Integer maxRam = SequencePipelineService.get().getMaxRam();
            if (maxRam != null)
            {
                writer.println("\t-e SEQUENCEANALYSIS_MAX_RAM=" + maxRam + " \\");
                writer.println("\t--memory='" + maxRam + "g' \\");
            }

            for (String key : _dockerEnvironment.keySet())
            {
                writer.println("\t-e " + key + "='" + _dockerEnvironment.get(key) + "' \\");
            }
            writer.println("\t" + getEffectiveContainerName() + " \\");
            writer.println("\t" + dockerBashScript.getPath());
            writer.println("DOCKER_EXIT_CODE=$?");
            writer.println("echo 'Docker run exit code: '$DOCKER_EXIT_CODE");
            writer.println("exit $DOCKER_EXIT_CODE");

            dockerWriter.println("#!/bin/bash");
            dockerWriter.println("set -x");
            dockerWriter.println(StringUtils.join(containerArgs, " "));
            dockerWriter.println("BASH_EXIT_CODE=$?");
            dockerWriter.println("echo 'Bash exit code: '$BASH_EXIT_CODE");
            dockerWriter.println("exit $BASH_EXIT_CODE");
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        localBashScript.setExecutable(true);
        dockerBashScript.setExecutable(true);
        executeWithRetry(Arrays.asList("/bin/bash", localBashScript.getPath()));

        if (_useLocalContainerStorage)
        {
            try
            {
                FileUtils.deleteDirectory(getLocalContainerDir());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    public int getMaxRetries()
    {
        return _maxRetries;
    }

    // NOTE: when running on a shared/cluster environment with multiple containers initializing concurrently, conflicts can result in these error codes.
    // As a convenience, build in auto-retry behavior if one of these occurs
    private final List<Integer> ALLOWABLE_FAIL_CODES = new UnmodifiableList<>(Arrays.asList(125, 127));

    private void executeWithRetry(final List<String> args) throws PipelineJobException
    {
        int retries = 0;
        while (retries <= getMaxRetries())
        {
            try
            {
                execute(args);
                break;
            }
            catch (PipelineJobException e)
            {
                if (ALLOWABLE_FAIL_CODES.contains(getLastReturnCode()))
                {
                    retries++;
                    if (retries > getMaxRetries())
                    {
                        getLogger().info("Maximum retries exceeded");
                        throw e;
                    }

                    getLogger().info("Exit code " + getLastReturnCode() + ", retrying after 1 sec (" + retries + " of " + getMaxRetries()+ ")");
                    try
                    {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException ex)
                    {
                        throw new PipelineJobException(ex);
                    }
                }
                else
                {
                    throw e;
                }
            }
        }
    }

    private String getEffectiveContainerName()
    {
        return _containerName;
    }

    public void addToDockerEnvironment(String key, String value)
    {
        _dockerEnvironment.put(key, value);
    }

    private Collection<File> inspectInputFiles(Collection<File> inputFiles)
    {
        Set<File> toAdd = inputFiles.stream().map(f -> f.isDirectory() ? f : f.getParentFile()).filter(x -> _ctx.getDockerVolumes().stream().noneMatch(x.getPath()::startsWith)).collect(Collectors.toSet());
        if (!toAdd.isEmpty())
        {
            Set<File> paths = new HashSet<>();
            toAdd.forEach(x -> {
                _ctx.getLogger().debug("Adding volume for path: " + x.getPath());

                File converted = SequencePipelineService.get().inferDockerVolume(x);
                if (!x.equals(converted))
                {
                    _ctx.getLogger().debug("added as: " + converted.getPath());
                }

                if (_ctx.getDockerVolumes().stream().noneMatch(converted.getPath()::startsWith))
                {
                    paths.add(converted);
                }
            });

            return paths;
        }

        return Collections.emptySet();
    }

    private File getLocalContainerDir()
    {
        return new File(SequencePipelineService.get().getJavaTempDir(), "containers");
    }

    private File prepareLocalStorage() throws PipelineJobException
    {
        try
        {
            if (getLocalContainerDir().exists())
            {
                getLogger().debug("Deleting existing container dir: " + getLocalContainerDir());
                FileUtils.deleteDirectory(getLocalContainerDir());
            }

            FileUtil.createDirectory(getLocalContainerDir().toPath());

            return getLocalContainerDir();
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private String getLocalStorageArgs()
    {
        if (!_useLocalContainerStorage)
        {
            return "";
        }

        return "--root=" + getLocalContainerDir().getPath() + " ";
    }
}
