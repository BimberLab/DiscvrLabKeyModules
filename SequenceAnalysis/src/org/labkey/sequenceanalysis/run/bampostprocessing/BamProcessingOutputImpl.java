package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.pipeline.BamProcessingStep;
import org.labkey.sequenceanalysis.api.pipeline.DefaultPipelineStepOutput;

import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 6/27/2014
 * Time: 9:26 AM
 */
public class BamProcessingOutputImpl extends DefaultPipelineStepOutput implements BamProcessingStep.Output
{
    public static final String PROCESSED_BAM_ROLE = "Processed BAM";

    public BamProcessingOutputImpl()
    {

    }

    public void setBAM(File file)
    {
        if (file == null)
        {
            throw new RuntimeException("BAM file is null");
        }

        addOutput(file, PROCESSED_BAM_ROLE);
    }

    @Override
    public File getBAM()
    {
        List<File> outputs = getOutputsOfRole(PROCESSED_BAM_ROLE);

        return outputs.size() == 1 ? outputs.get(0) : null;
    }
}
