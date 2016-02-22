package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepOutput;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.TaskFileManager;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.Compress;
import org.labkey.api.util.DebugInfoDumper;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
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
public class TaskFileManagerImpl implements TaskFileManager
{
    private PipelineJob _job;
    private WorkDirectory _wd;
    private File _workLocation;

    private HashMap<String, List<Object[]>> _outputFiles = new HashMap<>();
    private HashMap<String, List<Object[]>> _inputFiles = new HashMap<>();
    private Set<File> _finalOutputs = new HashSet<>();
    private Set<File> _unalteredOutputs = new HashSet<>();
    Map<File, File> _unzippedMap = new HashMap<>();

    private Set<File> _intermediateFiles = new HashSet<>();

    public TaskFileManagerImpl(PipelineJob job, File workDir, WorkDirectory wd)
    {
        _job = job;
        _workLocation = workDir;
        _wd = wd;
    }

    @Override
    public void addSequenceOutput(File file, String label, String category, @Nullable Integer readsetId, @Nullable Integer analysisId, @Nullable Integer genomeId)
    {
        String path = FilenameUtils.normalize(file.getPath());
        String relPath = FileUtil.relativePath(_workLocation.getPath(), path);
        _job.getLogger().debug("Adding sequence output file: " + relPath + " || " + path + "||" + readsetId + "||" + analysisId + "||" + genomeId);
        if (relPath == null)
        {
            relPath = file.getPath();
        }

        File log = getSequenceOutputLog(true);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(log, true)))
        {
            writer.write(StringUtils.join(new String[]{relPath, label, category, (readsetId == null ? "0" : readsetId.toString()), (analysisId == null ? "0" : analysisId.toString()), (genomeId == null ? "0" : genomeId.toString())}, '\t') + '\n');
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addOutput(RecordedAction action, String role, File file)
    {
        addInputOutput(action, role, file, _outputFiles, "Output");
    }

    @Override
    public void addInput(RecordedAction action, String role, File file)
    {
        addInputOutput(action, role, file, _inputFiles, "Input");
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

        for (File file : output.getDeferredDeleteIntermediateFiles())
        {
            addDeferredIntermediateFile(file);
        }

        for (File file : output.getDeferredDeleteIntermediateFiles())
        {
            addDeferredIntermediateFile(file);
        }

        for (PipelineStepOutput.SequenceOutput o : output.getSequenceOutputs())
        {
            addSequenceOutput(o.getFile(), o.getLabel(), o.getCategory(), o.getReadsetId(), o.getAnalysisId(), o.getGenomeId());
        }
    }

    private void addInputOutput(RecordedAction action, String role, File file, HashMap<String, List<Object[]>> target, String type)
    {
        addInputOutput(action, role, file, target, type, false);
    }

    private void addInputOutput(RecordedAction action, String role, File file, HashMap<String, List<Object[]>> target, String type, boolean isTransient)
    {
        String path = FilenameUtils.normalize(file.getPath());
        String relPath = FileUtil.relativePath(_workLocation.getPath(), path);
        if (relPath == null)
        {
            if (file.exists())
            {
                relPath = path;
            }
            else
            {
                _job.getLogger().warn("File not found: " + path);
            }
        }

        if (relPath != null)
            relPath = FilenameUtils.normalize(relPath);

        _job.getLogger().debug("Attempting to add " + type + ": " + relPath);
        _job.getLogger().debug("\toriginal path: " + path);
        _job.getLogger().debug("\tusing work dir: " + _workLocation.getPath());

        if (file.isDirectory())
        {
            for(File f : file.listFiles())
            {
                addInputOutput(action, role, f, target, type, isTransient);
            }
        }
        else
        {
            if (!target.containsKey(relPath))
            {
                if (relPath == null)
                {
                    _job.getLogger().warn("file is not a child of the work location: " + file.getPath());
                }
                else
                {
                    target.put(relPath, new ArrayList<Object[]>());
                }
            }

            // verify we dont have duplicate file/actions.
            // this could occur if we added a specific file in one step, then later tried to add the whole directory
            boolean shouldAdd = true;
            for(Object[] values : target.get(relPath))
            {
                RecordedAction action2 = (RecordedAction)values[0];
                if (action2 == action)
                {
                    shouldAdd = false;
                    _job.getLogger().debug("File already present in another action, will not add: " + file.getName() + " / pending action: " + action.getName() + " / existing action: " + action2.getName() + " / existing role: " + values[1] + " / new role: " + role + " / path: " + relPath);
                    break;
                }
            }

            if (shouldAdd)
            {
                _job.getLogger().debug("Adding file: " + relPath + " / " + file.getName() + " / role: " + role + " / action: " + action.getName() + " / " + isTransient);
                Object[] array = {action, role, file, isTransient};
                target.get(relPath).add(array);
            }
        }
    }

    @Override
    public void addDeferredIntermediateFile(File file)
    {
        String path = FilenameUtils.normalize(file.getPath());
        String relPath = FileUtil.relativePath(_workLocation.getPath(), path);
        _job.getLogger().debug("Adding deferred intermediate file: " + relPath + " || " + path);
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

    private File getSequenceOutputLog(boolean create)
    {
        File logFile = new File(getSupport().getAnalysisDirectory(), "sequenceOutputs.txt");
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
                try (BufferedReader reader = new BufferedReader(new FileReader(log)))
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

                    if (f.exists())
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
                        _job.getLogger().warn("could not find file to delete: " + f.getPath());
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
    public void createSequenceOutputRecords()
    {
        File log = getSequenceOutputLog(false);
        if (log != null && log.exists())
        {
            _job.getLogger().info("Importing sequence output files");

            try (BufferedReader reader = new BufferedReader(new FileReader(log)))
            {
                TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);
                String line;
                while ((line = reader.readLine()) != null)
                {
                    String[] tokens = StringUtils.split(line, '\t');
                    File f = new File(((FileAnalysisJobSupport)_job).getAnalysisDirectory(), tokens[0]);
                    if (!f.exists())
                    {
                        f = new File(tokens[0]);
                        if (!f.exists())
                        {
                            _job.getLogger().error("unable to find file: " + f.getPath());
                            continue;
                        }
                    }

                    ExpData d = ExperimentService.get().getExpDataByURL(f, _job.getContainer());
                    if (d == null)
                    {
                        _job.getLogger().info("creating ExpData for file: " + f.getPath());

                        d = ExperimentService.get().createData(_job.getContainer(), new DataType(tokens[2]));
                        d.setDataFileURI(f.toURI());
                        d.setName(tokens[1]);
                        d.save(_job.getUser());
                    }

                    Map<String, Object> map = new CaseInsensitiveHashMap<>();
                    map.put("name", tokens[1]);
                    map.put("category", tokens[2]);
                    map.put("dataid", d.getRowId());

                    if (tokens.length >= 4)
                    {
                        Integer readsetId = Integer.parseInt(tokens[3]);
                        if (readsetId > 0)
                        {
                            map.put("readset", readsetId);
                            _job.getLogger().debug("readset: " + map.get("readset"));
                        }
                    }

                    if (tokens.length >= 5 && StringUtils.trimToNull(tokens[4]) != null && Integer.parseInt(tokens[4]) > 0)
                    {
                        map.put("analysis_id", Integer.parseInt(tokens[4]));
                        _job.getLogger().debug("analysis id: " + map.get("analysis_id"));
                    }

                    if (tokens.length >= 6 && StringUtils.trimToNull(tokens[5]) != null && Integer.parseInt(tokens[5]) > 0)
                    {
                        map.put("library_id", Integer.parseInt(tokens[5]));
                        _job.getLogger().debug("library_id found in log: " + map.get("library_id"));
                    }
                    else if (getSequenceSupport() != null && getSequenceSupport().getReferenceGenome() != null)
                    {
                        map.put("library_id", getSequenceSupport().getReferenceGenome().getGenomeId());
                        _job.getLogger().debug("library_id taken from reference genome: " + map.get("library_id"));
                    }

                    Integer runId = SequenceTaskHelper.getExpRunIdForJob(_job);
                    map.put("runid", runId);
                    map.put("container", _job.getContainer().getId());
                    map.put("createdby", _job.getUser().getUserId());
                    map.put("modifiedby", _job.getUser().getUserId());
                    map.put("created", new Date());
                    map.put("modified", new Date());

                    Table.insert(_job.getUser(), ti, map);
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }

            log.delete();
        }
        else
        {
            _job.getLogger().debug("There are no sequence outputs to add");
        }
    }

    @Override
    public void addIntermediateFile(File f)
    {
        _job.getLogger().debug("adding intermediate file: " + f.getPath());
        _intermediateFiles.add(f);
    }

    @Override
    public void addIntermediateFiles(Collection<File> files)
    {
        for (File f : files)
        {
            addIntermediateFile(f);
        }
    }

    private void swapFiles(File original, File newFile)
    {
        try
        {
            _job.getLogger().debug("Swapping output file: ");
            _job.getLogger().debug("\tOriginal file: " + original.getPath());

            String relPath = FilenameUtils.normalize(_wd.getRelativePath(original));
            if (relPath == null)
            {
                relPath = FilenameUtils.normalize(FileUtil.relativize(getSupport().getAnalysisDirectory(), original, true));
                if (relPath == null)
                {
                    relPath = FilenameUtils.normalize(original.getPath());
                }
            }
            String relPath2 = FilenameUtils.normalize(_wd.getRelativePath(newFile));
            if (relPath2 == null && newFile.exists())
            {
                relPath2 = FilenameUtils.normalize(FileUtil.relativize(getSupport().getAnalysisDirectory(), newFile, true));
                if (relPath2 == null)
                {
                    relPath2 = FilenameUtils.normalize(newFile.getPath());
                }
            }

            _job.getLogger().debug("\tNew file: " + newFile.getPath());
            _job.getLogger().debug("\tRelative path: " + relPath);
            if (_inputFiles.containsKey(relPath))
            {
                if (!_inputFiles.containsKey(relPath2))
                {
                    _inputFiles.put(relPath2, new ArrayList<Object[]>());
                }

                for(Object[] array : _inputFiles.get(relPath))
                {
                    _job.getLogger().debug("\treplacing input: " + array[2] + " with " + newFile.getPath());
                    array[2] = newFile;

                    _inputFiles.get(relPath2).add(array);
                }

                _inputFiles.remove(relPath);
            }

            if (_outputFiles.containsKey(relPath))
            {
                if (!_outputFiles.containsKey(relPath2))
                {
                    _outputFiles.put(relPath2, new ArrayList<Object[]>());
                }

                for(Object[] array : _outputFiles.get(relPath))
                {
                    _job.getLogger().debug("\treplacing output: " + array[2] + " with " + newFile.getPath());
                    array[2] = newFile;

                    _outputFiles.get(relPath2).add(array);
                }

                _outputFiles.remove(relPath);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getInputfileTreatment()
    {
        return _job.getParameters().get("inputfile.inputTreatment");
    }

    @Override
    public void handleInputs() throws PipelineJobException
    {
        Set<File> inputs = new HashSet<>();
        inputs.addAll(getSupport().getInputFiles());

        _job.getLogger().info("Cleaning up input files");

        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException e)
        {
            throw new PipelineJobException(e);
        }

        String handling = getInputfileTreatment();
        if ("delete".equals(handling))
        {
            for (File input : inputs)
            {
                if (input.exists())
                {
                    if (!_finalOutputs.contains(input))
                    {
                        _job.getLogger().info("Deleting input file: " + input.getPath());

                        input.delete();
                        if (input.exists())
                        {
                            throw new PipelineJobException("Unable to delete file: " + input.getPath());
                        }
                    }
                    else
                    {
                        //this input file was not altered during normalization.  in this case, we move it into the analysis folder
                        _job.getLogger().info("File was not altered by normalization.  Copying to analysis folder: " + input.getPath());
                        copyInputToAnalysisDir(input);
                    }
                }
            }
        }
        else if ("compress".equals(handling))
        {
            FileType gz = new FileType(".gz");
            for (File input : inputs)
            {
                if (input.exists())
                {
                    if (gz.isType(input))
                    {
                        _job.getLogger().debug("Moving input file to analysis directory: " + input.getPath());
                        copyInputToAnalysisDir(input);
                    }
                    else
                    {
                        _job.getLogger().info("Compressing/Moving input file to analysis directory: " + input.getPath());
                        File compressed = Compress.compressGzip(input);
                        if (!compressed.exists())
                            throw new PipelineJobException("Unable to compress file: " + input);

                        swapFiles(input, compressed);

                        input.delete();
                        copyInputToAnalysisDir(compressed);
                    }
                }
            }
        }
        else
        {
            _job.getLogger().info("\tInput files will be left alone");
        }

        deleteIntermediateFiles();
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
            _job.getLogger().debug("Intermediate files will be removed");

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
                    _job.getLogger().debug("\tfile doesnt exist, skipping");
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

    private File copyInputToAnalysisDir(File input) throws PipelineJobException
    {
        _job.getLogger().debug("Copying input file to analysis directory: " + input.getPath());

        try
        {
            //NOTE: we assume the input is gzipped already
            File outputDir = getSupport().getAnalysisDirectory();
            File output = new File(outputDir, input.getName());
            if (output.exists())
            {
                if (_unalteredOutputs.contains(output))
                {
                    _job.getLogger().debug("\tThis input was unaltered during normalization and a copy already exists in the analysis folder so the original will be discarded");
                    input.delete();
                    swapFiles(input, output);
                    return output;
                }
                else
                {
                    output = new File(outputDir, FileUtil.getBaseName(input.getName()) + ".orig.gz");
                    _job.getLogger().debug("\tA file with the expected output name already exists, so the original will be renamed: " + output.getPath());
                }
            }

            FileUtils.moveFile(input, output);
            if (!output.exists())
            {
                throw new PipelineJobException("Unable to move file: " + input.getPath());
            }

            //TODO: kinda of a hack
            File fastqcHtml = new File(input.getParentFile(), input.getName().replaceAll(".fastq.gz", "") + "_fastqc.html.gz");
            if (fastqcHtml.exists())
            {
                _job.getLogger().debug("also moving FASTQC file: " + fastqcHtml.getName());
                File target = new File(output.getParentFile(), fastqcHtml.getName());
                if (target.exists())
                {
                    _job.getLogger().warn("FASTQ file already exists on the server.  not expected: " + target.getPath());
                }
                else
                {
                    FileUtils.moveFile(fastqcHtml, target);
                    swapFiles(fastqcHtml, target);
                }
            }

            swapFiles(input, output);

            return output;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private void processCopiedFile(File file)
    {
        //this should be harmless (although unnecessary) if the working dir is the same as the normal location
        if (file.isDirectory())
        {
            _job.getLogger().debug("Copying directory: " +  file.getPath());
            _job.getLogger().debug("Directory has " + file.listFiles().length + " children");
            for (File f : file.listFiles())
            {
                processCopiedFile(f);
            }
        }
        else
        {
            _job.getLogger().debug("Processing file: " + file.getName());

            String relPath = FilenameUtils.normalize(FileUtil.relativePath(getSupport().getAnalysisDirectory().getPath(), file.getPath()));
            _job.getLogger().debug("\tRelative path: " + relPath);
            if (_outputFiles.containsKey(relPath))
            {
                for(Object[] array : _outputFiles.get(relPath))
                {
                    RecordedAction action = (RecordedAction)array[0];
                    String role = (String)array[1];
                    boolean isTransient = (Boolean)array[3];
                    _job.getLogger().debug("\tProcessing output: " + file.getName() + " / " + role + " / " + action.getName());
                    if (!file.exists())
                    {
                        _job.getLogger().info("\tFile does not exist: " + file.getPath());
                        isTransient = true;
                    }
                    action.addOutput(file, role, isTransient, true);
                }
                _outputFiles.remove(relPath);
            }

            if (_inputFiles.containsKey(relPath))
            {
                for(Object[] array : _inputFiles.get(relPath))
                {
                    RecordedAction action = (RecordedAction)array[0];
                    String role = (String)array[1];
                    _job.getLogger().debug("\tProcessing input: " + file.getName() + " / " + role + " / " + action.getName());
                    if (!file.exists())
                    {
                        _job.getLogger().info("\tFile does not exist: " + file.getPath());
                    }

                    action.addInput(file, role);
                }
                _inputFiles.remove(relPath);
            }

            if (!_inputFiles.containsKey(relPath) && !_outputFiles.containsKey(relPath))
            {
                _job.getLogger().debug("File not associated as an input: " + file.getPath());
                for (String fn : _inputFiles.keySet())
                {
                    if (fn.contains(file.getName()))
                    {
                        _job.getLogger().debug("Found possible match in inputs: " + fn);
                        for (Object[] array : _inputFiles.get(fn))
                        {
                            _job.getLogger().debug("Action: " + ((RecordedAction)array[0]).getName());
                            _job.getLogger().debug("Role: " + array[1]);
                            _job.getLogger().debug("Path: " + array[2]);
                            _job.getLogger().debug(array[2]);
                        }
                    }
                }

                for (String fn : _outputFiles.keySet())
                {
                    if (fn.contains(file.getName()))
                    {
                        _job.getLogger().debug("Found possible match in outputs: " + fn);
                        for (Object[] array : _outputFiles.get(fn))
                        {
                            _job.getLogger().debug("\tAction: " + ((RecordedAction)array[0]).getName());
                            _job.getLogger().debug("\tRole: " + array[1]);
                            _job.getLogger().debug("\tPath: " + array[2]);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void cleanup() throws PipelineJobException
    {
        _job.getLogger().debug("performing file cleanup");
        _job.setStatus(PipelineJob.TaskStatus.running, "PERFORMING FILE CLEANUP");

        try
        {
            // Get rid of any copied input files.
            _job.getLogger().debug("discarding copied inputs");
            _wd.discardCopiedInputs();

            //NOTE: preserving relative locations is a pain.  therefore we copy all outputs, including directories
            //then sort out which files were specified as named outputs later
            for (File input : _wd.getDir().listFiles())
            {
                copyFile(input);
            }

            for (String relPath : _inputFiles.keySet())
            {
                for(Object[] array : _inputFiles.get(relPath))
                {
                    RecordedAction action = (RecordedAction)array[0];
                    String role = (String)array[1];
                    File file = (File)array[2];
                    boolean isTransient = (Boolean)array[3];
                    _job.getLogger().debug("\tProcessing input: " + file.getName() + " / " + role + " / " + action.getName());
                    action.addInput(file, role);
                }
            }

            for (String relPath : _outputFiles.keySet())
            {
                for(Object[] array : _outputFiles.get(relPath))
                {
                    RecordedAction action = (RecordedAction)array[0];
                    String role = (String)array[1];
                    File file = (File)array[2];
                    boolean isTransient = (Boolean)array[3];
                    _job.getLogger().debug("\tProcessing output: " + file.getName() + " / " + role + " / " + action.getName());
                    action.addOutput(file, role, isTransient, true);
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private void copyFile(File input) throws IOException
    {
        try
        {
            doCopyFile(input);
        }
        catch (IOException e)
        {
            _job.getLogger().error(e.getMessage(), e);
            _job.getLogger().error("pid: " + ManagementFactory.getRuntimeMXBean().getName());

            try
            {
                runLsof(input);
            }
            catch (PipelineJobException ex)
            {
                _job.getLogger().error(ex.getMessage(), ex);
            }

            DebugInfoDumper.dumpThreads(_job.getLogger());

            throw e;
        }
    }

    private void doCopyFile(File input) throws IOException
    {
        _job.getLogger().debug("copying file: " + input.getPath());

        if (!input.exists())
        {
            _job.getLogger().debug("file does not exists: " + input.getPath());
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
                    copyFile(child);
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

            dest = _wd.outputFile(input, dest);
            processCopiedFile(dest);
        }
    }

    //TODO: debugging only
    private void runLsof(File input) throws PipelineJobException
    {
        boolean isDir = input.isDirectory();
        AbstractCommandWrapper wrapper = new AbstractCommandWrapper(_job.getLogger()){};
        wrapper.setThrowNonZeroExits(false);
        wrapper.setWarnNonZeroExits(false);
        if (isDir)
            _job.error(wrapper.executeWithOutput(Arrays.asList("/usr/sbin/lsof", "+D", input.getPath())));
        else
            _job.error(wrapper.executeWithOutput(Arrays.asList("lsof", input.getPath())));
    }

    @Override
    public void addFinalOutputFile(File f)
    {
        _job.getLogger().debug("adding final output: " + f.getPath());

        _finalOutputs.add(f);
    }

    @Override
    public void addFinalOutputFiles(Collection<File> files)
    {
        for (File f : files)
        {
            _job.getLogger().debug("adding final output: " + f.getPath());
        }

        _finalOutputs.addAll(files);
    }

    @Override
    public void addUnalteredOutput(File file)
    {
        _unalteredOutputs.add(file);
    }

    @Override
    public Set<File> getFinalOutputFiles()
    {
        return _finalOutputs;
    }
    
    @Override
    public void compressFile(File file)
    {
        if (!file.exists())
        {
            return;
        }

        Set<String> inputPaths = getInputPaths();
        if (!inputPaths.contains(file.getPath()))
        {
            _job.getLogger().info("\tCompressing file: " + file.getPath());
            File zipped = Compress.compressGzip(file);
            swapFiles(file, zipped);
            file.delete();
        }
        else
        {
            _job.getLogger().debug("\tFile was an input, will not compress: " + file.getPath());
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

            action.addInput(i, "Compressed File");
            action.addOutput(unzipped, "Decompressed File", true, true);

            Date end = new Date();
            action.setEndTime(end);
            _job.getLogger().info("\tCompress Duration: " + DurationFormatUtils.formatDurationWords(end.getTime() - start.getTime(), true, true));

            actions.add(action);

            return unzipped;
        }

        return i;
    }
}
