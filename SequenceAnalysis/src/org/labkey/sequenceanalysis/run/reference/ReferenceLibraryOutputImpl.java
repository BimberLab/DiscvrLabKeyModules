package org.labkey.sequenceanalysis.run.reference;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.pipeline.DefaultPipelineStepOutput;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceLibraryStep;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryTask;

import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 6/21/2014
 * Time: 11:14 AM
 */
public class ReferenceLibraryOutputImpl extends DefaultPipelineStepOutput implements ReferenceLibraryStep.Output
{
    public ReferenceLibraryOutputImpl()
    {

    }

    @Override
    public File getReferenceFasta() throws PipelineJobException
    {
        List<File> ret = getOutputsOfRole(ReferenceLibraryTask.REFERENCE_DB_FASTA);
        if (ret.size() != 1)
        {
            throw new PipelineJobException("More than reference FASTA file found, expected 1");
        }

        return ret.get(0);
    }
}
