package org.labkey.sequenceanalysis;

import org.labkey.api.sequenceanalysis.ReferenceLibraryHelper;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.sequenceanalysis.util.ReferenceLibraryHelperImpl;

import java.io.File;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:21 PM
 */
public class SequenceAnalysisServiceImpl extends SequenceAnalysisService
{
    private static SequenceAnalysisServiceImpl _instance = new SequenceAnalysisServiceImpl();

    private SequenceAnalysisServiceImpl()
    {

    }

    public static SequenceAnalysisServiceImpl get()
    {
        return _instance;
    }

    @Override
    public ReferenceLibraryHelper getLibraryHelper(File refFasta)
    {
        return new ReferenceLibraryHelperImpl(refFasta);
    }
}
