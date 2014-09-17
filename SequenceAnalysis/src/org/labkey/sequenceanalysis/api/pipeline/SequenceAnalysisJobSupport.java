package org.labkey.sequenceanalysis.api.pipeline;

import org.labkey.api.exp.api.ExpData;
import org.labkey.api.util.Pair;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/7/2014.
 */
public interface SequenceAnalysisJobSupport
{
    public ReferenceGenome getReferenceGenome();

    public void cacheExpData(ExpData data);

    public File getCachedData(int dataId);

    public Map<Integer, File> getAllCachedData();
}
