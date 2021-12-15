package org.labkey.api.sequenceanalysis.pipeline;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;

import java.io.File;

/**
 * User: bimber
 * Date: 12/15/12
 * Time: 9:11 PM
 */
public class BcftoolsRunner extends AbstractCommandWrapper
{
    public BcftoolsRunner(@Nullable Logger logger)
    {
        super(logger);
    }

    public File getBcfToolsPath()
    {
        return SequencePipelineService.get().getExeForPackage("BCFTOOLSPATH", "bcftools");
    }
}
