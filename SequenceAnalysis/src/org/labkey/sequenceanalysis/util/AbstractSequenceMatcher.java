package org.labkey.sequenceanalysis.util;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.model.SequenceTag;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: bimber
 * Date: 11/28/12
 * Time: 4:18 PM
 */
abstract public class AbstractSequenceMatcher
{
    protected Logger _logger;
    protected File _outputDir;
    protected int _editDistance = 0;
    protected int _offsetDistance = 0;
    protected int _deletionsAllowed = 0;
    protected boolean _barcodesInReadHeader = false;
    protected boolean _createDetailedLog = false;
    protected boolean _createSummaryLog = true;

    //NOTE: this assumes only 1 call of execute(), which is probably ok, but might be better to track this info elsewhere
    protected TreeMap<String, Integer> _sequenceMatch5Counts;
    protected TreeMap<String, Integer> _sequenceMatch3Counts;
    protected Map<File, FastqWriter> _fileMap;

    protected File _detailLog = null;
    protected CSVWriter _detailLogWriter = null;
    protected File _summaryLog = null;
    protected CSVWriter _summaryLogWriter = null;

    abstract protected void initDetailLog(File fastq);

    abstract protected void initSummaryLog(File fastq);

    protected Pair<String, String> extractBarcodeFromHeader(FastqRecord rec)
    {
        String header = rec.getReadHeader();

        //example: NS500556:30:H2LMCAFXX:1:11101:0:0 1:N:0:TCCGGAGA+AGGCTATA
        if (!header.contains(":"))
        {
            _logger.error("Malformed read, expected barcodes after a semicolon: [" + header + "]");
            return null;
        }

        header = header.substring(header.lastIndexOf(":") + 1);

        String[] barcodes = header.split("\\+");
        if (barcodes.length == 0)
        {
            _logger.error("Malformed read, expected barcodes after a semicolon: [" + header + "]");
            return null;
        }

        return Pair.of(barcodes[0], barcodes.length > 2 ? barcodes[1] : null);
    }

    protected void processTag5(FastqRecord rec, String sequenceToTest, SequenceTag bc, int offset, Map<Integer, Map<String, SequenceMatch>> trackingMap)
    {
        String targetSeq;
        String barcodeSeq;

        if (offset >= 0)
        {
            barcodeSeq = bc.getSequence();
            targetSeq = sequenceToTest.substring(offset, offset + barcodeSeq.length());
        }
        else
        {
            barcodeSeq = bc.getSequence().substring(-offset);
            targetSeq = sequenceToTest.substring(0, barcodeSeq.length());
        }

        int editDist = StringUtils.getLevenshteinDistance(barcodeSeq, targetSeq);
        if (editDist <= _editDistance)
        {
            Map<String, SequenceMatch> matchesAtDist = trackingMap.get(editDist);
            if (matchesAtDist == null)
                matchesAtDist = new TreeMap<>();

            if (!matchesAtDist.containsKey(bc.getName()))
            {
                int start = barcodeSeq.length() + (offset > 0 ? offset : 0);
                SequenceMatch match = new SequenceMatch(bc, rec, true, editDist, offset, start, null);
                matchesAtDist.put(bc.getName(), match);
                trackingMap.put(editDist, matchesAtDist);

                writeDetailedLine(match, barcodeSeq, targetSeq);
            }
        }
    }

    protected void processTag3(FastqRecord rec, String sequenceToTest, SequenceTag bc, int offset, Map<Integer, Map<String, SequenceMatch>> trackingMap)
    {
        String targetSeq;
        String barcodeSeq;

        if (offset >= 0)
        {
            barcodeSeq = StringUtils.reverse(bc.getReverseComplement());
            targetSeq = StringUtils.reverse(sequenceToTest).substring(offset, offset + barcodeSeq.length());
        }
        else
        {
            barcodeSeq = StringUtils.reverse(bc.getReverseComplement()).substring(-offset);
            targetSeq = StringUtils.reverse(sequenceToTest).substring(0, barcodeSeq.length());
        }

        int editDist = StringUtils.getLevenshteinDistance(barcodeSeq, targetSeq);
        if (editDist <= _editDistance)
        {
            Map<String, SequenceMatch> matchesAtDist = trackingMap.get(editDist);
            if (matchesAtDist == null)
                matchesAtDist = new TreeMap<>();

            if (!matchesAtDist.containsKey(bc.getName()))
            {
                int stop = (sequenceToTest.length() - barcodeSeq.length() - (offset > 0 ? offset : 0));
                SequenceMatch match = new SequenceMatch(bc, rec, false, editDist, offset, null, stop);
                matchesAtDist.put(bc.getName(), match);
                trackingMap.put(editDist, matchesAtDist);

                writeDetailedLine(match, barcodeSeq, targetSeq);
            }
        }
    }

    private void writeDetailedLine(SequenceMatch match, String barcodeSeq, String targetSeq)
    {
        if (!_createDetailedLog)
            return;

        _detailLogWriter.writeNext(new String[]{match.getRec().getReadHeader(), match.getSequenceTag().getName(), match.getMoleculeEnd(), String.valueOf(match.getEditDistance()), String.valueOf(match.getOffset()), String.valueOf(match.getStart1()), String.valueOf(match.getStop()), barcodeSeq, targetSeq});
    }

    public void setEditDistance(int editDistance)
    {
        _editDistance = editDistance;
    }

    public void setOffsetDistance(int offsetDistance)
    {
        _offsetDistance = offsetDistance;
    }

    public void setDeletionsAllowed(int deletionsAllowed)
    {
        _deletionsAllowed = deletionsAllowed;
    }

