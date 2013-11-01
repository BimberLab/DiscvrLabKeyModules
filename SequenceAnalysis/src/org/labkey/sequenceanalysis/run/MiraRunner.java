package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.labkey.sequenceanalysis.pipeline.SequencePipelineSettings;

import java.io.File;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:16 PM
 */
public class MiraRunner extends AbstractRunner
{
    public MiraRunner(Logger logger)
    {
        _logger = logger;
    }

    public void doDeNovoAssembly(File input, String outputPrefix, SequencePipelineSettings settings)
    {

    }

    public void doMappingAssembly(File input, String outputPrefix, SequencePipelineSettings settings, File referenceFasta)
    {

    }
}
