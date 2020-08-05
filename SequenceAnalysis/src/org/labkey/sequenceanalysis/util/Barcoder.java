package org.labkey.sequenceanalysis.util;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.fastq.AsyncFastqWriter;
import htsjdk.samtools.fastq.BasicFastqWriter;
import htsjdk.samtools.fastq.FastqReader;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import htsjdk.samtools.fastq.FastqWriterFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.sequenceanalysis.SequenceIntegrationTests;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.model.BarcodeModel;
import org.labkey.sequenceanalysis.model.SequenceTag;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * User: bimber
 * Date: 11/24/12
 * Time: 10:38 AM
 */
public class Barcoder extends AbstractSequenceMatcher
{
    private int _totalReads = 0;
    private Map<String, Integer> _readsetCounts;
    private Map<String, Integer> _otherMatches;
    private Map<String, BarcodeModel> _barcodes = new HashMap<>();
    private FastqWriterFactory _fastqWriterFactory = new FastqWriterFactory();
    private boolean _scanAll = false;

    public Barcoder(Logger logger)
    {
        _logger = logger;
    }

    public Set<File> demultiplexFile(File fastq, List<Readset> readsets, List<BarcodeModel> barcodes, @Nullable File outputDir) throws IOException
    {
        return demultiplexPair(Pair.of(fastq, null), readsets, barcodes, outputDir);
    }

    public Set<File> demultiplexFiles(List<File> fastqs, List<Readset> readsets, List<BarcodeModel> barcodes, @Nullable File outputDir) throws IOException
    {
        List<Pair<File, File>> fastqPairs = new ArrayList<>();
        for (File f : fastqs)
        {
            fastqPairs.add(Pair.of(f, null));
        }

        return demultiplexPairs(fastqPairs, readsets, barcodes, outputDir);
    }

    public Set<File> demultiplexPair(Pair<File, File> fastqs, List<Readset> readsets, List<BarcodeModel> barcodes, @Nullable File outputDir) throws IOException
    {
        return demultiplexPairs(Arrays.asList(fastqs), readsets, barcodes, outputDir);
    }

