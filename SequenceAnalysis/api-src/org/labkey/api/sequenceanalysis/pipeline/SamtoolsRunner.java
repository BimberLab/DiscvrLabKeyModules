package org.labkey.api.sequenceanalysis.pipeline;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;

/**
 * User: bimber
 * Date: 12/15/12
 * Time: 9:11 PM
 */
public class SamtoolsRunner extends AbstractCommandWrapper
{
    public SamtoolsRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public File getSamtoolsPath()
    {
        return SequencePipelineService.get().getExeForPackage("SAMTOOLSPATH", "samtools");
    }
}
