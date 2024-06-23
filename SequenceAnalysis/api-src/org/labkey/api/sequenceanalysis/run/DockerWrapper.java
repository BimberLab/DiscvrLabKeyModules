package org.labkey.api.sequenceanalysis.run;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.PipelineOutputTracker;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.writer.PrintWriters;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class DockerWrapper extends AbstractCommandWrapper
{
    private final String _containerName;
    private File _tmpDir = null;

    public DockerWrapper(String containerName, Logger log)
    {
        super(log);
        _containerName = containerName;
    }

    public void setTmpDir(File tmpDir)
    {
        _tmpDir = tmpDir;
    }

    public void execute(List<String> containerArgs, File workDir, PipelineOutputTracker tracker) throws PipelineJobException
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
            writer.println("HOME=`echo ~/`");
            writer.println("DOCKER='" + SequencePipelineService.get().getDockerCommand() + "'");
            writer.println("sudo $DOCKER pull " + _containerName);
            writer.println("sudo $DOCKER run --rm=true \\");
            writer.println("\t-v \"${WD}:/work\" \\");
            writer.println("\t-v \"${HOME}:/homeDir\" \\");
            if (_tmpDir != null)
            {
                writer.println("\t-v \"" + _tmpDir.getPath() + ":/tmp\" \\");
            }
            writer.println("\t--entrypoint /bin/bash \\");
            writer.println("\t-w /work \\");
            Integer maxRam = SequencePipelineService.get().getMaxRam();
            if (maxRam != null)
            {
                writer.println("\t-e SEQUENCEANALYSIS_MAX_RAM=" + maxRam + " \\");
                writer.println("\t--memory='" + maxRam + "g' \\");
            }
            writer.println("\t" + _containerName + " \\");
            writer.println("\t/work/" + dockerBashScript.getName());
            writer.println("EXIT_CODE=$?");
            writer.println("echo 'Docker run exit code: '$EXIT_CODE");
            writer.println("exit $EXIT_CODE");

            dockerWriter.println("#!/bin/bash");
            dockerWriter.println("set -x");
            dockerWriter.println(StringUtils.join(containerArgs, " "));
            dockerWriter.println("EXIT_CODE=$?");
            dockerWriter.println("echo 'Exit code: '$?");
            dockerWriter.println("exit $EXIT_CODE");
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
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
