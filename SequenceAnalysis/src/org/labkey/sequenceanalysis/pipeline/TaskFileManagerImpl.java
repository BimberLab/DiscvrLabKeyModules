package org.labkey.sequenceanalysis.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.Compress;
import org.labkey.api.util.DebugInfoDumper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 6/20/2014
 * Time: 5:39 PM
 */
public class TaskFileManagerImpl implements TaskFileManager, Serializable
{
    transient SequenceJob _job;
    transient WorkDirectory _wd;
    transient File _workLocation;

    private Map<File, File> _unzippedMap = new HashMap<>();
    private Set<File> _intermediateFiles = new HashSet<>();
    private Set<SequenceOutputFile> _outputsToCreate = new HashSet<>();

    //for serialization
    public TaskFileManagerImpl()
    {

    }

    public TaskFileManagerImpl(SequenceJob job, File workDir, WorkDirectory wd)
    {
        _job = job;
        _workLocation = workDir;
        _wd = wd;
    }

    @Override
    public void addSequenceOutput(SequenceOutputFile o)
    {
        _outputsToCreate.add(o);

        // just in case..
        _intermediateFiles.remove(o.getFile());

        //this should only occur in test scenarios
        if (_job != null)
        {
            _job.getLogger().debug("added sequence output to TaskFileManager: " + (o.getFile() == null ? o.getName() : o.getFile().getPath()));
            _job.getLogger().debug("total cached: " + _outputsToCreate.size());
        }
    }

    @Override
    public void addSequenceOutput(File file, String label, String category, @Nullable Integer readsetId, @Nullable Integer analysisId, @Nullable Integer genomeId, @Nullable String description)
    {
        SequenceOutputFile so = new SequenceOutputFile();
        so.setFile(file);
        so.setName(label);
        so.setCategory(category);
        so.setReadset(readsetId);
        so.setAnalysis_id(analysisId);
        so.setLibrary_id(genomeId);
        so.setDescription(description);

        addSequenceOutput(so);
    }

    @Override
    public void addOutput(RecordedAction action, String role, File file)
    {
        action.addOutputIfNotPresent(file, role, false);
    }

    @Override
    public void addInput(RecordedAction action, String role, File file)
    {
        action.addInputIfNotPresent(file, role);
    }

    @Override
    public void addStepOutputs(RecordedAction action, PipelineStepOutput output)
    {
        for (Pair<File, String> pair : output.getInputs())
        {
            addInput(action, pair.second, pair.first);
        }

        for (Pair<File, String> pair : output.getOutputs())
        {
            addOutput(action, pair.second, pair.first);
        }

        addIntermediateFiles(output.getIntermediateFiles());
        addPicardMetricsFiles(output.getPicardMetricsFiles());

        for (File file : output.getDeferredDeleteIntermediateFiles())
        {
            addDeferredIntermediateFile(file);
        }

        for (PipelineStepOutput.SequenceOutput o : output.getSequenceOutputs())
        {
            addSequenceOutput(o.getFile(), o.getLabel(), o.getCategory(), o.getReadsetId(), o.getAnalysisId(), o.getGenomeId(), o.getDescription());
        }

        addCommandsToAction(output.getCommandsExecuted(), action);
    }

    public void addCommandsToAction(List<String> commands, RecordedAction action)
    {
        if (!commands.isEmpty())
        {
            for (String command : commands)
            {
                addCommandToAction(command, action);
            }
        }
    }

    private void addCommandToAction(String command, RecordedAction action)
    {
        int commandIdx = 0;
        RecordedAction.ParameterType paramType = new RecordedAction.ParameterType("command" + commandIdx, PropertyType.STRING);
        while (isParamterNameUsed(paramType, action))
        {
            commandIdx++;
            paramType = new RecordedAction.ParameterType("command" + commandIdx, PropertyType.STRING);
        }

        action.addParameter(paramType, command);
    }

