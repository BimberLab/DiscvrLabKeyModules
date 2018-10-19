package org.labkey.sequenceanalysis.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileUtil;

import java.io.File;

/**
 * Created by bimber on 9/15/2014.
 */
public class ReferenceGenomeImpl implements ReferenceGenome
{
    private String _name;
    private File _sourceFasta;
    private File _workingFasta;
    private Integer _genomeId;
    private Integer _expDataId;

    private static final int TEMPORARY_GENOME = -1;

    // Default constructor for serialization
    protected ReferenceGenomeImpl()
    {
    }

    public ReferenceGenomeImpl(@NotNull File sourceFasta, @Nullable ExpData fastaExpData, @Nullable Integer genomeId, @Nullable String name)
    {
        _sourceFasta = sourceFasta;
        _expDataId = fastaExpData == null ? null : fastaExpData.getRowId();
        _genomeId = genomeId == null ? TEMPORARY_GENOME : genomeId;
        _name = name;
    }

    public boolean isTemporaryGenome() {
        return TEMPORARY_GENOME == getGenomeId();
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

    @JsonIgnore
    @Override
    public File getFastaIndex()
    {
        return getWorkingFastaFile() == null ? null : new File(getWorkingFastaFile().getPath() + ".fai");
    }

    @JsonIgnore
    @Override
    public File getSequenceDictionary()
    {
        return getWorkingFastaFile() == null ? null : new File(FileUtil.getBaseName(getWorkingFastaFile().getPath()) + ".dict");
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public Integer getGenomeId()
    {
        return _genomeId;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public void setSourceFasta(File sourceFasta)
    {
        _sourceFasta = sourceFasta;
    }

    public void setGenomeId(Integer genomeId)
    {
        _genomeId = genomeId;
    }

    public void setExpDataId(Integer expDataId)
    {
        _expDataId = expDataId;
    }

    @Override
    public Integer getFastaExpDataId()
    {
        return _expDataId;
    }

    @JsonIgnore
    public File getAlignerIndexDir(String name)
    {
        //if genomeId is null, we are using an ad hoc genome
        if (_genomeId == null)
        {
            return new File(getWorkingFastaFile().getParentFile(), name);
        }

        //if _workingFasta is null, we are working on the primary webserver dir
        if (_workingFasta == null)
        {
            File ret = new File(_sourceFasta.getParentFile(), AlignerIndexUtil.INDEX_DIR);

            return new File(ret, name);
        }
        else
        {
            //if we rsync locally, these are cached w/ the same structure as the server
            //this only applies for saved reference genomes (i.e. genomeId != null)
            File remoteDir = SequencePipelineService.get().getRemoteGenomeCacheDirectory();
            if (remoteDir == null)
            {
                return new File(_workingFasta.getParentFile(), name);
            }
            else
            {
                File ret = new File(_workingFasta.getParentFile(), AlignerIndexUtil.INDEX_DIR);

                return new File(ret, name);
            }
        }
    }
}
