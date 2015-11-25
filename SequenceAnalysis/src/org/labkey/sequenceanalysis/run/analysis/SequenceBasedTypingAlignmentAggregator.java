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

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.fastq.FastqWriterFactory;
import htsjdk.samtools.reference.ReferenceSequence;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
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
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.api.picard.CigarPositionIterable;
import org.labkey.sequenceanalysis.run.alignment.FastqCollapser;
import org.labkey.sequenceanalysis.run.util.FlashWrapper;
import org.labkey.sequenceanalysis.run.util.NTSnp;
import org.labkey.sequenceanalysis.util.ReferenceLibraryHelperImpl;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

/**
 * User: bimber
 * Date: 9/22/12
 * Time: 4:27 PM
 */
public class SequenceBasedTypingAlignmentAggregator extends AbstractAlignmentAggregator
{
    private File _outputLog = null;

    private Set<String> _distinctReferences = new HashSet<>();
    private Map<String, Integer> _acceptedAlignments = new HashMap<>();
    private Set<String> _uniqueReads = new HashSet<>();
    private Map<String, Set<String>> _alignmentsByReadM1 = new HashMap<>();
    private Map<String, Set<String>> _alignmentsByReadM2 = new HashMap<>();

    private Set<String> _unaligned = new HashSet<>();
    //private Set<String> _mappedWithoutHits = new HashSet<>();
    private int _totalAlignmentsInspected = 0;
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
    private int _shortAlignments = 0;

    private boolean _onlyImportValidPairs = false;
    private Double _minCountForRef = null;
    private Double _minPctForRef = null;
    private Double _minPctWithinGroup = null;
    private int _minAlignmentLength = 0;

    public SequenceBasedTypingAlignmentAggregator(Logger log, File refFasta, AvgBaseQualityAggregator avgBaseQualityAggregator, Map<String, String> settings)
    {
        super(log, refFasta, avgBaseQualityAggregator, settings);

        if (settings.get("minCountForRef") != null)
            _minCountForRef = Double.parseDouble(settings.get("minCountForRef"));

        if (settings.get("minPctForRef") != null)
            _minPctForRef = Double.parseDouble(settings.get("minPctForRef"));

        if (settings.get("minPctWithinGroup") != null)
            _minPctWithinGroup = Double.parseDouble(settings.get("minPctWithinGroup"));

        if (settings.get("onlyImportValidPairs") != null)
            _onlyImportValidPairs = Boolean.parseBoolean(settings.get("onlyImportValidPairs"));

        if (settings.get("minAlignmentLength") != null)
            _minAlignmentLength = Integer.parseInt(settings.get("minAlignmentLength"));
    }

    public File getOutputLog()
    {
        return _outputLog;
    }

    public void setOutputLog(File outputLog)
    {
        _outputLog = outputLog;
    }

