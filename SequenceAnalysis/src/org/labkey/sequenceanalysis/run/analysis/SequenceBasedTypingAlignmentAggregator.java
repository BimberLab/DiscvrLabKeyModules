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
import htsjdk.samtools.SAMFormatException;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.fastq.FastqReader;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.fastq.FastqWriterFactory;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.labkey.api.reader.Readers;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.writer.PrintWriters;
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
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
    private File _lineageMapFile = null;
    private double _minPctForLineageFiltering = 0.0;

    private Set<String> _distinctReferences = new HashSet<>();
    private Map<String, Integer> _acceptedReferences = new HashMap<>();
    private Set<String> _acceptedReads = new HashSet<>();
    private Map<String, Integer> _acceptedAlignments = new HashMap<>();
    private Set<String> _uniqueReads = new HashSet<>();
    private Map<String, Set<String>> _alignmentsByReadM1 = new HashMap<>();
    private Map<String, Set<String>> _alignmentsByReadM2 = new HashMap<>();
    private Map<String, IntervalList> _intervalsByReference = new HashMap<>();
    private int _forwardAlignmentsDiscardedBySnps = 0;
    private int _reverseAlignmentsDiscardedBySnps = 0;

    private Set<String> _unaligned = new HashSet<>();
    private Set<String> _unmappedWithMappedMate = new HashSet<>();
    private Set<String> _mappedWithoutHits = new HashSet<>();
    private Set<String> _mappedWithoutHitsExcludingPassed = new HashSet<>();
    private int _totalAlignmentsInspected = 0;
    private int _maxSNPs = 0;
    private int _skippedReferencesByPct = 0;
    private int _skippedReferencesByRead = 0;

    private int _alignmentsIncludingDiscardedSnps = 0;
    private int _alignmentsHelpedByMate = 0;
    private int _alignmentsHelpedByAlleleFilters = 0;
    private int _secondarySupplementary = 0;

    private int _pairsWithoutSharedHits = 0;
    private int _singletonCalls = 0;
    private int _pairedCalls = 0;
    private Set<String> _rejectedSingletonReadNames = new HashSet<>();
    private int _rejectedSingletonAlignments = 0;
    private int _shortAlignments = 0;

    private boolean _onlyImportValidPairs = false;
    private Double _minCountForRef = null;
    private Double _minPctForRef = null;
    private Double _minPctWithinGroup = null;
    private int _minAlignmentLength = 0;
    private boolean doTrackIntervals = false;

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

    public void setOutputLog(File outputLog)
    {
        _outputLog = outputLog;
    }

    public void setLineageMapFile(File lineageMapFile)
    {
        _lineageMapFile = lineageMapFile;
    }

    public void setMinPctForLineageFiltering(double minPctForLineageFiltering)
    {
        _minPctForLineageFiltering = minPctForLineageFiltering;
    }

    @Override
    public void inspectAlignment(SAMRecord record, ReferenceSequence ref, Map<Integer, List<NTSnp>> snps) throws PipelineJobException
    {
        if (!super.inspectMapQual(record))
        {
            return;
        }

        _uniqueReads.add(record.getReadName());
        if (record.isSecondaryOrSupplementary())
        {
            _secondarySupplementary++;
        }

        if (record.getReferenceName().equals(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME) || record.getReadUnmappedFlag())
        {
            if (!record.getReadPairedFlag() || record.getMateUnmappedFlag())
            {
                _unaligned.add(record.getReadName());
            }
            else
            {
                _unmappedWithMappedMate.add(record.getReadName());
            }
        }
        else
        {
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
            else
            {
                if (!record.getReadPairedFlag() || record.getFirstOfPairFlag())
                {
                    _forwardAlignmentsDiscardedBySnps++;
                }
                else
                {
                    _reverseAlignmentsDiscardedBySnps++;
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
        appendInterval(record);

        _distinctReferences.add(record.getReferenceName());
    }

    private void appendInterval(SAMRecord record)
    {
        if (!doTrackIntervals)
        {
            return;
        }

        if (!_intervalsByReference.containsKey(record.getContig()))
        {
            _intervalsByReference.put(record.getContig(), new IntervalList(record.getHeader()));
        }

        IntervalList il = _intervalsByReference.get(record.getContig());
        il.add(new Interval(record.getContig(), record.getAlignmentStart(), record.getAlignmentEnd()));
        if (il.size() > 1)
        {
            List<Interval> list = IntervalList.getUniqueIntervals(il, true);
            _intervalsByReference.put(record.getContig(), new IntervalList(record.getHeader()));
            _intervalsByReference.get(record.getContig()).addall(list);
        }
        else
        {
            _intervalsByReference.put(record.getContig(), il);
        }
    }

    private int getNumMismatches(SAMRecord record, Map<Integer, List<NTSnp>> snps) throws PipelineJobException
    {
        int highQuality = 0;
        for (Integer pos : snps.keySet())
        {
            for (NTSnp snp : snps.get(pos))
            {
                if (isPassingSnp(record, snp))
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

    public Map<String, HitSet> getAlignmentSummary(File outputLog) throws IOException, PipelineJobException
    {
        try (CSVWriter writer = outputLog == null ? null : new CSVWriter(new BufferedWriter(new OutputStreamWriter(getLogOutputStream(outputLog), StandardCharsets.UTF_8)), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            //these are stage-1 filters, filtering on the read-pair level
            Map<String, HitSet> totals = doFilterStage1(writer);

            //stage 2 filters, filtering across the set of alignments
            Map<String, HitSet> totals2 = doFilterStage2(writer, totals);

            //stage 3 filters: filtering within each set
            Map<String, HitSet> totals3 = doFilterStage3(writer, totals2);

            //stage 4 filters: filtering by lineage
            Map<String, HitSet> totals4 = doFilterStage4(writer, totals3);

            Map<String, Integer> totalByReferenceFinal = new HashMap<>();
            int distinctFinalReads = 0;
            for (HitSet hs : totals4.values())
            {
                for (String refName : hs.refNames)
                {
                    int total = totalByReferenceFinal.containsKey(refName) ? totalByReferenceFinal.get(refName) : 0;
                    total += hs.readNames.size();
                    totalByReferenceFinal.put(refName, total);
                }

                if (!hs.refNames.isEmpty())
                {
                    distinctFinalReads += hs.readNames.size();
                }
            }

            getLogger().info("after filters:");
            getLogger().info("\tpassing references: " + totalByReferenceFinal.size());
            getLogger().info("\ttotal passing reads: " + distinctFinalReads);
            getLogger().info("\ttotal allele groups: " + totals4.size());
            getLogger().info("\ttotal unaligned reads: " + _unaligned.size());

            return totals4;
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
            List<String> refNames2 = null;

            //if this read has an aligned mate, we find the intersect between its alignments
            Boolean hasMate = false;
            if (_alignmentsByReadM2.containsKey(readName))
            {
                refNames2 = new ArrayList<>(_alignmentsByReadM2.get(readName));

                // note: if reverse read has no alignments, skip this optimization
                // it will only pass if we have onlyImportValidPairs=false
                if (!refNames2.isEmpty())
                {
                    int originalSize = refNames.size();
                    refNames.retainAll(refNames2);
                    if (!refNames.isEmpty())
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
                    {
                        _pairedCalls++;
                    }
                    else
                    {
                        _singletonCalls++;
                        if (writer != null)
                        {
                            writer.writeNext(new String[]{"Singleton", readName, StringUtils.join(refNames, ";"), (refNames2 == null ? "N/A" : StringUtils.join(refNames2, ";"))});
                        }
                    }
                }
                else
                {
                    _rejectedSingletonAlignments++;
                    _rejectedSingletonReadNames.add(readName);
                    _unaligned.add(readName);
                    _mappedWithoutHits.add(readName);

                    if (writer != null)
                    {
                        writer.writeNext(new String[]{"Singleton", readName, StringUtils.join(refNames, ";"), (refNames2 == null ? "N/A" : StringUtils.join(refNames2, ";"))});
                    }
                }
            }
            else
            {
                _unaligned.add(readName);
                _mappedWithoutHits.add(readName);
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
                    if (writer != null)
                    {
                        writer.writeNext(new String[]{"Singleton", mateName, "N/A", StringUtils.join(refNames, ";")});
                    }
                }
                else
                {
                    _unaligned.add(mateName);
                    _mappedWithoutHits.add(mateName);

                    if (writer != null)
                    {
                        writer.writeNext(new String[]{"Singleton", mateName, "N/A", StringUtils.join(refNames, ";")});
                    }
                }
            }
            else
            {
                _rejectedSingletonAlignments++;
                _rejectedSingletonReadNames.add(mateName);
                _unaligned.add(mateName);
                _mappedWithoutHits.add(mateName);
            }

            if (writer != null)
            {
                List<String> names = new ArrayList<>(_alignmentsByReadM2.get(mateName));
                Collections.sort(names);
                for (String refName : names)
                {
                    writer.writeNext(new String[]{"Reverse", mateName, String.valueOf(_alignmentsByReadM2.get(mateName).size()), String.valueOf(refNames.size()), refName, String.valueOf(refNames.contains(refName)), ""});
                }
            }
        }

        getLogger().info("\talignments helped using paired read: " + _alignmentsHelpedByMate);
        if (_onlyImportValidPairs)
        {
            getLogger().info("\talignments rejected because they lack a valid pair: " + _rejectedSingletonAlignments);
            getLogger().info("\tdistinct reads rejected because they lack a valid pair: " + _rejectedSingletonReadNames.size());
        }

        return totals;
    }
    
    private Map<String, HitSet> doFilterStage2(CSVWriter writer, Map<String, HitSet> stage1Totals)
    {
        //build map of totals by ref, only counting each read pair once
        Pair<Integer, Map<String, Integer>> pair = writeTotalSummary("stage 2", stage1Totals);
        Map<String, Integer> totalByReferenceStage2 = pair.second;
        int distinctStageTwoReads = pair.first;

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
                _mappedWithoutHits.addAll(hs.readNames);
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

    private Pair<Integer, Map<String, Integer>> writeTotalSummary(String label, Map<String, HitSet> stageTotals)
    {
        Map<String, Integer> totalByReference = new HashMap<>();
        int distinctReads = 0;
        for (HitSet hs : stageTotals.values())
        {
            for (String refName : hs.refNames)
            {
                int total = totalByReference.containsKey(refName) ? totalByReference.get(refName) : 0;
                total += hs.readNames.size();
                totalByReference.put(refName, total);
            }

            if (!hs.refNames.isEmpty())
            {
                distinctReads += hs.readNames.size();
            }
        }

        getLogger().info("starting " + label + " filters:");
        getLogger().info("\tinitial references: " + totalByReference.size());
        getLogger().info("\tinitial distinct reads: " + distinctReads);
        getLogger().info("\tinitial allele groups: " + stageTotals.size());
        getLogger().info("\tinitial unaligned reads: " + _unaligned.size());

        return Pair.of(distinctReads, totalByReference);
    }

    private Map<String, HitSet> doFilterStage3(CSVWriter writer, Map<String, HitSet> stage2Totals)
    {
        Pair<Integer, Map<String, Integer>> pair = writeTotalSummary("stage 3", stage2Totals);
        Map<String, Integer> totalByReferenceStage3 = pair.second;

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
                    _mappedWithoutHits.addAll(hs.readNames);
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
                            String.valueOf(100.0 * ((double) totalByReferenceStage3.get(refName) / pair.first)),
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
                _mappedWithoutHits.addAll(hs.readNames);
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

    private Map<String, String> getLineageMap() throws PipelineJobException
    {
        if (_lineageMapFile == null)
        {
            return null;
        }

        Map<String, String> ret = new HashMap<>();
        try (CSVReader reader = new CSVReader(Readers.getReader(_lineageMapFile), '\t'))
        {
            String[] line;
            while ((line = reader.readNext()) != null)
            {
                ret.put(line[0], line[1]);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return ret;
    }

    private Map<String, HitSet> doFilterStage4(CSVWriter writer, Map<String, HitSet> stage3Totals) throws PipelineJobException
    {
        Map<String, String> nameToLineageMap = getLineageMap();
        if (nameToLineageMap == null)
        {
            getLogger().info("no lineage map file found, skipping");
            return stage3Totals;
        }

        writeTotalSummary("stage 4", stage3Totals);

        Map<String, HitSet> stage4Totals = new HashMap<>();

        //build a map of distinct sets by lineage
        Map<String, List<HitSet>> resultByLineage = new HashMap<>();
        Map<String, Integer> totalByLineage = new HashMap<>();
        for (String key : stage3Totals.keySet())
        {
            HitSet hs = stage3Totals.get(key);
            Set<String> distinctLineages = new HashSet<>();
            for (String refName: hs.refNames)
            {
                if (!nameToLineageMap.containsKey(refName))
                {
                    //if we have missing lineages, abort and keep data as-is
                    distinctLineages.clear();
                    break;
                }

                distinctLineages.add(nameToLineageMap.get(refName));
            }

            if (distinctLineages.size() == 1)
            {
                String lineage = distinctLineages.iterator().next();
                if (!resultByLineage.containsKey(lineage))
                {
                    resultByLineage.put(lineage, new ArrayList<>());
                    totalByLineage.put(lineage, 0);
                }

                resultByLineage.get(lineage).add(hs);
                totalByLineage.put(lineage, totalByLineage.get(lineage) + hs.readNames.size());
            }
            else
            {
                if (writer != null)
                {
                    writer.writeNext(new String[]{"Stage4Filters", hs.getKey(), "MultipleLineges", StringUtils.join(distinctLineages, ";")});
                }
                stage4Totals.put(key, hs);
            }
        }

        //now filter by lineage
        getLogger().info("total lineages being inspected: " + resultByLineage.size());
        int totalLineagesImproved = 0;
        Set<String> distinctAllelesPruned = new HashSet<>();
        for (String lineage : resultByLineage.keySet())
        {
            getLogger().debug("inspecting lineage: " + lineage);
            List<HitSet> sets = resultByLineage.get(lineage);
            if (sets.size() == 1)
            {
                getLogger().debug("only one set, no filtering");
                stage4Totals.put(sets.get(0).getKey(), sets.get(0));
                continue;
            }

            if (!totalByLineage.containsKey(lineage))
            {
                getLogger().warn("unable to find lineage, cannot filter: [" + lineage + "]");
                for (HitSet hs : sets)
                {
                    stage4Totals.put(hs.getKey(), hs);
                }
                continue;
            }

            Set<String> sharedRefNames = new HashSet<>();
            boolean hasPassingSet = false;
            getLogger().debug("total hit sets: " + sets.size());
            int setsSkipped = 0;
            for (HitSet hs : sets)
            {
                double pctOfLineage = (double)hs.readNames.size() / (double)totalByLineage.get(lineage);
                if (pctOfLineage < _minPctForLineageFiltering)
                {
                    setsSkipped++;
                    continue;
                }

                if (!hasPassingSet)
                {
                    sharedRefNames.addAll(hs.refNames);
                    hasPassingSet = true;
                    getLogger().debug("initial size: " + sharedRefNames.size());
                }
                else
                {
                    boolean changed = sharedRefNames.retainAll(hs.refNames);
                    if (changed)
                    {
                        getLogger().debug("new size: " + sharedRefNames.size());
                    }
                }
            }

            getLogger().debug("total sets skipped due to pct: " + setsSkipped);
            if (sharedRefNames.isEmpty())
            {
                getLogger().debug("no shared references, will not filter");

                //if empty, there are no alleles common to all, so keep original data
                for (HitSet hs : sets)
                {
                    stage4Totals.put(hs.getKey(), hs);
                }
            }
            else
            {
                totalLineagesImproved++;

                //merge and make new
                HitSet merged = new HitSet(sharedRefNames);
                for (HitSet hs : sets)
                {
                    //if below the threshold, leave as is
                    double pctOfLineage = (double)hs.readNames.size() / (double)totalByLineage.get(lineage);
                    if (pctOfLineage < _minPctForLineageFiltering)
                    {
                        if (stage4Totals.containsKey(hs.getKey()))
                        {
                            stage4Totals.get(hs.getKey()).append(hs);
                        }
                        else
                        {
                            stage4Totals.put(hs.getKey(), hs);
                        }

                        continue;
                    }

                    int diff = hs.refNames.size() - sharedRefNames.size();
                    if (diff > 0)
                    {
                        Set<String> discarded = new HashSet<>(hs.refNames);
                        discarded.removeAll(sharedRefNames);
                        distinctAllelesPruned.addAll(discarded);
                    }

                    merged.append(hs);
                }

                if (stage4Totals.containsKey(merged.getKey()))
                {
                    stage4Totals.get(merged.getKey()).append(merged);
                }
                else
                {
                    stage4Totals.put(merged.getKey(), merged);
                }
            }
        }

        getLogger().info("after filters:");
        getLogger().info("\ttotal lineages inspected: " + resultByLineage.size());
        getLogger().info("\ttotal lineages improved: " + totalLineagesImproved);
        getLogger().info("\ttotal alleles pruned: " + distinctAllelesPruned.size());
        getLogger().info("\ttotal allele groups: " + stage4Totals.size());

        return stage4Totals;
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

    public Map<String, HitSet> writeSummary() throws IOException, PipelineJobException
    {
        Map<String, HitSet> map = getAlignmentSummary(_outputLog);

        getLogger().info("Saving SBT Results");
        getLogger().info("\tTotal alignments inspected: " + _totalAlignmentsInspected);
        getLogger().info("\tSecondary or supplementary alignments inspected: " + _secondarySupplementary);
        getLogger().info("\tTotal reads inspected: " + _uniqueReads.size());

        getLogger().info("\tAlignments discarded due to low mapping quality: " + _lowMappingQual);
        getLogger().info("\tAlignments discarded due to short length: " + _shortAlignments);
        getLogger().info("\tAlignments retained (lacking high quality SNPs): " + _acceptedAlignments.size());
        getLogger().info("\tAlignments discarded (due to presence of high quality SNPs): " + (_totalAlignmentsInspected - _acceptedAlignments.size()));
        getLogger().info("\tForward Alignments Discarded Due To SNPs: " + _forwardAlignmentsDiscardedBySnps);
        getLogger().info("\tReverse Alignments Discarded Due To SNPs: " + _reverseAlignmentsDiscardedBySnps);
        getLogger().info("\tAlignments retained that contained low qual SNPs (thse may have been discarded for other factors): " + _alignmentsIncludingDiscardedSnps);
        getLogger().info("\tReferences with at least 1 aligned read (these may get filtered out downstream): " + _distinctReferences.size());
        getLogger().info("\tReferences disallowed due to read count filters: " + _skippedReferencesByRead);
        getLogger().info("\tReferences disallowed due to percent filters: " + _skippedReferencesByPct);

        Map<String, Integer> acceptedReferences = new HashMap<>();
        Set<String> acceptedReads = new HashSet<>();
        for (HitSet hs : map.values())
        {
            for (String refName : hs.refNames)
            {
                int total = 0;
                if (acceptedReferences.containsKey(refName))
                {
                    total = acceptedReferences.get(refName);
                }

                total += hs.readNames.size();
                acceptedReads.addAll(hs.readNames);
                acceptedReferences.put(refName, total);
            }
        }

        _acceptedReferences = acceptedReferences;
        _acceptedReads = acceptedReads;

        getLogger().info("\tReads with no alignments: " + _unaligned.size());
        getLogger().info("\tReads unmapped with a mate mapped: " + _unmappedWithMappedMate.size());
        getLogger().info("\tMapped reads without passing hits: " + _mappedWithoutHits.size());
        _mappedWithoutHitsExcludingPassed = new HashSet<>(_mappedWithoutHits);
        _mappedWithoutHitsExcludingPassed.removeAll(acceptedReads);

        getLogger().info("\tMapped reads without passing hits (excluding passed): " + _mappedWithoutHitsExcludingPassed.size() + " (" + getPctMappedWithoutHits() + "%)");

        getLogger().info("\tSingleton or First Mate Reads with at least 1 alignment that passed thresholds: " + _alignmentsByReadM1.keySet().size());
        getLogger().info("\tSecond Mate Reads with at least 1 alignment that passed thresholds: " + _alignmentsByReadM2.keySet().size());

        getLogger().info("\tAlignment calls improved by paired read: " + _alignmentsHelpedByMate);
        getLogger().info("\tAlignment calls improved by allele filters (see references disallowed): " + _alignmentsHelpedByAlleleFilters);
        getLogger().info("\tPaired reads without common alignments: " + _pairsWithoutSharedHits);
        getLogger().info("\tAlignment calls using paired reads: " + _pairedCalls);
        getLogger().info("\tAlignment calls using only 1 read: " + _singletonCalls);
        getLogger().info("\tTotal references retained: " + acceptedReferences.size() + " (" + (100.0 * ((double) acceptedReferences.size() / (double) _distinctReferences.size())) + "%)");

        if (_onlyImportValidPairs)
        {
            getLogger().info("\tOnly alignments representing valid pairs will be included");
            Set<String> reject = new HashSet<>(_rejectedSingletonReadNames);
            reject.removeAll(_acceptedReads);
            getLogger().info("\tAlignments rejected because they lacked a valid pair: " + _rejectedSingletonAlignments);
            getLogger().info("\tDistinct read names involved: " + _rejectedSingletonReadNames.size());
            getLogger().info("\tRead pairs rejected because they lacked a valid pair: " + reject.size() + " (" + (100.0 * ((double) reject.size() / (double) _uniqueReads.size())) + "%)");
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
        try (CSVReader reader = new CSVReader(new BufferedReader(new InputStreamReader(new FileInputStream(output), StandardCharsets.UTF_8)), '\t', CSVWriter.DEFAULT_QUOTE_CHARACTER))
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
                            junction_row.put("container", c.getEntityId());
                            junction_row.put("createdby", u.getUserId());
                            junction_row.put("modifiedby", u.getUserId());
                            junction_row.put("created", new Date());
                            junction_row.put("modified", new Date());
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
        try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(output), '\t'))
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

    public File outputReferencesCovered(File outDir, String basename, File refFasta, String headerPrefix) throws PipelineJobException
    {
        if (!doTrackIntervals)
        {
            throw new PipelineJobException("This aggregator was not set to track intervals, so cannot output coverage!  This indicates an error in the code");
        }


        File referencesCovered = new File(outDir, basename + ".referencesCovered.fasta");
        try (IndexedFastaSequenceFile idx = new IndexedFastaSequenceFile(refFasta); PrintWriter writer = PrintWriters.getPrintWriter(referencesCovered))
        {
            getLogger().debug("total references with matches: " + _acceptedReferences.size());

            for (String refName : _acceptedReferences.keySet())
            {
                IntervalList il = _intervalsByReference.get(refName);
                if (il == null)
                {
                    throw new PipelineJobException("No intervals tracked for reference: " + refName);
                }

                Iterator<Interval> it = il.iterator();
                int min = -1;
                int max = -1;
                while (it.hasNext())
                {
                    Interval i = it.next();
                    if (min == -1 || i.getStart() < min)
                    {
                        min = i.getStart();
                    }

                    if (max < i.getEnd())
                    {
                        max = i.getEnd();
                    }
                }

                ReferenceSequence seq = idx.getSubsequenceAt(refName, min, max);
                writer.write(">" + headerPrefix + refName + "|" + min + "-" + max + "|TotalReads:" + _acceptedReferences.get(refName) + "\n");
                writer.write(seq.getBaseString() + "\n");
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        return referencesCovered;
    }

    public Pair<File, File> outputUnmappedReads(File bam, List<Pair<File, File>> readData, File outDir, String basename, String headerPrefix, int minLength) throws PipelineJobException
    {
        if (!_mappedWithoutHitsExcludingPassed.isEmpty())
        {
            File unmappedReadsF = new File(outDir, basename + ".unmapped-R1.fastq");
            File unmappedReadsR = new File(outDir, basename + ".unmapped-R2.fastq");
            File unmappedReadsSingleton = new File(outDir, basename + ".unmapped-singleton.fastq");

            FastqWriterFactory fact = new FastqWriterFactory();
            fact.setUseAsyncIo(true);
            int discardedForLength = 0;

            try (FastqWriter w1 = fact.newWriter(unmappedReadsF);FastqWriter w2 = fact.newWriter(unmappedReadsR);FastqWriter wS = fact.newWriter(unmappedReadsSingleton))
            {
                int idx = 0;
                long startTime = new Date().getTime();

                for (Pair<File, File> fqs : readData)
                {
                    getLogger().info("processing file: " + fqs.first.getPath());
                    int totalExported= 0;
                    try (FastqReader reader1 = new FastqReader(fqs.first);FastqReader reader2 = fqs.second == null ? null : new FastqReader(fqs.second))
                    {
                        while (reader1.hasNext())
                        {
                            FastqRecord r1 = reader1.next();
                            FastqRecord r2 = reader2 == null ? null : reader2.next();
                            idx++;

                            if (idx % 5000 == 0)
                            {
                                long newTime = new Date().getTime();
                                getLogger().info("processed " + idx + " reads in " + ((newTime - startTime) / 1000) + " seconds");
                                startTime = newTime;
                            }

                            //note: should we account for forward/reverse?
                            String[] tokens = r1.getReadHeader().split(" ");
                            if (_mappedWithoutHitsExcludingPassed.contains(tokens[0]))
                            {
                                totalExported++;
                                if (r2 == null)
                                {
                                    if (r1.getReadString().length() > minLength)
                                    {
                                        wS.write(new FastqRecord(r1));
                                    }
                                    else
                                    {
                                        discardedForLength++;
                                    }
                                }
                                else
                                {
                                    w1.write(r1);
                                    w2.write(r2);
                                }
                            }
                        }
                    }

                    getLogger().debug("total reads exported: " + totalExported);
                }

                getLogger().debug("reads discarded due to length: " + discardedForLength);
            }

            //join reads
            FlashWrapper flash = new FlashWrapper(getLogger());
            File joined = flash.execute(unmappedReadsF, unmappedReadsR, outDir, basename + ".joined", null);
            File notJoinedF = new File(outDir, basename + ".joined.notCombined_1.fastq");
            File notJoinedR = new File(outDir, basename + ".joined.notCombined_2.fastq");

            //create merged file
            discardedForLength = 0;
            File merged = new File(outDir, basename + ".unmapped.fastq");
            try (FastqWriter writer = fact.newWriter(merged))
            {
                for (File f : Arrays.asList(joined, notJoinedF, notJoinedR, unmappedReadsSingleton))
                {
                    getLogger().debug("processing file: " + f.getName());
                    if (!f.exists())
                    {
                        getLogger().debug("file not found, skipping: " + f.getName());
                        continue;
                    }

                    try (FastqReader reader = new FastqReader(f))
                    {
                        int lineNo = 0;
                        while (reader.hasNext())
                        {
                            FastqRecord rec = reader.next();
                            if (rec.getReadString().length() < minLength)
                            {
                                discardedForLength++;
                                continue;
                            }

                            if (f.getName().contains("_1"))
                            {
                                rec = new FastqRecord(rec.getReadHeader() + "/1", rec.getReadString(), rec.getBaseQualityHeader(), rec.getBaseQualityString());
                            }
                            else if (f.getName().contains("_2"))
                            {
                                rec = new FastqRecord(rec.getReadHeader() + "/2", rec.getReadString(), rec.getBaseQualityHeader(), rec.getBaseQualityString());
                            }

                            writer.write(rec);
                            lineNo++;
                        }
                        getLogger().debug("total lines: " + lineNo);
                    }

                    f.delete();
                }
            }

            unmappedReadsF.delete();
            unmappedReadsR.delete();
            unmappedReadsSingleton.delete();
            getLogger().debug("joined reads discarded due to length: " + discardedForLength);

            //collapse reads
            long lineCount = SequenceUtil.getLineCount(merged);
            if (lineCount < 2)
            {
                getLogger().info("there were no unmapped reads found, skipping this file: " + merged.getPath());
                return null;
            }

            FastqCollapser collapser = new FastqCollapser(getLogger());
            File collapsed = new File(outDir, basename + ".collapsed.fasta");
            collapser.collapseFile(merged, collapsed);

            //rename reads to make it easier to combine later
            File renamed = new File(outDir, basename + ".collapsed.tmp.fasta");
            try (BufferedReader reader = Readers.getReader(collapsed);PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(renamed), StringUtilsLabKey.DEFAULT_CHARSET))))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (line.startsWith(">"))
                    {
                        line = line.replaceAll(">", ">" + headerPrefix + "Contig");
                    }

                    writer.print(line + "\n");
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            try
            {
                collapsed.delete();
                FileUtils.moveFile(renamed, collapsed);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            File mergedGz = new File(merged.getPath() + ".gz");
            try (FastqReader reader = new FastqReader(merged);FastqWriter writer = fact.newWriter(mergedGz))
            {
                while (reader.hasNext())
                {
                    FastqRecord rec = reader.next();
                    writer.write(new FastqRecord(headerPrefix + rec.getReadHeader(), rec.getReadString(), rec.getBaseQualityHeader(), rec.getBaseQualityString()));
                }
            }

            merged.delete();

            return Pair.of(mergedGz, collapsed);
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
        "\tMinMapQual: " + getMinMapQual() + "\n" +
        "\tMinSnpQual: " + getMinSnpQual() + "\n" +
        "\tMinAvgSnpQual: " + getMinAvgSnpQual() + "\n" +
        "\tMinDipQual: " + getMinDipQual() + "\n" +
        "\tMinAvgDipQual: " + getMinAvgDipQual() + "\n" +
        "\tMinCountForRef: " + _minCountForRef + "\n" +
        "\tMinPctForRef: " + _minPctForRef + "\n" +
        "\tMinPctWithinGroup: " + _minPctWithinGroup + "\n" +
        "\tMappedWithoutHits: " + getPctMappedWithoutHits() + "\n"
        ;
    }

    public Set<String> getUniqueReads()
    {
        return _uniqueReads;
    }

    public double getPctMappedWithoutHits()
    {
        return (double)_mappedWithoutHitsExcludingPassed.size() / (double)_uniqueReads.size();
    }

    public void setDoTrackIntervals(boolean doTrackIntervals)
    {
        this.doTrackIntervals = doTrackIntervals;
    }
}
