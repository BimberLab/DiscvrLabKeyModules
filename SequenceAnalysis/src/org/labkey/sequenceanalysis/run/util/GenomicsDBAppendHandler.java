package org.labkey.sequenceanalysis.run.util;

import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;

import java.util.LinkedHashSet;

//See: https://gatk.broadinstitute.org/hc/en-us/articles/360035891051-GenomicsDB

public class GenomicsDBAppendHandler extends AbstractGenomicsDBImportHandler
{
    public static final String NAME = "GenomicsDB Append/Merge";

    public GenomicsDBAppendHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), NAME, "This will run GATK\'s GenomicsDBImport on a set of GVCF files.  Note: this cannot work against any VCF file - these are primarily VCFs created using GATK\'s HaplotypeCaller.", new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/SequenceOutputFileSelectorField.js")), getToolParameters(true));
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor(true);
    }
}
