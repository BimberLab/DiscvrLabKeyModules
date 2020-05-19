package org.labkey.sequenceanalysis.pipeline;

import java.io.File;

/**
 * This class is designed to represent a 'file type' which is a set of GenomicsDB workspaces stored
 * in one parent folder. Each workspace represents defined interval(s).  The JSON file at the root holds a
 * mapping of intervals within each shard, and can map contig -> GenomicsDB workspace.  This organizational scheme
 * is designed to facilitate scatter/gather processing.
 */
public class GenomicsDBCollection
{
    public static String NAME = "GenomicsDB Collection";

    public static String FILE_EXTENSION = ".genomicsdb.collection";

    final File _jsonFile;

    public GenomicsDBCollection(File jsonFile)
    {
        _jsonFile = jsonFile;
    }

    public File getJsonFile()
    {
        return _jsonFile;
    }
}
