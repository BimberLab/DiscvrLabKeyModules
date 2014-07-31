package org.labkey.sequenceanalysis.util;

import org.labkey.api.util.FileType;

import java.util.Arrays;

/**
 * User: bimber
 * Date: 12/3/12
 * Time: 10:32 PM
 */
public class FastqFileType extends FileType
{
    public FastqFileType()
    {
        super(Arrays.asList(".fastq", ".fq"), ".fastq", FileType.gzSupportLevel.SUPPORT_GZ);
    }
}
