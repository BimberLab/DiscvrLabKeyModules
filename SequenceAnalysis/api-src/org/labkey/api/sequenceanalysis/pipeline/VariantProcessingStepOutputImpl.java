package org.labkey.api.sequenceanalysis.pipeline;

import java.io.File;

/**
 * Created by bimber on 8/7/2014.
 */
public class VariantProcessingStepOutputImpl extends DefaultPipelineStepOutput implements VariantProcessingStep.Output
{
    File _vcf = null;

    public VariantProcessingStepOutputImpl()
    {

    }

    @Override
    public File getVCF()
    {
        return _vcf;
    }

    public void setVcf(File vcf)
    {
        this.addOutput(vcf, "Output VCF");

        File idx = new File(vcf.getPath() + ".tbi");
        if (idx.exists())
        {
            this.addOutput(idx, "Output VCF Index");
        }

        _vcf = vcf;
    }
}
