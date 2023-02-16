/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.sequenceanalysis.run;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.StringUtilsLabKey;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is the basic wrapper around command line tools.  It should manage the available arguments and
 * handle constructing and executing the command
 */
abstract public class AbstractCommandWrapper implements CommandWrapper
{
    private File _outputDir = null;
    private File _workingDir = null;
    private Logger _log = null;
    private Level _logLevel = Level.DEBUG;
    private boolean _warnNonZeroExits = true;
    private boolean _throwNonZeroExits = true;
    private Integer _lastReturnCode = null;
    private final Map<String, String> _environment = new HashMap<>();
    private final List<String> _commandsExecuted = new ArrayList<>();

    public AbstractCommandWrapper(@Nullable Logger logger)
    {
        _log = logger;

        //Apply some default environment vars:
        for (String varName : Arrays.asList("HOME", "UID", "JAVA_HOME"))
        {
            String val = StringUtils.trimToNull(System.getenv(varName));
            if (val != null)
            {
                _environment.put(varName, val);
            }
        }
    }

    @Override
    public void execute(List<String> params) throws PipelineJobException
    {
        execute(params, null, null);
    }

    @Override
    public String executeWithOutput(List<String> params) throws PipelineJobException
    {
        StringBuffer ret = new StringBuffer();
        execute(params, null, ret);

        return ret.toString();
    }

    public void addToEnvironment(String key, String value)
    {
        _environment.put(key, value);
    }

    @Override
    public List<String> getCommandsExecuted()
    {
        return _commandsExecuted;
    }

    @Override
    public void execute(List<String> params, File stdout) throws PipelineJobException
    {
        execute(params, ProcessBuilder.Redirect.to(stdout));
    }

    @Override
    public void execute(List<String> params, ProcessBuilder.Redirect redirect) throws PipelineJobException
    {
        execute(params, redirect, null);
    }

    public ProcessBuilder getProcessBuilder(List<String> params)
    {
        ProcessBuilder pb = new ProcessBuilder(params);
        setPath(pb);

        if (!_environment.isEmpty())
        {
            pb.environment().putAll(_environment);
        }

        if (getWorkingDir() != null)
        {
            getLogger().debug("using working directory: " + getWorkingDir().getPath());
            pb.directory(getWorkingDir());
        }

        return pb;
    }

    public void executeWithOutput(List<String> params, StringBuffer output) throws PipelineJobException
    {
        execute(params, null, output);
    }

    private void execute(List<String> params, ProcessBuilder.Redirect redirect, @Nullable StringBuffer output) throws PipelineJobException
    {
        getLogger().info("\t" + StringUtils.join(params, " "));
        _commandsExecuted.add(StringUtils.join(params, " "));

        ProcessBuilder pb = getProcessBuilder(params);
        pb.redirectErrorStream(false);
        if (redirect != null)
        {
            if (redirect.file() != null)
            {
                getLogger().info("\tredirecting STDOUT to: " + redirect.file().getPath());
            }
            pb.redirectOutput(redirect);
        }
        else
        {
            pb.redirectErrorStream(true);
        }

        Process p = null;
        try
        {
            p = pb.start();
            try (BufferedReader procReader = new BufferedReader(new InputStreamReader(redirect == null ? p.getInputStream() : p.getErrorStream(), StringUtilsLabKey.DEFAULT_CHARSET)))
            {
                String line;
                while ((line = procReader.readLine()) != null)
                {
                    if (output != null)
                    {
                        output.append(line);
                        output.append(System.getProperty("line.separator"));
                    }

                    getLogger().log(_logLevel, "\t" + line);
                }
            }

            _lastReturnCode = p.waitFor();
            if (_lastReturnCode != 0 && _warnNonZeroExits)
            {
                getLogger().warn("\tprocess exited with non-zero value: " + _lastReturnCode);
            }

            if (_lastReturnCode != 0 && _throwNonZeroExits)
            {
                throw new PipelineJobException("process exited with non-zero value: " + _lastReturnCode);
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
    }

    public Integer getLastReturnCode()
    {
        return _lastReturnCode;
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

    public void setWorkingDir(File workingDir)
    {
        _workingDir = workingDir;
    }

    private File getWorkingDir()
    {
        return _workingDir;
    }

    public Logger getLogger()
    {
        if (_log == null)
        {
              return  LogManager.getLogger("NoOpLogger");
        }

        return _log;
    }

    protected void setLogLevel(Level logLevel)
    {
        _logLevel = logLevel;
    }

    public Level getLogLevel()
    {
        return _logLevel;
    }

    public void setWarnNonZeroExits(boolean warnNonZeroExits)
    {
        _warnNonZeroExits = warnNonZeroExits;
    }

    public void setThrowNonZeroExits(boolean throwNonZeroExits)
    {
        _throwNonZeroExits = throwNonZeroExits;
    }

    protected boolean isWarnNonZeroExits()
    {
        return _warnNonZeroExits;
    }

    protected boolean isThrowNonZeroExits()
    {
        return _throwNonZeroExits;
    }

    protected static File resolveFileInPath(String exe, @Nullable String packageName, boolean throwIfNotFound)
    {
        File fn;
        String path;

        if (packageName != null)
        {
            path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(packageName);
            if (path != null)
            {
                fn = new File(path, exe);
                if (fn.exists())
                {
                    return fn;
                }
            }
        }

        path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(SequencePipelineService.SEQUENCE_TOOLS_PARAM);
        if (path != null)
        {
            fn = new File(path, exe);
            if (fn.exists())
            {
                return fn;
            }
        }

        path = PipelineJobService.get().getAppProperties().getToolsDirectory();
        if (path != null)
        {
            fn = new File(path, exe);
            if (fn.exists())
            {
                return fn;
            }
        }

        String[] paths = System.getenv("PATH").split(File.pathSeparator);
        for (String pathDir : paths)
        {
            fn = new File(pathDir, exe);
            if (fn.exists())
            {
                return fn;
            }
        }

        if (throwIfNotFound)
        {
            throw new IllegalArgumentException("Unable to find file: "+ exe);
        }

        return null;
    }
}
