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
import org.labkey.api.sequenceanalysis.ReferenceLibraryHelper;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.api.picard.CigarPositionIterable;
import org.labkey.sequenceanalysis.run.util.NTSnp;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 9/22/12
 * Time: 3:02 PM
 */
public class NtCoverageAggregator extends AbstractAlignmentAggregator
{
    private final Map<String, int[][]> _totalCoverage = new HashMap<>();
    private final Map<String, int[][]> _totalQual = new HashMap<>();
    private final Map<String, int[][]> _hcCoverage = new HashMap<>();
    private final Map<String, int[][]> _hcQual = new HashMap<>();
    private final Map<String, ReferenceSequence> _refSequences = new HashMap<>();
    private int _totalFilteredSnps = 0;
    private int _totalAlignments = 0;

    private final Map<String, int[][][]> _totalCoverageByBase = new HashMap<>();
    private final Map<String, int[][][]> _totalQualByBase = new HashMap<>();

    private final Set<String> _encounteredReferences = new HashSet<>();

    private final Map<Character, String> _baseMap = new HashMap<>()
    {
        {
            put('A', "a");
            put('T', "t");
            put('G', "g");
            put('C', "c");
            put('N', "n");
            put('-', "del");
        }
    };

    private final Map<Character, Integer> _baseIndexMap = new HashMap<>()
    {
        {
            put('A', 0);
            put('T', 1);
            put('G', 2);
            put('C', 3);
            put('N', 4);
            put('-', 5);
        }
    };

    public NtCoverageAggregator(Logger log, File refFasta, AvgBaseQualityAggregator avgQualAggregator, Map<String, String> settings)
    {
        super(log, refFasta, avgQualAggregator, settings);
    }

    @Override
    public void inspectAlignment(SAMRecord record, ReferenceSequence ref, Map<Integer, List<NTSnp>> snps) throws PipelineJobException
    {
        if (!isPassingAlignment(record, true))
        {
            return;
        }

        _totalAlignments++;

        initHashes(ref);

        CigarPositionIterable.CigarIterator ci = new CigarPositionIterable(record).iterator();
        while (ci.hasNext())
        {
            CigarPositionIterable.PositionInfo pi = ci.next();

            //We only want to include positions that would produce SNPs, which primarily means we skip soft-clipped sections
            if (!pi.includeInSnpCount())
                continue;

            if (snps.containsKey(pi.getRefPosition()))
            {
                for (NTSnp ntSnp : snps.get(pi.getRefPosition()))
                {
                    inspectSnp(record, ntSnp);
                }
            }
            else
            {
                char base = (char)pi.getReadBase();
                appendSnp(ref.getName(), pi.getLastRefPosition(), pi.getInsertIndex(), pi.getBaseQuality(), base, _totalCoverage, _totalQual, _totalCoverageByBase, _totalQualByBase);

                if (pi.getReadBase() != BamIterator.AMBIGUITY_CHARACTER)
                    appendSnp(ref.getName(), pi.getLastRefPosition(), pi.getInsertIndex(), pi.getBaseQuality(), base, _hcCoverage, _hcQual, null, null);
            }
        }
    }

    private void initHashes(ReferenceSequence ref)
    {
        if (_encounteredReferences.contains(ref.getName()))
            return;

        _encounteredReferences.add(ref.getName());

        int capacity = ref.length() + 1;
        final int INITIAL_SIZE = 4;
        final int BASE_INITIAL_SIZE = 6;
        _refSequences.put(ref.getName(), ref);
        _totalCoverage.put(ref.getName(), new int[capacity][INITIAL_SIZE]);
        _totalQual.put(ref.getName(), new int[capacity][INITIAL_SIZE]);
        _hcCoverage.put(ref.getName(), new int[capacity][INITIAL_SIZE]);
        _hcQual.put(ref.getName(), new int[capacity][INITIAL_SIZE]);

        _totalCoverageByBase.put(ref.getName(), new int[capacity][INITIAL_SIZE][BASE_INITIAL_SIZE]);
        _totalQualByBase.put(ref.getName(), new int[capacity][INITIAL_SIZE][BASE_INITIAL_SIZE]);
    }

    public int getDepthAtPosition(String ref, int position, int index)
    {
        if (!_totalCoverage.containsKey(ref))
            return 0;

        if (_totalCoverage.get(ref).length <= position)
            return 0;

        if (_totalCoverage.get(ref)[position].length <= index)
            return 0;

        return _totalCoverage.get(ref)[position][index];
    }

    public int getHcDepthAtPosition(String ref, int position, int index)
    {
        if (!_hcCoverage.containsKey(ref))
            return 0;

        if (_hcCoverage.get(ref).length <= position)
            return 0;

        if (_hcCoverage.get(ref)[position].length <= index)
            return 0;

        return _hcCoverage.get(ref)[position][index];
    }

