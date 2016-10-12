package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.sequenceanalysis.SequenceAnalysisServiceImpl;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 2/10/2015.
 */
public class SequenceJobSupportImpl implements SequenceAnalysisJobSupport, Serializable
{
    private Map<Integer, File> _cachedFilePaths = new HashMap<>();
    private List<SequenceReadsetImpl> _cachedReadsets = new ArrayList<>();
    private Map<Integer, AnalysisModel> _cachedAnalyses = new HashMap<>();
    private Map<Integer, ReferenceGenome> _cachedGenomes = new HashMap<>();
    private Map<String, Serializable> _cachedObjects = new HashMap<>();

    public SequenceJobSupportImpl()
    {

    }

    public SequenceJobSupportImpl(SequenceJobSupportImpl support)
    {
        _cachedFilePaths.putAll(support._cachedFilePaths);
        _cachedAnalyses.putAll(support._cachedAnalyses);
        _cachedReadsets.addAll(support._cachedReadsets);
        _cachedGenomes.putAll(support._cachedGenomes);
    }

    @Override
    public SequenceReadsetImpl getCachedReadset(Integer rowId)
    {
        for (SequenceReadsetImpl rs : _cachedReadsets)
        {
            if (rowId.equals(rs.getRowId()))
            {
                return rs;
            }
        }

        return null;
    }

    @Override
    public AnalysisModel getCachedAnalysis(int rowId)
    {
        return _cachedAnalyses.get(rowId);
    }

    public void cacheReadset(int readsetId, User u)
    {
        SequenceReadsetImpl rs = SequenceAnalysisServiceImpl.get().getReadset(readsetId, u);
        if (rs != null)
        {
            cacheReadset(rs);
        }
    }

    public void cacheReadset(SequenceReadsetImpl m)
    {
        m.cacheForRemoteServer();
        _cachedReadsets.add(m);
    }

    public void cacheAnalysis(AnalysisModelImpl m, PipelineJob job)
    {
        if (m.getAlignmentFile() != null)
        {
            cacheExpData(m.getAlignmentData());
        }

        SequenceReadsetImpl rs = SequenceAnalysisServiceImpl.get().getReadset(m.getReadset(), job.getUser());
        cacheReadset(rs);

        if (m.getLibraryId() != null)
        {
            try
            {
                ReferenceGenome rg = SequenceAnalysisService.get().getReferenceGenome(m.getLibraryId(), job.getUser());
                cacheGenome(rg);
                cacheExpData(ExperimentService.get().getExpData(rg.getFastaExpDataId()));
            }
            catch (PipelineJobException e)
            {
                job.getLogger().error(e.getMessage(), e);
            }
        }

        if (m.getReferenceLibrary() != null)
        {
            cacheExpData(ExperimentService.get().getExpData(m.getReferenceLibrary()));
        }

        _cachedAnalyses.put(m.getRowId(), m);
    }

    @Override
    public void cacheGenome(ReferenceGenome m)
    {
        _cachedGenomes.put(m.getGenomeId(), m);
    }

    @Override
    public List<Readset> getCachedReadsets()
    {
        return Collections.unmodifiableList(new ArrayList<Readset>(_cachedReadsets));
    }

    @Override
    public List<AnalysisModel> getCachedAnalyses()
    {
        return Collections.unmodifiableList(new ArrayList<>(_cachedAnalyses.values()));
    }

    @Override
    public ReferenceGenome getCachedGenome(int genomeId)
    {
        return _cachedGenomes.get(genomeId);
    }

    @Override
    public Collection<ReferenceGenome> getCachedGenomes()
    {
        return Collections.unmodifiableCollection(_cachedGenomes.values());
    }

    @Override
    public void cacheExpData(ExpData data)
    {
        if (data != null)
        {
            _cachedFilePaths.put(data.getRowId(), data.getFile());
        }
    }

    @Override
    public File getCachedData(int dataId)
    {
        return _cachedFilePaths.get(dataId);
    }

    @Override
    public Map<Integer, File> getAllCachedData()
    {
        return Collections.unmodifiableMap(_cachedFilePaths);
    }

    @Override
    public void cacheObject(String key, Serializable object)
    {
        _cachedObjects.put(key, object);
    }

    @Override
    public Object getCachedObject(String key)
    {
        return _cachedObjects.get(key);
    }
}
