package org.labkey.sequenceanalysis.run.alignment;

import org.labkey.api.util.FileUtil;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.DefaultPipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryTask;

import java.io.File;

/**
 * User: bimber
 * Date: 6/21/2014
 * Time: 10:25 AM
 */
public class IndexOutputImpl extends DefaultPipelineStepOutput implements AlignmentStep.IndexOutput
{
    public static final String PRIMARY_ALIGNER_INDEX_FILE = "Primary Aligner Index File";

    public IndexOutputImpl(ReferenceGenome referenceGenome)
    {
        addInput(referenceGenome.getWorkingFastaFile(), ReferenceLibraryTask.REFERENCE_DB_FASTA);
    }

    public void appendOutputs(File refFasta, File outputDir)
    {
        appendOutputs(refFasta, outputDir, true);
    }

    public void appendOutputs(File refFasta, File outputDir, boolean addOutputsAsIntermediates)
    {
        File refFastaIndex = new File(refFasta.getPath() + ".fai");
        File refFastaIdKey = new File(refFasta.getParentFile(), FileUtil.getBaseName(refFasta) + ".idKey.txt");
        if (outputDir == null || !outputDir.exists())
        {
            return;
        }

        addOutput(outputDir, ReferenceLibraryTask.REFERENCE_DB_FASTA_OUTPUT);
        for (File f : outputDir.listFiles())
        {
            if (!f.equals(refFasta) && !f.equals(refFastaIndex) && !f.equals(refFastaIdKey))
            {
                if (addOutputsAsIntermediates)
                {
                    addIntermediateFile(f);
                }
            }
        }
    }
}
