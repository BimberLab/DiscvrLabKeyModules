package org.labkey.sequenceanalysis.util;

import org.labkey.api.util.FileType;

import java.util.Arrays;

/**
 * Created by bimber on 2/3/2015.
 */
public class SequenceOutputFileType extends FileType
{
    public SequenceOutputFileType()
    {
        super(Arrays.asList(".bam", ".cram", ".gff", ".gtf", ".bed", ".vcf", ".rds", ".bw", ".txt"), ".bed", FileType.gzSupportLevel.SUPPORT_GZ);
    }
}
