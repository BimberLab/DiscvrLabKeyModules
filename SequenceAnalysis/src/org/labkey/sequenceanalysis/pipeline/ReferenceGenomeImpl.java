package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.sequenceanalysis.run.alignment.AlignerIndexUtil;

import java.io.File;

/**
 * Created by bimber on 9/15/2014.
 */
public class ReferenceGenomeImpl implements ReferenceGenome
{
    private File _sourceFasta;
    private File _workingFasta;
    private Integer _genomeId;
    private Integer _expDataId;

    public ReferenceGenomeImpl(@NotNull File sourceFasta, @Nullable ExpData fastaExpData, @Nullable Integer genomeId)
    {
        _sourceFasta = sourceFasta;
        _expDataId = fastaExpData == null ? null : fastaExpData.getRowId();
        _genomeId = genomeId;
    }

    @Override
    public @NotNull File getSourceFastaFile()
    {
        return _sourceFasta;
    }

    @Override
    public @NotNull File getWorkingFastaFile()
    {
        return _workingFasta == null ? _sourceFasta : _workingFasta;
    }

    @Override
    public void setWorkingFasta(File workingFasta)
    {
        _workingFasta = workingFasta;
    }

    @Override
    public File getFastaIndex()
    {
        return getWorkingFastaFile() == null ? null : new File(getWorkingFastaFile().getPath() + ".fai");
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

    public File getAlignerIndexDir(String name)
    {
        //if _workingFasta is null, we are working on the primary webserver dir
        if (_workingFasta == null)
        {
            File ret = new File(_sourceFasta.getParentFile(), AlignerIndexUtil.INDEX_DIR);

            return new File(ret, name);
        }
        else
        {
            return new File(_workingFasta.getParentFile(), name);
        }
    }
}
