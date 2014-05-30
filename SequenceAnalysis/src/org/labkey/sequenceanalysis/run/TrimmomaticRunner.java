package org.labkey.sequenceanalysis.run;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequence.IlluminaReadHeader;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.pipeline.IlluminaFastqSplitter;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.util.FastqUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/28/12
 * Time: 11:06 PM
 */
public class TrimmomaticRunner extends AbstractRunner
{
    private String _adaptersFilePath;
    private Integer _seedMismatches = 2;
    private Integer _simpleClipThreshold = 0;
    private Integer _palindromeClipThreshold = 0;
    private Integer _windowSize = 0;
    private Integer _windowQual = 15;
    private Integer _leadingTrimQual = 0;
    private Integer _trailingTrimQual = 0;
    private Integer _cropLength = 0;
    private Integer _headCropLength = 0;
    private Integer _minLength = 0;
    private List<TrimStep> _steps = new ArrayList<>();
    private FastqUtils.FASTQ_ENCODING _encoding;

    public TrimmomaticRunner(Logger logger)
    {
        _logger = logger;
    }

    public String getOutputFilename(File file)
    {
        return null;
    }

    public List<Pair<File, File>> execute(File input, @Nullable File input2) throws PipelineJobException
    {
        _logger.info("Using Trimmomatic to preprocess files:");
        _logger.info("\t" + input.getPath());
        if (input2 != null)
            _logger.info("\t" + input2.getPath());

        doExecute(getWorkingDir(input), getParams(input, input2));

        List<File> files = getExpectedFilenames(input, input2);
        for (File output : files)
        {
            if (!output.exists())
            {
                throw new PipelineJobException("Output file could not be found: " + output.getPath());
            }
        }

        List<Pair<File, File>> outputs = new ArrayList<>();
        if (files.size() == 1)
        {

            if (FileUtils.sizeOf(files.get(0)) > 0)
                outputs.add(Pair.of(files.get(0), (File)null));
            else
            {
                if (_logger != null)
                    _logger.warn("File had a size of zero: " + files.get(0).getPath());
                if (!files.get((0)).delete() || files.get(0).exists())
                    throw new PipelineJobException("File exists: " + files.get(0).getPath());
            }
        }
        else
        {
            if (FileUtils.sizeOf(files.get(0)) == 0)
            {
                if (_logger != null)
                    _logger.warn("File had a size of zero, deleting: " + files.get(0).getPath());
                if (!files.get((0)).delete() || files.get(0).exists())
                    throw new PipelineJobException("File exists: " + files.get(0).getPath());
            }
            else if (FileUtils.sizeOf(files.get(2)) == 0)
            {
                if (_logger != null)
                    _logger.warn("File had a size of zero, deleting: " + files.get(2).getPath());
                if (!files.get((2)).delete() || files.get(2).exists())
                    throw new PipelineJobException("File exists: " + files.get(2).getPath());
            }
            else
            {
                outputs.add(Pair.of(files.get(0), files.get(2)));
            }

            //merge singles, add
            File unpaired1 = files.get(1);
            File unpaired2 = files.get(3);
            String basename = SequenceTaskHelper.getMinimalBaseName(unpaired1);
            File output = new File(unpaired1.getParentFile(), basename + "_unpaired.fastq");
            BufferedWriter writer = null;
            BufferedReader reader = null;
            try
            {
                writer = new BufferedWriter(new FileWriter(output));

                if (unpaired1.exists())
                {
                    reader = new BufferedReader(new FileReader(unpaired1));
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        writer.write(line + System.getProperty("line.separator"));
                    }
                    reader.close();
                }

                if (unpaired2.exists())
                {
                    reader = new BufferedReader(new FileReader(unpaired2));
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        writer.write(line + System.getProperty("line.separator"));
                    }
                    reader.close();
                }
                writer.close();

                if (output.exists())
                {
                    if (FileUtils.sizeOf(output) == 0)
                    {
                        if (_logger != null)
                            _logger.warn("File had a size of zero, deleting: " + output.getPath());
                        if (!output.delete() || output.exists())
                            throw new PipelineJobException("File exists: " + output.getPath());
                    }
                    else
                    {
                        outputs.add(Pair.of(output, (File)null));
                    }
                }
                if (!unpaired1.delete() || unpaired1.exists())
                    throw new PipelineJobException("File exists: " + unpaired1.getPath());

                if (!unpaired2.delete() || unpaired2.exists())
                    throw new PipelineJobException("File exists: " + unpaired2.getPath());
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e.getMessage());
            }
            finally
            {
                try
                {
                    if (writer != null)
                        writer.close();
                }
                catch (IOException e)
                {
                    //ignore
                }

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

        }

        if (_logger != null)
            generateSummaryText(getTrimlog(getWorkingDir(input)));

        return outputs;
    }

    private File getTrimlog(File workingDir)
    {
        return new File(workingDir, "trimLog.txt");
    }
    private List<String> getParams(File input, @Nullable File input2) throws PipelineJobException
    {
        try
        {
            List<String> params = new LinkedList<>();
            params.add("java");

            File jar = SequenceTaskHelper.getJarPath("trimmomatic/trimmomatic-0.22.jar");

            params.add("-classpath");
            params.add(jar.getPath());
            params.add("org.usadellab.trimmomatic.Trimmomatic" + (input2 != null ? "PE" : "SE"));
            File trimLog = getTrimlog(getWorkingDir(input));
            params.add("-trimlog");
            params.add(trimLog.getPath());

            if (_encoding != null)
            {
                params.add("-phred" +  _encoding.offset);
            }

            params.add(input.getPath());
            if (input2 != null)
                params.add(input2.getPath());

            List<File> files = getExpectedFilenames(input, input2);
            for (File f : files)
            {
                params.add(f.getPath());
            }

            for (TrimStep step : _steps)
            {
                params.add(step.getParams(this));
            }

            return params;
        }
        catch (FileNotFoundException e)
        {
            throw new PipelineJobException(e.getMessage());
        }
    }

    private List<File> getExpectedFilenames(File input1, @Nullable File input2)
    {
        List<File> fileNames = new ArrayList<>();
        String basename = FileUtil.getBaseName(input1);

        if (input2 != null)
        {
            String basename2 = FileUtil.getBaseName(input2);
            if (basename2.equalsIgnoreCase(basename))
                basename2 = basename2 + "-2";

            File workingDir = getWorkingDir(input1);
            File outputPaired1 = new File(workingDir, basename + ".trimmed.paired1.fastq");
            File outputUnpaired1 = new File(workingDir, basename + ".trimmed.unpaired1.fastq");
            File outputPaired2 = new File(workingDir, basename2 + ".trimmed.paired2.fastq");
            File outputUnpaired2 = new File(workingDir, basename2 + ".trimmed.unpaired2.fastq");
            fileNames.add(outputPaired1);
            fileNames.add(outputUnpaired1);
            fileNames.add(outputPaired2);
            fileNames.add(outputUnpaired2);
        }
        else
        {
            File output1 = new File(input1.getParentFile(), basename + ".trimmed.fastq");
            fileNames.add(output1);
        }

        return fileNames;
    }

    public void setMinLength(int minLength)
    {
        _minLength = minLength;
        _steps.add(TrimStep.MINLEN);
    }

    public void setSlidingWindow(int windowSize, int windowQual)
    {
        _windowSize = windowSize;
        _windowQual = windowQual;
        _steps.add(TrimStep.SLIDINGWINDOW);
    }

    public void setCrop(int cropLength)
    {
        _cropLength = cropLength;
        _steps.add(TrimStep.CROP);
    }

    public void setHeadCrop(int cropSize)
    {
        _headCropLength = cropSize;
        _steps.add(TrimStep.HEADCROP);
    }

    public void setLeadingTrim(int trimQual)
    {
        _leadingTrimQual = trimQual;
        _steps.add(TrimStep.LEADING);
    }

    public void setTrailingTrim(int trimQual)
    {
        _trailingTrimQual = trimQual;
        _steps.add(TrimStep.TRAILING);
    }

    public void setAdapterClipping(String fileName, int seedMismatches, int palindromeClipThreshold, int simpleClipThreshold)
    {
        _adaptersFilePath = fileName;
        _seedMismatches = seedMismatches;
        _palindromeClipThreshold = palindromeClipThreshold;
        _simpleClipThreshold = simpleClipThreshold;
        _steps.add(TrimStep.ILLUMINACLIP);
    }

    public void setEncoding(FastqUtils.FASTQ_ENCODING encoding)
    {
        _encoding = encoding;
    }

    private void generateSummaryText(File logFile) throws PipelineJobException
    {
        if (!logFile.exists())
        {
            _logger.warn("Did not find expected logFile: " + logFile.getPath());
        }

        BufferedReader reader = null;
        try
        {
            reader = new BufferedReader(new FileReader(logFile));
            String line;

            int totalInspected = 0;
            int totalReadsRetained = 0;
            int totalLength = 0;

            int totalReadsTrimmed = 0;
            int totalBasesTrimmed = 0;
            int totalDiscarded = 0;

            int totalReadsTrimmedF = 0;
            int totalBasesTrimmedF = 0;
            int totalDiscardedF = 0;
            int totalReadsRetainedF = 0;
            int totalLengthF = 0;
            int totalReadsTrimmedR = 0;
            int totalBasesTrimmedR = 0;
            int totalDiscardedR = 0;
            int totalReadsRetainedR = 0;
            int totalLengthR = 0;

            while ((line = reader.readLine()) != null)
            {
                String[] cells = line.split(" ");

                //NOTE: if the readname has spaces, we need to rebuild it after the split
                StringBuilder nameBuilder = new StringBuilder();
                int i = 0;
                String delim = "";
                while (i < (cells.length - 4))
                {
                    nameBuilder.append(delim).append(cells[i]);
                    delim = " ";
                    i++;
                }
                String name = nameBuilder.toString();

                Integer survivingLength = Integer.parseInt(cells[cells.length - 4]);
                Integer basesTrimmed = Integer.parseInt(cells[cells.length - 1]);

                totalInspected++;
                if (survivingLength == 0)
                {
                    totalDiscarded ++;
                }
                else
                {
                    totalReadsRetained++;
                    totalLength += survivingLength;
                }

                if (basesTrimmed > 0)
                {
                    totalBasesTrimmed += basesTrimmed;
                    totalReadsTrimmed++;
                }

                //infer metrics for paired end data
                //aatempt to parse illumina
                IlluminaReadHeader header = IlluminaFastqSplitter.parseHeader(name);
                if ((header != null && header.getPairNumber() == 1 ) || name.endsWith("/1"))
                {
                    if (survivingLength == 0)
                    {
                        totalDiscardedF++;
                    }
                    else
                    {
                        totalLengthF += survivingLength;
                        totalReadsRetainedF++;
                    }

                    totalBasesTrimmedF += basesTrimmed;
                    totalReadsTrimmedF++;
                }
                else if ((header != null && header.getPairNumber() == 2) || name.endsWith("/2"))
                {
                    if (survivingLength == 0)
                    {
                        totalDiscardedR++;
                    }
                    else
                    {
                        totalLengthR += survivingLength;
                        totalReadsRetainedR++;
                    }

                    totalBasesTrimmedR += basesTrimmed;
                    totalReadsTrimmedR++;
                }
            }

            Double avgBasesTrimmed = (double)totalBasesTrimmed / (double)totalReadsTrimmed;
            Double avgReadLength = (double)totalLength / (double)totalReadsRetained;
            _logger.info("Trimming summary:");
            _logger.info("\tTotal reads inspected: " + totalInspected);
            _logger.info("\tTotal reads discarded: " + totalDiscarded);
            _logger.info("\tTotal reads trimmed (includes discarded): " +  totalReadsTrimmed);
            _logger.info("\tAvg bases trimmed: " +  avgBasesTrimmed);
            _logger.info("\tTotal reads remaining: " + totalReadsRetained);
            _logger.info("\tAvg read length after trimming (excludes discarded reads): " + avgReadLength);

            if (totalBasesTrimmedF > 0)
            {
                Double avgBasesTrimmedF = (double)totalBasesTrimmedF / (double)totalReadsTrimmedF;
                Double avgReadLengthF = (double)totalLengthF / (double)totalReadsRetainedF;
                _logger.info("Forward read trimming summary: ");
                _logger.info("\tTotal forward reads discarded: " + totalDiscardedF);
                _logger.info("\tTotal forward reads trimmed (includes discarded): " +  totalReadsTrimmedF);
                _logger.info("\tAvg bases trimmed from forward reads: " +  avgBasesTrimmedF);
                _logger.info("\tTotal forward reads remaining: " + totalReadsRetainedF);
                _logger.info("\tAvg forward read length after trimming (excludes discarded reads): " + avgReadLengthF);
            }

            if (totalBasesTrimmedR > 0)
            {
                Double avgBasesTrimmedR = (double)totalBasesTrimmedR / (double)totalReadsTrimmedR;
                Double avgReadLengthR = (double)totalLengthR / (double)totalReadsRetainedR;
                _logger.info("Reverse read trimming summary: ");
                _logger.info("\tTotal reverse reads discarded: " + totalDiscardedR);
                _logger.info("\tTotal reverse reads trimmed (includes discarded): " +  totalReadsTrimmedR);
                _logger.info("\tAvg bases trimmed from reverse reads: " +  avgBasesTrimmedR);
                _logger.info("\tTotal reverse reads remaining: " + totalReadsRetainedR);
                _logger.info("\tAvg reverse read length after trimming (excludes discarded reads): " + avgReadLengthR);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                    logFile.delete();
                }
                catch (IOException e)
                {
                    //ignore
                }
            }
        }
    }

    public enum TrimStep
    {
        ILLUMINACLIP()
        {
            public String getParams(TrimmomaticRunner runner)
            {
                List<String> params = new ArrayList<>();
                params.add(runner._adaptersFilePath);
                params.add(runner._seedMismatches.toString());
                params.add(runner._palindromeClipThreshold.toString());
                params.add(runner._simpleClipThreshold.toString());

                return this.name() + ":" + StringUtils.join(params, ":");
            }
        },
        SLIDINGWINDOW()
        {
            public String getParams(TrimmomaticRunner runner)
            {
                List<String> params = new ArrayList<>();
                params.add(runner._windowSize.toString());
                params.add(runner._windowQual.toString());

                return this.name() + ":" + StringUtils.join(params, ":");
            }
        },
        LEADING()
        {
            public String getParams(TrimmomaticRunner runner)
            {
                return this.name() + ":" + runner._leadingTrimQual.toString();
            }
        },
        TRAILING()
        {
            public String getParams(TrimmomaticRunner runner)
            {
                return this.name() + ":" + runner._trailingTrimQual.toString();
            }
        },
        CROP()
        {
            public String getParams(TrimmomaticRunner runner)
            {
                return this.name() + ":" + runner._cropLength.toString();
            }
        },
        HEADCROP()
        {
            public String getParams(TrimmomaticRunner runner)
            {
                return this.name() + ":" + runner._headCropLength.toString();
            }
        },
        MINLEN()
        {
            public String getParams(TrimmomaticRunner runner)
            {
                return this.name() + ":" + runner._minLength.toString();
            }
        };

        TrimStep()
        {

        }

        abstract public String getParams(TrimmomaticRunner runner);
    }
}
