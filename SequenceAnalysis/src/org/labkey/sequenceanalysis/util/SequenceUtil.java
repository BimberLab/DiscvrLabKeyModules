package org.labkey.sequenceanalysis.util;

import org.labkey.api.util.FileType;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 11/20/12
 * Time: 9:49 PM
 */
public class SequenceUtil
{
    public static FILETYPE inferType(File file)
    {
        for (FILETYPE f : FILETYPE.values())
        {
            FileType ft = f.getFileType();
            if (ft.isType(file))
                return f;
        }
        return null;
    }

    public static enum FILETYPE
    {
        fastq(".fastq", ".fq"),
        fasta(".fasta"),
        sff(".sff");

        List<String> _extensions;

        FILETYPE(String... extensions)
        {
            _extensions = Arrays.asList(extensions);
        }

        public FileType getFileType()
        {
            return new FileType(_extensions, _extensions.get(0));
        }

        public String getPrimaryExtension()
        {
            return _extensions.get(0);
        }
    }

}