    public int getDepthQualityAtPositionForBase(String ref, int position, int index, char base)
    {
        if (!_totalCoverageByBase.containsKey(ref))
            return 0;

        if (_totalCoverageByBase.get(ref).length <= position)
            return 0;

        if (_totalCoverageByBase.get(ref)[position].length <= index)
            return 0;

        if (_totalCoverageByBase.get(ref)[position][_baseIndexMap.get(base)].length <= index)
            return 0;

        return _totalCoverageByBase.get(ref)[position][index][_baseIndexMap.get(base)];
    }

    public int getTotalQualityAtPositionForBase(String ref, int position, int index, char base)
    {
        if (!_totalQualByBase.containsKey(ref))
            return 0;

        if (_totalQualByBase.get(ref).length <= position)
            return 0;

        if (_totalQualByBase.get(ref)[position].length <= index)
            return 0;

        if (_totalQualByBase.get(ref)[position][_baseIndexMap.get(base)].length <= index)
            return 0;

        return _totalQualByBase.get(ref)[position][index][_baseIndexMap.get(base)];
    }

    private void inspectSnp(SAMRecord record, NTSnp ntSnp) throws PipelineJobException
    {
        char base = (char)ntSnp.getReadBase();
        if (!isPassingSnp(record, ntSnp))
        {
            base = (char) BamIterator.AMBIGUITY_CHARACTER;
            _totalFilteredSnps++;
        }

        appendSnp(ntSnp.getReferenceName(), ntSnp.getLastRefPosition(), ntSnp.getInsertIndex(), ntSnp.getBaseQuality(), base, _totalCoverage, _totalQual, _totalCoverageByBase, _totalQualByBase);

        if ((char)BamIterator.AMBIGUITY_CHARACTER != base)
        {
            appendSnp(ntSnp.getReferenceName(), ntSnp.getLastRefPosition(), ntSnp.getInsertIndex(), ntSnp.getBaseQuality(), (char)ntSnp.getReadBase(), _hcCoverage, _hcQual, null, null);
        }
    }

    private int[] extendArray(int[] source, int newLength, int buffer)
    {
        newLength += buffer;
        int[] newArray = new int[newLength];
        System.arraycopy(source, 0, newArray, 0, source.length);

        return newArray;
    }

    private int[][] extendArray(int[][] source, int newLength, int buffer)
    {
        newLength += buffer;
        int[][] newArray = new int[newLength][6];
        System.arraycopy(source, 0, newArray, 0, source.length);

        return newArray;
    }

    private void appendSnp(String refName, int position, int index, int qual, char base, Map<String, int[][]> coverageMap, Map<String, int[][]> qualMap, Map<String, int[][][]> coverageByBase, Map<String, int[][][]> qualByBase)
    {
        final int DEFAULT_EXTENSION = 20;

        //first track total coverage
        int[] totals = coverageMap.get(refName)[position];
        if (totals.length <= index)
        {
            totals = extendArray(totals, index + 1, DEFAULT_EXTENSION);
            coverageMap.get(refName)[position] = totals;
        }
        totals[index]++;


        //also add quality score
        int[] qualTotals = qualMap.get(refName)[position];
        if (qualTotals.length <= index)
        {
            qualTotals = extendArray(qualTotals, index + 1, DEFAULT_EXTENSION);
            qualMap.get(refName)[position] = qualTotals;
        }
        qualTotals[index] += qual;


        int baseIndex = getBaseIndex(base);

        //also track coverage per base
        if (coverageByBase != null)
        {
            int[][] totalsByBase = coverageByBase.get(refName)[position];
            if (totalsByBase.length <= index)
            {
                totalsByBase = extendArray(totalsByBase, index + 1, DEFAULT_EXTENSION);
                coverageByBase.get(refName)[position] = totalsByBase;
            }
            totalsByBase[index][baseIndex]++;
        }

        //and quality
        if (qualByBase != null)
        {
            int[][] qualTotalByBase = qualByBase.get(refName)[position];
            if (qualTotalByBase.length <= index)
            {
                qualTotalByBase = extendArray(qualTotalByBase, index + 1, DEFAULT_EXTENSION);
                qualByBase.get(refName)[position] = qualTotalByBase;
            }
            qualTotalByBase[index][baseIndex] += qual;
        }
    }

    private int getBaseIndex(char base)
    {
        return _baseIndexMap.get(base);
    }

    private int getValueForPosition(String refName, int position, int index, Map<String, int[][]> map)
    {
        if (!map.containsKey(refName))
            return 0;

        int[][] arr = map.get(refName);
        if (arr[position].length <= index)
            return 0;

        return arr[position][index];
    }

