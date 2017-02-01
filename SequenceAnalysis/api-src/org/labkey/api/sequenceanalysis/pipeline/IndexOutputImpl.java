package org.labkey.api.sequenceanalysis.pipeline;

import org.labkey.api.util.FileUtil;

import java.io.File;

/**
 * User: bimber
 * Date: 6/21/2014
 * Time: 10:25 AM
 */
public class IndexOutputImpl extends DefaultPipelineStepOutput implements AlignmentStep.IndexOutput
{
    public static final String PRIMARY_ALIGNER_INDEX_FILE = "Primary Aligner Index File";
    public static final String REFERENCE_DB_FASTA_OUTPUT = "Reference Output";
    public static final String REFERENCE_DB_FASTA = "Reference FASTA";

    public IndexOutputImpl(ReferenceGenome referenceGenome)
    {
        addInput(referenceGenome.getWorkingFastaFile(), REFERENCE_DB_FASTA);
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

        addOutput(outputDir, REFERENCE_DB_FASTA_OUTPUT);
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

        addDeferredDeleteIntermediateFile(outputDir);
    }
}
