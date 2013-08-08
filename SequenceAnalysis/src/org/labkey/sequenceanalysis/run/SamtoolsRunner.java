package org.labkey.sequenceanalysis.run;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 12/15/12
 * Time: 9:11 PM
 */
public class SamtoolsRunner extends AbstractRunner
{
    protected File getSamtoolsPath()
    {
        return getExeForPackage("SAMTOOLSPATH", "samtools");
    }

    public void merge(File output, File... input)
    {

    }
}
