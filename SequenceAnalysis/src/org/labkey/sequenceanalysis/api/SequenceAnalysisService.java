package org.labkey.sequenceanalysis.api;


/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 12:53 PM
 */
abstract public class SequenceAnalysisService
{
    static SequenceAnalysisService _instance;

    public static SequenceAnalysisService get()
    {
        return _instance;
    }

    static public void setInstance(SequenceAnalysisService instance)
    {
        _instance = instance;
    }
}
