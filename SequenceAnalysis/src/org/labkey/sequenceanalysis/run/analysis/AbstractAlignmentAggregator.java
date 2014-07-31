/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.sequenceanalysis.run.analysis;

import net.sf.picard.reference.ReferenceSequence;
import net.sf.samtools.SAMRecord;
import org.apache.log4j.Logger;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.sequenceanalysis.ReferenceLibraryHelper;
import org.labkey.sequenceanalysis.run.util.AASnp;
import org.labkey.sequenceanalysis.run.util.NTSnp;
import org.labkey.sequenceanalysis.util.ReferenceLibraryHelperImpl;
import org.labkey.sequenceanalysis.util.TranslatingReferenceSequence;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 9/22/12
 * Time: 4:25 PM
 */
abstract public class AbstractAlignmentAggregator implements AlignmentAggregator
{
    private Logger _log;
    private Map<String, TranslatingReferenceSequence> _ntReferences = new HashMap<>();
    protected Map<String, CacheKeyInfo> _cacheDef = new HashMap<>();

    private int _minAvgSnpQual = 0;
    private int _minSnpQual = 0;
    private int _minAvgDipQual = 0;
    private int _minDipQual = 0;
    protected AvgBaseQualityAggregator _avgQualAggregator;
    protected boolean _logProgress = true;
    private File _refFasta;
    private ReferenceLibraryHelper _libraryHelper;

    public AbstractAlignmentAggregator(Logger log, File refFasta, AvgBaseQualityAggregator avgQualAggregator, Map<String, String> settings)
    {
        _log = log;
        _refFasta = refFasta;
        initSettings(settings);

        _avgQualAggregator = avgQualAggregator;
    }

    protected File getRefFasta()
    {
        return _refFasta;
    }

    protected ReferenceLibraryHelper getReferenceLibraryHelper()
    {
        if (_libraryHelper == null)
        {
            _libraryHelper = new ReferenceLibraryHelperImpl(_refFasta);
        }

        return _libraryHelper;
    }

    protected Logger getLogger()
    {
        return _log;    
    }

    protected boolean evaluateSnp(SAMRecord r, NTSnp snp)
    {
        //this SNP will likely be inspected by multiple aggregators, so we
        if (snp.isFlagSet())
        {
            return snp.getFlag() == null;
        }

        Map<Integer, Map<String, Double>> avgQualsForRef = getQualsForReference(r.getReferenceIndex());
        assert avgQualsForRef != null;

        int lastRef = snp.getLastRefPosition();
        if (snp.isDel())
            lastRef += snp.getIndel();

        Map<String, Double> avgQuals = avgQualsForRef.get(lastRef);
        int qual = r.getBaseQualities()[snp.getLastReadPosition()]; //always used last available read quality

        //NOTE: for insertions, default to the last non-indel base
        //TODO: track avgQuals based on insertIndex too
        String qBase;
        if (snp.isInsertion())
        {
            qBase = Character.toString((char) r.getReadBases()[snp.getLastReadPosition() - snp.getIndel()]);
        }
        else
        {
            qBase = Character.toString((char) r.getReadBases()[snp.getLastReadPosition()]);
        }

        Double avgQualAtPosition = null;
        if (avgQuals != null && avgQuals.containsKey(qBase))
        {
            avgQualAtPosition = avgQuals.get(qBase);
        }

        if (avgQualAtPosition == null)
        {
            getLogger().error("missing avgQual: " + r.getReadName() + " / " + snp.getIndel() + " / " + snp.getReadPosition() + " / " + snp.getLastReadPosition() + " / [" + snp.getReadBaseString() + "] / " + snp.getLastRefPosition() + " / " + (avgQuals == null ? "" : StringUtils.join(Arrays.asList(avgQuals.keySet()), ";")));
            avgQualAtPosition = 95.0;
        }

        snp.setFlag(null); //so we only perform inspection once
        if (snp.isIndel())
        {
            if (qual < _minDipQual)
            {
                snp.setFlag("Below Minimum DIP Quality");
                return false;
            }
            if (avgQualAtPosition < _minAvgDipQual)
            {
                snp.setFlag("Below Avg. Minimum DIP Quality");
                return false;
            }
        }
        else
        {
            if (qual < _minSnpQual)
            {
                snp.setFlag("Below Minimum SNP Quality");
                return false;
            }
            if (avgQualAtPosition < _minAvgSnpQual)
            {
                snp.setFlag("Below Avg. Minimum SNP Quality");
                return false;
            }
        }
        return true;
    }

