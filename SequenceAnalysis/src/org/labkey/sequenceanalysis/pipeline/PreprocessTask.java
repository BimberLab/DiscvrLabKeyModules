package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.model.AdapterModel;
import org.labkey.sequenceanalysis.run.DownsampleSamRunner;
import org.labkey.sequenceanalysis.run.FastqToSamRunner;
import org.labkey.sequenceanalysis.run.SamToFastqRunner;
import org.labkey.sequenceanalysis.run.TrimmomaticRunner;
import org.labkey.sequenceanalysis.util.FastqUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/28/12
 * Time: 9:52 PM
 */
public class PreprocessTask
{
    SequenceTaskHelper _helper;
    SequencePipelineSettings _settings;
    WorkDirectory _wd;
    PipelineJob _job;

    public PreprocessTask(PipelineJob job, WorkDirectory wd, SequenceTaskHelper helper)
    {
        _job = job;
        _wd = wd;
        _helper = helper;
        _settings = _helper.getSettings();
    }

    public List<Pair<File, File>> processFiles(File inputFile, @Nullable File inputFile2) throws PipelineJobException
    {
        _job.getLogger().info("Beginning preprocessing of file: " + inputFile.getPath());
        if (inputFile2 != null)
            _job.getLogger().info("and file: " + inputFile2.getPath());

        List<Pair<File, File>> outputs = new ArrayList<>();
        Pair<File, File> currentFiles = Pair.of(inputFile, inputFile2);

        String basename = FileUtil.getBaseName(inputFile);
        String basename2 = null;
        if (inputFile2 != null)
            basename2 = FileUtil.getBaseName(inputFile2);

        File workingDir = new File(_wd.getDir(), SequenceTaskHelper.PREPROCESSING_SUBFOLDER_NAME + "/" + basename);
        if (!workingDir.exists())
            workingDir.mkdirs();

        //infer encoding
        FastqUtils.FASTQ_ENCODING encoding = FastqUtils.inferFastqEncoding(inputFile);
        if (encoding != null)
        {
            _job.getLogger().info("\tInferred FASTQ encoding of file " + inputFile.getName() + " was: " + encoding);
        }
        else
        {
            encoding = FastqUtils.FASTQ_ENCODING.Standard;
            _job.getLogger().warn("\tUnable to infer FASTQ encoding for file: " + inputFile.getPath() + ", defaulting to " + encoding);
        }

        //downsample
        if (_settings.doDownsample())
        {
            Integer readNumber = _settings.getDownsampleReadNumber();
            _job.getLogger().info("Downsampling file to ~" + readNumber + " random reads: " + inputFile.getName());

            int total = FastqUtils.getSequenceCount(inputFile);
            double pctRetained = readNumber / (double)total;
            pctRetained = Math.min(pctRetained, 1);
            _job.getLogger().info("\tTotal reads: " + total + ". Desired number: " + readNumber);
            _job.getLogger().info("\tRetaining " + (pctRetained * 100.0) + "% of reads");

            if(pctRetained == 1)
            {
                _job.getLogger().info("\tNothing to do");
            }
            else
            {
                FastqToSamRunner fq2sam = new FastqToSamRunner(_job.getLogger());
                fq2sam.setWorkingDir(workingDir);
                fq2sam.setFastqEncoding(encoding);
                File sam = fq2sam.execute(inputFile, inputFile2);

                DownsampleSamRunner downsample = new DownsampleSamRunner(_job.getLogger());
                downsample.setWorkingDir(workingDir);
                File downsampledSam = downsample.execute(sam, pctRetained);
                _job.getLogger().info("\tDeleting file: " + sam.getPath());
                if (!sam.delete() || sam.exists())
                    throw new PipelineJobException("File exists: " + sam.getPath());

                SamToFastqRunner sam2fq = new SamToFastqRunner(_job.getLogger());
                sam2fq.setWorkingDir(workingDir);
                String fn1 = FileUtil.getBaseName(inputFile) + ".downsampled.fastq";
                String fn2 = null;
                if (inputFile2 != null)
                    fn2 = FileUtil.getBaseName(inputFile2) + ".downsampled.fastq";

                currentFiles = sam2fq.execute(downsampledSam, fn1, fn2);
                _job.getLogger().info("\tDeleting file: " + downsampledSam.getPath());
                if (!downsampledSam.delete() || downsampledSam.exists())
                    throw new PipelineJobException("File exists: " + downsampledSam.getPath());
            }
        }

        //run trimmomatic for quality trim, adapters, etc.
        if (_settings.isDoTrimming())
        {
            TrimmomaticRunner trimmer = new TrimmomaticRunner(_job.getLogger());
            trimmer.setWorkingDir(workingDir);
            trimmer.setEncoding(encoding);

            //NOTE: the ordering of these events is deliberate
            if (_settings.isDoAdapterTrimming())
            {
                //create FASTA
                File fasta = createAdapterFasta(workingDir);
                trimmer.setAdapterClipping(fasta.getName(), _settings.getAdapterSeedMismatches(), _settings.getPalindromeClipThreshold(), _settings.getSimpleClipThreshold());
            }

            if (_settings.doHeadCrop())
                trimmer.setHeadCrop(_settings.getHeadCropLength());

            if (_settings.doCrop())
                trimmer.setCrop(_settings.getCropLength());

            if (_settings.doQualityTrimByWindow())
                trimmer.setSlidingWindow(_settings.getSlidingWindowSize(), _settings.getSlidingWindowQuality());

            if (_settings.getMinReadLength() > 0)
                trimmer.setMinLength(_settings.getMinReadLength());

            outputs.addAll(trimmer.execute(currentFiles.first, currentFiles.second));

            _job.getLogger().info("\tDeleting file: " + currentFiles.first.getPath());
            if (!currentFiles.first.delete() || currentFiles.first.exists())
                throw new PipelineJobException("File exists: " + currentFiles.first.getPath());

            if (currentFiles.second != null)
            {
                _job.getLogger().info("\tDeleting file: " + currentFiles.second.getPath());
                if (!currentFiles.second.delete() || currentFiles.second.exists())
                    throw new PipelineJobException("File exists: " + currentFiles.second.getPath());
            }
        }
        else
        {
            outputs.add(currentFiles);
        }

        //rename files for consistency, but only rename the first pair of files
        List<Pair<File, File>> newFiles = new ArrayList<>();
        int idx = 0;
        for (Pair<File, File> pair : outputs)
        {
            //only rename first files of pair
            if (idx == 0)
            {
                if (pair.second != null)
                {
                    File newFile1 = new File(pair.first.getParentFile(), basename + ".fastq");
                    File newFile2 = new File(pair.second.getParentFile(), basename2 + ".fastq");
                    boolean renameFirst = true;
                    boolean renameSecond = true;

                    if (newFile1.getPath().equals(pair.first.getPath()))
                        renameFirst = false;

                    if (newFile2.getPath().equals(pair.second.getPath()))
                        renameSecond = false;

                    if (renameFirst || renameSecond)
                    {
                        try
                        {
                            _job.getLogger().info("\tRenaming files for consistency");

                            if (renameFirst)
                            {
                                _job.getLogger().info("\t" + pair.first.getPath() + " to " + newFile1.getPath());
                                FileUtils.moveFile(pair.first, newFile1);
                            }

                            if (renameSecond)
                            {
                                _job.getLogger().info("\t" + pair.second.getPath() + " to " + newFile2.getPath());
                                FileUtils.moveFile(pair.second, newFile2);
                            }
                        }
                        catch (IOException e)
                        {
                            throw new PipelineJobException(e.getMessage());
                        }
                    }

                    newFiles.add(Pair.of(newFile1, newFile2));
                }
                else
                {
                    try
                    {
                        File newFile = new File(pair.first.getParentFile(), basename + ".fastq");

                        if (!pair.first.getPath().equals(newFile.getPath()))
                        {
                            _job.getLogger().info("\tRenaming files for consistency");
                            _job.getLogger().info("\t" + pair.first.getPath() + " to " + newFile.getPath());
                            FileUtils.moveFile(pair.first, newFile);
                        }

                        newFiles.add(Pair.of(newFile, (File)null));
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e.getMessage());
                    }
                }
            }
            else
            {
                newFiles.add(pair);
            }
            idx++;
        }

        //clean up empty folders
        File parent = workingDir.getParentFile();
        if (workingDir.list().length == 0)
            workingDir.delete();

        if (parent.list().length == 0)
            parent.delete();

        return newFiles;
    }

    private File createAdapterFasta(File workingDir) throws PipelineJobException
    {
        BufferedWriter writer = null;
        File fasta = null;
        try
        {
            fasta = new File(workingDir, "adapters.fasta");
            if (!fasta.exists())
            {
                writer = new BufferedWriter(new FileWriter(fasta));

                for (AdapterModel ad : _settings.getAdapters())
                {
                    writer.write(ad.getFastaLines());
                }
            }
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
                throw new PipelineJobException("Error writing adapter FASTA file");
            }
        }
        return fasta;
    }
}