    @Override
    public void inspectAlignment(SAMRecord record, ReferenceSequence ref, Map<Integer, List<NTSnp>> snps, CigarPositionIterable cpi)
    {
        _uniqueReads.add(record.getReadName());

        if (record.getReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME) || record.getReadUnmappedFlag())
        {
            if (!record.getReadPairedFlag() || record.getMateUnmappedFlag())
            {
                _unaligned.add(record.getReadName());
            }
        }
        else
        {
            assert cpi != null;

            _totalAlignmentsInspected++;

            if (record.getCigar().getReferenceLength() < _minAlignmentLength)
            {
                _shortAlignments++;
                return;
            }

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

        _distinctReferences.add(record.getReferenceName());
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

    public OutputStream getLogOutputStream(File outputLog) throws IOException
    {
        FileType gz = new FileType(".gz");
        if (gz.isType(outputLog))
        {
            return new GZIPOutputStream(new FileOutputStream(outputLog));
        }
        else
        {
            return new FileOutputStream(outputLog);
        }
    }

    public Map<String, HitSet> getAlignmentSummary(File outputLog) throws IOException
    {
        try (CSVWriter writer = outputLog == null ? null : new CSVWriter(new BufferedWriter(new OutputStreamWriter(getLogOutputStream(outputLog), "UTF-8")), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            //these are stage-1 filters, filtering on the read-pair level
            Map<String, HitSet> totals = doFilterStage1(writer);

            //stage 2 filters, filtering across the set of alignments
            Map<String, HitSet> totals2 = doFilterStage2(writer, totals);

            //stage 3 filters: filtering within each set
            Map<String, HitSet> totals3 = doFilterStage3(writer, totals2);

            Map<String, Integer> totalByReferenceStage3 = new HashMap<>();
            int distinctStageThreeReads = 0;
            for (HitSet hs : totals3.values())
            {
                for (String refName : hs.refNames)
                {
                    int total = totalByReferenceStage3.containsKey(refName) ? totalByReferenceStage3.get(refName) : 0;
                    total += hs.readNames.size();
                    totalByReferenceStage3.put(refName, total);
                }

                if (!hs.refNames.isEmpty())
                {
                    distinctStageThreeReads += hs.readNames.size();
                }
            }

            getLogger().info("after filters:");
            getLogger().info("\tpassing references: " + totalByReferenceStage3.size());
            getLogger().info("\ttotal passing reads: " + distinctStageThreeReads);
            getLogger().info("\ttotal allele groups: " + totals3.size());
            getLogger().info("\ttotal unaligned reads: " + _unaligned.size());

            return totals3;
        }
    }

    private Map<String, HitSet> doFilterStage1(CSVWriter writer)
    {
        Map<String, HitSet> totals = new HashMap<>();
        if (writer != null)
        {
            writer.writeNext(new String[]{""});
            writer.writeNext(new String[]{"*****Summary By Read*****"});
            writer.writeNext(new String[]{"Orientation", "ReadName", "InitialRefs", "PassingRefs", "RefName", "PassedFilters", "ReadsForReference", "Has Aligned Mate?"});
        }

        getLogger().info("starting stage 1 filters (by read pair)");
        getLogger().info("\tinitial references: " + _distinctReferences.size());
        getLogger().info("\tinitial reads: " + _uniqueReads.size());
        getLogger().info("\tinitial unaligned reads: " + _unaligned.size());

        //handle single or first-mate reads first
        for (String readName : _alignmentsByReadM1.keySet())
        {
            List<String> refNames = new ArrayList(_alignmentsByReadM1.get(readName));

            //if this read has an aligned mate, we find the intersect between its alignments
            Boolean hasMate = false;
            if (_alignmentsByReadM2.containsKey(readName))
            {
                List<String> refNames2 = new ArrayList<>(_alignmentsByReadM2.get(readName));

                // note: if reverse read has no alignments, skip this optimization
                // it will only pass if we have onlyImportValidPairs=false
                if (!refNames2.isEmpty())
                {
                    int originalSize = refNames.size();
                    refNames.retainAll(refNames2);
                    if (refNames.size() > 0)
                    {
                        if (refNames.size() != originalSize)
                        {
                            _alignmentsHelpedByMate++;
                        }
                        hasMate = true;
                    }
                    else
                        _pairsWithoutSharedHits++;
                }
            }

            if (writer != null)
            {
                List<String> names = new ArrayList<>(_alignmentsByReadM1.get(readName));
                Collections.sort(names);

                for (String refName : names)
                {
                    writer.writeNext(new String[]{"Forward", readName, String.valueOf(_alignmentsByReadM1.get(readName).size()), String.valueOf(refNames.size()), refName, String.valueOf(refNames.contains(refName)), hasMate.toString()});
                }
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
                    _unaligned.add(readName);
                    //_mappedWithoutHits.add(readName);
                }
            }
            else
            {
                _unaligned.add(readName);
                //_mappedWithoutHits.add(readName);
            }
        }

        for (String mateName : _alignmentsByReadM2.keySet())
        {
            if (_alignmentsByReadM1.containsKey(mateName))
                continue;

            List<String> refNames = new ArrayList(_alignmentsByReadM2.get(mateName));
            if (!_onlyImportValidPairs)
            {
                if (refNames.size() > 0)
                {
                    appendReadToTotals(mateName, refNames, totals, false, true);
                    _singletonCalls++;
                }
                else
                {
                    _unaligned.add(mateName);
                    //_mappedWithoutHits.add(mateName);
                }
            }
            else
            {
                _rejectedSingletons++;
                _unaligned.add(mateName);
                //_mappedWithoutHits.add(mateName);
            }

            if (writer != null)
            {
                List<String> names = new ArrayList<>(_alignmentsByReadM2.get(mateName));
                Collections.sort(names);
                for (String refName : names)
                {
                    writer.writeNext(new String[]{"Reverse", mateName, String.valueOf(_alignmentsByReadM2.get(mateName).size()), String.valueOf(refNames.size()), refName, String.valueOf(refNames.contains(refName))});
                }
            }
        }

        getLogger().info("\talignments helped using paired read: " + _alignmentsHelpedByMate);
        getLogger().info("\trejected singleton reads: " + _rejectedSingletons);

        return totals;
    }
    
    private Map<String, HitSet> doFilterStage2(CSVWriter writer, Map<String, HitSet> stage1Totals)
    {
        //build map of totals by ref, only counting each read pair once
        Map<String, Integer> totalByReferenceStage2 = new HashMap<>();
        int distinctStageTwoReads = 0;
        for (HitSet hs : stage1Totals.values())
        {
            for (String refName : hs.refNames)
            {
                int total = totalByReferenceStage2.containsKey(refName) ? totalByReferenceStage2.get(refName) : 0;
                total += hs.readNames.size();
                totalByReferenceStage2.put(refName, total);
            }

            if (!hs.refNames.isEmpty())
            {
                distinctStageTwoReads += hs.readNames.size();
            }
        }
        
        getLogger().info("starting stage 2 filters:");
        getLogger().info("\tinitial references: " + totalByReferenceStage2.size());
        getLogger().info("\tinitial distinct reads: " + distinctStageTwoReads);
        getLogger().info("\tinitial allele groups: " + stage1Totals.size());
        getLogger().info("\tinitial unaligned reads: " + _unaligned.size());

        //optionally filter by total read #
        Set<String> disallowedReferences = new HashSet<>();
        if (writer != null)
        {
            writer.writeNext(new String[]{"*****Summary By Reference*****"});
            writer.writeNext(new String[]{"RefName", "PassingReadsForRef", "TotalReads", "PctOfTotal"});
        }

        List<String> sortedReferences = new ArrayList<>(totalByReferenceStage2.keySet());
        Collections.sort(sortedReferences);
        for (String refName : sortedReferences)
        {
            int totalForRef = totalByReferenceStage2.get(refName);

            //NOTE: the user is entering this as a number 0-100
            double pct = ((double) totalByReferenceStage2.get(refName) / distinctStageTwoReads) * 100.0;
            String msg = "";
            if (_minCountForRef != null && totalForRef < _minCountForRef)
            {
                _skippedReferencesByRead++;
                getLogger().debug("Reference discarded due to read count: " + refName + " / " + distinctStageTwoReads + " / " + totalForRef + " / " + pct + "%");
                msg = "**skipped due to read count";
                disallowedReferences.add(refName);
            }
            else if (_minPctForRef != null && pct < _minPctForRef)
            {
                _skippedReferencesByPct++;
                getLogger().debug("Reference discarded due to percent: " + refName + " / " + distinctStageTwoReads + " / " + totalForRef + " / " + pct + "%");
                msg = "**skipped due to percent";
                disallowedReferences.add(refName);
            }

            if (writer != null)
            {
                writer.writeNext(new String[]{refName, String.valueOf(totalForRef), String.valueOf(distinctStageTwoReads), String.valueOf(pct), msg});
            }
        }

        //then actually use these for filtering
        Map<String, HitSet> totals2 = new HashMap<>();
        for (HitSet hs : stage1Totals.values())
        {
            List<String> refNames = new ArrayList<>(hs.refNames);
            refNames.removeAll(disallowedReferences);
            if (refNames.isEmpty())
            {
                _unaligned.addAll(hs.readNames);
            }
            else
            {
                if (refNames.size() != hs.refNames.size())
                {
                    _alignmentsHelpedByAlleleFilters++;
                }

                //merge sets
                Collections.sort(refNames);
                String newKey = StringUtils.join(refNames, "||");
                HitSet hs2 = totals2.containsKey(newKey) ? totals2.get(newKey) : new HitSet(refNames);
                hs2.append(hs);

                totals2.put(newKey, hs2);
            }
        }
        
        return totals2;
    }

    private Map<String, HitSet> doFilterStage3(CSVWriter writer, Map<String, HitSet> stage2Totals)
    {
        Map<String, Integer> totalByReferenceStage3 = new HashMap<>();
        int distinctStageThreeReads = 0;
        for (HitSet hs : stage2Totals.values())
        {
            for (String refName : hs.refNames)
            {
                int total = totalByReferenceStage3.containsKey(refName) ? totalByReferenceStage3.get(refName) : 0;
                total += hs.readNames.size();
                totalByReferenceStage3.put(refName, total);
            }

            if (!hs.refNames.isEmpty())
            {
                distinctStageThreeReads += hs.readNames.size();
            }
        }

        getLogger().info("starting stage 3 filters:");
        getLogger().info("\tinitial references: " + totalByReferenceStage3.size());
        getLogger().info("\tinitial distinct reads: " + distinctStageThreeReads);
        getLogger().info("\tinitial allele groups: " + stage2Totals.size());
        getLogger().info("\tinitial unaligned reads: " + _unaligned.size());
        
        Map<String, HitSet> totals3 = new HashMap<>();
        List<String> sortedRefs = new ArrayList<>(stage2Totals.keySet());
        Collections.sort(sortedRefs);

        if (writer != null)
        {
            writer.writeNext(new String[]{"*****Summary By Hit Set*****"});
            writer.writeNext(new String[]{"Alleles", "RefName", "TotalReadsInGroup", "TotalReadsForRef", "RefPctOfTotal", "PctWithinGroup"});
        }

        int totalFiltered = 0;
        for (String refGroup : sortedRefs)
        {
            HitSet hs = stage2Totals.get(refGroup);

            int maxForSet = 0;
            for (String refName : hs.refNames)
            {
                if (totalByReferenceStage3.get(refName) > maxForSet)
                {
                    maxForSet = totalByReferenceStage3.get(refName);
                }
            }

            int idx = 0;
            List<String> passingRefs = new ArrayList<>();
            for (String refName : hs.refNames)
            {
                Double pct = 100.0 * ((double) totalByReferenceStage3.get(refName) / maxForSet);
                String msg = "";
                if (_minPctWithinGroup != null && pct < _minPctWithinGroup)
                {
                    msg = "**discarded due to group pct filter";
                    _unaligned.addAll(hs.readNames);
                    //_mappedWithoutHits.addAll(hs.readNames);
                    totalFiltered++;
                }
                else
                {
                    passingRefs.add(refName);
                }

                if (writer != null)
                {
                    writer.writeNext(new String[]{
                            idx == 0 ? refGroup : "",
                            refName,
                            String.valueOf(hs.readNames.size()),
                            String.valueOf(totalByReferenceStage3.get(refName)),
                            String.valueOf(100.0 * ((double) totalByReferenceStage3.get(refName) / distinctStageThreeReads)),
                            String.valueOf(pct),
                            msg
                    });
                }

                idx++;
            }

            //merge sets
            if (passingRefs.isEmpty())
            {
                _unaligned.addAll(hs.readNames);
                //_mappedWithoutHits.addAll(hs.readNames);
            }
            else
            {
                Collections.sort(passingRefs);
                String newKey = StringUtils.join(passingRefs, "||");
                HitSet hs2 = totals3.containsKey(newKey) ? totals3.get(newKey) : new HitSet(passingRefs);
                hs2.append(hs);

                totals3.put(newKey, hs2);
            }
        }

        getLogger().info("\ttotal alleles filtered: " + totalFiltered);

        return totals3;
    }

    private class HitSet
    {
        public Set<String> readNames = new HashSet<>();
        public Set<String> refNames = new TreeSet<>();

        public HitSet(Collection<String> refNames)
        {
            this.refNames.addAll(refNames);
        }

        public int forward = 0;
        public int reverse = 0;
        public int valid_pair = 0;

        public void append(HitSet other)
        {
            forward += other.forward;
            reverse += other.reverse;
            valid_pair += other.valid_pair;
            readNames.addAll(other.readNames);
        }

        public String getKey()
        {
            List<String> ret = new ArrayList<>(refNames);
            Collections.sort(ret);

            return StringUtils.join(ret, "||");
        }
    }

    private void appendReadToTotals(String readName, List<String> refNames, Map<String, HitSet> totals, boolean hasForward, boolean hasReverse)
    {
        Collections.sort(refNames);
        String refs = StringUtils.join(refNames, "||");

        HitSet hs = totals.get(refs);
        if (hs == null)
        {
            hs = new HitSet(refNames);
        }

        if (hasForward)
        {
            hs.forward = hs.forward + 1;
        }

        if (hasReverse)
        {
            hs.reverse = hs.reverse + 1;
        }

        if (hasForward && hasReverse)
        {
            hs.valid_pair = hs.valid_pair + 1;
        }

        hs.readNames.add(readName);

        totals.put(refs, hs);
    }

    public Map<String, HitSet> writeSummary() throws IOException
    {
        Map<String, HitSet> map = getAlignmentSummary(_outputLog);

        getLogger().info("Saving SBT Results");
        getLogger().info("\tTotal alignments inspected: " + _totalAlignmentsInspected);
        getLogger().info("\tTotal reads inspected: " + _uniqueReads.size());

        getLogger().info("\tAlignments discarded due to short length: " + _shortAlignments);
        getLogger().info("\tAlignments retained (lacking high quality SNPs): " + _acceptedAlignments.size());
        getLogger().info("\tAlignments discarded (due to presence of high quality SNPs): " + (_totalAlignmentsInspected - _acceptedAlignments.size()));
        getLogger().info("\tAlignments retained that contained low qual SNPs (thse may have been discarded for other factors): " + _alignmentsIncludingDiscardedSnps);
        getLogger().info("\tReferences with at least 1 aligned read (these may get filtered out downstream): " + _distinctReferences.size());
        getLogger().info("\tReferences disallowed due to read count filters: " + _skippedReferencesByRead);
        getLogger().info("\tReferences disallowed due to percent filters: " + _skippedReferencesByPct);

        getLogger().info("\tReads with no alignments: " + _unaligned.size());
        //getLogger().info("\tMapped reads without passing hits: " + _mappedWithoutHits.size());
        getLogger().info("\tSingleton or First Mate Reads with at least 1 alignment that passed thresholds: " + _alignmentsByReadM1.keySet().size());
        getLogger().info("\tSecond Mate Reads with at least 1 alignment that passed thresholds: " + _alignmentsByReadM2.keySet().size());

        getLogger().info("\tAlignment calls improved by paired read: " + _alignmentsHelpedByMate);
        getLogger().info("\tAlignment calls improved by allele filters (see references disallowed): " + _alignmentsHelpedByAlleleFilters);
        getLogger().info("\tPaired reads without common alignments: " + _pairsWithoutSharedHits);
        getLogger().info("\tAlignment calls using paired reads: " + _pairedCalls);
        getLogger().info("\tAlignment calls using only 1 read: " + _singletonCalls);

        Set<String> acceptedReferences = new HashSet<>();
        for (HitSet hs : map.values())
        {
            acceptedReferences.addAll(hs.refNames);
        }

        getLogger().info("\tTotal references retained: " + acceptedReferences.size() + " (" + (100.0 * ((double) acceptedReferences.size() / (double) _distinctReferences.size())) + "%)");

        if (_onlyImportValidPairs)
        {
            getLogger().info("\tOnly alignments representing valid pairs will be included");
            getLogger().info("\tAlignments rejected because they lacked a valid pair: " + _rejectedSingletons);
        }

        Set<String> allReadNames = new HashSet<>();
        allReadNames.addAll(_alignmentsByReadM1.keySet());
        allReadNames.addAll(_alignmentsByReadM2.keySet());
        int noHits = _uniqueReads.size() - allReadNames.size();
        getLogger().info("\tReads discarded due to no passing alignments: " + noHits + " (" + (100.0 * (noHits / (double) _uniqueReads.size())) + "%)");

        return map;
    }

    @Override
    public void writeOutput(User u, Container c, AnalysisModel model)
    {

    }

    public static void processSBTSummary(User u, Container c, AnalysisModel model, File output, File refFasta, Logger log) throws PipelineJobException
    {
        try (CSVReader reader = new CSVReader(new BufferedReader(new InputStreamReader(new FileInputStream(output), "UTF-8")), '\t', CSVWriter.DEFAULT_QUOTE_CHARACTER))
        {
            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
            {
                //delete existing records
                TableInfo ti_summary = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY);
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("analysis_id"), model.getAnalysisId());
                Table.delete(ti_summary, filter);

                TableInfo ti_junction = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION);
                Table.delete(ti_junction, filter);

                //insert new
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    Map<String, Object> row = new HashMap<>();
                    row.put("analysis_id", model.getAnalysisId());
                    row.put("file_id", model.getAlignmentFile());

                    row.put("total", line[1]);
                    row.put("total_forward", line[2]);
                    row.put("total_reverse", line[3]);
                    row.put("valid_pairs", line[4]);

                    row.put("container", c.getEntityId());
                    row.put("createdby", u.getUserId());
                    row.put("modifiedby", u.getUserId());
                    row.put("created", new Date());
                    row.put("modified", new Date());
                    Map<String, Object> newRow = Table.insert(u, ti_summary, row);

                    if (!StringUtils.isEmpty(line[0]))
                    {
                        String[] names = line[0].split("\\|\\|");
                        ReferenceLibraryHelperImpl helper = new ReferenceLibraryHelperImpl(refFasta, log);
                        for (String refName : names)
                        {
                            Integer refId = helper.resolveSequenceId(refName);
                            if (refId == null)
                            {
                                log.error("unknown reference id: [" + refName + "]");
                            }

                            Map<String, Object> junction_row = new HashMap<>();
                            junction_row.put("analysis_id", model.getAnalysisId());
                            junction_row.put("ref_nt_id", refId);
                            junction_row.put("alignment_id", newRow.get("rowid"));
                            junction_row.put("status", true);
                            Table.insert(u, ti_junction, junction_row);
                        }
                    }
                }

