package org.labkey.sequenceanalysis.run;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.util.Interval;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.util.Pair;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.sequenceanalysis.api.picard.CigarPositionIterable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by bimber on 2/3/2016.
 */
public class BamHaplotyper
{
    private static final Logger _log = LogManager.getLogger(BamHaplotyper.class);
    private User _u;

    public BamHaplotyper(User u)
    {
        _u = u;
    }

    public JSONObject calculateAAHaplotypes(int[] inputFileIds) throws UnauthorizedException
    {
        List<SequenceOutputFile> bams = getOutputFiles(inputFileIds);

        return null;
    }

    public JSONObject calculateNTHaplotypes(int[] inputFileIds, String[] regions, int minQual, boolean requireCompleteCoverage) throws UnauthorizedException, IOException, PipelineJobException
    {
        List<SequenceOutputFile> bams = getOutputFiles(inputFileIds);
        List<Interval> intervals = getIntervals(regions);
        JSONObject ret = new JSONObject();
        ret.put("intervals", new JSONArray());
        ret.put("sampleNames", new JSONObject());
        for (SequenceOutputFile so : bams)
        {
            if (so.getReadset() != null)
            {
                Readset rs = SequenceAnalysisService.get().getReadset(so.getReadset(), _u);
                if (rs == null)
                {
                    throw new IllegalArgumentException("Unable to find readset: " + so.getReadset());
                }

                ret.getJSONObject("sampleNames").put(so.getRowid().toString(), rs.getName() + "(" + so.getName() + ")");
            }
            else
            {
                ret.getJSONObject("sampleNames").put(so.getRowid().toString(), so.getName());
            }
        }

        for (Interval i : intervals)
        {
            List<Pair<Character[][], Map<Integer, Integer>>> combinedResults = new ArrayList<>();
            ReferenceSequence ref = null;
            for (SequenceOutputFile so : bams)
            {
                ReferenceGenome referenceGenome = SequenceAnalysisService.get().getReferenceGenome(so.getLibrary_id(), _u);
                try (IndexedFastaSequenceFile idx = new IndexedFastaSequenceFile(referenceGenome.getWorkingFastaFile()))
                {
                    ref = idx.getSubsequenceAt(i.getContig(), i.getStart(), i.getEnd());
                }

                List<Pair<Character[][], Integer>> haplotypes = processBam(i, so.getFile(), minQual, requireCompleteCoverage);
                for (Pair<Character[][], Integer> newPair : haplotypes)
                {
                    //add to our map
                    boolean found = false;
                    for (Pair<Character[][], Map<Integer, Integer>> pair : combinedResults)
                    {
                        if (Arrays.deepEquals(pair.first, newPair.first))
                        {
                            Integer count = pair.second.containsKey(so.getRowid()) ? pair.second.get(so.getRowid()) : 0;
                            count += newPair.second;
                            pair.second.put(so.getRowid(), count);

                            found = true;
                        }
                    }

                    if (!found)
                    {
                        Map<Integer, Integer> map = new HashMap<>();
                        map.put(so.getRowid(), newPair.second);
                        combinedResults.add(Pair.of(newPair.first, map));
                    }
                }
            }

            JSONObject o = new JSONObject();

            Map<Integer, TreeSet<Integer>> indels = getInsertionMap(combinedResults);
            o.put("intervals", convertResults(combinedResults, ref.getBases(), indels));
            o.put("referenceSequence", getReferenceSequence(ref, indels));
            o.put("name", (i.getContig() + ":" + i.getStart() + "-" + i.getEnd()));
            ret.getJSONArray("intervals").put(o);
        }

        return ret;
    }

    private String getReferenceSequence(ReferenceSequence ref, Map<Integer, TreeSet<Integer>> indels)
    {
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        for (byte b : ref.getBases())
        {
            sb.append((char)b);
            if (indels.containsKey(idx))
            {
                for (int insertIdx : indels.get(idx))
                {
                    sb.append("-");
                }
            }

            idx++;
        }

        return sb.toString();
    }

