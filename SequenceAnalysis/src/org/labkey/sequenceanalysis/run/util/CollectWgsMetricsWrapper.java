package org.labkey.sequenceanalysis.run.util;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by bimber on 4/24/2015.
 */
public class CollectWgsMetricsWrapper extends PicardWrapper
{
    public CollectWgsMetricsWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public File executeCommand(File inputFile, File outputFile, File refFasta) throws PipelineJobException
    {
        getLogger().info("Running CollectWgsMetrics: " + inputFile.getPath());

        List<String> params = new LinkedList<>();
        params.add("java");
        params.addAll(getBaseParams());
        params.add("-jar");
        params.add(getPicardJar().getPath());
        params.add(getTooName());
        params.add("VALIDATION_STRINGENCY=" + getStringency().name());
        inferMaxRecordsInRam(params);
        params.add("INPUT=" + inputFile.getPath());
        params.add("OUTPUT=" + outputFile.getPath());
        params.add("REFERENCE_SEQUENCE=" + refFasta.getPath());
        params.add("INCLUDE_BQ_HISTOGRAM=true");
        execute(params);

        if (!outputFile.exists())
        {
            //this can occur if the input does not have paired data
            return null;
        }

        return outputFile;
    }

    protected String getTooName()
    {
        return "CollectWgsMetrics";
    }
}
