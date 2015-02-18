package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.exp.api.ExpData;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.ReadsetModel;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
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
    private ReferenceGenome _referenceGenome;
    private Map<Integer, ReadsetModel> _cachedReadsets = new HashMap<>();
    private Map<Integer, AnalysisModel> _cachedAnalyses = new HashMap<>();
    private Map<Integer, ReferenceGenome> _cachedGenomes = new HashMap<>();

    public SequenceJobSupportImpl()
    {

    }

    public SequenceJobSupportImpl(SequenceJobSupportImpl support)
    {
        _cachedFilePaths.putAll(support._cachedFilePaths);
        _referenceGenome = support.getReferenceGenome();
        _cachedAnalyses.putAll(support._cachedAnalyses);
        _cachedReadsets.putAll(support._cachedReadsets);
        _cachedGenomes.putAll(support._cachedGenomes);
    }

    @Override
    public ReadsetModel getCachedReadset(int rowId)
    {
        return _cachedReadsets.get(rowId);
    }

    @Override
    public AnalysisModel getCachedAnalysis(int rowId)
    {
        return _cachedAnalyses.get(rowId);
    }

    public void cacheReadset(ReadsetModel m)
    {
        _cachedReadsets.put(m.getRowId(), m);
    }

    public void cacheAnalysis(AnalysisModel m)
    {
        _cachedAnalyses.put(m.getRowId(), m);
    }

    public void cacheGenome(ReferenceGenome m)
    {
        _cachedGenomes.put(m.getGenomeId(), m);
    }

    public List<ReadsetModel> getCachedReadsets()
    {
        return Collections.unmodifiableList(new ArrayList<>(_cachedReadsets.values()));
    }

    public List<AnalysisModel> getCachedAnalyses()
    {
        return Collections.unmodifiableList(new ArrayList<>(_cachedAnalyses.values()));
    }

    public ReferenceGenome getCachedGenome(int genomeId)
    {
        return _cachedGenomes.get(genomeId);
    }

    @Override
    public PipelineJob getJob()
    {
        return null;
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
    public ReferenceGenome getReferenceGenome()
    {
        return _referenceGenome;
    }

    public void setReferenceGenome(ReferenceGenome referenceGenome)
    {
        _referenceGenome = referenceGenome;
    }
}
