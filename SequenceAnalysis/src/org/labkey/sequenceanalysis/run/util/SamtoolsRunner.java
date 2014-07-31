package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.api.run.AbstractCommandWrapper;

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

    protected File getSamtoolsPath()
    {
        return SequencePipelineService.get().getExeForPackage("SAMTOOLSPATH", "samtools");
    }
}