    public void setBarcodesInReadHeader(boolean val)
    {
        _barcodesInReadHeader = val;
    }

    public boolean isCreateDetailedLog()
    {
        return _createDetailedLog;
    }

    public void setCreateDetailedLog(boolean createDetailedLog)
    {
        _createDetailedLog = createDetailedLog;
    }

    public boolean isCreateSummaryLog()
    {
        return _createSummaryLog;
    }

    public void setCreateSummaryLog(boolean createSummaryLog)
    {
        _createSummaryLog = createSummaryLog;
    }

    protected void scanForMatches(FastqRecord rec, Collection<SequenceTag> barcodes5, Collection<SequenceTag> barcodes3, Map<Integer, Map<String, SequenceMatch>> matches5, Map<Integer, Map<String, SequenceMatch>> matches3)
    {
        //first scan 5' end

        //try default matching from end, without deletions first
        String sequenceToTest5;
        if (_barcodesInReadHeader)
        {
            Pair<String, String> barcodes = extractBarcodeFromHeader(rec);
            if (barcodes == null)
            {
                return;
            }

            //TODO: consider if this should be reversed?
            sequenceToTest5 = barcodes.first;
        }
        else
        {
            sequenceToTest5 = rec.getReadString();
        }

        for (SequenceTag bc : barcodes5)
        {
            processTag5(rec, sequenceToTest5, bc, 0, matches5);
        }

        if (_offsetDistance > 0)
        {
            int i = 1;
            while (i <= _offsetDistance)
            {
                for (SequenceTag bc : barcodes5)
                {
                    processTag5(rec, sequenceToTest5, bc, i, matches5);
                }
                i++;

                if (matches5.size() > 0)
                    break;
            }
        }

        //then try patrial matches, only if no matches found
        if (matches5.isEmpty() && _deletionsAllowed > 0)
        {
            int i = 1;
            while (i <= _deletionsAllowed)
            {
                for (SequenceTag bc : barcodes5)
                {
                    processTag5(rec, sequenceToTest5, bc, -i, matches5);
                }
                i++;

                if (matches5.size() > 0)
                    break;
            }
        }

        //then 3' end
        String sequenceToTest3;
        if (_barcodesInReadHeader)
        {
            Pair<String, String> barcodes = extractBarcodeFromHeader(rec);
            if (barcodes == null || barcodes.second == null)
            {
                return;
            }

            //TODO: consider if this should be reversed?
            sequenceToTest3 = barcodes.second;
        }
        else
        {
            sequenceToTest3 = rec.getReadString();
        }

        //try default matching from end, without deletions first
        for (SequenceTag bc : barcodes3)
        {
            processTag3(rec, sequenceToTest3, bc, 0, matches3);
        }

        if (_offsetDistance > 0)
        {
            int i = 1;
            while (i <= _offsetDistance)
            {
                for (SequenceTag bc : barcodes3)
                {
                    processTag3(rec, sequenceToTest3, bc, i, matches3);
                }
                i++;

                if (matches3.size() > 0)
                    break;
            }
        }

        //then try patrial matches, only if no matches found
        if (matches3.isEmpty() && _deletionsAllowed > 0)
        {
            int i = 1;
            while (i <= _deletionsAllowed)
            {
                for (SequenceTag bc : barcodes3)
                {
                    processTag3(rec, sequenceToTest3, bc, -i, matches3);
                }
                i++;

                if (matches3.size() > 0)
                    break;
            }
        }
    }

    protected SequenceMatch findBestMatch(Map<Integer, Map<String, SequenceMatch>> matchesMap, Map<String, Integer> counter)
    {
        SequenceMatch bestMatch = null;
        if (matchesMap.size() > 0)
        {
            //get the match w/ the lowest edit distance
            Map<String, SequenceMatch> matches = matchesMap.get(matchesMap.keySet().iterator().next());
            if (matches.size() == 1)
            {
                bestMatch = matches.get(matches.keySet().iterator().next());
                Integer count = counter.get(bestMatch.getSequenceTag().getName());
                if (count == null)
                    count = 0;
                count++;

                counter.put(bestMatch.getSequenceTag().getName(), count);
            }
        }

        return bestMatch;
    }
    
    public File getDetailedLogFile()
    {
        return _createDetailedLog ? _detailLog : null;
    }

    public File getSummaryLogFile()
    {
        return _createSummaryLog ? _summaryLog : null;
    }

    protected class SequenceMatch
    {
        private final FastqRecord _rec;
        private final SequenceTag _tag;
        private final int _editDistance;
        private final int _offset;

        //NOTE: these are the coordinated used in substring(), which means start is 0-based and stop is 1-based
        private final int _start;
        private final int _stop;
        private final boolean _is5Prime;

        public SequenceMatch(SequenceTag tag, FastqRecord rec, boolean is5Prime, int editDistance, int offset, Integer start, Integer stop)
        {
            _tag = tag;
            _rec = rec;
            _is5Prime = is5Prime;
            _editDistance = editDistance;
            _offset = offset;
            _start = (start == null ? 0 : start);
            _stop = (stop == null ? rec.getReadString().length() : stop);
        }

        public FastqRecord getRec()
        {
            return _rec;
        }

        public int getEditDistance()
        {
            return _editDistance;
        }

        public int getOffset()
        {
            return _offset;
        }

        //0-based
        public int getStart()
        {
            return _start;
        }

        //1-based
        public int getStart1()
        {
            return _start + 1;
        }

        //1-based, since substring() expects that
        public int getStop()
        {
            return _stop;
        }

        public SequenceTag getSequenceTag()
        {
            return _tag;
        }

        public String getMoleculeEnd()
        {
            return _is5Prime ? "5'" : "3'";
        }
    }
}
