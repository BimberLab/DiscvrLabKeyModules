package org.labkey.sequenceanalysis.run.util;

import org.labkey.api.module.ModuleLoader;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

/**
 * Created by bimber on 4/2/2017.
 */
public class GenomicsDBImportHandler extends AbstractGenomicsDBImportHandler
{
    public static final String NAME = "GenomicsDB Import";

    public GenomicsDBImportHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), NAME, "This will run GATK's GenomicsDBImport on a set of GVCF files.  Note: this cannot work against any VCF file - these are primarily VCFs created using GATK's HaplotypeCaller.", null, getToolParameters(false));
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor(false);
    }
}
