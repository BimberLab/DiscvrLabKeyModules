package org.labkey.sequenceanalysis.run.util;

import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.run.AbstractGatk4Wrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class IndexFeatureFileWrapper extends AbstractGatk4Wrapper
{
    public IndexFeatureFileWrapper(Logger log)
    {
        super(log);
    }

    public void ensureFeatureFileIndex(File input) throws PipelineJobException
    {
        List<String> args1 = new ArrayList<>(getBaseArgs());
        args1.add("IndexFeatureFile");
        args1.add("-I");
        args1.add(input.getPath());
        execute(args1);
    }
}
