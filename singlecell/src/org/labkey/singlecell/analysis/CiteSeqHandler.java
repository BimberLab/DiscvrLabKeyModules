package org.labkey.singlecell.analysis;

import org.labkey.api.sequenceanalysis.SequenceOutputFile;

public class CiteSeqHandler extends CellHashingHandler
{
    private static final String DEFAULT_TAG_GROUP = "TotalSeq-C";

    public CiteSeqHandler()
    {
        super("CITE-Seq Count", "This will run CITE-Seq Count to generate a table of features counts from CITE-Seq", CellHashingHandler.getDefaultParams(false, DEFAULT_TAG_GROUP, BARCODE_TYPE.citeseq));
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return false;
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public boolean requiresSingleGenome()
    {
        return false;
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public SequenceReadsetProcessor getProcessor()
    {
        return new Processor(BARCODE_TYPE.citeseq);
    }
}
