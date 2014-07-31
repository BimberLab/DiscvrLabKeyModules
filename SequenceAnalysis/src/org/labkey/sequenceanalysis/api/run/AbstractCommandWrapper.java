package org.labkey.sequenceanalysis.api.run;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.NOPLoggerRepository;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * This is the basic wrapper around command line tools.  It should manage the available arguments and
 * handle constructing and executing the command
 */
abstract public class AbstractCommandWrapper implements CommandWrapper
{
    private File _outputDir = null;
    private Logger _log = null;

    public AbstractCommandWrapper(@Nullable Logger logger)
    {
        _log = logger;
    }

    @Override
    public String execute(List<String> params) throws PipelineJobException
    {
        return execute(params, null);
    }

    @Override
    public String execute(List<String> params, File stdout) throws PipelineJobException
    {
        StringBuffer output = new StringBuffer();
        getLogger().info("\t" + StringUtils.join(params, " "));

        ProcessBuilder pb = new ProcessBuilder(params);
        setPath(pb);
        pb.redirectErrorStream(false);
        if (stdout != null)
        {
            getLogger().info("\twriting STDOUT to file: " + stdout.getPath());
            pb.redirectOutput(stdout);
        }
        else
        {
            pb.redirectErrorStream(true);
        }

        Process p = null;
        try
        {
            p = pb.start();
            //InputStreamHandler in = new InputStreamHandler(getLogger(), Level.INFO, p.getInputStream());
            //InputStreamHandler err = new InputStreamHandler(getLogger(), Level.ERROR, p.getErrorStream());
            try (BufferedReader procReader = new BufferedReader(new InputStreamReader(p.getInputStream())))
            {
                String line;
                while ((line = procReader.readLine()) != null)
                {
                    output.append(line);
                    output.append(System.getProperty("line.separator"));

                    getLogger().debug("\t" + line);
                }
            }

            int returnCode = p.waitFor();
            if (returnCode != 0)
            {
                getLogger().warn("\tprocess exited with non-zero value: " + returnCode);
            }
        }
        catch (IOException | InterruptedException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            if (p != null)
            {
                p.destroy();
            }
        }

        return output.toString();
    }

    private void setPath(ProcessBuilder pb)
    {
        // Update PATH environment variable to make sure all files in the tools
        // directory and the directory of the executable or on the path.
        String toolDir = PipelineJobService.get().getAppProperties().getToolsDirectory();
        if (!StringUtils.isEmpty(toolDir))
        {
            String path = System.getenv("PATH");
            if (path == null)
            {
                path = toolDir;
            }
            else
            {
                path = toolDir + File.pathSeparatorChar + path;
            }

            // If the command has a path, then prepend its parent directory to the PATH
            // environment variable as well.
            String exePath = pb.command().get(0);
            if (exePath != null && !"".equals(exePath) && exePath.indexOf(File.separatorChar) != -1)
            {
                File fileExe = new File(exePath);
                String exeDir = fileExe.getParent();
                if (!exeDir.equals(toolDir) && fileExe.exists())
                    path = fileExe.getParent() + File.pathSeparatorChar + path;
            }

            getLogger().debug("using path: " + path);
            pb.environment().put("PATH", path);
        }
    }

    public void setOutputDir(File outputDir)
    {
        _outputDir = outputDir;
    }

    public File getOutputDir(File file)
    {
        return _outputDir == null ? file.getParentFile() : _outputDir;
    }

    public Logger getLogger()
    {
        if (_log == null)
        {
            return new org.apache.log4j.spi.NOPLogger(new NOPLoggerRepository(), "null");
        }

        return _log;
    }
}
