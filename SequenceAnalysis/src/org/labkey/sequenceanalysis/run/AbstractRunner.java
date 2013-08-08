package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.util.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/28/12
 * Time: 10:52 PM
 */
abstract public class AbstractRunner
{
    protected Logger _logger = null;
    protected File _workingDir = null;

    protected void doExecute(File workingDir, List<String> params) throws PipelineJobException
    {
        BufferedReader procReader = null;
        try
        {
            if (_logger != null)
            {
                _logger.info("\t" + StringUtils.join(params, " "));
            }

            ProcessBuilder pb = new ProcessBuilder(params);

            //pb.directory(workingDir);
            pb.redirectErrorStream(true);

            Process p = pb.start();
            StringBuffer output = new StringBuffer();

            procReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = procReader.readLine()) != null)
            {
                output.append(line);
                output.append(System.getProperty("line.separator"));

                if (_logger != null)
                    _logger.debug("\t" + line);
            }

            int returnCode = p.waitFor();

            if (returnCode != 0)
            {
                throw new IOException("Failed with error code " + returnCode + " - " + output.toString());
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
        catch (InterruptedException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            if (procReader != null)
                try {procReader.close();} catch(IOException ignored) {}

        }
    }

    public void setWorkingDir(File workingDir)
    {
        _workingDir = workingDir;
    }

    protected File getWorkingDir(File file)
    {
        return _workingDir == null ? file.getParentFile() : _workingDir;
    }

    protected String runCommand(List<String> params)
    {
        StringBuffer output = new StringBuffer();

        try
        {
            ProcessBuilder pb = new ProcessBuilder(params);
            pb.directory(FileUtil.getTempDirectory());
            pb.redirectErrorStream(true);

            Process p = pb.start();
            BufferedReader procReader = null;

            try
            {
                procReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = procReader.readLine()) != null)
                {
                    output.append(line);
                    output.append(System.getProperty("line.separator"));
                }

                int returnCode = p.waitFor();

            }
            catch (InterruptedException e)
            {
                //ignore
            }
            finally
            {
                if (procReader != null)
                    procReader.close();
            }
        }
        catch (IOException e)
        {
            //ignore
        }

        return output.toString();
    }

    protected File getExeForPackage(String packageName, String exe)
    {
        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(packageName);
        if (path != null && StringUtils.trimToNull(path) != null)
            return new File(path, exe);
        else
            return new File(exe);
    }
}
