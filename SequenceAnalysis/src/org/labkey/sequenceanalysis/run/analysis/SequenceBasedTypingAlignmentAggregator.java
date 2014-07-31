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
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.api.picard.CigarPositionIterable;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.run.util.NTSnp;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 9/22/12
 * Time: 4:27 PM
 */
public class SequenceBasedTypingAlignmentAggregator extends AbstractAlignmentAggregator
{
    private Map<String, Integer> _totalByReference = new HashMap<>();
    private Map<String, Integer> _acceptedAlignments = new HashMap<>();
    private Set<String> _uniqueReads = new HashSet<>();
    private Map<String, Set<String>> _alignmentsByReadM1 = new HashMap<>();
    private Map<String, Set<String>> _alignmentsByReadM2 = new HashMap<>();

    private Set<String> _unaligned = new HashSet<>();
    private int _totalAlignmentsInspected = 0;
    private int _totalAlignmentsIncluded = 0;
    private int _maxSNPs = 0;
    private int _skippedReferencesByPct = 0;
    private int _skippedReferencesByRead = 0;

    private int _alignmentsIncludingDiscardedSnps = 0;
    private int _alignmentsHelpedByMate = 0;
    private int _alignmentsHelpedByAlleleFilters = 0;

    private int _pairsWithoutSharedHits = 0;
    private int _singletonCalls = 0;
    private int _pairedCalls = 0;
    private int _rejectedSingletons = 0;

    private boolean _onlyImportValidPairs = false;
    private Double _minCountForRef = null;
    private Double _minPctForRef = null;

    public SequenceBasedTypingAlignmentAggregator(Logger log, File refFasta, AvgBaseQualityAggregator avgBaseQualityAggregator, Map<String, String> settings)
    {
        super(log, refFasta, avgBaseQualityAggregator, settings);

        if (settings.get("minCountForRef") != null)
            _minCountForRef = Double.parseDouble(settings.get("minCountForRef"));

        if (settings.get("minPctForRef") != null)
            _minPctForRef = Double.parseDouble(settings.get("minPctForRef"));
    }

