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
package org.labkey.sequenceanalysis.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.fastq.FastqReader;
import htsjdk.samtools.util.FastqQualityFormat;
import htsjdk.samtools.util.QualityEncodingDetector;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.WorkDirectoryTask;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.pipeline.AbstractSequenceTaskFactory;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.FileGroup;
import org.labkey.sequenceanalysis.ReadDataImpl;
import org.labkey.sequenceanalysis.SequenceReadsetImpl;
import org.labkey.sequenceanalysis.model.BarcodeModel;
import org.labkey.sequenceanalysis.run.preprocessing.SeqtkRunner;
import org.labkey.sequenceanalysis.run.preprocessing.Sff2FastqRunner;
import org.labkey.sequenceanalysis.run.util.FastqcRunner;
import org.labkey.sequenceanalysis.run.util.SamToFastqWrapper;
import org.labkey.sequenceanalysis.util.Barcoder;
import org.labkey.sequenceanalysis.util.FastaToFastqConverter;
import org.labkey.sequenceanalysis.util.FastqMerger;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.NucleotideSequenceFileType;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bbimber
 * Date: 4/21/12
 * Time: 3:37 PM
 */
public class SequenceNormalizationTask extends WorkDirectoryTask<SequenceNormalizationTask.Factory>
{
    private SequenceTaskHelper _taskHelper;
    private static final String PREPARE_INPUT_ACTIONNAME = "Converting to FASTQ";
    private static final String MERGE_ACTIONNAME = "Merge FASTQs";
    private static final String PREPARE_INPUT_STATUS = "NORMALIZING INPUT FILES";
    private static final String DECOMPRESS_ACTIONNAME = "Decompressing Inputs";
    private static final String BARCODE_ACTIONNAME = "Separate By Barcode";
    private static final String FASTQ_ACTION_NAME = "FASTQC";
    private static final String COMPRESS_ACTIONNAME = "Compressing Outputs";
    private static final String EXTRACT_READ_GROUP_ACTION = "Extract Read Groups";

