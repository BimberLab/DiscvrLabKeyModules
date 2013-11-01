package org.labkey.sequenceanalysis;

import org.labkey.sequenceanalysis.run.AlignerWrapper;
import org.labkey.sequenceanalysis.run.SequenceAlignerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: bimber
 * Date: 10/27/13
 * Time: 9:21 PM
 */
public class SequenceAnalysisServiceImpl
{
    private static SequenceAnalysisServiceImpl _instance = new SequenceAnalysisServiceImpl();

    private Set<SequenceAlignerFactory> _aligners = new HashSet<>();

    private SequenceAnalysisServiceImpl()
    {

    }

    public static SequenceAnalysisServiceImpl get()
    {
        return _instance;
    }

    public void registerAlignerWrapper(SequenceAlignerFactory aligner)
    {
        _aligners.add(aligner);
    }

    public Set<SequenceAlignerFactory> getAligners()
    {
        return Collections.unmodifiableSet(_aligners);
    }
}