    private Map<Integer, TreeSet<Integer>> getInsertionMap(List<Pair<Character[][], Map<Integer, Integer>>> combinedResults)
    {
        //build list of all insertions that are present
        Map<Integer, TreeSet<Integer>> indels = new HashMap<>();
        for (Pair<Character[][], Map<Integer, Integer>> pair : combinedResults)
        {
            for (int idx = 0;idx < pair.first.length;idx++)
            {
                Character[] arr = pair.first[idx];
                if (arr == null)
                {
                    continue;
                }

                if (arr.length > 1)
                {
                    for (int i = 1; i < arr.length; i++)
                    {
                        Set<Integer> l = indels.containsKey(idx) ? indels.get(idx) : new TreeSet<>();
                        l.add(i);
                    }
                }
            }
        }

        return indels;
    }
    private Map<String, Map<Integer, Integer>> convertResults(List<Pair<Character[][], Map<Integer, Integer>>> combinedResults, byte[] refBases, Map<Integer, TreeSet<Integer>> indels)
    {
        //now iterate each array, convert to string and return results
        Map<String, Map<Integer, Integer>> ret = new HashMap<>();
        for (Pair<Character[][], Map<Integer, Integer>> pair : combinedResults)
        {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0;idx < pair.first.length;idx++)
            {
                Character[] arr = pair.first[idx];
                char ref = (char)refBases[idx];

                if (arr == null)
                {
                    sb.append(':');
                }
                else if (arr[0] == ref)
                {
                    sb.append('.');
                }
                else
                {
                    sb.append(arr[0]);
                }

                if (indels.containsKey(idx))
                {
                    for (int insertIdx : indels.get(idx))
                    {
                        if (arr != null && insertIdx < arr.length)
                        {
                            sb.append(arr[insertIdx]);
                        }
                        else
                        {
                            sb.append("-");
                        }
                    }
                }
            }

            ret.put(sb.toString(), pair.second);
        }

