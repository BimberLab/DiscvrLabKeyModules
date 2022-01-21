package org.labkey.sequenceanalysis.model;

import org.biojava.nbio.core.exceptions.CompoundNotFoundException;
import org.biojava.nbio.core.sequence.DNASequence;

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
        try
        {
            DNASequence seq = new DNASequence(getSequence());
            return seq.getReverseComplement().getSequenceAsString();
        }
        catch (CompoundNotFoundException e)
        {
            throw new IllegalArgumentException(e);
        }
    }
}
