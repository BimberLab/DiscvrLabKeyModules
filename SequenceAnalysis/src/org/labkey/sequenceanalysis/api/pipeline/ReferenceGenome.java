package org.labkey.sequenceanalysis.api.pipeline;

import java.io.File;
import java.io.Serializable;

/**
 * Created by bimber on 9/15/2014.
 */
public interface ReferenceGenome extends Serializable
{
    /**
     * @return The FASTA file intended to be used during the pipeline, which is usually a copied version of an original FASTA or
     * a file created de novo by querying the sequence DB
     */
    public File getFastaFile();

    /**
     * @return The FASTA index file associated with the working FASTA file
     */
    public File getFastaIndex();

    /**
     * @return The rowId of the corresponding record from
     */
    public Integer getGenomeId();

    /**
     * @return The rowId of the ExpData record matching the final version of the FASTA file.  If this is a saved reference,
     * this will correspond to the permanent FASTA, as opposed to the copy used in this job.  If this FASTA was created specifically for
     * this job then the FASTA will be in the analysis directory.
     */
    public Integer getFastaExpDataId();
}
