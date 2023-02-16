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

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.reference.ReferenceSequence;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.api.picard.CigarPositionIterable;
import org.labkey.sequenceanalysis.run.util.AASnp;
import org.labkey.sequenceanalysis.run.util.NTSnp;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 10/7/12
 * Time: 10:39 PM
 */
public class AASnpByCodonAggregator extends NtSnpByPosAggregator
{
    private final Map<String, Integer> _snps = new HashMap<>();
    private final Map<String, CacheKeyInfo> _cacheDef = new HashMap<>();
    private final Map<String, byte[]> _refSequenceMap = new HashMap<>();
    private static final String SNP_KEY = "_snpKey";
    private int _totalAlignments = 0;
    private int _totalFilteredSnps = 0;

    public AASnpByCodonAggregator(Logger log, File refFasta, AvgBaseQualityAggregator avgQualAggregator, Map<String, String> settings)
    {
        super(log, refFasta, avgQualAggregator, settings);
    }

    @Override
    public void inspectAlignment(SAMRecord record, ReferenceSequence ref, Map<Integer, List<NTSnp>> snps) throws PipelineJobException
    {
        super.inspectAlignment(record, ref, snps);

        if (!isPassingAlignment(record, true))
        {
            return;
        }

        _totalAlignments++;

        assert ref != null;
        _refSequenceMap.put(ref.getName(), ref.getBases());

        Map<Integer, List<NTSnp>> highQualitySnpsByPos = new HashMap<>();

        for (Integer pos : snps.keySet())
        {
            List<NTSnp> highQualitySnps = new ArrayList<>();
            for (NTSnp snp : snps.get(pos))
            {
                if (isPassingSnp(record, snp))
                {
                    highQualitySnps.add(snp);
                }
                else
                {
                    _totalFilteredSnps++;
                }
            }

            if (!highQualitySnps.isEmpty())
                highQualitySnpsByPos.put(pos, highQualitySnps);
        }

        if (!highQualitySnpsByPos.isEmpty())
        {
            //recalculate insert index in case SNPs within an insert were discarded
            for (Integer pos : highQualitySnpsByPos.keySet())
            {
                highQualitySnpsByPos.put(pos, recalculateInsertIndex(highQualitySnpsByPos.get(pos)));
            }

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
        else
            info.addSNP(snp);

        _cacheDef.put(key, info);

        Integer count = _snps.getOrDefault(key, 0);
        count++;

        _snps.put(key, count);
    }

    private String getAAKey(AASnp snp)
    {
        return snp.getAaRef().getRowId() + "||" +
                snp.getReferenceAaPosition() + "||" + snp.getAaInsertIndex() + "||" +
                snp.getCodon() + "||";
    }

    public List<Map<String, Object>> getResults(User u, Container c, AnalysisModel model)
    {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (String key : _snps.keySet())
        {
            CacheKeyInfo info = _cacheDef.get(key);
            Map<String, Object> row = new HashMap<>();

            row.put(SNP_KEY, key);
            row.put("analysis_id", model.getAnalysisId());
            row.put("ref_aa_id", info.getAaRefId());
            row.put("ref_nt_id", info.getRefNtId());
            row.put("ref_aa_position", info.getAaRefPos());
            row.put("ref_aa_insert_index", info.getAaInsertIndex());
            row.put("ref_aa", info.getRefResidue());
            row.put("q_aa", info.getReadResidue());
            row.put("codon", info.getCodon());

            int readCount = info.getReadCount();
            row.put("readcount", readCount);
            row.put("ref_nt_positions", info.getNtPositionString());

            Double depth = info.calculateNtDepth(false);
            row.put("depth", depth);

            if (depth != null && !info.getReadResidue().equals(":"))
            {
                Double adj_depth = info.calculateNtDepth(true);
                double pct = depth == 0 ? 0 : ((double) readCount / adj_depth ) * 100.0;
                row.put("adj_depth", adj_depth);
                row.put("pct", pct);
            }

            row.put("container", c.getEntityId());
            row.put("createdby", u.getUserId());
            row.put("modifiedby", u.getUserId());
            row.put("created", new Date());
            row.put("modified", new Date());

            rows.add(row);
        }

        return rows;
    }

    @Override
    public void writeOutput(User u, Container c, AnalysisModel model)
    {
        getLogger().info("Saving AA SNP Results");
        Map<String, Integer> summary = new HashMap<>();

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            //delete existing records
            TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("analysis_id"), model.getAnalysisId());
            long deleted = Table.delete(ti, filter);

            List<Map<String, Object>> rows = getResults(u, c, model);
            for (Map<String, Object> row : rows)
            {
                //keep track of positions by reference
                CacheKeyInfo info = _cacheDef.get(row.get(SNP_KEY));
                String refId = info.getNtRefName() + " " + info.getAaRefName();
                Integer totalSaved = summary.get(refId);
                if (totalSaved == null)
                    totalSaved = 0;

                totalSaved++;
                summary.put(refId, totalSaved);

                Table.insert(u, ti, row);
            }

            transaction.commit();

            getLogger().info("\tTotal AA Reference sequences encountered: " + summary.keySet().size());
            getLogger().info("\tTotal alignments discarded due to low mapping quality: " + _lowMappingQual);
            getLogger().info("\tTotal filtered SNPs: " + _totalFilteredSnps);
            getLogger().info("\tTotal alignments inspected: " + _totalAlignments);
            getLogger().info("\tSNPs saved by reference:");
            for (String refId : summary.keySet())
            {
                getLogger().info("\t" + refId + ": " + summary.get(refId));
            }
        }
    }

    private class CacheKeyInfo
    {
        private final int _aaRefId;
        private final String _aaRefName;
        private final String _ntRefName;
        private final int _ntRefId;
        private final int _aaRefPos;
        private final int _aaInsertIndex;
        private final String _readResidue;
        private final String _refResidue;
        private final String _codon;

        private final Set<Pair<Integer, Integer>> _ntPositions = new HashSet<>();
        private final Map<String, Integer> _ntPositionStrings = new HashMap<>();
        private final Map<String, Set<String>> _readnamesByPos = new HashMap<>();
        private final Set<String> _readnames = new HashSet<>();
        private final Set<String> _dupReadnames = new HashSet<>();

        public CacheKeyInfo(AASnp snp)
        {
            _aaRefId = snp.getAaRef().getRowId();
            _aaRefName = snp.getAaRef().getName();
            _ntRefName = snp.getNtSnp().getReferenceName();
            _aaRefPos = snp.getReferenceAaPosition();
            _aaInsertIndex = snp.getAaInsertIndex();

            _readResidue = snp.getReadAaResidue();
            _refResidue = snp.getReferenceAaResidue();
            _codon = snp.getCodon();

            //infer ID
            _ntRefId = getReferenceLibraryHelper().resolveSequenceId(_ntRefName);

            addSNP(snp);
        }

        public int getAaRefId()
        {
            return _aaRefId;
        }

        public int getAaRefPos()
        {
            return _aaRefPos;
        }

        public int getAaInsertIndex()
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
            for (String n : _ntPositionStrings.keySet())
            {
                sb.append(delim).append(n).append(" (").append(_ntPositionStrings.get(n)).append(")");
                delim = ",\n";
            }
            return sb.toString();
        }

        public void addSNP(AASnp snp)
        {
            _ntPositions.add(Pair.of(snp.getNtSnp().getLastRefPosition(), snp.getNtSnp().getInsertIndex()));
            _readnames.add(snp.getNtSnp().getReadname());

            //calculate display string for SNP
            String ntPosString = snp.getNtPositionString();
            if (!_readnamesByPos.containsKey(ntPosString))
            {
                _readnamesByPos.put(ntPosString, new HashSet<String>());
            }

            if (_readnamesByPos.get(ntPosString).contains(snp.getNtSnp().getReadname()))
            {
                _dupReadnames.add(snp.getNtSnp().getReadname());
            }
            else
            {
                Integer count = _ntPositionStrings.containsKey(ntPosString) ? _ntPositionStrings.get(ntPosString) : 0;
                count++;
                _ntPositionStrings.put(ntPosString, count);

                _readnamesByPos.get(ntPosString).add(snp.getNtSnp().getReadname());
            }
        }

        public double calculateNtDepth(boolean useHCDepth)
        {
            int depth = 0;
            for (Pair<Integer, Integer> nt : _ntPositions)
            {
                depth += useHCDepth? getCoverageAggregator().getHcDepthAtPosition(_ntRefName, nt.first, 0) : getCoverageAggregator().getDepthAtPosition(_ntRefName, nt.first, 0); //always use depth at last non-indel position
            }

            return (depth / (double)_ntPositions.size());
        }

        public int getRefNtId()
        {
            return _ntRefId;
        }

        public int getReadCount()
        {
            return _readnames.size();
        }
    }

    @Override
    public String getSynopsis()
    {
        return "AA SNP By Codon Aggregator:\n" +
                "\tMinMapQual: " + getMinMapQual() + "\n" +
                "\tMinSnpQual: " + getMinSnpQual() + "\n" +
                "\tMinAvgSnpQual: " + getMinAvgSnpQual() + "\n" +
                "\tMinDipQual: " + getMinDipQual() + "\n" +
                "\tMinAvgDipQual: " + getMinAvgDipQual() + "\n"
                ;
    }
}
