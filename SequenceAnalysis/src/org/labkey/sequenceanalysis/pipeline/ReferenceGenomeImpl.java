package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.api.ExpData;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceGenome;

import java.io.File;

/**
 * Created by bimber on 9/15/2014.
 */
public class ReferenceGenomeImpl implements ReferenceGenome
{
    private File _fastaFileWork;
    private Integer _genomeId;
    private Integer _expDataId;

    public ReferenceGenomeImpl(File fastaFileWork, @Nullable ExpData fastaExpData, @Nullable Integer genomeId)
    {
        _fastaFileWork = fastaFileWork;
        _expDataId = fastaExpData == null ? null : fastaExpData.getRowId();
        _genomeId = genomeId;
    }

    @Override
    public File getFastaFile()
    {
        return _fastaFileWork;
    }

    @Override
    public File getFastaIndex()
    {
        return _fastaFileWork == null ? null : new File(_fastaFileWork.getPath() + ".fai");
    }

    @Override
    public Integer getGenomeId()
    {
        return _genomeId;
    }

    @Override
    public Integer getFastaExpDataId()
    {
        return _expDataId;
    }
}
