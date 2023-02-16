
/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.sequenceanalysis.pipeline;

import htsjdk.samtools.fastq.AsyncFastqWriter;
import htsjdk.samtools.fastq.BasicFastqWriter;
import htsjdk.samtools.fastq.FastqReader;
import htsjdk.samtools.fastq.FastqRecord;
import htsjdk.samtools.fastq.FastqWriter;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Formats;
import org.labkey.api.util.Pair;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * This is designed to parse the FASTQ files produced by a single run on an illumina instructment and produce one gzipped FASTQ
 * for each sample in that run.  Parsing that CSV file to obtain the sample list is upstream of this class.
 * It is designed to be called from a pipeline job, although it should not need to be.
 *
 * User: bbimber
 * Date: 4/18/12
 * Time: 11:35 AM
 */
public class IlluminaFastqSplitter<SampleIdType>
{
    private final String _outputPrefix;
    private final Map<String, SampleIdType> _illuminaIdToLocalIdMap;
    private File _destinationDir;
    private final List<File> _files;
    private Map<Pair<SampleIdType, Integer>, Pair<File, FastqWriter>> _fileMap;
    private Map<Pair<SampleIdType, Integer>, Integer> _sequenceTotals;
    private final Set<String> _skippedSampleId = new HashSet<>();
    private final Logger _logger;
    private static final FileType FASTQ_FILETYPE = new FileType(Arrays.asList("fastq", "fq"), "fastq", FileType.gzSupportLevel.SUPPORT_GZ);
    private boolean _outputGzip = false;

    public IlluminaFastqSplitter(@Nullable String outputPrefix, Map<String, SampleIdType> sampleMap, Logger logger, List<File> files)
    {
        _outputPrefix = outputPrefix;
        _illuminaIdToLocalIdMap = sampleMap;
        _files = files;
        _logger = logger;
    }

    // NOTE: sampleMap maps the sample index used internally within illumina (ie. the order of this sample in the CSV), to a sampleId used
    // by the callee
    public IlluminaFastqSplitter(String outputPrefix, Map<String, SampleIdType> sampleMap, Logger logger, String sourcePath, String fastqPrefix)
    {
        _outputPrefix = outputPrefix;
        _illuminaIdToLocalIdMap = sampleMap;
        _logger = logger;

        _files = inferIlluminaInputsFromPath(sourcePath, fastqPrefix);
    }

    // because ilumina sample CSV files do not provide a clear way to identify the FASTQ files/
    // this method accepts the CSV input and an optional FASTQ file prefix.  it will return any
    // FASTQ files or zipped FASTQs in the same folder as the CSV and filter using the prefix, if provided.
    public static List<File> inferIlluminaInputsFromPath(String path, @Nullable String fastqPrefix)
    {
        File folder = new File(path);
        List<File> _fastqFiles = new ArrayList<>();

        for (File f : folder.listFiles())
        {
            if (!FASTQ_FILETYPE.isType(f))
                continue;

            if (fastqPrefix != null && !f.getName().startsWith(fastqPrefix))
                continue;

            _fastqFiles.add(f);
        }

        return _fastqFiles;
    }

