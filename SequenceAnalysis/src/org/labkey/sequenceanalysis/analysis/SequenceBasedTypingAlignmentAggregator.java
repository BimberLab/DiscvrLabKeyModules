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
package org.labkey.sequenceanalysis.analysis;

import net.sf.picard.reference.ReferenceSequence;
import net.sf.samtools.SAMRecord;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.model.AnalysisModel;
import org.labkey.sequenceanalysis.pipeline.SequencePipelineSettings;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/22/12
 * Time: 4:27 PM
 */
public class SequenceBasedTypingAlignmentAggregator extends AbstractAlignmentAggregator
{
    private Map<String, Integer> _acceptedAlignments = new HashMap<>();
    private Set<String> _uniqueReads = new HashSet<>();
    private Map<String, Set<String>> _alignmentsByReadM1 = new HashMap<>();
    private Map<String, Set<String>> _alignmentsByReadM2 = new HashMap<>();
//    private Map<String, Map<String, Boolean>> _orientationMapM1 = new HashMap<String, Map<String, Boolean>>();
//    private Map<String, Map<String, Boolean>> _orientationMapM2 = new HashMap<String, Map<String, Boolean>>();
    private Set<String> _unaligned = new HashSet<>();
    private int _totalAlignmentsInspected = 0;
    private int _maxSNPs = 0;

    private int _alignmentsHelpedByMate = 0;
    private int _pairsWithoutSharedHits = 0;
    private int _singletonCalls = 0;
    private int _pairedCalls = 0;
    private int _rejectedSingletons = 0;

    private boolean _onlyImportValidPairs = false;
    private Double _minPctToImport = null;

    public SequenceBasedTypingAlignmentAggregator(SequencePipelineSettings settings, Logger log, AvgBaseQualityAggregator avgBaseQualityAggregator)
    {
        super(settings, log, avgBaseQualityAggregator);

        _onlyImportValidPairs = settings.getSBTonlyImportPairs();
        if (settings.getSBTminPctToImport() != null)
            _minPctToImport = settings.getSBTminPctToImport();
    }

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
        Map<String, Map<String, Integer>> totals = new HashMap<>();

        //handle single or first-mate reads first
        for (String readName : _alignmentsByReadM1.keySet())
        {
            List<String> refNames = new ArrayList(_alignmentsByReadM1.get(readName));
            Collections.sort(refNames);

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
                String refs = StringUtils.join(refNames, "||");
                if (!_onlyImportValidPairs || hasMate)
                {
                    appendReadToTotals(readName, refs, totals, true, hasMate);

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
            Collections.sort(refNames);
            String refs = StringUtils.join(refNames, "||");

            if (!_onlyImportValidPairs)
            {
                appendReadToTotals(mateName, refs, totals, false, true);
                _singletonCalls++;
            }
            else
            {
                _rejectedSingletons++;
            }
        }

        return totals;
    }

    private void appendReadToTotals(String readName, String refs, Map<String, Map<String, Integer>> totals, boolean hasForward, boolean hasReverse)
    {
//        Map<String, Boolean> orientations = _orientationMapM1.get(readName);
//        Set<Boolean> unique = new HashSet<Boolean>();
//        if (orientations != null)
//            unique.addAll(orientations.values());

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

    public void saveToDb(User u, Container c, AnalysisModel model)
    {
        Map<String, Map<String, Integer>> map = getAlignmentSummary();

        _log.info("Saving SBT Results to DB");
        _log.info("\tTotal alignments inspected: " + _totalAlignmentsInspected);
        _log.info("\tTotal reads inspected: " + _uniqueReads.size());

        _log.info("\tAlignments retained (lacking high quality SNPs): " + _acceptedAlignments.size());
        _log.info("\tAlignments discarded (due to presence of high quality SNPs): " + (_totalAlignmentsInspected - _acceptedAlignments.size()));
        _log.info("\tReads with no alignments: " + _unaligned.size());
        _log.info("\tSingleton or First Mate Reads with at least 1 alignment that passed thresholds: " + _alignmentsByReadM1.keySet().size());
        _log.info("\tSecond Mate Reads with at least 1 alignment that passed thresholds: " + _alignmentsByReadM2.keySet().size());

        _log.info("\tAlignment calls improved by paired read: " + _alignmentsHelpedByMate);
        _log.info("\tPaired reads without common alignments: " + _pairsWithoutSharedHits);
        _log.info("\tAlignment calls using paired reads: " + _pairedCalls);
        _log.info("\tAlignment calls using only 1 read: " + _singletonCalls);

        if (_onlyImportValidPairs)
        {
            _log.info("\tOnly alignments representing valid pairs will be included");
            _log.info("\tAlignments rejected because they lacked a valid pair: " + _rejectedSingletons);
        }

        Set<String> allReadNames = new HashSet<>();
        allReadNames.addAll(_alignmentsByReadM1.keySet());
        allReadNames.addAll(_alignmentsByReadM2.keySet());
        int noHits = _uniqueReads.size() - allReadNames.size();
        _log.info("\tReads discarded due to no passing alignments: " + noHits);

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
                    String[] tokens = refName.split("\\|");
                    Integer refId = Integer.parseInt(tokens[0]);

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
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
}
