package org.labkey.api.sequenceanalysis.run;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 12/15/12
 * Time: 9:07 PM
 */
public class CreateSequenceDictionaryWrapper extends PicardWrapper
{
    public CreateSequenceDictionaryWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public File getExpectedDictionaryFile(File referenceFasta)
    {
        return new File(getOutputDir(referenceFasta), FileUtil.getBaseName(referenceFasta) + ".dict");
    }

    public File execute(File referenceFasta, boolean forceRecreate) throws PipelineJobException
    {
        File expected = getExpectedDictionaryFile(referenceFasta);
        if (expected.exists())
        {
            if (forceRecreate)
            {
                expected.delete();
            }
            else
            {
                getLogger().info("Dictionary already exists for: " + referenceFasta.getPath());
                return expected;
            }
        }

        getLogger().info("Creating dictionary for: " + referenceFasta.getPath());

        List<String> params = getBaseArgs();
        inferMaxRecordsInRam(params);
        params.add("REFERENCE=" + referenceFasta.getPath());
        params.add("OUTPUT=" + expected.getPath());

        execute(params);

        if (!expected.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + expected.getPath());
        }

        return expected;
    }

    protected String getToolName()
    {
        return "CreateSequenceDictionary";
    }
}