    private boolean isParamterNameUsed(RecordedAction.ParameterType paramType, RecordedAction action)
    {
        for (RecordedAction.ParameterType param : action.getParams().keySet())
        {
            if (paramType.getName().equals(param.getName()))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public void addDeferredIntermediateFile(File file)
    {
        String path = FilenameUtils.normalize(file.getPath());
        String relPath = FileUtil.relativePath(_workLocation.getPath(), path);
        _job.getLogger().debug("Adding deferred intermediate file.  relative path: " + relPath + ", path: " + path);
        if (relPath == null)
        {
            relPath = file.getPath();
        }

        File log = getDeferredDeleteLog(true);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(log, true)))
        {
            writer.write(relPath + '\n');
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private FileAnalysisJobSupport getSupport()
    {
        return (FileAnalysisJobSupport)_job;
    }

    private SequenceAnalysisJobSupport getSequenceSupport()
    {
        return _job instanceof SequenceAnalysisJobSupport ? (SequenceAnalysisJobSupport)_job : null;
    }

    private File getDeferredDeleteLog(boolean create)
    {
        File logFile = new File(getSupport().getAnalysisDirectory(), "toDelete.txt");
        if (create && !logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                _job.getLogger().error("Unable to create log file: " + logFile.getPath());
                return null;
            }
        }

        return logFile;
    }

    private File getMetricsLog(boolean create)
    {
        File logFile = new File(getSupport().getAnalysisDirectory(), "metricsToCreate.txt");
        if (create && !logFile.exists())
        {
            try (PrintWriter writer = PrintWriters.getPrintWriter(logFile))
            {
                writer.write(StringUtils.join(Arrays.asList("ReadsetId", "FileRelPath", "Type", "Category", "MetricName", "Value"), "\t"));
                writer.write("\n");
            }
            catch (IOException e)
            {
                _job.getLogger().error("Unable to create log file: " + logFile.getPath());
                return null;
            }
        }

        return logFile;
    }

    @Override
    public void deleteDeferredIntermediateFiles()
    {
        File log = getDeferredDeleteLog(false);
        if (log != null && log.exists())
        {
            _job.getLogger().info("Deleting deferred intermediate files");

            if (isDeleteIntermediateFiles())
            {
                _job.getLogger().debug("Intermediate files will be removed");

                Set<String> toDelete = new HashSet<>();
                try (BufferedReader reader = Readers.getReader(log))
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        toDelete.add(line);
                    }
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }

                for (String line : toDelete)
                {
                    _job.getLogger().debug("attempting to delete deferred file: [" + line + "]");
                    File f = convertRelPathToFile(line);
                    if (f != null && f.exists())
                    {
                        _job.getLogger().debug("deleting file: " + f.getPath());
                        if (f.isDirectory())
                        {
                            try
                            {
                                FileUtils.deleteDirectory(f);
                            }
                            catch (IOException e)
                            {
                                throw new RuntimeException(e);
                            }
                        }
                        else
                        {
                            f.delete();
                        }

                        File parentDir = f.getParentFile();
                        if (parentDir.list().length == 0)
                        {
                            parentDir.delete();
                        }
                    }
                    else
                    {
                        _job.getLogger().warn("could not find file to delete: " + (f == null ? "null" : f.getPath()));
                    }
                }

                if (toDelete.isEmpty())
                {
                    _job.getLogger().debug("there are no deferred delete intermediate files");
                }
            }
            else
            {
                _job.getLogger().debug("Intermediate files will be left alone");
            }

            //always delete the log
            log.delete();
        }
    }

    @Override
    public void createSequenceOutputRecords(@Nullable Integer analysisId) throws PipelineJobException
    {
        if (!_job.getOutputsToCreate().isEmpty())
        {
            Integer runId = SequenceTaskHelper.getExpRunIdForJob(_job);
            SequenceOutputHandlerFinalTask.createOutputFiles(_job, runId, analysisId);
        }
        else
        {
            _job.getLogger().info("no outputs created, nothing to do");
        }
    }

    private File convertRelPathToFile(String line)
    {
        if (line == null)
        {
            return null;
        }

        File f = new File(_workLocation, line);
        if (!f.exists())
        {
            File test = new File(getSupport().getAnalysisDirectory(), line);
            if (test.exists())
            {
                f = test;
            }
        }

        if (!f.exists())
        {
            File test = new File(line);
            if (test.exists())
            {
                f = test;
            }
        }

        return f;
    }

    @Override
    public void addIntermediateFile(File f)
    {
        _job.getLogger().debug("adding intermediate file: " + f.getPath());
        _intermediateFiles.add(f);
    }

    public void removeIntermediateFile(File f)
    {
        _job.getLogger().debug("removing intermediate file: " + f.getPath());
        _intermediateFiles.remove(f);
    }

    @Override
    public void addIntermediateFiles(Collection<File> files)
    {
        for (File f : files)
        {
            addIntermediateFile(f);
        }
    }

