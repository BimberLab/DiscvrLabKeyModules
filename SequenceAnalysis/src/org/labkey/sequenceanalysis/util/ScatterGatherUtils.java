package org.labkey.sequenceanalysis.util;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.Interval;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScatterGatherUtils
{
    public static enum ScatterGatherMethod
    {
        none(),
        contig(),
        chunked(),
        fixedJobs()
    }

    private static class ActiveIntervalSet
    {
        private static final int MIN_FINAL_CHUNK_SIZE = 500;

        private final int _optimalBasesPerJob;
        private final LinkedHashMap<String, List<Interval>> _results;
        private final boolean _allowSplitChromosomes;

        private List<Interval> _intervalList = new ArrayList<>();
        private int _basesPerActiveIntervalList = 0;
        private int _activeJobId = 1;

        public ActiveIntervalSet(int optimalBasesPerJob, boolean allowSplitChromosomes)
        {
            _optimalBasesPerJob = optimalBasesPerJob;
            _results = new LinkedHashMap<>();
            _allowSplitChromosomes = allowSplitChromosomes;
        }

        private void possiblyEndSet()
        {
            int basesRemaining = getBasesRemainingForInterval();
            if (basesRemaining <= 0)
            {
                closeSet();
            }
        }

        public void closeSet()
        {
            if (!_intervalList.isEmpty())
            {
                _results.put("Job" + _activeJobId, new ArrayList<>(_intervalList));
                _intervalList.clear();
                _basesPerActiveIntervalList = 0;
                _activeJobId++;
            }
        }

        public LinkedHashMap<String, List<Interval>> getResults()
        {
            return _results;
        }

        private int getBasesRemainingForInterval()
        {
            return _optimalBasesPerJob - _basesPerActiveIntervalList;
        }

        private void add(SAMSequenceRecord rec)
        {
            if (rec.getSequenceLength() <= getBasesRemainingForInterval() || !_allowSplitChromosomes)
            {
                addInterval(rec.getSequenceName(), 1, rec.getSequenceLength());
            }
            else
            {
                int start0 = 0;  //0-based
                while (start0 < rec.getSequenceLength())
                {
                    int end = Math.min(start0 + getBasesRemainingForInterval(), rec.getSequenceLength());

                    //If the bases remaining would be tiny, just batch with this job:
                    int basesAfterChunk = rec.getSequenceLength() - end;
                    if (basesAfterChunk > 0 && basesAfterChunk < MIN_FINAL_CHUNK_SIZE)
                    {
                        end = rec.getSequenceLength();
                    }

                    addInterval(rec.getSequenceName(), start0 + 1, end);
                    start0 = end;
                }

            }
        }

        private void addInterval(String refName, int start, int end)
        {
            _intervalList.add(new Interval(refName, start, end));
            _basesPerActiveIntervalList += (end - start + 1);

            possiblyEndSet();
        }
    }

    public static LinkedHashMap<String, List<Interval>> divideGenome(SAMSequenceDictionary dict, int optimalBasesPerJob, boolean allowSplitChromosomes)
    {
        ActiveIntervalSet ais = new ActiveIntervalSet(optimalBasesPerJob, allowSplitChromosomes);
        for (SAMSequenceRecord rec : dict.getSequences())
        {
            ais.add(rec);
        }

        ais.closeSet();

        return ais.getResults();
    }

    public static class TestCase extends Assert
    {
        private SAMSequenceDictionary getDict()
        {
            SAMSequenceDictionary dict = new SAMSequenceDictionary();
            dict.addSequence(new SAMSequenceRecord("Seq1", 1000));
            dict.addSequence(new SAMSequenceRecord("Seq2", 2000));
            dict.addSequence(new SAMSequenceRecord("Seq3", 1000));
            dict.addSequence(new SAMSequenceRecord("Seq4", 2000));
            dict.addSequence(new SAMSequenceRecord("Seq5", 1000));
            dict.addSequence(new SAMSequenceRecord("Seq6", 20));
            dict.addSequence(new SAMSequenceRecord("Seq7", 20));
            dict.addSequence(new SAMSequenceRecord("Seq8", 20));
            dict.addSequence(new SAMSequenceRecord("Seq9", 20));

            return dict;
        }

        @Test
        public void testScatter()
        {
            SAMSequenceDictionary dict = getDict();
            Map<String, List<Interval>> ret = divideGenome(dict, 1000, true);
            assertEquals("Incorrect number of jobs", 8, ret.size());
            assertEquals("Incorrect interval end", 2000, ret.get("Job3").get(0).getEnd());
            assertEquals("Incorrect start", 1001, ret.get("Job3").get(0).getStart());
            assertEquals("Incorrect interval end", 4, ret.get("Job8").size());

            Map<String, List<Interval>> ret2 = divideGenome(dict, 3000, false);
            assertEquals("Incorrect number of jobs", 3, ret2.size());
            for (String jobName : ret2.keySet())
            {
                for (Interval i : ret2.get(jobName))
                {
                    assertEquals("Incorrect start", 1, i.getStart());
                }
            }

            Map<String, List<Interval>> ret3 = divideGenome(dict, 3002, false);
            assertEquals("Incorrect number of jobs", 3, ret3.size());
            for (String jobName : ret3.keySet())
            {
                for (Interval i : ret3.get(jobName))
                {
                    assertEquals("Incorrect start", 1, i.getStart());
                }
            }

            Map<String, List<Interval>> ret4 = divideGenome(dict, 2999, false);
            assertEquals("Incorrect number of jobs", 3, ret4.size());
            for (String jobName : ret4.keySet())
            {
                for (Interval i : ret4.get(jobName))
                {
                    assertEquals("Incorrect start", 1, i.getStart());
                }
            }

            Map<String, List<Interval>> ret5 = divideGenome(dict, 750, true);
            assertEquals("Incorrect number of jobs", 10, ret5.size());
            assertEquals("Incorrect interval end", 1000, ret5.get("Job1").get(0).getEnd());
            assertEquals("Incorrect interval end", 4, ret5.get("Job10").size());

            assertEquals("Incorrect interval start", 751, ret5.get("Job3").get(0).getStart());
            assertEquals("Incorrect interval start", 1501, ret5.get("Job8").get(0).getStart());
        }
    }
}