    //this returns a map connecting samples with output FASTQ files.
    // the key of the map is a pair where the first item is the sampleId and the second item indicated whether this file is the forward (1) or reverse (2) reads
    public Map<Pair<SampleIdType, Integer>, File> parseFastqFiles() throws PipelineJobException
    {
        _fileMap = new HashMap<>();
        _sequenceTotals = new HashMap<>();

        int _parsedReads;

        try
        {
            for (File f : _files)
            {
                try
                {
                    _logger.info("Beginning to parse file: " + f.getName());
                    File targetDir = _destinationDir == null ? f.getParentFile() : _destinationDir;

                    _parsedReads = 0;

                    try (FastqReader reader = new FastqReader(f))
                    {
                        while (reader.hasNext())
                        {
                            FastqRecord fq = reader.next();
                            String header = fq.getReadHeader();
                            IlluminaReadHeader parsedHeader = new IlluminaReadHeader(header);
                            String illuminaSampleId = null;
                            if (parsedHeader.getIndexSequenceString() != null)
                            {
                                illuminaSampleId = parsedHeader.getIndexSequenceString();
                            }
                            else
                            {
                                illuminaSampleId  = "S" + parsedHeader.getSampleNum();
                            }

                            int pairNumber = parsedHeader.getPairNumber();

                            FastqWriter writer = getWriter(illuminaSampleId, targetDir, pairNumber);
                            if (writer != null)
                            {
                                writer.write(fq);

                                _parsedReads++;
                                updateCount(illuminaSampleId, pairNumber);

                                if (0 == _parsedReads % 25000)
                                    logReadsProgress(_parsedReads);
                            }
                        }

                        if (0 != _parsedReads % 25000)
                            logReadsProgress(_parsedReads);

                        _logger.info("Finished parsing file: " + f.getName());
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }
        finally
        {
            for (Pair<File, FastqWriter> pair : _fileMap.values())
            {
                if (pair.second != null)
                    pair.second.close();
            }
        }

        Map<Pair<SampleIdType, Integer>, File> outputs = new HashMap<>();
        for (Pair<SampleIdType, Integer> key :_fileMap.keySet())
        {
            outputs.put(key, _fileMap.get(key).getKey());
        }

        return outputs;
    }

    private void logReadsProgress(int count)
    {
        String formattedCount = Formats.commaf0.format(count);
        _logger.info(formattedCount + " reads processed");
    }

    private void updateCount(String illuminaSampleId, int pairNumber)
    {
        if (_illuminaIdToLocalIdMap.containsKey(illuminaSampleId))
        {
            Pair<SampleIdType, Integer> key = Pair.of(_illuminaIdToLocalIdMap.get(illuminaSampleId), pairNumber);

            Integer total = _sequenceTotals.get(key);
            if (total == null)
                total = 0;

            total++;

            _sequenceTotals.put(key, total);
        }
    }

    private FastqWriter getWriter(String illuminaSampleId, File targetDir, int pairNumber) throws IOException
    {
        if (!_illuminaIdToLocalIdMap.containsKey(illuminaSampleId))
        {
            if (!_skippedSampleId.contains(illuminaSampleId))
            {
                _logger.warn("The CSV input does not contain sample info for a sample with index: " + illuminaSampleId);
                _skippedSampleId.add(illuminaSampleId);
            }
            return null;
        }

        // NOTE: sampleIdx is the index of the sample according to the Illumina CSV file, and the number assigned
        // by the illumina barcode callers.  Sample 0 always refers to control reads
        SampleIdType sampleId = _illuminaIdToLocalIdMap.get(illuminaSampleId);
        Pair<SampleIdType, Integer> sampleKey = Pair.of(sampleId, pairNumber);
        if (_fileMap.containsKey(sampleKey))
        {
            return _fileMap.get(sampleKey).getValue();
        }
        else
        {
            String suffix;
            if (Integer.valueOf(0).equals(sampleId))
            {
                suffix = "Control";
            }
            else if (sampleId == null || Integer.valueOf(-1).equals(sampleId))
            {
                suffix = "Undetermined";
            }
            else
            {
                suffix = String.valueOf(sampleId);
            }

            String name = (_outputPrefix == null ? "Reads" : _outputPrefix) + "-R" + pairNumber + "-" + suffix + ".fastq" + (_outputGzip ? ".gz" : "");
            File newFile = new File(targetDir, name);
            newFile.createNewFile();

            // Buffer the output so we aren't constantly writing through to file system. See issue 19633
            FastqWriter syncWriter;
            if (_outputGzip)
            {
                syncWriter = new BasicFastqWriter(new PrintStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(newFile), 64 * 1024))));
            }
            else
            {
                syncWriter = new BasicFastqWriter(new PrintStream(new BufferedOutputStream(new FileOutputStream(newFile), 64 * 1024)));
            }

            // Also use an async IO for better perf
            FastqWriter writer = new AsyncFastqWriter(syncWriter, AsyncFastqWriter.DEFAULT_QUEUE_SIZE);

            _fileMap.put(Pair.of(sampleId, pairNumber), Pair.of(newFile, writer));
            return writer;
        }
    }

    public void setOutputGzip(boolean outputGzip)
    {
        _outputGzip = outputGzip;
    }

    public File getDestinationDir()
    {
        return _destinationDir;
    }

    public void setDestinationDir(File destinationDir)
    {
        _destinationDir = destinationDir;
    }

    public Map<Pair<SampleIdType, Integer>, Integer> getReadCounts()
    {
        return _sequenceTotals;
    }

    public List<File> getFiles()
    {
        return _files;
    }

    public static IlluminaReadHeader parseHeader(String header)
    {
        try
        {
            return new IlluminaReadHeader(header);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }
}