//    public void addQualityMetric(PipelineStepOutput.PicardMetricsOutput type, Integer readsetId, File bam, String category, String metricName, Double value) throws PipelineJobException
//    {
//        //write to log
//        File metricLog = getMetricsLog(true);
//        try (BufferedWriter writer = new BufferedWriter(new FileWriter(metricLog, true)))
//        {
//            String bamRelPath = bam == null ? "" : FilenameUtils.normalize(_wd.getRelativePath(bam));
//            writer.write(StringUtils.join(Arrays.asList(readsetId, bamRelPath, type, category, metricName, String.valueOf(value)), "\t"));
//            writer.write('\n');
//        }
//        catch (IOException e)
//        {
//            throw new PipelineJobException(e);
//        }
//    }

    @Override
    public void addPicardMetricsFiles(List<PipelineStepOutput.PicardMetricsOutput> files)
    {
        for (PipelineStepOutput.PicardMetricsOutput mf : files)
        {
            _job.getLogger().debug("adding picard metrics file: " + mf.getMetricFile().getPath());

            //write to log
            File metricLog = getMetricsLog(true);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(metricLog, true)))
            {
                String bamRelPath = mf.getInputFile() == null ? "" : FilenameUtils.normalize(_wd.getRelativePath(mf.getInputFile()));
                String type = mf.getType() == null ? "" : mf.getType().name();

                List<Map<String, Object>> metricLines = PicardMetricsUtil.processFile(mf.getMetricFile(), _job.getLogger());
                for (Map<String, Object> line : metricLines)
                {
                    writer.write(StringUtils.join(Arrays.asList(mf.getReadsetId(), bamRelPath, type, line.get("category"), line.get("metricname"), line.get("metricvalue")), "\t"));
                    writer.write('\n');
                }
            }
            catch (IOException | PipelineJobException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void writeMetricsToDb(Map<Integer, Integer> readsetMap, Map<Integer, Map<PipelineStepOutput.PicardMetricsOutput.TYPE, File>> typeMap) throws PipelineJobException
    {
        if (PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer)
        {
            throw new IllegalArgumentException("Your code is attempting to process metrics from a remote server.  This indicates an upstream problem with the code");
        }

        File metricLog = getMetricsLog(false);
        if (metricLog.exists())
        {
            _job.getLogger().debug("importing picard metrics from: " + metricLog.getPath());

            TableInfo ti = SequenceAnalysisManager.get().getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS);
            try (CSVReader reader = new CSVReader(Readers.getReader(metricLog), '\t'))
            {
                String[] line;
                int i = 0;
                while ((line = reader.readNext()) != null)
                {
                    i++;
                    if (i == 1)
                    {
                        continue; //header
                    }

                    if (line.length != 6)
                    {
                        throw new RuntimeException("Line length is not 6: " + i + "[" + StringUtils.join(line, ";") + "]");
                    }

                    Map<String, Object> toInsert = new HashMap<>();
                    toInsert.put("container", _job.getContainer().getId());
                    toInsert.put("createdby", _job.getUser().getUserId());
                    toInsert.put("created", new Date());

                    Integer readsetId = Integer.parseInt(line[0]);
                    toInsert.put("readset", readsetId);
                    if (readsetMap.containsKey(readsetId))
                    {
                        toInsert.put("analysis_id", readsetMap.get(readsetId));
                    }

                    String type = StringUtils.trimToNull(line[2]);
                    Integer dataId = null;
                    if (typeMap.containsKey(readsetId) && type != null)
                    {
                        _job.getLogger().debug("importing metrics using readsetId");
                        try
                        {
                            PipelineStepOutput.PicardMetricsOutput.TYPE t = PipelineStepOutput.PicardMetricsOutput.TYPE.valueOf(type);
                            if (typeMap.get(readsetId).containsKey(t))
                            {
                                _job.getLogger().debug("attempting to find file: " + typeMap.get(readsetId).get(t).getPath());
                                ExpData d = ExperimentService.get().getExpDataByURL(typeMap.get(readsetId).get(t), _job.getContainer());
                                if (d != null)
                                {
                                    dataId = d.getRowId();
                                }
                                else
                                {
                                    _job.getLogger().warn("unable to find ExpData for file: " + typeMap.get(readsetId).get(t).getPath());
                                }
                            }
                            else
                            {
                                _job.getLogger().warn("unable to find dataId for type: " + type);
                            }
                        }
                        catch (IllegalArgumentException e)
                        {
                            _job.getLogger().error("unknown type: " + type);
                        }
                    }

                    if (dataId == null)
                    {
                        String relPath = StringUtils.trimToNull(line[1]);
                        File f = convertRelPathToFile(relPath);
                        if (f != null)
                        {
                            ExpData d = ExperimentService.get().getExpDataByURL(f, _job.getContainer());
                            if (d != null)
                            {
                                dataId = d.getRowId();
                            }
                            else
                            {
                                _job.getLogger().warn("unable to find ExpData for relPath: " + relPath + " / " + f.getPath());
                            }
                        }
                        else
                        {
                            _job.getLogger().warn("unable to find file for picard metrics: " + relPath + " / " + type);
                        }
                    }

                    if (dataId != null)
                    {
                        toInsert.put("dataid", dataId);
                    }
                    else
                    {
                        _job.getLogger().warn("unable to find ExpData for picard metrics: " + line[1] + " / " + type);
                    }

                    toInsert.put("category", line[3]);
                    toInsert.put("metricname", line[4]);
                    toInsert.put("metricvalue", line[5]);

                    Table.insert(_job.getUser(), ti, toInsert);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            metricLog.delete();
        }
        else
        {
            _job.getLogger().info("no metrics file found");
        }
    }

    private void swapFilesInRecordedActions(File original, File newFile, Collection<RecordedAction> actions, SequenceJob job, @Nullable AbstractResumer resumer) throws PipelineJobException
    {
        swapFilesInRecordedActions(_job.getLogger(), original, newFile, actions, job, resumer);
    }

    public static void swapFilesInRecordedActions(Logger log, File original, File newFile, Collection<RecordedAction> actions, SequenceJob job, @Nullable AbstractResumer resumer) throws PipelineJobException
    {
        log.debug("Swapping copied output file in actions: ");
        log.debug("\tOriginal file: " + original.getPath());
        log.debug("\tNew location: " + newFile.getPath());

        for (RecordedAction a : actions)
        {
            if (a.updateForMovedFile(original, newFile))
            {
                log.debug("updated action: " + a.getName());
            }
        }

        //also sequence outputs
        log.debug("also inspecting outputs: " + (job.getOutputsToCreate() == null ? "none" : job.getOutputsToCreate().size()));
        for (SequenceOutputFile so : job.getOutputsToCreate())
        {
            if (so.getFile().equals(original))
            {
                log.debug("swapping file in sequence output: " + original.getPath());
                so.setFile(newFile);
            }
        }

        //NOTE: this was added to allow better resume if the job is killed during the cleanup process.
        if (resumer != null)
        {
            resumer.addFileCopiedLocally(original, newFile);
            resumer.saveState();
        }
    }

    @Override
    public InputFileTreatment getInputFileTreatment()
    {
        return InputFileTreatment.valueOf(_job.getParameters().get("inputFileTreatment"));
    }

    @Override
    public boolean isDeleteIntermediateFiles()
    {
        return "true".equals(_job.getParameters().get("deleteIntermediateFiles"));
    }

    @Override
    public boolean isCopyInputsLocally()
    {
        return "true".equals(_job.getParameters().get("copyInputsLocally"));
    }

    private Set<String> getInputPaths()
    {
        Set<String> inputPaths = new HashSet<>();
        for (File f : getSupport().getInputFiles())
        {
            inputPaths.add(f.getPath());
        }
        return inputPaths;
    }

    @Override
    public void deleteIntermediateFiles() throws PipelineJobException
    {
        _job.getLogger().info("Cleaning up intermediate files");

        Set<File> inputs = new HashSet<>();
        inputs.addAll(getSupport().getInputFiles());

        Set<String> inputPaths = getInputPaths();

        if (isDeleteIntermediateFiles())
        {
            _job.getLogger().debug("Intermediate files will be removed, total: " + _intermediateFiles.size());

            for (File f : _intermediateFiles)
            {
                _job.getLogger().debug("\tDeleting intermediate file: " + f.getPath());

                if (inputPaths.contains(f.getPath()))
                {
                    _job.getLogger().debug("\tpath matches an input, skipping");
                    continue;
                }

                if (!f.exists())
                {
                    _job.getLogger().debug("\tfile doesn't exist, skipping");
                    continue;
                }

                if (f.isDirectory())
                {
                    try
                    {
                        FileUtils.deleteDirectory(f);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
                else
                {
                    f.delete();
                }

                if (f.exists())
                {
                    throw new PipelineJobException("Unable to delete file: " + f.getPath());
                }

                if (f.getParentFile().listFiles().length == 0)
                {
                    //Do not delete the primary working directory
                    if (_wd != null && _wd.getDir().equals(f.getParentFile()))
                    {
                        continue;
                    }

                    _job.getLogger().debug("\talso deleting empty parent folder: " + f.getParentFile().getPath());
                    f.getParentFile().delete();
                }
            }
        }
        else
        {
            _job.getLogger().debug("Intermediate files will be left alone");
        }
    }

    private void processCopiedFile(File original, File moved, Collection<RecordedAction> actions, @Nullable AbstractResumer resumer) throws IOException, PipelineJobException
    {
        //this should be harmless (although unnecessary) if the working dir is the same as the normal location
        if (moved.isDirectory())
        {
            if (moved.list().length == 0)
            {
                _job.getLogger().debug("Deleting empty directory: " + moved.getPath());
                FileUtils.deleteDirectory(moved);
            }
            else
            {
                _job.getLogger().debug("Copying directory: " + moved.getPath());
                _job.getLogger().debug("Directory has " + moved.listFiles().length + " children");
                for (File f : moved.listFiles())
                {
                    processCopiedFile(new File(original, f.getName()), f, actions, resumer);
                }
            }
        }
        else
        {
            _job.getLogger().debug("Processing file: " + moved.getName());
            swapFilesInRecordedActions(original, moved, actions, _job, resumer);
        }
    }

    public Set<SequenceOutputFile> getOutputsToCreate()
    {
        return _outputsToCreate;
    }

    public void setOutputsToCreate(Set<SequenceOutputFile> outputsToCreate)
    {
        _outputsToCreate = outputsToCreate;
    }

    @Override
    public void cleanup(Collection<RecordedAction> actions) throws PipelineJobException
    {
        cleanup(actions, null);
    }

    public void cleanup(Collection<RecordedAction> actions, @Nullable AbstractResumer resumer) throws PipelineJobException
    {
        _job.getLogger().debug("performing file cleanup");
        _job.setStatus(PipelineJob.TaskStatus.running, "PERFORMING FILE CLEANUP");

        _job.getLogger().debug("transferring " + _outputsToCreate.size() + " sequence outputs to pipeline job, existing: " + _job.getOutputsToCreate().size());
        for (SequenceOutputFile so : _outputsToCreate)
        {
            _job.addOutputToCreate(so);
        }

        //Snapshot this list
        List<Pair<File, File>> previouslyCopiedFiles = resumer == null ? null : new ArrayList<>(resumer.getFilesCopiedLocally());

        try
        {
            if (_wd != null)
            {
                // Get rid of any copied input files.
                _job.getLogger().debug("discarding copied inputs");
                _wd.discardCopiedInputs();

                if (!_wd.getDir().exists())
                {
                    throw new PipelineJobException("work dir does not exist: " + _wd.getDir());
                }

                //NOTE: preserving relative locations is a pain.  therefore we copy all outputs, including directories
                //then sort out which files were specified as named outputs later
                for (File input : _wd.getDir().listFiles())
                {
                    copyFile(input, actions, resumer);
                }
            }
            else
            {
                _job.getLogger().debug("no workDir provided, skipping");
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        if (previouslyCopiedFiles != null)
        {
            _job.getLogger().debug("Inspecting previously copied files (due to job resume): " + previouslyCopiedFiles.size());
            for (Pair<File, File> pair : previouslyCopiedFiles)
            {
                swapFilesInRecordedActions(pair.first, pair.second, actions, _job, null);
            }
        }

        //Defer this until the end, since we will be saving the resumer periodically as we copy files
        _outputsToCreate.clear();
    }

    private void copyFile(File input, Collection<RecordedAction> actions, @Nullable AbstractResumer resumer) throws IOException, PipelineJobException
    {
        try
        {
            doCopyFile(input, actions, resumer);
        }
        catch (IOException e)
        {
            _job.getLogger().error(e.getMessage(), e);
            _job.getLogger().error("pid: " + ManagementFactory.getRuntimeMXBean().getName());

            DebugInfoDumper.dumpThreads(_job.getLogger());

            throw e;
        }
    }

    private void doCopyFile(File input, Collection<RecordedAction> actions, @Nullable AbstractResumer resumer) throws IOException, PipelineJobException
    {
        _job.getLogger().debug("copying file: " + input.getPath());

        if (!input.exists())
        {
            _job.getLogger().debug("file does not exist: " + input.getPath());
            return;
        }

        if (input.isDirectory() && input.list().length == 0)
        {
            _job.getLogger().debug("deleting empty directory: " + input.getPath());
            FileUtils.deleteDirectory(input);
            return;
        }

        String path = _wd.getRelativePath(input);
        File dest = new File(getSupport().getAnalysisDirectory(), path);
        _job.getLogger().debug("to: " + dest.getPath());

        boolean doMove = true;
        if (dest.exists())
        {
            if (input.isDirectory())
            {
                _job.getLogger().debug("attempting to copy a directory that already exists in destination.  will try to merge");
                File[] children = input.listFiles();
                for (File child : children)
                {
                    copyFile(child, actions, resumer);
                }

                FileUtils.deleteDirectory(input);
                doMove = false;
            }
            else
            {
                _job.getLogger().debug("file already exists, deleting pre-existing local file");
                if (!dest.delete())
                {
                    _job.getLogger().error("unable to delete local file: " + dest.getPath());
                }
            }
        }

        if (doMove)
        {
            _job.getLogger().debug("copying input: " + input.getPath());
            _job.getLogger().debug("to: " + dest.getPath());
            if (Files.isSymbolicLink(input.toPath()) && !input.exists())
            {
                _job.getLogger().info("found broken symlink: " + input.getPath());
                Files.delete(input.toPath());
            }

            dest = _wd.outputFile(input, dest);
            processCopiedFile(input, dest, actions, resumer);
        }
    }

    @Override
    public void processUnzippedInputs()
    {
        _job.getLogger().info("Removing unzipped inputs");
        if (_unzippedMap.isEmpty())
        {
            _job.getLogger().debug("\tthere are no unzipped files to process");
        }
        else
        {
            for (File z : _unzippedMap.keySet())
            {
                _job.getLogger().debug("\tremoving: " + _unzippedMap.get(z).getPath());
                _job.getLogger().debug("\toriginal file: " + z.getPath());
                //swapFiles(_unzippedMap.get(z), z);

                if (_unzippedMap.get(z).exists())
                    _unzippedMap.get(z).delete();
            }
        }
    }

    @Override
    public void decompressInputFiles(Pair<File, File> pair, List<RecordedAction> actions)
    {
        List<File> list = new ArrayList<>();
        if (pair.first != null)
            pair.first = decompressFile(pair.first, actions);
        if (pair.second != null)
            pair.second = decompressFile(pair.second, actions);
    }

    private File decompressFile(File i, List<RecordedAction> actions)
    {
        //NOTE: because we can initate runs on readsets from different containers, we cannot rely on dataDirectory() to be consistent
        //b/c inputs are always copied to the root of the analysis folder, we will use relative paths
        FileType gz = new FileType(".gz");
        File unzipped = null;
        if (gz.isType(i))
        {
            RecordedAction action = new RecordedAction(SequenceAlignmentTask.DECOMPRESS_ACTIONNAME);
            Date start = new Date();
            action.setStartTime(start);

            //NOTE: we use relative paths in all cases here
            _job.getLogger().info("Decompressing file: " + i.getPath());

            unzipped = new File(_wd.getDir(), i.getName().replaceAll(".gz$", ""));
            unzipped = Compress.decompressGzip(i, unzipped);
            _job.getLogger().debug("\tunzipped: " + unzipped.getPath());

            _unzippedMap.put(i, unzipped);

            action.addInputIfNotPresent(i, "Compressed File");
            action.addOutputIfNotPresent(unzipped, "Decompressed File", true);

            Date end = new Date();
            action.setEndTime(end);
            _job.getLogger().info("\tCompress Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));

            actions.add(action);

            return unzipped;
        }

        return i;
    }

    public Map<File, File> getUnzippedMap()
    {
        return _unzippedMap;
    }

    public void setUnzippedMap(Map<File, File> unzippedMap)
    {
        _unzippedMap = unzippedMap;
    }

    public Set<File> getIntermediateFiles()
    {
        return _intermediateFiles;
    }

    public void setIntermediateFiles(Set<File> intermediateFiles)
    {
        _intermediateFiles = intermediateFiles;
    }
}