        return ret;
    }

    private List<Pair<Character[][], Integer>> processBam(Interval interval, File bam, int minQual, boolean requireCompleteCoverage) throws IOException
    {
        _log.info("processing bam: " + bam.getName());

        File index = new File(bam.getPath() + ".bai");
        if (!index.exists())
        {
            throw new IOException("No index found for BAM: " + bam.getPath());
        }

        List<Pair<Character[][], Integer>> ret = new ArrayList<>();

        SamReaderFactory fact = SamReaderFactory.makeDefault();
        try (SamReader reader = fact.open(bam);SamReader mateReader = fact.open(bam))
        {
            SAMFileHeader header = reader.getFileHeader();
            int idx = header.getSequenceIndex(interval.getContig());
            if (idx == -1)
            {
                throw new IllegalArgumentException("Invalid reference name: " +  interval.getContig());
            }

            try (SAMRecordIterator it = reader.queryOverlapping(interval.getContig(), interval.getStart(), interval.getEnd()))
            {
                int row = 0;
                while (it.hasNext())
                {
                    row++;
                    if (row % 5000 == 0)
                    {
                        _log.info("processed: " + row + " records");
                    }

                    SAMRecord r = it.next();
                    if (r.getReadPairedFlag() && r.getSecondOfPairFlag())
                    {
                        //we will address this read with first of pair
                        continue;
                    }

                    Character[][] arr = new Character[interval.length()][];
                    Integer[][] qualArr = new Integer[interval.length()][];

                    //process read
                    processRead(r, interval, arr, qualArr, minQual);

                    if (r.getReadPairedFlag())
                    {
                        SAMRecord mate = mateReader.queryMate(r);
                        if (mate != null)
                        {
                            processRead(mate, interval, arr, qualArr, minQual);
                        }
                    }

                    if (requireCompleteCoverage)
                    {
                        if (ArrayUtils.indexOf(arr, null) > -1)
                        {
                            continue;
                        }
                    }

                    //add to our map
                    boolean found = false;
                    for (Pair<Character[][], Integer> pair : ret)
                    {
                        if (Arrays.deepEquals(pair.first, arr))
                        {
                            pair.second = pair.second + 1;
                            found = true;
                        }
                    }

                    if (!found)
                    {
                        ret.add(Pair.of(arr, 1));
                    }
                }
            }
        }

        return ret;
    }

    private void processRead(SAMRecord r, Interval interval, Character[][] arr, Integer[][] qualArr, int minQual)
    {
        //add this value to a reference coordinate to find array position
        final int offset = interval.getStart() * -1;

        CigarPositionIterable cpi = new CigarPositionIterable(r);
        CigarPositionIterable.CigarIterator ci = cpi.iterator();

        int effectiveInsertIdx = 0;
        while (ci.hasNext())
        {
            CigarPositionIterable.PositionInfo pi = ci.next();
            if (pi.getInsertIndex() == 0)
            {
                //reset each time we hit a new position
                effectiveInsertIdx = 0;
            }

            //note: getRefPosition is 0-based, interval is 1-based
            if (pi.getRefPosition() + 1 < interval.getStart())
            {
                continue;
            }
            else if (pi.getRefPosition() + 1 > interval.getEnd())
            {
                break;
            }

            //getRefPosition() is 0-based
            int arrayPos = pi.getRefPosition() + offset + 1;

            if (pi.isSkipped())
            {
                //ignore
            }
            else if (pi.isIndel())
            {
                if (pi.isDel())
                {
                    if (arr[arrayPos] == null)
                    {
                        arr[arrayPos] = new Character[]{pi.getBaseQuality() < minQual ? 'N' : '-'};
                        qualArr[arrayPos] = new Integer[]{pi.getBaseQuality()};
                    }
                    else
                    {
                        mergePositions(arr, qualArr, arrayPos, 0, pi.getBaseQuality() < minQual ? 'N' : '-', pi.getBaseQuality(), pi);
                    }
                }
                else if (pi.isInsertion() && pi.getBaseQuality() >= minQual)
                {
                    _log.info("indel: " + pi.getRecord().getReadName() + ", " + pi.getRefPosition());
                    //TODO: account for second mate
                    effectiveInsertIdx++;
                    Character[] posArr = arr[arrayPos];
                    if (posArr == null)
                    {
                        throw new IllegalArgumentException("No previous array for position: " + pi.getRefPosition());
                    }

                    Character[] newArr = Arrays.copyOf(posArr, effectiveInsertIdx + 1);
                    newArr[effectiveInsertIdx] = (char)pi.getReadBase();

                    arr[arrayPos] = newArr;
                }
            }
            else
            {
                if (arr[arrayPos] == null)
                {
                    arr[arrayPos] = new Character[]{pi.getBaseQuality() < minQual ? 'N' : (char) pi.getReadBase()};
                    qualArr[arrayPos] = new Integer[]{pi.getBaseQuality()};
                }
                else
                {
                    mergePositions(arr, qualArr, arrayPos, 0, pi.getBaseQuality() < minQual ? 'N' : (char) pi.getReadBase(), pi.getBaseQuality(), pi);
                }
            }
        }
    }

    private void mergePositions(Character[][] arr, Integer[][] qualArray, int arrayPos, int idx, char base, int qual, CigarPositionIterable.PositionInfo pi)
    {
        char existing = arr[arrayPos][idx];
        if (existing == 'N')
        {
            arr[arrayPos][idx] = base;
        }
        else if (base == 'N')
        {
            return;
        }
        else if (existing != base)
        {
            Integer existingQual = qualArray[arrayPos][idx];
            if (existingQual < qual)
            {
                arr[arrayPos][idx] = base;
            }
            else if (existingQual == qual)
            {
                _log.info("conflicting bases: " + pi.getRecord().getReadName() + ", " + pi.getRefPosition() + ", " + arrayPos + ", " + idx + ", " + existing + ", " + base + ", " + qual);
            }
        }
    }

    private List<Interval> getIntervals(String[] regions)
    {
        List<Interval> ret = new ArrayList<>();
        for (String i : regions)
        {
            //TODO: validation
            String[] tokens = i.split(":");
            String[] coordinates = tokens[1].split("-");

            ret.add(new Interval(tokens[0], Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1])));
        }

        return ret;
    }

    public List<SequenceOutputFile> getOutputFiles(int[] inputFileIds) throws UnauthorizedException
    {
        if (PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer)
        {
            throw new IllegalAccessError("This method should only be called from the webserver");
        }

        List<Integer> rowIds = new ArrayList<>();
        for (Integer i : inputFileIds)
        {
            rowIds.add(i);
        }

        List<SequenceOutputFile> ret = new TableSelector(DbSchema.get("sequenceanalysis").getTable("outputfiles"), new SimpleFilter(FieldKey.fromString("rowid"), rowIds, CompareType.IN), null).getArrayList(SequenceOutputFile.class);
        for (SequenceOutputFile so : ret)
        {
            Container c = ContainerManager.getForId(so.getContainer());
            if (!c.hasPermission(_u, ReadPermission.class))
            {
                throw new UnauthorizedException("User does not have permission to view the file: " + so.getRowid());
            }
        }

        return ret;
    }

    private Map<String, Map<Integer, Integer>> getSummary()
    {


        return null;
    }
}