    public Set<File> demultiplexPairs(List<Pair<File, File>> fastqPairs, List<Readset> readsets, List<BarcodeModel> barcodes, @Nullable File outputDir) throws IOException
    {
        try
        {
            if (outputDir == null)
                outputDir = fastqPairs.get(0).first.getParentFile();

            _outputDir = outputDir;
            _outputDir.mkdirs();

            for (BarcodeModel m : barcodes)
            {
                _barcodes.put(m.getName(), m);
            }

            if (_createDetailedLog)
            {
                initDetailLog(fastqPairs.get(0).first);
            }

            if (_createSummaryLog)
            {
                initSummaryLog(fastqPairs.get(0).first);
            }

            _sequenceMatch5Counts = new TreeMap<>();
            _sequenceMatch3Counts = new TreeMap<>();
            _readsetCounts = new TreeMap<>();
            _otherMatches = new TreeMap<>();

            _fileMap = new HashMap<>();

            _logger.info("Scanning file for barcodes");
            _logger.info("\tMismatches tolerated: " + _editDistance);
            _logger.info("\tBarcode can be within " + _offsetDistance + " bases of the sequence end");
            _logger.info("\tDeletions tolerated (allows partial barcodes): " + _deletionsAllowed);
            if (_barcodesInReadHeader)
            {
                _logger.info("\tWill scan for barcodes in the read header, which assumes something upstream has already called barcodes.");
            }

            _logger.info("\tThe following barcode combinations will be used:");
            Map<String, SequenceTag> barcodes5 = new HashMap<>();
            Map<String, SequenceTag> barcodes3 = new HashMap<>();

            for (Readset rs : readsets)
            {
                _logger.info("\t\t" + rs.getName() + ": " + rs.getBarcode5() + (rs.getBarcode3() != null ? ", " + rs.getBarcode3() : ""));

                if (StringUtils.trimToNull(rs.getBarcode5()) != null)
                {
                    BarcodeModel model = _barcodes.get(rs.getBarcode5());
                    if (model == null)
                        throw new IllegalArgumentException("Readset uses a 5' barcode that was not supplied: [" + rs.getBarcode5() + "]");
                    barcodes5.put(model.getName(), model);
                }

                if (StringUtils.trimToNull(rs.getBarcode3()) != null)
                {
                    BarcodeModel model = _barcodes.get(rs.getBarcode3());
                    if (model == null)
                        throw new IllegalArgumentException("Readset uses a 3' barcode that was not supplied: [" + rs.getBarcode3() + "]");
                    barcodes3.put(model.getName(), model);
                }
            }

            if (_scanAll)
            {
                for (BarcodeModel barcode : _barcodes.values())
                {
                    barcodes5.put(barcode.getName(), barcode);
                    barcodes3.put(barcode.getName(), barcode);
                }
            }

            _logger.info("\tWill scan for a total of " + _barcodes.size() + " barcodes");

            for (Pair<File, File> fastqs : fastqPairs)
            {
                _logger.info("processing file(s): " + fastqs.first.getPath());
                if (fastqs.second != null)
                {
                    _logger.info("\tand: " + fastqs.second.getPath());
                }

                try (FastqReader reader1 = new FastqReader(fastqs.first); FastqReader reader2 = fastqs.second == null ? null : new FastqReader(fastqs.second))
                {
                    Iterator<FastqRecord> it = reader1.iterator();
                    Iterator<FastqRecord> it2 = null;
                    if (reader2 != null)
                    {
                        it2 = reader2.iterator();
                    }

                    long count = 0;
                    while (it.hasNext())
                    {
                        count++;
                        if (count % 100000 == 0)
                        {
                            _logger.info("\tprocessed " + NumberFormat.getInstance().format(count) + " reads");
                        }

                        if (reader2 == null)
                        {
                            FastqRecord rec = it.next();
                            processSequence(fastqs.first, rec, readsets, barcodes5.values(), barcodes3.values());
                        }
                        else
                        {
                            FastqRecord rec1 = it.next();
                            if (!it2.hasNext())
                            {
                                throw new IllegalArgumentException("Second FASTQ has fewer records than the primary FASTQ");
                            }

                            FastqRecord rec2 = it2.next();

                            processSequencePair(fastqs.first, fastqs.second, rec1, rec2, readsets, barcodes5.values(), barcodes3.values());
                        }
                    }

                    _logger.info("\tfinished " + NumberFormat.getInstance().format(count) + " reads");
                }
            }
        }
        finally
        {
            for (FastqWriter writer : _fileMap.values())
            {
                if (writer != null)
                    writer.close();
            }

            try
            {
                if (_detailLogWriter != null)
                    _detailLogWriter.close();
            }
            catch (IOException e)
            {
                //ignore
            }

            try
            {
                if (_summaryLogWriter != null)
                    _summaryLogWriter.close();
            }
            catch (IOException e)
            {
                //ignore
            }
        }

        //write summary
        _logger.info("5' Match Summary:");
        for (String key : _sequenceMatch5Counts.keySet())
        {
            _logger.info("\t" + key + ": " + _sequenceMatch5Counts.get(key));
        }

        if (_sequenceMatch5Counts.isEmpty())
        {
            _logger.info("\tNo matches found");
        }

        _logger.info("3' Match Summary:");
        for (String key : _sequenceMatch3Counts.keySet())
        {
            _logger.info("\t" + key + ": " + _sequenceMatch3Counts.get(key));
        }

        if (_sequenceMatch3Counts.isEmpty())
        {
            _logger.info("\tNo matches found");
        }

        _logger.info("Readset Match Summary:");
        for (String rs : _readsetCounts.keySet())
        {
            _logger.info("\t" + rs + ": " + _readsetCounts.get(rs) + " (" + (100.0 * (double)_readsetCounts.get((rs)) / _totalReads) + "%)");
        }

        if (_readsetCounts.isEmpty())
        {
            _logger.info("\tNo matches found");
        }

        if (_otherMatches.size() > 0)
        {
            _logger.info("Reads Not Matching A Readset:");
            for (String key : _otherMatches.keySet())
            {
                _logger.info("\t" + key + ": " + _otherMatches.get(key) + " (" + (100.0 * (double)_otherMatches.get((key)) / _totalReads) + "%)");
            }
        }

        return _fileMap.keySet();
    }

