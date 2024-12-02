package org.labkey.api.sequenceanalysis.run;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineOutputTracker;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class DockerWrapper extends AbstractCommandWrapper
{
    private final String _containerName;
    private final PipelineContext _ctx;
    private File _tmpDir = null;
    private String _entryPoint = null;
    private boolean _runPrune = true;

    public DockerWrapper(String containerName, Logger log, PipelineContext ctx)
    {
        super(log);
        _containerName = containerName;
        _ctx = ctx;

        _environment.clear();
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

    public void executeWithDocker(List<String> containerArgs, File workDir, PipelineOutputTracker tracker) throws PipelineJobException
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
            writer.println("WD=`pwd`");

            writer.println("DOCKER='" + SequencePipelineService.get().getDockerCommand() + "'");
            if (_runPrune)
            {
                writer.println("$DOCKER image prune -f");
            }

            writer.println("$DOCKER pull " + _containerName);
            writer.println("$DOCKER run --rm=true \\");

            // NOTE: getDockerVolumes() should be refactored to remove the -v and this logic should be updated accordingly:
            File homeDir = new File(System.getProperty("user.home"));
            if (homeDir.exists())
            {
                final String searchString = "-v '" + homeDir.getPath() + "'";
                if (_ctx.getDockerVolumes().stream().noneMatch(searchString::startsWith))
                {
                    writer.println("\t-v \"" + homeDir.getPath() + ":/homeDir\" \\");
                }
                else
                {
                    _ctx.getLogger().debug("homeDir already present in docker volumes, omitting");
                }

                _environment.put("USER_HOME", homeDir.getPath());
            }

            _ctx.getDockerVolumes().forEach(ln -> writer.println(ln + " \\"));
            if (_tmpDir != null)
            {
                // NOTE: getDockerVolumes() should be refactored to remove the -v and this logic should be updated accordingly:
                final String searchString = "-v '" + _tmpDir.getPath() + "'";
                if (_ctx.getDockerVolumes().stream().noneMatch(searchString::startsWith))
                {
                    writer.println("\t-v \"" + _tmpDir.getPath() + ":/tmp\" \\");
                }
                else
                {
                    _ctx.getLogger().debug("tmpDir already present in docker volumes, omitting");
                }
            }

            if (_entryPoint != null)
            {
                writer.println("\t--entrypoint \"" + _entryPoint + "\"\\");
            }

            writer.println("\t-w " + workDir.getPath() + " \\");
            Integer maxRam = SequencePipelineService.get().getMaxRam();
            if (maxRam != null)
            {
                writer.println("\t-e SEQUENCEANALYSIS_MAX_RAM=" + maxRam + " \\");
                writer.println("\t--memory='" + maxRam + "g' \\");
            }

            for (String key : _environment.keySet())
            {
                writer.println("\t-e " + key + "=" + _environment.get(key) + " \\");
            }
            writer.println("\t" + _containerName + " \\");
            writer.println("\t" + workDir.getPath() + "/" + dockerBashScript.getName());
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

        execute(Arrays.asList("/bin/bash", localBashScript.getPath()));
    }

    public File ensureLocalCopy(File input, File workingDirectory, PipelineOutputTracker output) throws PipelineJobException
    {
        try
        {
            if (workingDirectory.equals(input.getParentFile()))
            {
                return input;
            }

            File local = new File(workingDirectory, input.getName());
            if (!local.exists())
            {
                getLogger().debug("Copying file locally: " + input.getPath());
                FileUtils.copyFile(input, local);
            }

            output.addIntermediateFile(local);

            return local;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
