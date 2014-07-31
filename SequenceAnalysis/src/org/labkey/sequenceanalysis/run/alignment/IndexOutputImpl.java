package org.labkey.sequenceanalysis.run.alignment;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.pipeline.AlignmentStep;
import org.labkey.sequenceanalysis.api.pipeline.DefaultPipelineStepOutput;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryTask;

import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 6/21/2014
 * Time: 10:25 AM
 */
public class IndexOutputImpl extends DefaultPipelineStepOutput implements AlignmentStep.IndexOutput
{
    public static final String PRIMARY_ALIGNER_INDEX_FILE = "Primary Aligner Index File";

    public IndexOutputImpl(File refFasta)
    {
        addOutput(refFasta, ReferenceLibraryTask.REFERENCE_DB_FASTA);
    }

    @Override
    public File getPrimaryIndexFile() throws PipelineJobException
    {
        List<File> ret = getOutputsOfRole(PRIMARY_ALIGNER_INDEX_FILE);
        if (ret.size() != 1)
        {
            throw new PipelineJobException("More than aligner index file found, expected 1");
        }

        return ret.get(0);
    }

    public void appendOutputs(File refFasta, File outputDir)
    {
        File refFastaIndex = new File(refFasta.getPath() + ".fai");
        addOutput(outputDir, ReferenceLibraryTask.REFERENCE_DB_FASTA_OUTPUT);
        for (File f : outputDir.listFiles())
        {
            if (!f.equals(refFasta) && !f.equals(refFastaIndex))
            {
                addDeferredDeleteIntermediateFile(f);
            }
        }
    }
}
