package org.labkey.sequenceanalysis.run;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 12/14/12
 * Time: 6:01 AM
 */
abstract public class PicardRunner extends AbstractRunner
{
    public String getVersion()
    {
        List<String> params = new LinkedList<>();
        params.add("java");
        params.add("-jar");
        params.add(getJar().getPath());
        params.add("--version");

        return StringUtils.trim(runCommand(params));
    }

    abstract protected File getJar();
}
