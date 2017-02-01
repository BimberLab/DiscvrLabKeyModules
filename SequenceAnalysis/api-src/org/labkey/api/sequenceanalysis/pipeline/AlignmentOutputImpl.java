package org.labkey.api.sequenceanalysis.pipeline;

import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 6/21/2014
 * Time: 12:01 PM
 */
public class AlignmentOutputImpl extends DefaultPipelineStepOutput implements AlignmentStep.AlignmentOutput
{
    public static final String BAM_INDEX = "BAM File Index";
    public static final String BAM_ROLE = "BAM File";
    public static final String UNALIGNED_FASTQ_ROLE = "Unaligned FASTQ File";
    public static final String ALIGNMENT_OUTPUT_ROLE = "Alignment Output";

    public AlignmentOutputImpl()
    {

    }

    @Override
    public File getBAM()
    {
        List<File> files = getOutputsOfRole(BAM_ROLE);
        return files.size() == 1 ? files.get(0) : null;
    }

    @Override
    public void addIntermediateFile(File file)
    {
        super.addIntermediateFile(file, ALIGNMENT_OUTPUT_ROLE);
    }

    @Override
    public File getUnalignedReadsFastq()
    {
        List<File> files = getOutputsOfRole(UNALIGNED_FASTQ_ROLE);
        return files.size() == 1 ? files.get(0) : null;
    }

    public void setBAM(File outputAlignment)
    {
        if (!outputAlignment.exists())
        {
            return;
        }

        addOutput(outputAlignment, BAM_ROLE);
    }
}