                transaction.commit();
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public void writeTable(File output) throws PipelineJobException
    {
        try (CSVWriter writer = new CSVWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8")), '\t'))
        {
            Map<String, HitSet> map = writeSummary();


            //insert new
            for (String key : map.keySet())
            {
                HitSet totals = map.get(key);
                writer.writeNext(new String[]{key, String.valueOf(totals.readNames.size()), String.valueOf(totals.forward), String.valueOf(totals.reverse), String.valueOf(totals.valid_pair)});
            }

            //append unaligned
            writer.writeNext(new String[]{"", String.valueOf(_unaligned.size()), "", "", ""});
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public Pair<File, File> outputUnmappedReads(File bam, File outDir, String basename) throws PipelineJobException
    {
        if (!_unaligned.isEmpty())
        {
            File unmappedReadsF = new File(outDir, basename + ".unmapped-R1.fastq");
            File unmappedReadsR = new File(outDir, basename + ".unmapped-R2.fastq");

            FastqWriterFactory fact = new FastqWriterFactory();
            try (FastqWriter w1 = fact.newWriter(unmappedReadsF);FastqWriter w2 = fact.newWriter(unmappedReadsR))
            {
                Set<String> encountered = new HashSet<>();
                try (SAMFileReader reader = new SAMFileReader(bam);SAMFileReader mateReader = new SAMFileReader(bam))
                {
                    try (SAMRecordIterator it = reader.iterator())
                    {
                        while (it.hasNext())
                        {
                            SAMRecord r = it.next();
                            //note: should we account for forward/reverse?
                            if (_unaligned.contains(r.getReadName()))
                            {
                                if (r.isSecondaryOrSupplementary())
                                {
                                    continue;
                                }

                                if (r.getReadPairedFlag() && r.getFirstOfPairFlag())
                                {
                                    if (encountered.contains(r.getReadName()))
                                    {
                                        continue;
                                    }

                                    encountered.add(r.getReadName());

                                    SAMRecord mate = mateReader.queryMate(r);
                                    if (mate != null)
                                    {
                                        w1.write(new FastqRecord(r.getReadName(), r.getReadString(), null, r.getBaseQualityString()));
                                        w2.write(new FastqRecord(mate.getReadName(), mate.getReadString(), null, mate.getBaseQualityString()));
                                    }
                                    else
                                    {
                                        getLogger().warn("unable to find mate for read: " + r.getReadName() + ", skipping");
                                    }
                                }
                            }
                        }
                    }
                }
            }

            //join reads
            FlashWrapper flash = new FlashWrapper(getLogger());
            File joined = flash.execute(unmappedReadsF, unmappedReadsR, outDir, basename + ".joined", null);
            File notJoinedF = new File(outDir, basename + ".joined.notCombined_1.fastq");
            if (notJoinedF.exists())
            {
                Compress.compressGzip(notJoinedF);
                notJoinedF.delete();
            }
            File notJoinedR = new File(outDir, basename + ".joined.notCombined_2.fastq");
            if (notJoinedR.exists())
            {
                Compress.compressGzip(notJoinedR);
                notJoinedR.delete();
            }
            unmappedReadsF.delete();
            unmappedReadsR.delete();

            //collapse reads
            long lineCount = SequenceUtil.getLineCount(joined);
            if (lineCount < 2)
            {
                getLogger().info("there were no forward/reverse reads able to join, skipping this file");
                return null;
            }

            FastqCollapser collapser = new FastqCollapser(getLogger());
            File collapsed = new File(outDir, basename + ".collapsed.fastq");
            collapser.collapseFile(joined, collapsed);

            return Pair.of(joined, collapsed);
        }
        else
        {
            getLogger().info("there are no unmapped reads to output");
        }

        return null;
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
        "\tMinPctForRef: " + _minPctForRef + "\n" +
        "\tMinPctWithinGroup: " + _minPctWithinGroup + "\n"
        ;
    }

    public Set<String> getUniqueReads()
    {
        return _uniqueReads;
    }
}