    @Override
    public void inspectAlignment(SAMRecord record, ReferenceSequence ref, Map<Integer, List<NTSnp>> snps, CigarPositionIterable cpi)
    {
        _uniqueReads.add(record.getReadName());

        if(record.getReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME))
        {
            _unaligned.add(record.getReadName());
        }
        else
        {
            assert cpi != null;

            _totalAlignmentsInspected++;

            Integer numSnps = getNumMismatches(record, snps);
            String key = getKey(record);
            if (numSnps <= _maxSNPs)
            {
                Integer total = _acceptedAlignments.get(key);
                if (total == null)
                    total = 0;

                total++;
                _acceptedAlignments.put(key, total);

                if (numSnps != snps.size())
                    _alignmentsIncludingDiscardedSnps++;

                if (!record.getReadPairedFlag() || record.getFirstOfPairFlag())
                {
                    appendAlignment(record, ref, _alignmentsByReadM1);
                }
                else
                {
                    appendAlignment(record, ref, _alignmentsByReadM2);
                }
            }
        }
    }

    private void appendAlignment(SAMRecord record, ReferenceSequence ref, Map<String, Set<String>> alignmentsByRead)
    {
        Set<String> alignments = alignmentsByRead.get(record.getReadName());
        if (alignments == null)
            alignments = new HashSet<>();

        alignments.add(record.getReferenceName());

        alignmentsByRead.put(record.getReadName(), alignments);

        //retain a tracking of alignments by ref
        Integer total = _totalByReference.get(record.getReferenceName());
        if (total == null)
            total = 0;

        total++;
        _totalAlignmentsIncluded++;
        _totalByReference.put(record.getReferenceName(), total);
    }

    private int getNumMismatches(SAMRecord record, Map<Integer, List<NTSnp>> snps)
    {
        int highQuality = 0;
        for (Integer pos : snps.keySet())
        {
            for (NTSnp snp : snps.get(pos))
            {
                if (evaluateSnp(record, snp))
                    highQuality++;
            }
        }

        return highQuality;
    }

    public String getKey(SAMRecord record)
    {
        return record.getReadName() + "||" +
                record.getReferenceName() + "||" +
                record.getReadNegativeStrandFlag()
                ;
    }

    private static final String TOTAL = "total";
    private static final String FORWARD = "forward";
    private static final String REVERSE = "reverse";
    private static final String VALID_PAIRS = "valid_pair";

    public Map<String, Map<String, Integer>> getAlignmentSummary()
    {
        //optionally filter by total read #
        Set<String> disallowedReferences = new HashSet<>();
        if (_minCountForRef != null || _minPctForRef != null)
        {
            for (String refName : _totalByReference.keySet())
            {
                //NOTE: the user is entering this as a number 0-100
                double pct = ((double)_totalByReference.get(refName) / _totalAlignmentsIncluded) * 100.0;

                if (_minCountForRef != null && _totalByReference.get(refName) < _minCountForRef)
                {
                    _skippedReferencesByRead++;
                    getLogger().debug("Reference discarded due to read count: " + refName + " / " + _totalAlignmentsIncluded + " / " + _totalByReference.get(refName) + " / " + pct + "%");
                    disallowedReferences.add(refName);
                    continue;
                }

                if (_minPctForRef != null && pct < _minPctForRef)
                {
                    _skippedReferencesByPct++;
                    getLogger().debug("Reference discarded due to percent: " + refName + " / " + _totalAlignmentsIncluded + " / " + _totalByReference.get(refName) + " / " + pct + "%");
                    disallowedReferences.add(refName);
                    continue;
                }
            }
        }

        Map<String, Map<String, Integer>> totals = new HashMap<>();

        //handle single or first-mate reads first
        for (String readName : _alignmentsByReadM1.keySet())
        {
            List<String> refNames = new ArrayList(_alignmentsByReadM1.get(readName));
            if (disallowedReferences.size() > 0)
            {
                int originalSize = refNames.size();
                refNames.removeAll(disallowedReferences);
                if (refNames.size() != originalSize)
                {
                    _alignmentsHelpedByAlleleFilters++;
                }
            }

            //if this read has an aligned mate, we find the intersect between its alignments
            boolean hasMate = false;
            if (_alignmentsByReadM2.containsKey(readName))
            {
                List<String> refNames2 = new ArrayList(_alignmentsByReadM2.get(readName));
                int originalSize = refNames.size();
                refNames.retainAll(refNames2);
                if (refNames.size() > 0)
                {
                    if (refNames.size() != originalSize){
                        _alignmentsHelpedByMate++;
                    }
                    hasMate = true;
                }
                else
                    _pairsWithoutSharedHits++;
            }

            if (refNames.size() > 0)
            {
                if (!_onlyImportValidPairs || hasMate)
                {
                    appendReadToTotals(readName, refNames, totals, true, hasMate);

                    if (hasMate)
                        _pairedCalls++;
                    else
                        _singletonCalls++;
                }
                else
                {
                    _rejectedSingletons++;
                }
            }
            else
            {
                _unaligned.add(readName);
            }
        }

        for (String mateName : _alignmentsByReadM2.keySet())
        {
            if (_alignmentsByReadM1.containsKey(mateName))
                continue;

            List<String> refNames = new ArrayList(_alignmentsByReadM2.get(mateName));
            if (!_onlyImportValidPairs)
            {
                if (disallowedReferences.size() > 0)
                {
                    int originalSize = refNames.size();
                    refNames.removeAll(disallowedReferences);
                    if (refNames.size() != originalSize)
                    {
                        _alignmentsHelpedByAlleleFilters++;
                    }
                }

                if (refNames.size() > 0)
                {
                    appendReadToTotals(mateName, refNames, totals, false, true);
                    _singletonCalls++;
                }
                else
                {
                    _unaligned.add(mateName);
                }
            }
            else
            {
                _rejectedSingletons++;
            }
        }

        return totals;
    }

    private void appendReadToTotals(String readName, List<String> refNames, Map<String, Map<String, Integer>> totals, boolean hasForward, boolean hasReverse)
    {
        String refs = StringUtils.join(refNames, "||");
        Collections.sort(refNames);

        Map<String, Integer> counts = totals.get(refs);
        if (counts == null)
        {
            counts = new HashMap<>();
            counts.put(TOTAL, 0);
            counts.put(FORWARD, 0);
            counts.put(REVERSE, 0);
            counts.put(VALID_PAIRS, 0);
        }

        Integer total = counts.get(TOTAL);
        total++;
        counts.put(TOTAL, total);

        if (hasForward)
        {
            total = counts.get(FORWARD);
            total++;
            counts.put(FORWARD, total);
        }

        if (hasReverse)
        {
            total = counts.get(REVERSE);
            total++;
            counts.put(REVERSE, total);
        }

        if (hasForward && hasReverse)
        {
            total = counts.get(VALID_PAIRS);
            total++;
            counts.put(VALID_PAIRS, total);
        }

        totals.put(refs, counts);
    }

    @Override
    public void saveToDb(User u, Container c, AnalysisModel model)
    {
        Map<String, Map<String, Integer>> map = getAlignmentSummary();

        getLogger().info("Saving SBT Results to DB");
        getLogger().info("\tTotal alignments inspected: " + _totalAlignmentsInspected);
        getLogger().info("\tTotal reads inspected: " + _uniqueReads.size());

        getLogger().info("\tAlignments retained (lacking high quality SNPs): " + _acceptedAlignments.size());
        getLogger().info("\tAlignments discarded (due to presence of high quality SNPs): " + (_totalAlignmentsInspected - _acceptedAlignments.size()));
        getLogger().info("\tAlignments retained that contained low qual SNPs (thse may have been discarded for other factors): " + _alignmentsIncludingDiscardedSnps);
        getLogger().info("\tReferences with at least 1 aligned read (these may get filtered out downstream): " + _totalByReference.size());
        getLogger().info("\tReferences disallowed due to read count filters: " + _skippedReferencesByRead);
        getLogger().info("\tReferences disallowed due to percent filters: " + _skippedReferencesByPct);

        getLogger().info("\tReads with no alignments: " + _unaligned.size());
        getLogger().info("\tSingleton or First Mate Reads with at least 1 alignment that passed thresholds: " + _alignmentsByReadM1.keySet().size());
        getLogger().info("\tSecond Mate Reads with at least 1 alignment that passed thresholds: " + _alignmentsByReadM2.keySet().size());

        getLogger().info("\tAlignment calls improved by paired read: " + _alignmentsHelpedByMate);
        getLogger().info("\tAlignment calls improved by allele filters (see references disallowed): " + _alignmentsHelpedByAlleleFilters);
        getLogger().info("\tPaired reads without common alignments: " + _pairsWithoutSharedHits);
        getLogger().info("\tAlignment calls using paired reads: " + _pairedCalls);
        getLogger().info("\tAlignment calls using only 1 read: " + _singletonCalls);

        if (_onlyImportValidPairs)
        {
            getLogger().info("\tOnly alignments representing valid pairs will be included");
            getLogger().info("\tAlignments rejected because they lacked a valid pair: " + _rejectedSingletons);
        }

        Set<String> allReadNames = new HashSet<>();
        allReadNames.addAll(_alignmentsByReadM1.keySet());
        allReadNames.addAll(_alignmentsByReadM2.keySet());
        int noHits = _uniqueReads.size() - allReadNames.size();
        getLogger().info("\tReads discarded due to no passing alignments: " + noHits);

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            //delete existing records
            TableInfo ti_summary = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("analysis_id"), model.getAnalysisId());
            Table.delete(ti_summary, filter);

            TableInfo ti_junction = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION);
            Table.delete(ti_junction, filter);

            //insert new
            for (String key : map.keySet())
            {
                Map<String, Object> row = new HashMap<>();
                row.put("analysis_id", model.getAnalysisId());
                row.put("file_id", model.getAlignmentFile());

                Map<String, Integer> totals = map.get(key);
                row.put("total", totals.get(TOTAL));
                row.put("total_forward", totals.get(FORWARD));
                row.put("total_reverse", totals.get(REVERSE));
                row.put("valid_pairs", totals.get(VALID_PAIRS));

                row.put("container", c.getEntityId());
                row.put("createdby", u.getUserId());
                row.put("modifiedby", u.getUserId());
                row.put("created", new Date());
                row.put("modified", new Date());
                Map<String, Object> newRow = Table.insert(u, ti_summary, row);

                String[] names = key.split("\\|\\|");
                for (String refName: names)
                {
                    Integer refId = getReferenceLibraryHelper().resolveSequenceId(refName);

                    Map<String, Object> junction_row = new HashMap<>();
                    junction_row.put("analysis_id", model.getAnalysisId());
                    junction_row.put("ref_nt_id", refId);
                    junction_row.put("alignment_id", newRow.get("rowid"));
                    junction_row.put("status", true);
                    Table.insert(u, ti_junction, junction_row);
                }
            }

            //append unaligned
            Map<String, Object> row = new HashMap<>();
            row.put("analysis_id", model.getAnalysisId());
            row.put("file_id", model.getAlignmentFile());
            row.put("total", _unaligned.size());

            transaction.commit();
        }
    }

    @Override
    public String getSynopsis()
    {
        return "Sequence Based Typing Aggregator:\n" +
        "\tMaxSnpsTolerated: " + _maxSNPs + "\n" +
        "\tOnlyImportValidPairs: " + _onlyImportValidPairs + "\n" +
        "\tMinSnpQual: " + getMinSnpQual() + "\n" +
        "\tMinAvgSnpQual: " + getMinAvgSnpQual() + "\n" +
        "\tMinDipQual: " + getMinDipQual() + "\n" +
        "\tMinAvgDipQual: " + getMinAvgDipQual() + "\n" +
        "\tMinCountForRef: " + _minCountForRef + "\n" +
        "\tMinPctForRef: " + _minPctForRef + "\n"
        ;
    }
}
