package org.labkey.sequenceanalysis.util;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import net.sf.picard.fastq.FastqReader;
import net.sf.picard.fastq.FastqRecord;
import net.sf.picard.fastq.FastqWriter;
import net.sf.picard.fastq.FastqWriterFactory;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.TestHelper;
import org.labkey.sequenceanalysis.model.BarcodeModel;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;
import org.labkey.sequenceanalysis.model.SequenceTag;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * User: bimber
 * Date: 11/24/12
 * Time: 10:38 AM
 */
public class Barcoder extends AbstractSequenceMatcher
{
    private Map<String, Integer> _readsetCounts;
    private Map<String, Integer> _otherMatches;
    private Map<String, BarcodeModel> _barcodes = new HashMap<>();
    private FastqWriterFactory _fastqWriterFactory = new FastqWriterFactory();
    private boolean _scanAll = false;

    public Barcoder(Logger logger)
    {
        _logger = logger;
    }

    public Set<File> execute(File fastq, List<ReadsetModel> readsets, List<BarcodeModel> barcodes, @Nullable File outputDir)
    {
        if (outputDir == null)
            outputDir = fastq.getParentFile();

        _outputDir = outputDir;
        _outputDir.mkdirs();

        for (BarcodeModel m : barcodes)
        {
            _barcodes.put(m.getName(), m);
        }

        FastqReader reader = null;

        if (_createDetailedLog)
            initDetailLog(fastq);
        if (_createSummaryLog)
            initSummaryLog(fastq);

        _sequenceMatch5Counts =  new TreeMap<>();
        _sequenceMatch3Counts =  new TreeMap<>();
        _readsetCounts = new TreeMap<>();
        _otherMatches = new TreeMap<>();

        _fileMap = new HashMap<>();

        _logger.info("Scanning file for barcodes: " + fastq.getPath());
        _logger.info("\tMismatches tolerated: " + _editDistance);
        _logger.info("\tBarcode can be within " + _offsetDistance + " bases of the sequence end");
        _logger.info("\tDeletions tolerated (allows partial barcodes): " + _deletionsAllowed);

        _logger.info("\tThe following barcode combinations will be used:");
        Map<String, SequenceTag> barcodes5 = new HashMap<>();
        Map<String, SequenceTag> barcodes3 = new HashMap<>();

        for (ReadsetModel rs : readsets)
        {
            _logger.info("\t\t" + rs.getName() + ": " + rs.getMid5() + (rs.getMid3() != null ? ", " + rs.getMid3() : ""));

            if (rs.getMid5() != null)
            {
                BarcodeModel model = _barcodes.get(rs.getMid5());
                if (model == null)
                    throw new IllegalArgumentException("Readset uses a 5' barcode that was not supplied: [" + rs.getMid5() + "]");
                barcodes5.put(model.getName(), model);
            }

            if (rs.getMid3() != null)
            {
                BarcodeModel model = _barcodes.get(rs.getMid3());
                if (model == null)
                    throw new IllegalArgumentException("Readset uses a 3' barcode that was not supplied: [" + rs.getMid3() + "]");
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

        try
        {
            reader = new FastqReader(fastq);
            Iterator<FastqRecord> it = reader.iterator();
            while (it.hasNext())
            {
                FastqRecord rec = it.next();
                processSequence(fastq, rec, readsets, barcodes5.values(), barcodes3.values());
            }
        }
        finally
        {
            if (reader != null)
                reader.close();

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

        _logger.info("3' Match Summary:");
        for (String key : _sequenceMatch3Counts.keySet())
        {
            _logger.info("\t" + key + ": " + _sequenceMatch3Counts.get(key));
        }

        _logger.info("Readset Match Summary:");
        for (String rs : _readsetCounts.keySet())
        {
            _logger.info("\t" + rs + ": " + _readsetCounts.get(rs));
        }

        if (_otherMatches.size() > 0)
        {
            _logger.info("Reads Not Matching A Readset:");
            for (String key : _otherMatches.keySet())
            {
                _logger.info("\t" + key + ": " + _otherMatches.get(key));
            }
        }

        return _fileMap.keySet();
    }

    protected void initDetailLog(File fastq)
    {
        try
        {
            _detailLog = getDetailedLogFile(fastq);
            _detailLogWriter = new CSVWriter(new FileWriter(_detailLog), '\t', CSVWriter.NO_QUOTE_CHARACTER, System.getProperty("line.separator"));
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
            _summaryLogWriter = new CSVWriter(new FileWriter(_summaryLog), '\t', CSVWriter.NO_QUOTE_CHARACTER, System.getProperty("line.separator"));
            _summaryLogWriter.writeNext(new String[]{"Readname", "Readset", "5' Barcode", "3' Barcode", "5' Edit Distance", "3' Edit Distance", "Start", "Stop", "Original Length", "Final Length", "Trimmed Sequence"});
        }
        catch (IOException e)
        {

        }
    }

    private void writeSummaryLine(FastqRecord rec, @Nullable ReadsetModel rs, SequenceMatch match5, SequenceMatch match3, int start, int stop, int originalLength)
    {
        if (!_createSummaryLog)
            return;
        int length = originalLength;
        int newLength = stop - start + 1;
        _summaryLogWriter.writeNext(new String[]{rec.getReadHeader(), (rs == null ? "" : rs.getName()), (match5 == null ? "" : match5.getSequenceTag().getName()), (match3 == null ? "" : match3.getSequenceTag().getName()), (match5 == null ? "" : String.valueOf(match5.getEditDistance())), (match3 == null ? "" : String.valueOf(match3.getEditDistance())), String.valueOf(start), String.valueOf(stop), String.valueOf(length), String.valueOf(newLength), rec.getReadString()});
    }

    private File getDetailedLogFile(File fastq)
    {
        String basename = FileUtil.getBaseName(fastq);
        return new File(_outputDir, basename + ".barcode-detailed.txt");
    }

    private File getSummaryLogFile(File fastq)
    {
        String basename = FileUtil.getBaseName(fastq);
        return new File(_outputDir, basename + ".barcode-summary.txt");
    }

    private void processSequence(File fastq, FastqRecord rec, List<ReadsetModel> readsets, Collection<SequenceTag> barcodes5, Collection<SequenceTag> barcodes3)
    {
        Map<Integer, Map<String, SequenceMatch>> matches5 = new TreeMap<>();
        Map<Integer, Map<String, SequenceMatch>> matches3 = new TreeMap<>();
        scanForMatches(fastq, rec, barcodes5, barcodes3, matches5, matches3);

        //find the best match for each end:
        SequenceMatch bc5 = findBestMatch(matches5, _sequenceMatch5Counts);
        SequenceMatch bc3 = findBestMatch(matches3, _sequenceMatch3Counts);

        boolean found = false;
        for (ReadsetModel model : readsets)
        {
            if (model.getMid5() != null)
            {
                if (bc5 == null)
                    continue;

                if (!model.getMid5().equals(bc5.getSequenceTag().getName()))
                    continue;
            }

            if (model.getMid3() != null)
            {
                if (bc3 == null)
                    continue;

                if (!model.getMid3().equals(bc3.getSequenceTag().getName()))
                    continue;
            }

            addMatchingRead(fastq, rec, model, bc5, bc3);
            found = true;
        }

        if (!found)
        {
            addMatchingRead(fastq, rec, null, bc5, bc3);
        }
    }

    private void addMatchingRead(File fastq, FastqRecord rec, @Nullable ReadsetModel rs, @Nullable SequenceMatch match5, @Nullable SequenceMatch match3)
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

        int start = (match5 == null ? 0 : match5.getStart());
        int stop = (match3 == null ? rec.getReadString().length() : match3.getStop());

        File output = new File(_outputDir, getOutputFilename(fastq, rs));
        FastqWriter writer = _fileMap.get(output);
        if (writer == null)
        {
            writer = _fastqWriterFactory.newWriter(output);
            _fileMap.put(output, writer);
        }

        FastqRecord trimmed = new FastqRecord(rec.getReadHeader(), rec.getReadString().substring(start, stop), (rec.getBaseQualityHeader() == null ? "" : rec.getBaseQualityHeader()), rec.getBaseQualityString().substring(start, stop));
        writer.write(trimmed);

        if (_createSummaryLog)
        {
            writeSummaryLine(trimmed, rs, match5, match3, (start + 1), stop, rec.getReadString().length());
        }
    }

    private String getOutputFilename(File fastq, @Nullable ReadsetModel rs)
    {
        String basename = FileUtil.getBaseName(fastq);
        if (rs != null)
        {
            if (rs.getMid3() == null && rs.getMid5() != null)
                return basename + "_" + rs.getMid5() + ".fastq";
            else if (rs.getMid3() != null && rs.getMid5() != null)
                return basename + "_" + rs.getMid5() + "_" + rs.getMid3() + ".fastq";
            else
                throw new IllegalArgumentException("Improper readset for barcoding.  Must have either a 5' barcode or 5' + 3' barcodes.  Readset name was: " + rs.getName());
        }
        else
        {
            return basename + "_unknowns.fastq";
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
        TestHelper _helper = TestHelper.get();
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
            Logger log = Logger.getLogger(Barcoder.class);
            testBarcoder(log, 0, 0, 0);
            testBarcoder(log, 1, 0, 0);
            testBarcoder(log, 1, 1, 0);
            testBarcoder(log, 1, 1, 1);
        }

        private void testBarcoder(Logger log, int editDistance, int offset, int deletions)
        {
            Barcoder bc = new Barcoder(log);
            bc.setCreateDetailedLog(true);
            bc.setCreateSummaryLog(true);

            bc.setEditDistance(editDistance);
            bc.setOffsetDistance(offset);
            bc.setDeletionsAllowed(deletions);

            List<ReadsetModel> readsets = getReadsets();
            Set<BarcodeModel> barcodes5 = new HashSet<>();
            Set<BarcodeModel> barcodes3 = new HashSet<>();
            for (ReadsetModel rs : getReadsets())
            {
                //we assume this is run w/ full access to the DB
                if (rs.getMid5() != null)
                    barcodes5.add(BarcodeModel.getByName(rs.getMid5()));

                if (rs.getMid3() != null)
                    barcodes3.add(BarcodeModel.getByName(rs.getMid3()));
            }

            List<BarcodeModel> allBarcodes = new ArrayList<>();
            allBarcodes.addAll(barcodes5);
            allBarcodes.addAll(barcodes3);

            Set<File> files = bc.execute(_input, readsets, allBarcodes, null);
            File detailLog = bc.getDetailedLogFile(_input);
            File summaryLog = bc.getSummaryLogFile(_input);
            Map<String, Integer> readMap = new HashMap<>();

            Assert.assertTrue("Detailed log not found", detailLog.exists());
            detailLog.delete();

            CSVReader reader = null;
            try
            {
                reader = new CSVReader(new FileReader(summaryLog), '\t');
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
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            finally
            {
                try
                {
                    if (reader != null)
                        reader.close();
                }
                catch (IOException e)
                {
                    //ignore
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
                    String[] tokens = FileUtil.getBaseName(f.getName()).split("_");
                    if (tokens.length == 3)
                        key = tokens[1] + "_" +  tokens[2];
                    else
                        key = tokens[1];

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
            for (ReadsetModel rs : getReadsets())
            {
                if (rs.getMid5() != null)
                    names.add(rs.getMid5());

                if (rs.getMid3() != null)
                    names.add(rs.getMid3());
            }

            return BarcodeModel.getByNames(names.toArray(new String[names.size()]));
        }

        private List<ReadsetModel> getReadsets()
        {
            List<ReadsetModel> readsets = new ArrayList<>();

            ReadsetModel rs1 = new ReadsetModel();
            rs1.setName("Readset1");
            rs1.setMid5("MID002");
            rs1.setMid3("MID003");
            readsets.add(rs1);

            ReadsetModel rs2 = new ReadsetModel();
            rs2.setName("Readset2");
            rs2.setMid5("MID002");
            rs2.setMid3("MID002");
            readsets.add(rs2);

            ReadsetModel rs3 = new ReadsetModel();
            rs3.setName("Readset3");
            rs3.setMid5("MID002");
            rs3.setMid3("MID001");
            readsets.add(rs3);

            ReadsetModel rs4 = new ReadsetModel();
            rs4.setName("Readset4");
            rs4.setMid5("MID001");
            rs4.setMid3("MID003");
            readsets.add(rs4);

            ReadsetModel rs5 = new ReadsetModel();
            rs5.setName("Readset5");
            rs5.setMid5("MID003");
            rs5.setMid3("MID004");
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