    private int getValueForPositionAndBase(String refName, int position, int index, char base, Map<String, int[][][]> map)
    {
        if (!map.containsKey(refName))
            return 0;

        int[][][] arr = map.get(refName);
        if (arr[position].length <= index)
            return 0;

        int baseInx = getBaseIndex(base);

        if (arr[position][index].length <= baseInx)
        {
            //TOOD: error?
            return 0;
        }

        return arr[position][index][baseInx];
    }

    @Override
    public void writeOutput(User u, Container c, AnalysisModel model)
    {
        getLogger().info("Saving Coverage Results");
        Map<String, Integer> summary = new HashMap<>();

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            //delete existing records
            TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_COVERAGE);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("analysis_id"), model.getAnalysisId());
            long deleted = Table.delete(ti, filter);
            int processed = 0;

            ReferenceLibraryHelper libraryHelper = getReferenceLibraryHelper();

            //insert new
            for (String refName : _totalCoverage.keySet())
            {
                Integer refId = libraryHelper.resolveSequenceId(refName);

                //keep track of positions by reference
                Integer totalSaved = summary.get(refName);
                if (totalSaved == null)
                    totalSaved = 0;

                int[][] coverageForRef = _totalCoverage.get(refName);
                for (int position = 0; position < coverageForRef.length; position++)
                {
                    int[] coverageByIndex = coverageForRef[position];
                    for (int index = 0; index < coverageByIndex.length; index++)
                    {
                        int savedDepth = getValueForPosition(refName, position, index, _totalCoverage);
                        if (savedDepth == 0)
                            continue;

                        int savedHCDepth = getValueForPosition(refName, position, index, _hcCoverage);

                        Map<String, Object> row = new HashMap<>();
                        row.put("analysis_id", model.getAnalysisId());
                        row.put("ref_nt_id", refId);
                        row.put("ref_nt_position", position + 1); //convert to 1-based
                        row.put("ref_nt_insert_index", index);
                        row.put("depth", savedDepth);
                        row.put("adj_depth", savedHCDepth);

                        int total = 0;
                        int n_total = 0;
                        char wtBase = index == 0 ? (char)_refSequences.get(refName).getBases()[position] : 'x';

                        for (char base : _baseMap.keySet())
                        {
                            String fieldSuffix = _baseMap.get(base);
                            Integer baseTotal = getValueForPositionAndBase(refName, position, index, base, _totalCoverageByBase);

                            row.put("total_" + fieldSuffix, baseTotal);

                            total += baseTotal;
                            if ('N' == base)
                                n_total += baseTotal;

                            double totalQual = getValueForPositionAndBase(refName, position, index, base, _totalQualByBase);
                            double avgQual = baseTotal == 0 ? 0 : totalQual / baseTotal.doubleValue();
                            row.put("avgqual_" + fieldSuffix, avgQual);

                            if (index == 0 && base == wtBase)
                            {
                                row.put("wt", baseTotal);
                            }
                        }

                        if (savedHCDepth > 0)
                        {
                            int totalWithoutN = total - n_total;
                            if (totalWithoutN != savedHCDepth)
                            {
                                getLogger().error("High quality coverage total doesn't match at position " + position + ": " + totalWithoutN + " / " + savedHCDepth);
                            }
                            //assert totalWithoutN == savedHCDepth;
                        }

                        if (total != savedDepth)
                        {
                            getLogger().error("Coverage doesn't match " + position + ": " + total + " / " + savedDepth);
                        }
                        //assert total == savedDepth;

                        row.put("container", c.getEntityId());
                        row.put("createdby", u.getUserId());
                        row.put("modifiedby", u.getUserId());
                        row.put("created", new Date());
                        row.put("modified", new Date());
                        Table.insert(u, ti, row);
                        totalSaved++;
                    }

                    processed++;
                    if (_logProgress && processed % 10000 == 0)
                    {
                        getLogger().info("processed " + processed + " positions for DB insert in NTCoverageAggregator");
                    }
                }

                summary.put(refName, totalSaved);
            }

            transaction.commit();

            getLogger().info("\tReference sequences saved: " + summary.keySet().size());
            getLogger().info("\tTotal filtered SNPs: " + _totalFilteredSnps);
            getLogger().info("\tTotal alignments inspected: " + _totalAlignments);
            getLogger().info("\tPositions saved by reference (may include indels, so total could exceed reference length):");
            for (String refId : summary.keySet())
            {
                getLogger().info("\t" + refId + ": " + summary.get(refId));
            }
        }
    }

    @Override
    public String getSynopsis()
    {
        return "NT Coverage Aggregator:\n" +
                "\tMinMapQual: " + getMinMapQual() + "\n" +
                "\tMinSnpQual: " + getMinSnpQual() + "\n" +
                "\tMinAvgSnpQual: " + getMinAvgSnpQual() + "\n" +
                "\tMinDipQual: " + getMinDipQual() + "\n" +
                "\tMinAvgDipQual: " + getMinAvgDipQual() + "\n"
                ;
    }
}
