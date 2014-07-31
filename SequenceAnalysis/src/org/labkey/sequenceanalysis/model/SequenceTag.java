package org.labkey.sequenceanalysis.model;

import org.biojava3.core.sequence.DNASequence;

/**
 * User: bimber
 * Date: 11/28/12
 * Time: 4:15 PM
 */
abstract public class SequenceTag
{
    abstract public String getName();

    abstract public String getSequence();

    public String getReverseComplement()
    {
        DNASequence seq = new DNASequence(getSequence());
        return seq.getReverseComplement().getSequenceAsString();
    }
}
