package org.labkey.api.sequenceanalysis.pipeline;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.util.StringUtilsLabKey;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by bimber on 7/12/2016.
 */
public class ProcessUtils
{
    //adapted from: http://stackoverflow.com/questions/35706921/redirecting-the-output-of-a-process-into-the-input-of-another-process-using-proc
    public static class StreamRedirector
    {
        private final Logger _log;

        public StreamRedirector(Logger log)
        {
            _log = log;
        }

        public void redirectStreams(Process process1, Process process2) throws IOException
        {
            final Process tmpProcess1 = process1;
            final Process tmpProcess2 = process2;
            new Thread(new Runnable()
            {
                private static final int BUFFER_SIZE = 1024 * 4;

                @Override
                public void run()
                {
                    try (BufferedInputStream bufferedInputStream = new BufferedInputStream(tmpProcess1.getInputStream());
                         BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(tmpProcess2.getOutputStream());
                    )
                    {
                        IOUtils.copy(bufferedInputStream, bufferedOutputStream);
                    }
                    catch (IOException e)
                    {
                        if (tmpProcess1.isAlive())
                        {
                            _log.error(e.getMessage(), e);
                        }
                        else
                        {
                            _log.info("process closed");
                        }
                    }
                }
            }, "ProcessUtils.StreamRedirector").start();
        }
    }

    public static class ProcessReader
    {
        private final Logger _log;
        private final boolean _writeOutputToLog;
        private final boolean _readStdErr;

        public ProcessReader(Logger log, boolean writeOutputToLog, boolean readStdErr)
        {
            _log = log;
            _writeOutputToLog = writeOutputToLog;
            _readStdErr = readStdErr;
        }

        public void readProcess(Process process) throws IOException
        {
            final Process tmpProcess = process;
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    try (BufferedReader procReader = new BufferedReader(new InputStreamReader(_readStdErr ? tmpProcess.getErrorStream() : tmpProcess.getInputStream(), StringUtilsLabKey.DEFAULT_CHARSET)))
                    {
                        String line;
                        while ((line = procReader.readLine()) != null)
                        {
                            if (_writeOutputToLog)
                                _log.log(Level.DEBUG, "\t" + line);
                        }
                    }
                    catch (IOException e)
                    {
                        if (_log != null)
                        {
                            if ("Stream closed".equals(e.getMessage()))
                            {
                                return; //ignore.  unsure how to avoid this error?
                            }

                            _log.error(e.getMessage(), e);
                        }
                    }
                }
            }, "ProcessUtils.ProcessReader").start();
        }
    }
}