    private SequenceNormalizationTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractSequenceTaskFactory<Factory>
    {
        public Factory()
        {
            super(SequenceNormalizationTask.class);
            setJoin(true);
        }

        @Override
        public boolean isParticipant(PipelineJob job)
        {
            try
            {
                boolean isParticipant = shouldRunRemote(job);
                if (!isParticipant)
                    job.getLogger().debug("Skipping sequence normalization step on remove server since there are no files to normalize");

                return isParticipant;
            }
            catch (FileNotFoundException e)
            {
                //this should never occur, since the above flag allows missing files
            }

            return true;
        }

        @Override
        public String getStatusName()
        {
            return PREPARE_INPUT_STATUS;
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Arrays.asList(DECOMPRESS_ACTIONNAME, EXTRACT_READ_GROUP_ACTION, PREPARE_INPUT_ACTIONNAME, MERGE_ACTIONNAME, BARCODE_ACTIONNAME, FASTQ_ACTION_NAME, COMPRESS_ACTIONNAME);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            SequenceNormalizationTask task = new SequenceNormalizationTask(this, job);
            setJoin(true);

            return task;
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.singletonList(new NucleotideSequenceFileType());
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    public static boolean shouldRunRemote(PipelineJob job) throws FileNotFoundException
    {
        SequencePipelineSettings settings = new SequencePipelineSettings(job.getParameters());

        return settings.isRunFastqc() || getFilesToNormalize(job, ((FileAnalysisJobSupport) job).getInputFiles(), true).size() > 0;
    }

    private ReadsetImportJob getPipelineJob()
    {
        return (ReadsetImportJob)getJob();
    }

    private static List<File> getFilesToNormalize(PipelineJob job, List<File> files, boolean allowMissingFiles) throws FileNotFoundException
    {
        SequencePipelineSettings settings = new SequencePipelineSettings(job.getParameters());
        if (settings.isDoBarcode())
        {
            return files;
        }

        List<File> toNormalize = new ArrayList<>();
        for (File f : files)
        {
            if (!f.exists())
            {
                if (allowMissingFiles)
                    continue;

                throw new FileNotFoundException("Missing file: " + f.getPath());
            }

            if (isNormalizationRequired(job, f))
                toNormalize.add(f);
        }

        try
        {
            List<FileGroup> fileGroups = settings.getFileGroups((ReadsetImportJob)job, allowMissingFiles);
            for (FileGroup fg : fileGroups)
            {
                List<List<FileGroup.FilePair>> filePairs = fg.groupByPlatformUnit();
                for (List<FileGroup.FilePair> lanes : filePairs)
                {
                    Set<String> distinctPlatformUnits = new HashSet<>();
                    for (FileGroup.FilePair m : lanes)
                    {
                        if (m.platformUnit != null)
                        {
                            if (distinctPlatformUnits.contains(m.platformUnit))
                            {
                                //indicates 2 file pairs from same platformUnit
                                job.getLogger().debug("lane will be merged: " + lanes.get(0).platformUnit);
                                for (FileGroup.FilePair fp : lanes)
                                {
                                    if (!toNormalize.contains(fp.file1))
                                        toNormalize.add(fp.file1);
                                    if (fp.file2 != null)
                                    {
                                        if (!toNormalize.contains(fp.file2))
                                            toNormalize.add(fp.file2);
                                    }
                                }
                            }

                            distinctPlatformUnits.add(m.platformUnit);
                        }
                    }
                }
            }
        }
        catch (PipelineJobException e)
        {
            job.getLogger().error(e.getMessage(), e);
        }

        return toNormalize;
    }

    private static boolean isNormalizationRequired(PipelineJob job, File f)
    {
        try
        {
            SequencePipelineSettings settings = new SequencePipelineSettings(job.getParameters());
            if (settings.isDoBarcode())
            {
                return true;
            }

            if (FastqUtils.FqFileType.isType(f))
            {
                if (!f.exists())
                {
                    job.getLogger().error("file does not exist: " + f.getPath(), new Exception());
                }

                try
                {
                    if (!SequenceUtil.hasMinLineCount(f, 4))
                    {
                        job.getLogger().warn("file has fewer than 4 lines: " + f.getPath());
                        return false;
                    }
                }
                catch (PipelineJobException e)
                {
                    job.getLogger().error(e.getMessage(), e);
                }

                QualityEncodingDetector detector = new QualityEncodingDetector();
                try (FastqReader reader = new FastqReader(f))
                {
                    detector.add(QualityEncodingDetector.DEFAULT_MAX_RECORDS_TO_ITERATE * 10, reader);
                }

                if (detector.isDeterminationAmbiguous())
                {
                    job.getLogger().warn("ambigous encoding detected: " + f.getPath() + ". matched:");
                    EnumSet<FastqQualityFormat> formats = detector.generateCandidateQualities(true);
                    for (FastqQualityFormat fmt : formats)
                    {
                        job.getLogger().warn(fmt.name());
                    }

                    return true;
                }
                else if (!FastqQualityFormat.Standard.equals(detector.generateBestGuess(QualityEncodingDetector.FileContext.FASTQ, null)))
                {
                    job.getLogger().debug("fastq file does not appear to use standard encoding (ASCII 33): " + f.getPath());
                    return true;
                }
                else
                {
                    job.getLogger().debug("normalization not required: " + f.getPath());
                    return false;
                }
            }
            else
            {
                return true;
            }
        }
        catch (SAMException e)
        {
            //this is called from pipeline code, so just report the exception for now, and deal with downstream
            job.getLogger().error(e.getMessage(), e);
            return true;
        }
    }

    private SequenceTaskHelper getHelper()
    {
        return _taskHelper;
    }

    private final Set<File> _finalOutputs = new HashSet<>();

    @Override
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        _taskHelper = new SequenceTaskHelper(getPipelineJob(), _wd);
        List<RecordedAction> actions = new ArrayList<>();

        getJob().getLogger().info("Starting Normalization Task");
        File normalizationDir = new File(_wd.getDir(), SequenceTaskHelper.NORMALIZATION_SUBFOLDER_NAME);
        normalizationDir.mkdirs();

        try
        {
            List<FileGroup> normalizedGroups = new ArrayList<>();
            List<FileGroup> fileGroups = getHelper().getSettings().getFileGroups(getPipelineJob(), false);
            for (FileGroup fg : fileGroups)
            {
                getJob().getLogger().debug("processing group: " + fg.name);

                List<FileGroup.FilePair> normalizedPairs = new ArrayList<>();
                List<List<FileGroup.FilePair>> filePairs = fg.groupByPlatformUnit();
                for (List<FileGroup.FilePair> lane : filePairs)
                {
                    for (FileGroup.FilePair fp : lane)
                    {
                        File localCopy = _wd.getWorkingCopyForInput(fp.file1);
                        if (localCopy != null)
                        {
                            getJob().getLogger().debug("using local working copy for file: " + fp.file1.getPath());
                            fp.file1 = localCopy;
                        }

                        if (SequenceUtil.FILETYPE.bam.getFileType().isType(fp.file1))
                        {
                            File outDir = new File(normalizationDir, FileUtil.getBaseName(fp.file1));
                            Pair<File, File> p = extractReadGroup(fp.file1, fp.platformUnit, actions, outDir);
                            fp.file1 = p.first;
                            fp.file2 = p.second;
                            getHelper().getFileManager().addIntermediateFile(fp.file1);
                            if (fp.file2 != null)
                            {
                                getHelper().getFileManager().addIntermediateFile(fp.file2);
                            }
                        }

                        //NOTE: continue to normalize sequence, in case the BAM-derived data has non-standard encoding
                        fp.file1 = normalizeFastqSequence(fp.file1, actions);
                        if (fp.file1 == null)
                        {
                            throw new PipelineJobException("There was an error processing lane: " + fg.name);
                        }

                        if (fp.file2 != null)
                        {
                            localCopy = _wd.getWorkingCopyForInput(fp.file2);
                            if (localCopy != null)
                            {
                                getJob().getLogger().debug("using local working copy for file: " + fp.file2.getPath());
                                fp.file2 = localCopy;
                            }

                            fp.file2 = normalizeFastqSequence(fp.file2, actions);
                        }
                    }

                    if (lane.size() > 1)
                    {
                        RecordedAction mergeAction = new RecordedAction(MERGE_ACTIONNAME);
                        Date start = new Date();
                        mergeAction.setStartTime(start);

                        String platformUnit = lane.get(0).platformUnit == null ? "" : lane.get(0).platformUnit;
                        String status = "Merging lane: " + fg.name + " / " + platformUnit;
                        getJob().getLogger().info(status);
                        getJob().setStatus(PipelineJob.TaskStatus.running, status);

                        File merged1 = new File(normalizationDir, SequenceTaskHelper.getUnzippedBaseName(lane.get(0).file1.getName()) + ".merged.fastq.gz");
                        getJob().getLogger().debug("\tto: " + merged1.getPath());

                        File merged2 = lane.get(0).file2 == null ? null : new File(normalizationDir, SequenceTaskHelper.getUnzippedBaseName(lane.get(0).file2.getName()) + ".merged.fastq.gz");

                        List<File> toMerge1 = new ArrayList<>();
                        List<File> toMerge2 = new ArrayList<>();
                        for (FileGroup.FilePair fp : lane)
                        {
                            toMerge1.add(fp.file1);
                            if (fp.file2 != null)
                            {
                                toMerge2.add(fp.file2);
                            }
                        }

                        new FastqMerger(getJob().getLogger()).mergeFiles(merged1, toMerge1);
                        if (!toMerge2.isEmpty())
                        {
                            new FastqMerger(getJob().getLogger()).mergeFiles(merged2, toMerge2);
                        }

                        FileGroup.FilePair fp = new FileGroup.FilePair();
                        fp.centerName = lane.get(0).centerName;
                        fp.platformUnit = platformUnit;
                        fp.file1 = merged1;
                        fp.file2 = merged2;
                        normalizedPairs.add(fp);

                        getHelper().getFileManager().addOutput(mergeAction, SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME, merged1);
                        if (merged2 != null && merged2.exists())
                        {
                            getHelper().getFileManager().addOutput(mergeAction, SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME, merged2);
                        }

                        Date end = new Date();
                        getJob().getLogger().info("Merge Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));

                        mergeAction.setEndTime(end);
                        actions.add(mergeAction);
                    }
                    else if (lane.isEmpty())
                    {
                        throw new PipelineJobException("There are no files associated with file group: " + fg.name);
                    }
                    else
                    {
                        normalizedPairs.add(lane.get(0));
                    }
                }

                fg.filePairs = normalizedPairs;
                normalizedGroups.add(fg);
            }

            getJob().getLogger().debug("total file groups: " + normalizedGroups.size());

            List<SequenceReadsetImpl> readsets = getPipelineJob().getCachedReadsetModels();

            //barcode, if needed
            if (getHelper().getSettings().isDoBarcode())
            {
                getJob().setStatus(PipelineJob.TaskStatus.running, "Demultiplexing reads");
                getJob().getLogger().info("Separating reads by barcode");

                Barcoder barcoder = getBarcoder();
                int readdataIdx = 0;
                for (FileGroup fg : normalizedGroups)
                {
                    readdataIdx++;
                    getJob().setStatus(PipelineJob.TaskStatus.running, "Demultiplexing " + readdataIdx + " of " + normalizedGroups.size());
                    List<SequenceReadsetImpl> readsetsForGroup = new ArrayList<>();
                    for (SequenceReadsetImpl rs : readsets)
                    {
                        if (fg.name.equals(rs.getFileSetName()))
                        {
                            readsetsForGroup.add(rs);
                        }
                    }

                    if (readsetsForGroup.isEmpty())
                    {
                        getJob().getLogger().warn("no readsets found for group: " + fg);
                    }

                    RecordedAction barcodeAction = new RecordedAction(BARCODE_ACTIONNAME);
                    barcodeAction.setStartTime(new Date());

                    Set<File> outputs;
                    List<BarcodeModel> barcodeModels = getHelper().getSettings().getBarcodes();
                    barcodeModels.addAll(getExtraBarcodesFromFile());

                    getJob().getLogger().info("demultiplexing file group: " + fg.name);
                    File outputDir = new File(normalizationDir, SequenceTaskHelper.getUnzippedBaseName(fg.filePairs.get(0).file1.getName()));
                    outputDir.mkdirs();

                    if (!fg.isPaired())
                    {
                        List<File> fastqs = new ArrayList<>();
                        for (FileGroup.FilePair fp : fg.filePairs)
                        {
                            fastqs.add(fp.file1);
                            if (fp.file2 != null)
                            {
                                throw new PipelineJobException("The file group " + fg.name + " contains a mixture of paired and non-paired reads");
                            }
                        }

                        outputs = barcoder.demultiplexFiles(fastqs, new ArrayList<>(readsetsForGroup), barcodeModels, outputDir);
                        for (File output : outputs)
                        {
                            getHelper().getFileManager().addOutput(barcodeAction, SequenceTaskHelper.BARCODED_FASTQ_OUTPUTNAME, output);
                        }
                    }
                    else
                    {
                        List<Pair<File, File>> fastqPairs = new ArrayList<>();
                        for (FileGroup.FilePair fp : fg.filePairs)
                        {
                            fastqPairs.add(Pair.of(fp.file1, fp.file2));
                            if (fp.file2 == null)
                            {
                                throw new PipelineJobException("The file group " + fg.name + " contains a mixture of paired and non-paired reads");
                            }
                        }

                        outputs = barcoder.demultiplexPairs(fastqPairs, new ArrayList<>(readsetsForGroup), barcodeModels, outputDir);
                        for (File output : outputs)
                        {
                            getHelper().getFileManager().addOutput(barcodeAction, SequenceTaskHelper.BARCODED_FASTQ_OUTPUTNAME, output);
                        }
                    }

                    if (outputs.size() > 0)
                    {
                        for (FileGroup.FilePair fp : fg.filePairs)
                        {
                            getHelper().getFileManager().addIntermediateFile(fp.file1);
                            if (fp.file2 != null)
                            {
                                getHelper().getFileManager().addIntermediateFile(fp.file2);
                            }
                        }

                        for (SequenceReadsetImpl rs : readsetsForGroup)
                        {
                            List<ReadDataImpl> rd = new ArrayList<>();
                            for (FileGroup.FilePair fp : fg.filePairs)
                            {
                                File barcode1 = new File(outputDir, barcoder.getOutputFilename(fp.file1, rs));
                                File barcode2 = fp.file2 == null ? null : new File(outputDir, barcoder.getOutputFilename(fp.file2, rs));

                                if (barcode1.exists())
                                {
                                    ReadDataImpl r = new ReadDataImpl();
                                    r.setPlatformUnit(fp.platformUnit);
                                    r.setCenterName(fp.centerName);
                                    if (barcode1.exists())
                                        r.setFile(barcode1, 1);
                                    if (barcode2 != null && barcode2.exists())
                                        r.setFile(barcode2, 2);
                                    rd.add(r);

                                    if (barcode1.exists())
                                    {
                                        getHelper().getFileManager().addOutput(barcodeAction, SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME, barcode1);
                                    }

                                    if (barcode2 != null && barcode2.exists())
                                    {
                                        getHelper().getFileManager().addOutput(barcodeAction, SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME, barcode2);
                                    }
                                }
                            }

                            getJob().getLogger().debug("readset: " + rs.getName() + ", " + rd.size() + " file pairs");
                            rs.setReadData(rd);
                        }
                    }

                    File detailLog = barcoder.getDetailedLogFile();
                    if (detailLog != null && detailLog.exists())
                    {
                        getHelper().getFileManager().addOutput(barcodeAction, "Barcode Log", detailLog);
                    }

                    File summaryLog = barcoder.getSummaryLogFile();
                    if (summaryLog != null && summaryLog.exists())
                    {
                        getHelper().getFileManager().addOutput(barcodeAction, "Barcode Log", summaryLog);
                    }

                    _finalOutputs.addAll(outputs);
                    if (outputDir.list().length == 0)
                    {
                        getJob().getLogger().debug("deleting empty directory: " + outputDir.getName());
                        FileUtils.deleteDirectory(outputDir);
                    }

                    barcodeAction.setEndTime(new Date());
                    actions.add(barcodeAction);
                }

                getJob().setStatus(PipelineJob.TaskStatus.running);
            }
            else
            {
                for (FileGroup fg : normalizedGroups)
                {
                    for (FileGroup.FilePair pair : fg.filePairs)
                    {
                        _finalOutputs.add(pair.file1);
                        if (pair.file2 != null)
                        {
                            _finalOutputs.add(pair.file2);
                        }
                    }
                }

                for (SequenceReadsetImpl rs : readsets)
                {
                    List<ReadDataImpl> rd = new ArrayList<>();
                    for (FileGroup fg : normalizedGroups)
                    {
                        if (rs.getFileSetName() != null && rs.getFileSetName().equals(fg.name))
                        {
                            for (FileGroup.FilePair fp : fg.filePairs)
                            {
                                ReadDataImpl r = new ReadDataImpl();
                                r.setPlatformUnit(fp.platformUnit);
                                r.setCenterName(fp.centerName);
                                r.setFile(fp.file1, 1);
                                r.setFile(fp.file2, 2);
                                rd.add(r);
                            }

                            getJob().getLogger().debug("readset: " + rs.getName() + ", " + rd.size() + " file pairs");
                        }
                    }

                    if (rd.isEmpty())
                    {
                        getJob().getLogger().warn("no file groups found ofr rs: " + rs.getName() + " with fileGroupId: " + rs.getFileSetName());
                    }

                    rs.setReadData(rd);
                }
            }

            //add final action for later tracking based on role
            File baseDirectory = new File(_wd.getDir(), SequenceTaskHelper.NORMALIZATION_SUBFOLDER_NAME);
            baseDirectory.mkdirs();

            Set<File> finalOutputs = ReadsetInitTask.handleInputs(getPipelineJob(), getHelper().getFileManager().getInputFileTreatment(), actions, _finalOutputs, null);
            FileType gz = new FileType("gz");
            int idx = 0;
            for (File f : finalOutputs)
            {
                idx ++;

                if (!gz.isType(f))
                {
                    //this should no longer ever happen
                    throw new PipelineJobException("FASTQ was not gzipped");
                }

                //NOTE: this could result is the artifact being created on the source directory in the case of
                if (getHelper().getSettings().isRunFastqc())
                {
                    RecordedAction fqAction = new RecordedAction(FASTQ_ACTION_NAME);
                    fqAction.setStartTime(new Date());
                    _taskHelper.getFileManager().addInput(fqAction, "FASTQ File", f);

                    getJob().setStatus(PipelineJob.TaskStatus.running, "RUNNING FASTQC (" + idx + " of " + finalOutputs.size() + ")");
                    getJob().getLogger().info("running FastQC for file: " + f);
                    FastqcRunner runner = new FastqcRunner(getJob().getLogger());
                    Integer threads = SequenceTaskHelper.getMaxThreads(getJob());
                    if (threads != null)
                    {
                        runner.setThreads(threads);
                    }

                    runner.execute(Collections.singletonList(f), null);

                    File fq = new File(f.getParentFile(), runner.getExpectedBasename(f) + "_fastqc.html.gz");
                    File zip = new File(f.getParentFile(), runner.getExpectedBasename(f) + "_fastqc.zip");

                    _taskHelper.getFileManager().addOutput(fqAction, "FASTQC Report", fq);
                    if (zip.exists())
                    {
                        _taskHelper.getFileManager().addOutput(fqAction, "FASTQC Output", zip);
                    }
                    else
                    {
                        getJob().getLogger().error("zip not found: " + zip.getPath());
                    }

                    fqAction.setEndTime(new Date());
                    actions.add(fqAction);
                }

                getJob().setStatus(PipelineJob.TaskStatus.running, "CALCULATING QUALITY METRICS (" + idx + " of " + finalOutputs.size() + ")");
                generateAndWriteMetrics(getJob(), f);
            }

            if (baseDirectory.list().length == 0)
            {
                getJob().getLogger().debug("deleting empty directory: " + baseDirectory.getName());
                FileUtils.deleteDirectory(baseDirectory);
            }

            getJob().setStatus(PipelineJob.TaskStatus.running, "CLEANUP FILES");

            getHelper().getFileManager().deleteIntermediateFiles();
            getHelper().getFileManager().cleanup(actions);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        getJob().getLogger().debug("This job created " + actions.size() + " actions:");
        for (RecordedAction a : actions)
        {
            getJob().getLogger().debug("\t" + a.getName());
        }

        getPipelineJob().getSequenceSupport().markModified();

        return new RecordedActionSet(actions);
    }

    public static void generateAndWriteMetrics(PipelineJob job, File f) throws PipelineJobException
    {
        //calculate/cache metrics to save time on server
        File cachedMetrics = new File(f.getPath() + ".metrics");
        if (cachedMetrics.exists())
        {
            job.getLogger().debug("reusing cached metrics file");
        }
        else
        {
            Map<String, Object> metricsMap = FastqUtils.getQualityMetrics(f, job.getLogger());
            job.getLogger().debug("saving quality metrics to file: " + cachedMetrics.getPath());
            try (CSVWriter writer = new CSVWriter(PrintWriters.getPrintWriter(cachedMetrics), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                for (String key : metricsMap.keySet())
                {
                    writer.writeNext(new String[]{key, String.valueOf(metricsMap.get(key))});
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }
    }

    private Barcoder getBarcoder() throws PipelineJobException
    {
        Barcoder barcoder = new Barcoder(getJob().getLogger());

        if (getMismatchesTolerated() != null)
        {
            barcoder.setEditDistance(getMismatchesTolerated());
        }

        if (getBarcodeOffset() != null)
        {
            barcoder.setOffsetDistance(getBarcodeOffset());
        }

        //default is false
        barcoder.setBarcodesInReadHeader(getBarcodesInReadHeader());

        if (getDeletionsTolerated() != null)
        {
            barcoder.setDeletionsAllowed(getDeletionsTolerated());
        }

        barcoder.setCreateSummaryLog(true);

        if (getHelper().getSettings().isDebugMode())
        {
            getJob().getLogger().debug("will create detailed barcode log");
            barcoder.setCreateDetailedLog(true);
        }

        if (getBarcodeGroupsToScan(getHelper().getSettings()).size() > 0)
            barcoder.setScanAll(true);

        return barcoder;
    }

    private Pair<File, File> extractReadGroup(File input, @Nullable String platformUnit, List<RecordedAction> actions, File outDir) throws PipelineJobException, IOException
    {
        RecordedAction action = new RecordedAction(EXTRACT_READ_GROUP_ACTION);
        action.setStartTime(new Date());

        SamToFastqWrapper wrapper = new SamToFastqWrapper(getJob().getLogger());
        wrapper.setOutputDir(outDir);
        if (!outDir.exists())
        {
            outDir.mkdirs();
        }

        File out1 = new File(outDir, (platformUnit == null ? FileUtil.getBaseName(input) : platformUnit) + "_R1.fastq");
        File out2 = new File(outDir, (platformUnit == null ? FileUtil.getBaseName(input) : platformUnit) + "_R2.fastq");
        if (out1.exists() || out2.exists())
        {
            getJob().getLogger().info("existing FASTQ files found, FastqToSam has probably already been run for a different readGroup");
        }
        else
        {
            if (platformUnit == null)
            {
                getJob().getLogger().info("no platform unit supplied, extracting all reads");
                wrapper.executeCommand(input, out1.getName(), out2.getName());
            }
            else
            {
                getHelper().getFileManager().addInput(action, "Input BAM File", input);
                List<File> created = wrapper.extractByReadGroup(input, outDir);
                for (File f : created)
                {
                    getJob().getLogger().info("\tfile created: " + f.getName());
                    String renamedPath = f.getName().replaceAll("_1.fastq", "_R1.fastq");
                    renamedPath = renamedPath.replaceAll("_2.fastq", "_R2.fastq");
                    File renamed = new File(f.getParentFile(), renamedPath);
                    getJob().getLogger().debug("\trenaming to: " + renamed.getName());
                    if (renamed.exists())
                    {
                        getJob().getLogger().debug("\tdeleting pre-existing file");
                        renamed.delete();
                    }

                    FileUtils.moveFile(f, renamed);
                    getHelper().getFileManager().addOutput(action, "Extracted Read Group FASTQ", renamed);
                    getHelper().getFileManager().addIntermediateFile(renamed);
                }
            }
        }

        Pair<File, File> ret = new Pair<>(null, null);
        if (out1.exists() && SequenceUtil.hasLineCount(out1))
        {
            ret.first = out1;
        }
        else
        {
            getJob().getLogger().error("Unable to find file for readgroup: " + platformUnit);
        }

        if (out2.exists() && SequenceUtil.hasLineCount(out2))
        {
            ret.second = out2;
        }
        else
        {
            getJob().getLogger().info("no reverse read file found for readgroup: " + platformUnit);
        }

        action.setEndTime(new Date());
        actions.add(action);

        return ret;
    }

    // normalize to fastq.gz
    private File normalizeFastqSequence(File input, List<RecordedAction> actions) throws PipelineJobException, IOException
    {
        if (input == null)
        {
            return null;
        }

        PipelineJob job = getJob();
        job.setStatus(PipelineJob.TaskStatus.running, PREPARE_INPUT_STATUS);

        RecordedAction action = new RecordedAction(PREPARE_INPUT_ACTIONNAME);
        action.setStartTime(new Date());

        getJob().getLogger().info("Normalizing to Gzipped FASTQ: " + input.getName());

        getHelper().getFileManager().addInput(action, SequenceTaskHelper.SEQUENCE_DATA_INPUT_NAME, input);
        String basename = SequenceTaskHelper.getUnzippedBaseName(input.getName());

        SequenceUtil.FILETYPE type = SequenceUtil.inferType(input);
        if (type == null)
        {
            getJob().getLogger().error("Unable to determine type for file: " + input.getPath());
            return null;
        }

        getJob().getLogger().info("\tFile format: " + type.getPrimaryExtension());

        File baseDirectory = new File(_wd.getDir(), SequenceTaskHelper.NORMALIZATION_SUBFOLDER_NAME);
        baseDirectory.mkdirs();

        File workingDir = new File(baseDirectory, basename);
        workingDir.mkdirs();

        FileType gz = new FileType("gz");
        File output = null;
        //NOTE: if the file is ASCII33 FASTQ, it should not reach this stage, unless we're doing barcoding or merging
        if (type.equals(SequenceUtil.FILETYPE.fastq))
        {
            QualityEncodingDetector detector = new QualityEncodingDetector();
            try (FastqReader reader = new FastqReader(input))
            {
                detector.add(QualityEncodingDetector.DEFAULT_MAX_RECORDS_TO_ITERATE * 10, reader);
            }

            EnumSet<FastqQualityFormat> formats = detector.generateCandidateQualities(true);
            if (formats.isEmpty())
            {
                throw new PipelineJobException("Unable to infer FASTQ encoding for file: " + input.getPath());
            }
            else if (formats.size() > 1)
            {
                job.getLogger().warn("ambiguous encoding detected: " + input.getPath() + ". matched:");
                for (FastqQualityFormat fmt : formats)
                {
                    job.getLogger().warn(fmt.name());
                }

                job.getLogger().info("trying to scan more reads to reduce ambiguity.  new formats detected: ");
                detector = new QualityEncodingDetector();
                try (FastqReader reader = new FastqReader(input))
                {
                    detector.add(QualityEncodingDetector.DEFAULT_MAX_RECORDS_TO_ITERATE * 5000, reader);
                }

                formats = detector.generateCandidateQualities(true);
                for (FastqQualityFormat fmt : formats)
                {
                    job.getLogger().warn(fmt.name());
                }
                if (formats.isEmpty())
                {
                    throw new PipelineJobException("Unable to infer FASTQ encoding for file: " + input.getPath());
                }
            }

            //NOTE: apparently it's common to have data ambiguous between Solexa and Illumina encoding
            //so long as we dont also potentially match Standard encoding, assume
            //Illumina and convert
            if (formats.contains(FastqQualityFormat.Standard) && formats.size() == 1)
            {
                if (!gz.isType(input))
                {
                    File compressed = new File(workingDir, input.getName() + ".gz");
                    getJob().getLogger().info("compressing file: " + input.getName());
                    Compress.compressGzip(input, compressed);
                    output = compressed;
                }
                else
                {
                    output = input;
                }
            }
            else if (formats.contains(FastqQualityFormat.Illumina) && !formats.contains(FastqQualityFormat.Standard))
            {
                getJob().getLogger().info("Converting Illumina/Solexa FASTQ (ASCII 64) to standard encoding (ASCII 33)");
                File toConvert = input;

                SeqtkRunner runner = new SeqtkRunner(getJob().getLogger());
                output = new File(workingDir, SequenceTaskHelper.getUnzippedBaseName(input.getName()) + ".fastq");
                runner.convertIlluminaToSanger(toConvert, output);

                File compressed = new File(output.getPath() + ".gz");
                getJob().getLogger().info("compressing file: " + output.getName());
                Compress.compressGzip(output, compressed);
                output.delete();
                output = compressed;
            }
            else
            {
                throw new PipelineJobException("Unknown FASTQ encoding");
            }
        }
        else if (type.equals(SequenceUtil.FILETYPE.fasta))
        {
            getJob().getLogger().info("Converting FASTA file to FASTQ");
            FastaToFastqConverter converter = new FastaToFastqConverter(getJob().getLogger());
            output = new File(workingDir, SequenceTaskHelper.getUnzippedBaseName(input.getName()) + ".fastq");
            converter.execute(input, output);

            File compressed = new File(output.getPath() + ".gz");
            getJob().getLogger().info("compressing file: " + output.getName());
            Compress.compressGzip(output, compressed);
            output.delete();
            output = compressed;
        }
        else if (type.equals(SequenceUtil.FILETYPE.sff))
        {
            Sff2FastqRunner sff = new Sff2FastqRunner(getJob().getLogger());
            output = new File(workingDir, SequenceTaskHelper.getUnzippedBaseName(input.getName()) + ".fastq");
            sff.convertFormat(input, output);

            File compressed = new File(output.getPath() + ".gz");
            getJob().getLogger().info("compressing file: " + output.getName());
            Compress.compressGzip(output, compressed);
            output.delete();
            output = compressed;
        }
        else
        {
            getJob().getLogger().error("Unknown file type: " + type);
            if (workingDir.list().length == 0)
            {
                FileUtils.deleteDirectory(workingDir);
            }

            return null;
        }

        getHelper().getFileManager().addOutput(action, SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME, output);

        //we expect each input file to create a subfolder of the same name.
        File directory = new File(baseDirectory, basename);
        if (directory.exists())
            getHelper().getFileManager().addOutput(action, "Normalization Files", directory);

        action.setEndTime(new Date());
        actions.add(action);

        if (!input.getPath().equals(output.getPath()))
        {
            if (getHelper().getFileManager().getInputFileTreatment() == TaskFileManager.InputFileTreatment.delete)
            {
                if (!getHelper().getSettings().isDoBarcode())
                {
                    getJob().getLogger().debug("Marking input as intermediate file: " + input.getPath());
                    getHelper().getFileManager().addIntermediateFile(input);
                }
                else
                {
                    getJob().getLogger().info("Because demultiplexing was used, the original input files will not be deleted: " + input.getPath());
                }
            }
            else
            {
                getJob().getLogger().debug("Inputs were not selected for deletion, not marking as intermediate: " + input.getPath());
            }
        }

        if (workingDir.list().length == 0)
        {
            FileUtils.deleteDirectory(workingDir);
        }

        return output;
    }

    @NotNull
    public static List<String> getBarcodeGroupsToScan(SequencePipelineSettings settings)
    {
        String json = settings.getParams().get("inputfile.barcodeGroups");
        if (json == null)
            return Collections.emptyList();

        JSONArray array = new JSONArray(json);
        List<String> ret = new ArrayList<>();
        for (Object o : array.toList())
        {
            ret.add((String)o);
        }

        return ret;
    }

    private Integer getMismatchesTolerated()
    {
        return getHelper().getSettings().getParams().containsKey("inputfile.barcodeEditDistance") ? Integer.parseInt(getHelper().getSettings().getParams().get("inputfile.barcodeEditDistance")) : null;
    }

    private Integer getDeletionsTolerated()
    {
        return getHelper().getSettings().getParams().containsKey("inputfile.barcodeDeletions") ? Integer.parseInt(getHelper().getSettings().getParams().get("inputfile.barcodeDeletions")) : null;
    }

    private Integer getBarcodeOffset()
    {
        return getHelper().getSettings().getParams().containsKey("inputfile.barcodeOffset") ? Integer.parseInt(getHelper().getSettings().getParams().get("inputfile.barcodeOffset")) : null;
    }

    private boolean getBarcodesInReadHeader()
    {
        return getHelper().getSettings().getParams().containsKey("inputfile.barcodesInReadHeader") && Boolean.parseBoolean(getHelper().getSettings().getParams().get("inputfile.barcodesInReadHeader"));
    }

    public List<BarcodeModel> getExtraBarcodesFromFile() throws PipelineJobException
    {
        List<BarcodeModel> models = new ArrayList<>();
        File barcodes = ReadsetInitTask.getExtraBarcodesFile(getHelper());
        if (barcodes.exists())
        {
            getJob().getLogger().debug("\tReading additional barcodes from file");

            try (CSVReader reader = new CSVReader(Readers.getReader(barcodes), '\t'))
            {
                String[] line;
                while ((line = reader.readNext()) != null)
                {
                    BarcodeModel model = new BarcodeModel();
                    if (!StringUtils.isEmpty(line[0]))
                    {
                        model.setName(line[0]);
                        model.setSequence(line[1]);
                        models.add(model);
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return models;
    }
}
