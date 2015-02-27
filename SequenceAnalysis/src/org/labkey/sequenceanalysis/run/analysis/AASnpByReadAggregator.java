package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.reference.ReferenceSequence;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.api.picard.CigarPositionIterable;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.sequenceanalysis.run.util.AASnp;
import org.labkey.sequenceanalysis.run.util.NTSnp;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 11/11/12
 * Time: 9:07 PM
 */
public class AASnpByReadAggregator extends AASnpByCodonAggregator
{
    private Map<String, Integer> _snps = new HashMap<>();
    private Map<String, CacheKeyInfo> _cacheDef = new HashMap<>();
    private Map<String, byte[]> _refSequenceMap = new HashMap<>();
    private static final String SNP_KEY = "_snpKey";

    public AASnpByReadAggregator(Logger log, File refFasta, AvgBaseQualityAggregator avgQualAggregator, Map<String, String> settings)
    {
        super(log, refFasta, avgQualAggregator, settings);
    }

    @Override
    public void inspectAlignment(SAMRecord record, ReferenceSequence ref, Map<Integer, List<NTSnp>> snps, CigarPositionIterable cpi)
    {
        super.inspectAlignment(record, ref, snps, cpi);

        //NOTE: in order to match the behavior of SamLocusIterator, skip over Duplicate or Secondary/Supplemental reads
        if (record.getReadUnmappedFlag() || record.getDuplicateReadFlag() || record.isSecondaryOrSupplementary())
        {
            return;
        }

        assert ref != null;

        if (!_refSequenceMap.containsKey(ref.getName()));
            _refSequenceMap.put(ref.getName(), ref.getBases());

        Map<Integer, List<NTSnp>> highQualitySnpsByPos = new HashMap<>();

        for (Integer pos : snps.keySet())
        {
            List<NTSnp> highQualitySnps = new ArrayList<>();
            for (NTSnp snp : snps.get(pos))
            {
                inspectNtSnp(snp, record, ref);
                if (snp.getFlag() == null)
                    highQualitySnps.add(snp);
            }
            highQualitySnpsByPos.put(pos, highQualitySnps);
        }

        if (highQualitySnpsByPos.size() > 0)
        {
            for (AASnp snp : translateSnpsForRead(record, highQualitySnpsByPos))
            {
                addSnp(snp);
            }
        }
    }

    private void addSnp(AASnp snp)
    {
        String key = getAAKey(snp);
        CacheKeyInfo info = _cacheDef.get(key);
        if (info == null)
            info = new CacheKeyInfo(snp);

        info.addSNP(snp);

        _cacheDef.put(key, info);

        if (!snp.getReadAaResidue().equals(snp.getReferenceAaResidue()))
        {
            Integer count = _snps.get(key);
            if (count == null)
            {
                count = 1;
            }
            else
            {
                count++;
            }
            _snps.put(key, count);
        }
    }

    private String getAAKey(AASnp snp)
    {
        return new StringBuilder().append(snp.getAaRef().getRowId()).append("||")
            .append(snp.getReferenceAaPosition()).append("||")
            .append(snp.getCodon()).append("||")
            .append(snp.getNtSnp().getReadname()).toString();
    }

    @Override
    public List<Map<String, Object>> getResults(User u, Container c, AnalysisModel model)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        return rows;
    }

    @Override
    public void saveToDb(User u, Container c, AnalysisModel model)
    {
        throw new UnsupportedOperationException("Not supported for this aggregator");
    }

    private class CacheKeyInfo
    {
        private Integer _aaRefId;
        private String _aaRefName;
        private String _ntRefName;
        private Integer _aaRefPos;
        private Integer _aaInsertIndex;
        private String _readResidue;
        private String _refResidue;
        private String _codon;

        private Set<Pair<Integer, Integer>> _ntPositions = new HashSet<>();
        private Set<String> _ntPositionStrings = new HashSet<>();

        public CacheKeyInfo(AASnp snp)
        {
            _aaRefId = snp.getAaRef().getRowId();
            _aaRefName = snp.getAaRef().getName();
            _ntRefName = snp.getNtSnp().getReferenceName();
            _aaRefPos = snp.getReferenceAaPosition();
            _aaInsertIndex = 0;


            _readResidue = snp.getReadAaResidue();
            _refResidue = snp.getReferenceAaResidue();
            _codon = snp.getCodon();

            addSNP(snp);
        }

        public Integer getAaRefId()
        {
            return _aaRefId;
        }

        public Integer getAaRefPos()
        {
            return _aaRefPos;
        }

        public Integer getAaInsertIndex()
        {
            return _aaInsertIndex;
        }

        public String getReadResidue()
        {
            return _readResidue;
        }

        public String getRefResidue()
        {
            return _refResidue;
        }

        public String getCodon()
        {
            return _codon;
        }

        public String getDepthKey(Integer ntPos, Integer ntIndex)
        {
            return _ntRefName + "||" + ntPos + "||" + ntIndex;
        }

        public String getAaRefName()
        {
            return _aaRefName;
        }

        public String getNtRefName()
        {
            return _ntRefName;
        }

        public String getNtPositionString()
        {
            StringBuilder sb = new StringBuilder();
            String delim = "";
            for (String n : _ntPositionStrings)
            {
                sb.append(delim + n);
                delim = ",";
            }
            return sb.toString();
        }

        public void addSNP(AASnp snp)
        {
            _ntPositions.add(Pair.of(snp.getNtSnp().getLastRefPosition(), snp.getNtSnp().getInsertIndex()));

            //calculate display string for SNP
            _ntPositionStrings.add(
                    snp.getNtSnp().getReferenceBaseString(_refSequenceMap.get(snp.getNtSnp().getReferenceName())) +
                            snp.getNtSnp().getLastRefPosition() + (snp.getNtSnp().getInsertIndex() == 0 ? "" : "." + snp.getNtSnp().getInsertIndex()) +
                            snp.getNtSnp().getReadBaseString()
            );
        }
    }
}