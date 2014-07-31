package org.labkey.sequenceanalysis.api.run;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * User: bimber
 * Date: 6/29/2014
 * Time: 11:08 AM
 */
class InputStreamHandler extends Thread
{
    private BufferedReader _reader;
    private Logger _log;
    private Level _level;

    /**
     * Constructor.
     *
     * @param
     */

    InputStreamHandler(Logger log, Level level, InputStream stream)
    {
        _reader = new BufferedReader(new InputStreamReader(stream));
        _log = log;
        _level = level;
        
        start();
    }

    /**
     * Stream the data.
     */

    public void run()
    {
        try
        {
            String line;
            while((line = _reader.readLine()) != null)
            {
                _log.log(_level, "o\t" + line);
            }
        }
        catch(IOException e)
        {
            
        }
        finally
        {
            try
            {
                _reader.close();
            }
            catch (IOException e)
            {
                //ignore
            }
        }
    }
}