    protected void initDetailLog(File fastq)
    {
        try
        {
            _detailLog = getDetailedLogFile(fastq);
            _detailLogWriter = new CSVWriter(new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(_detailLog)), StringUtilsLabKey.DEFAULT_CHARSET)), '\t', CSVWriter.NO_QUOTE_CHARACTER, System.getProperty("line.separator"));
            _detailLogWriter.writeNext(new String[]{"Readname", "Barcode", "End of Molecule", "Edit Distance", "Offset", "Start", "Stop", "Barcode Sequence", "Target Sequence"});
        }
        catch (IOException e)
        {

        }
    }

    protected void initSummaryLog(File fastq)
    {
        try
        {
            _summaryLog = getSummaryLogFile(fastq);
            _summaryLogWriter = new CSVWriter(new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(_summaryLog)), StringUtilsLabKey.DEFAULT_CHARSET)), '\t', CSVWriter.NO_QUOTE_CHARACTER, System.getProperty("line.separator"));
            _summaryLogWriter.writeNext(new String[]{"Readname", "Readset", "5' Barcode", "3' Barcode", "5' Edit Distance", "3' Edit Distance", "Start", "Stop", "Original Length", "Final Length", "Trimmed Sequence"});
        }
        catch (IOException e)
        {

        }
    }

    private void writeSummaryLine(FastqRecord rec, @Nullable Readset rs, SequenceMatch match5, SequenceMatch match3, int start, int stop, int originalLength)
    {
        if (!_createSummaryLog)
            return;
        int length = originalLength;
        int newLength = stop - start + 1;
        _summaryLogWriter.writeNext(new String[]{rec.getReadHeader(), (rs == null ? "" : rs.getName()), (match5 == null ? "" : match5.getSequenceTag().getName()), (match3 == null ? "" : match3.getSequenceTag().getName()), (match5 == null ? "" : String.valueOf(match5.getEditDistance())), (match3 == null ? "" : String.valueOf(match3.getEditDistance())), String.valueOf(start), String.valueOf(stop), String.valueOf(length), String.valueOf(newLength), rec.getReadString()});
    }

    private File getDetailedLogFile(File fastq)
    {
        String basename = FileUtil.getBaseName(fastq.getName().replaceAll("\\.gz$", ""));
        return new File(_outputDir, basename + ".barcode-detailed.txt.gz");
    }

    private File getSummaryLogFile(File fastq)
    {
        String basename = FileUtil.getBaseName(fastq.getName().replaceAll("\\.gz$", ""));
        return new File(_outputDir, basename + ".barcode-summary.txt.gz");
    }

    private void processSequencePair(File fastq1, File fastq2, FastqRecord rec1, FastqRecord rec2, List<Readset> readsets, Collection<SequenceTag> barcodes5, Collection<SequenceTag> barcodes3) throws IOException
    {
        Map<Integer, Map<String, SequenceMatch>> forwardMatches5 = new TreeMap<>();
        Map<Integer, Map<String, SequenceMatch>> forwardMatches3 = new TreeMap<>();
        Map<Integer, Map<String, SequenceMatch>> reverseMatches5 = new TreeMap<>();
        Map<Integer, Map<String, SequenceMatch>> reverseMatches3 = new TreeMap<>();
        _totalReads++;

        scanForMatches(rec1, barcodes5, barcodes3, forwardMatches5, forwardMatches3);
        scanForMatches(rec2, barcodes3, barcodes5, reverseMatches5, reverseMatches3);

        //find the best match for each end:
        SequenceMatch forwardBc5 = findBestMatch(forwardMatches5, _sequenceMatch5Counts);
        SequenceMatch forwardBc3 = findBestMatch(forwardMatches3, _sequenceMatch3Counts);
        SequenceMatch reverseBc5 = findBestMatch(reverseMatches5, _sequenceMatch5Counts);
        SequenceMatch reverseBc3 = findBestMatch(reverseMatches3, _sequenceMatch3Counts);

        List<Readset> readsetMatches = new ArrayList<>();
        for (Readset model : readsets)
        {
            boolean forwardMatches = false;
            boolean reverseMatches = false;

            //either forward barcode is not null and matches the read's 5' barcode, or both are null
            if (model.getBarcode5() != null && forwardBc5 != null && model.getBarcode5().equals(forwardBc5.getSequenceTag().getName()))
            {
                forwardMatches = true;
            }
            else if (model.getBarcode5() == null && forwardBc5 == null)
            {
                forwardMatches = true;
            }

            //the 3' code is optional, since if we find the bc, this is probably read-through
            if (model.getBarcode3() != null && reverseBc5 != null && model.getBarcode3().equals(reverseBc5.getSequenceTag().getName()))
            {
                reverseMatches = true;
            }
            else if (model.getBarcode3() == null && reverseBc5 == null)
            {
                reverseMatches = true;
            }

            if (forwardMatches && reverseMatches)
            {
                readsetMatches.add(model);
            }
        }

        if (readsetMatches.size() == 1)
        {
            addMatchingRead(fastq1, rec1, readsetMatches.get(0), forwardBc5, forwardBc3, true);
            addMatchingRead(fastq2, rec2, readsetMatches.get(0), reverseBc5, reverseBc3, false);
        }
        else
        {
            addMatchingRead(fastq1, rec1, null, forwardBc5, forwardBc3, true);
            addMatchingRead(fastq2, rec2, null, reverseBc5, reverseBc3, false);
        }
    }

    private void processSequence(File fastq, FastqRecord rec, List<Readset> readsets, Collection<SequenceTag> barcodes5, Collection<SequenceTag> barcodes3) throws IOException
    {
        Map<Integer, Map<String, SequenceMatch>> matches5 = new TreeMap<>();
        Map<Integer, Map<String, SequenceMatch>> matches3 = new TreeMap<>();
        scanForMatches(rec, barcodes5, barcodes3, matches5, matches3);

        //find the best match for each end:
        SequenceMatch bc5 = findBestMatch(matches5, _sequenceMatch5Counts);
        SequenceMatch bc3 = findBestMatch(matches3, _sequenceMatch3Counts);
        _totalReads++;

        boolean found = false;
        for (Readset model : readsets)
        {
            if (model.getBarcode5() != null)
            {
                if (bc5 == null)
                    continue;

                if (!model.getBarcode5().equals(bc5.getSequenceTag().getName()))
                    continue;
            }

            if (model.getBarcode3() != null)
            {
                if (bc3 == null)
                    continue;

                if (!model.getBarcode3().equals(bc3.getSequenceTag().getName()))
                    continue;
            }

            addMatchingRead(fastq, rec, model, bc5, bc3, true);
            found = true;
        }

        if (!found)
        {
            addMatchingRead(fastq, rec, null, bc5, bc3, true);
        }
    }

    private void addMatchingRead(File fastq, FastqRecord rec, @Nullable Readset rs, @Nullable SequenceMatch match5, @Nullable SequenceMatch match3, boolean includeInCounts) throws IOException
    {
        //a read would be excluded from counts if it is the second mate and the first-mate is already being counted
        if (includeInCounts)
        {
            if (rs != null)
            {
                Integer count = _readsetCounts.get(rs.getName());
                if (count == null)
                    count = 0;
                count++;

                _readsetCounts.put(rs.getName(), count);
            }
            else
            {
                String key;
                if (match5 != null && match3 != null)
                {
                    key = match5.getSequenceTag().getName() + " / " + match3.getSequenceTag().getName();
                }
                else if (match5 != null)
                {
                    key = match5.getSequenceTag().getName() + " / No Tag";
                }
                else if (match3 != null)
                {
                    key = "No Tag / " + match3.getSequenceTag().getName();
                }
                else
                {
                    key = "No Matches";
                }

                Integer count = _otherMatches.get(key);
                if (count == null)
                    count = 0;

                count++;
                _otherMatches.put(key, count);
            }
        }

        int start = (match5 == null ? 0 : match5.getStart());
        if (start < 0)
        {
            throw new IOException("Error with read: " + rec.getReadHeader() + ", length: " + rec.getReadString().length() + ".  5' barcode: " + match5.getSequenceTag().getSequence() + ", " + start + ", offset: " + match5.getOffset() + ", edit distance: " + match5.getEditDistance());
        }

        int stop = (match3 == null ? rec.getReadString().length() : match3.getStop());
        if (stop > rec.getReadString().length())
        {
            throw new IOException("Error with read: " + rec.getReadHeader() + ", length: " + rec.getReadString().length() + ".  3' barcode: " + match3.getSequenceTag().getSequence() + ", " + start + "-" + stop + ", offset: " + match3.getOffset() + ", edit distance: " + match3.getEditDistance());
        }

        //NOTE: always compressed now
        File output = new File(_outputDir, getOutputFilename(fastq, rs));
        FastqWriter writer = _fileMap.get(output);
        if (writer == null)
        {
            writer = new AsyncFastqWriter(new BasicFastqWriter(new PrintStream(new GZIPOutputStream(new FileOutputStream(output)))), AsyncFastqWriter.DEFAULT_QUEUE_SIZE);
            _fileMap.put(output, writer);
        }

        FastqRecord trimmed = new FastqRecord(rec.getReadHeader(), rec.getReadString().substring(start, stop), (rec.getBaseQualityHeader() == null ? "" : rec.getBaseQualityHeader()), rec.getBaseQualityString().substring(start, stop));
        writer.write(trimmed);

        if (_createSummaryLog)
        {
            writeSummaryLine(trimmed, rs, match5, match3, (start + 1), stop, rec.getReadString().length());
        }
    }

    public String getOutputFilename(File fastq, @Nullable Readset rs)
    {
        String basename = FileUtil.getBaseName(fastq.getName().replaceAll("\\.gz$", ""));
        if (rs != null)
        {
            if (rs.getBarcode3() == null && rs.getBarcode5() != null)
                return basename + "_" + rs.getBarcode5() + ".fastq.gz";
            else if (rs.getBarcode3() != null && rs.getBarcode5() != null)
                return basename + "_" + rs.getBarcode5() + "_" + rs.getBarcode3() + ".fastq.gz";
            else
                throw new IllegalArgumentException("Improper readset for barcoding.  Must have either a 5' barcode or 5' + 3' barcodes.  Readset name was: " + rs.getName());
        }
        else
        {
            return basename + "_unknowns.fastq.gz";
        }
    }

    public boolean isScanAll()
    {
        return _scanAll;
    }

    public void setScanAll(boolean scanAll)
    {
        _scanAll = scanAll;
    }

    public static class TestCase extends Assert
    {
        SequenceIntegrationTests _helper = SequenceIntegrationTests.get();
        File _input = null;

        @Before
        public void setUp()
        {
            FastqWriter writer = null;

            try
            {
                //create input data
                _input = File.createTempFile("barcodeTest", ".fastq");
                if (_input.exists())
                    _input.delete();

                _input.createNewFile();

                FastqWriterFactory fact = new FastqWriterFactory();
                fact.setUseAsyncIo(true);
                writer = fact.newWriter(_input);

                for (FastqRecord fq : _helper.getBarcodedFastqData())
                {
                    writer.write(fq);
                }
            }
            catch (IOException e)
            {
                throw new AssertionError("Unable to create temp file");
            }
            finally
            {
                if (writer != null)
                    writer.close();
            }
        }

        @Test
        public void test() throws Exception
        {
            //TODO: can I make a fake logger?
            Logger log = LogManager.getLogger(Barcoder.class);
            testBarcoder(log, 0, 0, 0);
            testBarcoder(log, 1, 0, 0);
            testBarcoder(log, 1, 1, 0);
            testBarcoder(log, 1, 1, 1);
        }

        //TODO: write this test case
//        @Test
//        public void test2() throws Exception
//        {
//            //tag: CCGGTG
//
//            //@M00370:50:000000000-AE8A2:1:1101:15970:1641 1:N:0:0
//            //ATGAGCGATGCAGTGCAGTGGCATGATCATGGCTCACTGCAAGATCGGAAGAGCGGTTCAGCAGGAATGCCGAGACCGATGTCGTAGGCCGTCTTCTGCTTGAAAAAAAAATGTTCTTCGATAGCTTATTGTTTCATGAGGAATTGAGCCTTCGCCTTTGTAGTTACTGATTGCTTGGTTTTGGCTATGCTATGTCTTATCTATGTGGCTTTGTTTTCACATACCCAACTTGCCCGGACTCAACCCGGTG
//
//            //@M00370:50:000000000-AE8A2:1:1101:15970:1641 2:N:0:0
//            //TGCAGTGAGCCATGATCATGCCACTGCACTGCATCGCCCAT
//        }

        private void testBarcoder(Logger log, int editDistance, int offset, int deletions) throws IOException
        {
            Barcoder bc = new Barcoder(log);
            bc.setCreateDetailedLog(true);
            bc.setCreateSummaryLog(true);

            bc.setEditDistance(editDistance);
            bc.setOffsetDistance(offset);
            bc.setDeletionsAllowed(deletions);

            List<Readset> readsets = getReadsets();
            Set<BarcodeModel> barcodes5 = new HashSet<>();
            Set<BarcodeModel> barcodes3 = new HashSet<>();
            for (Readset rs : getReadsets())
            {
                //we assume this is run w/ full access to the DB
                if (rs.getBarcode5() != null)
                    barcodes5.add(BarcodeModel.getByName(rs.getBarcode5()));

                if (rs.getBarcode3() != null)
                    barcodes3.add(BarcodeModel.getByName(rs.getBarcode3()));
            }

            List<BarcodeModel> allBarcodes = new ArrayList<>();
            allBarcodes.addAll(barcodes5);
            allBarcodes.addAll(barcodes3);

            Set<File> files = bc.demultiplexFile(_input, readsets, allBarcodes, null);
            File detailLog = bc.getDetailedLogFile(_input);
            File summaryLog = bc.getSummaryLogFile(_input);
            Map<String, Integer> readMap = new HashMap<>();

            Assert.assertTrue("Detailed log not found", detailLog.exists());
            detailLog.delete();

            try (CSVReader reader = new CSVReader(Readers.getReader(new GZIPInputStream(new FileInputStream(summaryLog))), '\t'))
            {
                String[] line;
                int lineNum = 0;
                while ((line = reader.readNext()) != null)
                {
                    lineNum++;

                    if (lineNum == 1)
                        continue;

                    String[] readname = line[0].split("_");
                    String mid5 = readname[0].split("-")[0];
                    String mid3 = readname[1].split("-")[0];

                    Integer editDist5 = Integer.parseInt(readname[0].split("-")[1].substring(0, 1));
                    Integer offset5 = Integer.parseInt(readname[0].split("-")[1].substring(1, 2));
                    Integer deletion5 = Integer.parseInt(readname[0].split("-")[1].substring(2, 3));

                    Integer editDist3 = Integer.parseInt(readname[1].split("-")[1].substring(0, 1));
                    Integer offset3 = Integer.parseInt(readname[1].split("-")[1].substring(1, 2));
                    Integer deletion3 = Integer.parseInt(readname[1].split("-")[1].substring(2, 3));

                    boolean shouldFindMid5 = false;
                    if (editDist5 <= editDistance && offset5 <= offset && deletion5 <= deletions)
                        shouldFindMid5 = true;

                    boolean shouldFindMid3 = false;
                    if (editDist3 <= editDistance && offset3 <= offset && deletion3 <= deletions)
                        shouldFindMid3 = true;

                    Assert.assertEquals("Incorrect 5' barcode on line: " + lineNum, (shouldFindMid5 ? mid5 : ""), line[2]);
                    Assert.assertEquals("Incorrect 3' barcode on line: " + lineNum, (shouldFindMid3 ? mid3 : ""), line[3]);

                    Assert.assertEquals("Incorrect 5' editDistance on line: " + lineNum, (shouldFindMid5 ? editDist5.toString() : ""), line[4]);
                    Assert.assertEquals("Incorrect 3' editDistance on line: " + lineNum, (shouldFindMid3 ? editDist3.toString() : ""), line[5]);

                    Integer barcodeLength = 10; //all barcodes are 10bp in this test

                    Integer start = Integer.parseInt(line[6]);
                    Integer stop = Integer.parseInt(line[7]);
                    Integer originalLength = Integer.parseInt(line[8]);
                    Integer finalLength = Integer.parseInt(line[9]);

                    Integer expectedStart = shouldFindMid5 ? (barcodeLength + 1 + offset5 - deletion5) : 1;
                    Integer expectedStop = shouldFindMid3 ? (originalLength - barcodeLength - offset3 + deletion3) : originalLength;
                    Integer expectedFinalLength = expectedStop - expectedStart + 1;

                    Assert.assertEquals("Incorrect start on line: " + lineNum, expectedStart, start);
                    Assert.assertEquals("Incorrect stop on line: " + lineNum, expectedStop, stop);
                    Assert.assertEquals("Incorrect final length on line: " + lineNum, expectedFinalLength, finalLength);

                    String key;
                    if (!"".equals(line[1]))
                    {
                        StringBuilder sb = new StringBuilder();
                        if (shouldFindMid5)
                            sb.append(mid5);

                        if (shouldFindMid5 && shouldFindMid3)
                            sb.append("_");

                        if (shouldFindMid3)
                            sb.append(mid3);

                        key = sb.toString();
                    }
                    else
                        key = "unknowns";

                    Integer count = readMap.get(key);
                    if (count == null)
                        count = 0;
                    count++;

                    readMap.put(key, count);
                }
            }

            summaryLog.delete();

            for (File f : files)
            {
                FastqReader fqReader = null;
                try
                {
                    fqReader = new FastqReader(f);

                    int count = 0;
                    while (fqReader.hasNext())
                    {
                        FastqRecord rec = fqReader.next();
                        count++;
                    }

                    String key;
                    String basename = FileUtil.getBaseName(f.getName().replaceAll("\\.gz$", ""));
                    String[] tokens = basename.split("_");
                    if (tokens.length == 3)
                        key = tokens[1] + "_" +  tokens[2];
                    else
                        key = tokens[1];

                    Assert.assertTrue("key missing: " + key, readMap.containsKey(key));
                    int expectedCount = readMap.get(key);

                    Assert.assertEquals("Incorrect read number for file: " + f.getName(), expectedCount, count);
                }
                finally
                {
                    if (fqReader != null)
                        fqReader.close();
                }

                f.delete();
            }
        }

        private BarcodeModel[] getBarcodes()
        {
            List<String> names = new ArrayList<>();
            for (Readset rs : getReadsets())
            {
                if (rs.getBarcode5() != null)
                    names.add(rs.getBarcode5());

                if (rs.getBarcode3() != null)
                    names.add(rs.getBarcode3());
            }

            return BarcodeModel.getByNames(names.toArray(new String[names.size()]));
        }

        private List<Readset> getReadsets()
        {
            List<Readset> readsets = new ArrayList<>();

            SequenceReadsetImpl rs1 = new SequenceReadsetImpl();
            rs1.setName("Readset1");
            rs1.setBarcode5("MID002");
            rs1.setBarcode3("MID003");
            readsets.add(rs1);

            SequenceReadsetImpl rs2 = new SequenceReadsetImpl();
            rs2.setName("Readset2");
            rs2.setBarcode5("MID002");
            rs2.setBarcode3("MID002");
            readsets.add(rs2);

            SequenceReadsetImpl rs3 = new SequenceReadsetImpl();
            rs3.setName("Readset3");
            rs3.setBarcode5("MID002");
            rs3.setBarcode3("MID001");
            readsets.add(rs3);

            SequenceReadsetImpl rs4 = new SequenceReadsetImpl();
            rs4.setName("Readset4");
            rs4.setBarcode5("MID001");
            rs4.setBarcode3("MID003");
            readsets.add(rs4);

            SequenceReadsetImpl rs5 = new SequenceReadsetImpl();
            rs5.setName("Readset5");
            rs5.setBarcode5("MID003");
            rs5.setBarcode3("MID004");
            readsets.add(rs5);

            return readsets;
        }

        @After
        public void cleanup()
        {
            if (_input != null && _input.exists())
                _input.delete();
        }
    }
}