    protected void initSettings(Map<String, String> settings)
    {
        if (settings.get("minAvgSnpQual") != null)
        {
            _minAvgSnpQual = Integer.parseInt(settings.get("minAvgSnpQual"));
            //note: this could be overridden below
            _minAvgDipQual = Integer.parseInt(settings.get("minAvgSnpQual"));
        }

        if (settings.get("minSnpQual") != null)
        {
            _minSnpQual = Integer.parseInt(settings.get("minSnpQual"));
            //note: this could be overridden below
            _minDipQual = Integer.parseInt(settings.get("minSnpQual"));
        }

        if (settings.get("minAvgDipQual") != null)
            _minAvgDipQual = Integer.parseInt(settings.get("minAvgDipQual"));
        if (settings.get("minDipQual") != null)
            _minDipQual = Integer.parseInt(settings.get("minDipQual"));
    }

    protected List<AASnp> translateSnpsForRead(SAMRecord record, Map<Integer, List<NTSnp>> readSnps)
    {
        TranslatingReferenceSequence ref = getReferenceFromDb(record.getReferenceName());
        return ref.translateSnpsForRead(readSnps);
    }

    private TranslatingReferenceSequence getReferenceFromDb(String refName)
    {
        if (_ntReferences.containsKey(refName))
            return _ntReferences.get(refName);

        Integer refId = getReferenceLibraryHelper().resolveSequenceId(refName);
        if (refId == null)
        {
            throw new RuntimeException("Improper sequence name format: " + refName);
        }

        TranslatingReferenceSequence ref = new TranslatingReferenceSequence(refId);
        _ntReferences.put(refName, ref);

        return ref;
    }

    private Map<Integer, Map<Integer, Map<String, Double>>> _qualMap = new HashMap<>();

    private Map<Integer, Map<String, Double>> getQualsForReference(Integer refId)
    {
        if (!_qualMap.containsKey(refId))
            _qualMap.put(refId, _avgQualAggregator.getQualsForReference(refId));

        return _qualMap.get(refId);
    }

    /**
     * Because SNPs within an insert can be discarded, we might need to re-number the SNPs
     */
    protected List<NTSnp> recalculateInsertIndex(List<NTSnp> snps)
    {
        int idx = 0;
        for (NTSnp snp : snps)
        {
            if (snp.getInsertIndex() > 0)
            {
                assert snp.getInsertIndex() >= idx;  //assume already sorted
                idx++;
                if (idx != snp.getInsertIndex())
                {
                    //getLogger().info("resetting snp insert index at position " + snp.getLastRefPosition());
                    snp.setInsertIndex(idx);
                }
            }
        }

        return snps;
    }

    public void setLogProgress(boolean logProgress)
    {
        _logProgress = logProgress;
    }

    public int getMinAvgSnpQual()
    {
        return _minAvgSnpQual;
    }

    public void setMinAvgSnpQual(int minAvgSnpQual)
    {
        _minAvgSnpQual = minAvgSnpQual;
    }

    public int getMinSnpQual()
    {
        return _minSnpQual;
    }

    public void setMinSnpQual(int minSnpQual)
    {
        _minSnpQual = minSnpQual;
    }

    public int getMinAvgDipQual()
    {
        return _minAvgDipQual;
    }

    public void setMinAvgDipQual(int minAvgDipQual)
    {
        _minAvgDipQual = minAvgDipQual;
    }

    public int getMinDipQual()
    {
        return _minDipQual;
    }

    public void setMinDipQual(int minDipQual)
    {
        _minDipQual = minDipQual;
    }

    protected class CacheKeyInfo
    {
        private String _refName;
        private Integer _refPosition;
        private Integer _insertIndex;
        private String _readBase;
        private String _refBase;
        private String _coverageKey;

        public CacheKeyInfo(NTSnp snp, ReferenceSequence ref)
        {
            _refName = snp.getPositionInfo().getReferenceName();
            _refPosition = snp.getLastRefPosition();
            _insertIndex = snp.getInsertIndex();
            _readBase = snp.getReadBaseString();
            _refBase = Character.toString((char)snp.getReferenceBase(ref.getBases()));

            _coverageKey = new StringBuilder()
                .append(snp.getPositionInfo().getReferenceName())
                .append("||")
                .append(snp.getLastRefPosition()).append("||")
                .append(snp.getInsertIndex()).toString();
        }

        public String getRefName()
        {
            return _refName;
        }

        public int getRefPosition()
        {
            return _refPosition;
        }

        //1-based ref-position
        public int getRefPosition1()
        {
            return _refPosition + 1;
        }

        public String getReadBase()
        {
            return _readBase;
        }

        public int getInsertIndex()
        {
            return _insertIndex;
        }

        public String getRefBase()
        {
            return _refBase;
        }

        public String getCoverageKey()
        {
            return _coverageKey;
        }
    }
}
