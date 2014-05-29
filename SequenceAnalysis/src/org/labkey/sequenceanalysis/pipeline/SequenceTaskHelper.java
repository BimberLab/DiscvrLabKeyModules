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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.model.BarcodeModel;
import org.labkey.sequenceanalysis.model.ReadsetModel;
import org.labkey.sequenceanalysis.util.FastqUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 4/21/12
 * Time: 5:40 PM
 */
public class SequenceTaskHelper
{
    private PipelineJob _job;
    private SequencePipelineSettings _settings;
    private File _workDir;
    public static final String FASTQ_DATA_INPUT_NAME = "Input FASTQ File";
    public static final String BAM_INPUT_NAME = "Input BAM File";
    public static final String SEQUENCE_DATA_INPUT_NAME = "Input Sequence File";
    public static final String NORMALIZED_FASTQ_OUTPUTNAME = "Normalized FASTQ File";
    public static final String MERGED_FASTQ_OUTPUTNAME = "Merged FASTQ File";
    public static final String BARCODED_FASTQ_OUTPUTNAME = "Barcoded FASTQ File";
    public static final String NORMALIZATION_SUBFOLDER_NAME = "Normalization";
    public static final String PREPROCESSING_SUBFOLDER_NAME = "Preprocessing";
    public static final String SHARED_SUBFOLDER_NAME = "Shared";  //the subfolder within which the Reference DB and aligner index files will be created

    public static final String ARCHIVED_INPUT_SEQUENCE = "Archived Input Sequence";

    private HashMap<String, List<Object[]>> _outputFiles = new HashMap<>();
    private HashMap<String, List<Object[]>> _inputFiles = new HashMap<>();
    private Set<File> _intermediateFiles = new HashSet<>();
    private List<File> _finalOutputs = new ArrayList<>();
    private Set<File> _unalteredOutputs = new HashSet<>();

    public SequenceTaskHelper(PipelineJob job)
    {
        _job = job;
        _workDir = getSupport().getAnalysisDirectory();
        init(_job.getParameters());
    }

    public SequenceTaskHelper(PipelineJob job, File workLocation)
    {
        _job = job;
        _workDir = workLocation;
        init(_job.getParameters());
    }

    private void init(Map<String, String> params)
    {
        _settings = new SequencePipelineSettings(params);
    }

    public void createExpDatasForInputs()
    {
        //make sure Exp data objects exist for all input files.
        Map<String, String> params = _settings.getParams();

        for(ReadsetModel rs : _settings.getReadsets())
        {
            String fn = rs.getFileName();
            Integer id = rs.getFileId();
            if (StringUtils.isNotBlank(fn) && id == null)
            {
                File f = new File(getSupport().getDataDirectory(), fn);
                ExpData d = createExpData(f);
                if (d != null)
                {
                    rs.setFileId(d.getRowId());
                }
            }

            fn = rs.getFileName2();
            id = rs.getFileId2();
            if (StringUtils.isNotBlank(fn) && id == null)
            {
                File f = new File(getSupport().getDataDirectory(), fn);
                ExpData d = createExpData(f);
                if (d != null)
                {
                    rs.setFileId2(d.getRowId());
                }
            }
        }
    }

    public ExpData createExpData(File f)
    {
        _job.getLogger().debug("Creating Exp data for file: " + f.getName());
        ExpData d = ExperimentService.get().createData(_job.getContainer(), new DataType("SequenceFile"));

        f = FileUtil.getAbsoluteCaseSensitiveFile(f);

        d.setName(f.getName());
        d.setDataFileURI(f.toURI());
        _job.getLogger().debug("The saved filepath is: " + f.getPath());
        d.save(_job.getUser());
        return d;
    }

    private PipelineJob getJob()
    {
        return _job;
    }

    public void setWorkDir(File workDir)
    {
        _workDir = workDir;
    }

    public Map<String,String> getEnvironment()
    {
        PipelineJob job = getJob();
        Map<String,String> environment = new HashMap<>();

        String[] variables = {
            "BLATPATH",
            "BOWTIEPATH",
            "BWAPATH",
            "BFASTPATH",
            "CAP3PATH",
            "FASTQCPATH",
            "FASTXPATH",
            "GATKPATH",
            "LASTZPATH",
            "MOSAIK_NETWORKFILEPATH",
            "MOSAIKPATH",
            "PYROBAYESPATH",
            "PICARDPATH",
            "ROCHETOOLSPATH",
            "SAMTOOLSPATH",
            "SEQUENCEANALYSIS_NETRC",
            "SEQUENCEANALYSIS_BASEURL",
            "SEQUENCEANALYSIS_CODELOCATION",
            "SEQUENCEANALYSIS_EXTERNALDIR",
            "SEQUENCEANALYSIS_MAX_THREADS",
            "SEQUENCEANALYSIS_TOOLS"
        };

        for(String variable : variables)
        {
            String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(variable);
            if (path != null)
            {
                environment.put(variable, path);
                job.getLogger().debug("\tSetting environment variable: " + variable + " to: " + path);
            }
        }

        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("SEQUENCEANALYSIS_CODELOCATION");
        if (path != null)
        {
            environment.put("SEQUENCEANALYSIS_CODELOCATION", path);
            Map<String, String> env = System.getenv();
            if (env.containsKey("PERL5LIB"))
            {
                path = env.get("PERL5LIB") + File.pathSeparator + path;
            }

            environment.put("PERL5LIB", path);
            job.getLogger().debug("\tSetting environment variable: PERL5LIB to: " + path);
        }

        String pipelinePath = PipelineJobService.get().getAppProperties().getToolsDirectory();
        if (pipelinePath != null)
        {
            environment.put("PIPELINE_TOOLS_DIR", pipelinePath);
        }

        return environment;
    }

    private String getExePath(String exePath, String packageName)
    {
        String packagePath = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath(packageName);
        if (packagePath != null && packagePath.length() > 0)
        {
            exePath = (new File(packagePath, exePath)).getPath();
        }
        return exePath;
    }

    public void runPerlScript (List<File> inputFiles, String scriptName, PipelineJob job) throws PipelineJobException
    {
        job.getLogger().info("Preparing to run perl script: " + scriptName);

        String exePath = getExePath("perl", "Perl");
        String scriptPath = getExePath(scriptName, "SEQUENCEANALYSIS_CODELOCATION");

        List<String> args = new ArrayList<>();
        args.add(exePath);
        args.add(scriptPath);
        for(File f : inputFiles)
        {
            args.add(f.getPath());
        }
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.environment().putAll(getEnvironment());
        job.runSubProcess(pb, _workDir);
    }

    public static File getJarPath(String name) throws FileNotFoundException
    {
        String packagePath = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("SEQUENCEANALYSIS_EXTERNALDIR");
        if (packagePath != null && packagePath.length() > 0)
        {
            File jar = new File(packagePath, name);
            if (!jar.exists())
                throw new FileNotFoundException("Unable to find file: " + jar.getPath());

            return jar;
        }

        throw new FileNotFoundException("SEQUENCEANALYSIS_EXTERNALDIR not set in pipeline XML");
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
                getJob().getLogger().error("Unable to create log file: " + logFile.getPath());
                return null;
            }
        }

        return logFile;
    }

    /**
     * Registers a file that will be deleted only at the very end of the protocol
     */
    public void addDeferredIntermediateFile(File file)
    {
        String path = FilenameUtils.normalize(file.getPath());
        String relPath = FileUtil.relativePath(_workDir.getPath(), path);

        File log = getDeferredDeleteLog(true);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(log, true)))
        {
            getJob().getLogger().debug("Adding deferred intermediate file: " + relPath);
            writer.write(relPath + '\n');
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void deleteDeferredIntermediateFiles()
    {
        File log = getDeferredDeleteLog(false);
        if (log != null && log.exists())
        {
            getJob().getLogger().info("Deleting deferred intermediate files");

            if (getSettings().isDeleteIntermediateFiles())
            {
                getJob().getLogger().debug("Intermediate files will be removed");

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
                    File f = new File(_workDir, line);
                    getJob().getLogger().debug("deleting file: " + f.getPath());
                    if (f.exists())
                    {
                        f.delete();

                        File parentDir = f.getParentFile();
                        if (parentDir.list().length == 0)
                        {
                            parentDir.delete();
                        }
                    }
                    else
                    {
                        getJob().getLogger().warn("could not find file to delete");
                    }
                }
            }
            else
            {
                getJob().getLogger().debug("Intermediate files will be left alone");
            }

            //always delete the log
            log.delete();
        }
    }

    public void addIntermediateFile(File f)
    {
        _intermediateFiles.add(f);
    }

    public void addIntermediateFiles(Collection<File> files)
    {
        _intermediateFiles.addAll(files);
    }

    public void addFinalOutputFile(File f)
    {
        _finalOutputs.add(f);
    }

    public void addFinalOutputFiles(Collection<File> files)
    {
        _finalOutputs.addAll(files);
    }

    /**
     * These are output files that are unaltered versions of an input file
     * This is tracked to avoid duplications when inputs are process/archived
     * @param f
     */
    public void addUnalteredOutput(File f)
    {
        _unalteredOutputs.add(f);
    }

    public List<File> getFinalOutputfiles()
    {
        return _finalOutputs;
    }

    public void addInputOutput(RecordedAction action, String role, File file, HashMap<String, List<Object[]>> target, String type)
    {
        addInputOutput(action, role, file, target, type, false);
    }

    public void addInputOutput(RecordedAction action, String role, File file, HashMap<String, List<Object[]>> target, String type, boolean isTransient)
    {
        String path = FilenameUtils.normalize(file.getPath());
        String relPath = FileUtil.relativePath(_workDir.getPath(), path);
        if (relPath == null)
        {
            if (file.exists())
            {
                relPath = path;
            }
            else
            {
                getJob().getLogger().warn("File not found: " + path);
            }
        }

        if (relPath != null)
            relPath = FilenameUtils.normalize(relPath);

        getJob().getLogger().debug("Adding " + type + ": " + relPath);

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
                target.put(relPath, new ArrayList<Object[]>());
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
                    getJob().getLogger().debug("File already present in another action, will not add: " + file.getName() + " / pending action: " + action.getName() + " / existing action: " + action2.getName() + " / existing role: " + values[1] + " / new role: " + role + " / path: " + relPath);
                    break;
                }
            }

            if (shouldAdd)
            {
                getJob().getLogger().debug("Adding file: " + relPath + " / " + file.getName() + " / " + role + " / " + action.getName() + " / " + isTransient);
                Object[] array = {action, role, file, isTransient};
                target.get(relPath).add(array);
            }
        }
    }

    public void addOutput(RecordedAction action, String role, File file)
    {
        addInputOutput(action, role, file, _outputFiles, "Output");
    }

    public void addOutput(RecordedAction action, String role, File file, boolean isTransient)
    {
        addInputOutput(action, role, file, _outputFiles, "Output", isTransient);
    }

    public void addInput(RecordedAction action, String role, File file)
    {
        addInputOutput(action, role, file, _inputFiles, "Input");
    }

    private void processCopiedFile(File file)
    {
        //this should be harmless (although unnecessary) if the working dir is the same as the normal location
        if (file.isDirectory())
        {
            getJob().getLogger().debug("Copying directory: " +  file.getPath());
            getJob().getLogger().debug("Directory has " + file.listFiles().length + " children");
            for(File f : file.listFiles())
            {
                processCopiedFile(f);
            }
        }
        else
        {
            getJob().getLogger().debug("Processing file: " + file.getName());

            String relPath = FilenameUtils.normalize(FileUtil.relativePath(getSupport().getAnalysisDirectory().getPath(), file.getPath()));
            getJob().getLogger().debug("\tRelative path: " + relPath);
            if (_outputFiles.containsKey(relPath))
            {
                for(Object[] array : _outputFiles.get(relPath))
                {
                    RecordedAction action = (RecordedAction)array[0];
                    String role = (String)array[1];
                    boolean isTransient = (Boolean)array[3];
                    getJob().getLogger().debug("\tProcessing output: " + file.getName() + " / " + role + " / " + action.getName());
                    if (!file.exists())
                    {
                        getJob().getLogger().info("\tFile does not exist: " + file.getPath());
                        isTransient = true;
                    }
                    action.addOutput(file, role, isTransient);
                }
                _outputFiles.remove(relPath);
            }

            if (_inputFiles.containsKey(relPath))
            {
                for(Object[] array : _inputFiles.get(relPath))
                {
                    RecordedAction action = (RecordedAction)array[0];
                    String role = (String)array[1];
                    getJob().getLogger().debug("\tProcessing input: " + file.getName() + " / " + role + " / " + action.getName());
                    if (!file.exists())
                    {
                        getJob().getLogger().info("\tFile does not exist: " + file.getPath());
                    }

                    action.addInput(file, role);
                }
                _inputFiles.remove(relPath);
            }

            if (!_inputFiles.containsKey(relPath) && !_outputFiles.containsKey(relPath))
            {
                getJob().getLogger().debug("File not associated as an input: " + file.getPath());
                for (String fn : _inputFiles.keySet())
                {
                    if (fn.contains(file.getName()))
                    {
                        _job.getLogger().debug("Found possible match in inputs: " + fn);
                        for (Object[] array : _inputFiles.get(fn))
                        {
                            getJob().getLogger().debug("Action: " + ((RecordedAction)array[0]).getName());
                            getJob().getLogger().debug("Role: " + array[1]);
                            getJob().getLogger().debug("Path: " + array[2]);
                            getJob().getLogger().debug(array[2]);
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
                            getJob().getLogger().debug("\tAction: " + ((RecordedAction)array[0]).getName());
                            getJob().getLogger().debug("\tRole: " + array[1]);
                            getJob().getLogger().debug("\tPath: " + array[2]);
                        }
                    }
                }
            }
        }
    }

    //should be used for remote jobs or local jobs running in a separate working directory
    public void cleanup(WorkDirectory wd) throws PipelineJobException
    {
        try
        {
            // Get rid of any copied input files.
            wd.discardCopiedInputs();

            //NOTE: preserving relative locations is a pain.  therefore we copy all outputs, including directories
            //then sort out which files were specified as named outputs later
            for(File input : wd.getDir().listFiles())
            {
                String path = wd.getRelativePath(input);
                File dest = new File(getSupport().getAnalysisDirectory(), path);
                dest = wd.outputFile(input, dest);

                processCopiedFile(dest);
            }

            for (String relPath : _inputFiles.keySet())
            {
                for(Object[] array : _inputFiles.get(relPath))
                {
                    RecordedAction action = (RecordedAction)array[0];
                    String role = (String)array[1];
                    File file = (File)array[2];
                    boolean isTransient = (Boolean)array[3];
                    getJob().getLogger().debug("\tProcessing input: " + file.getName() + " / " + role + " / " + action.getName());
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
                    getJob().getLogger().debug("\tProcessing output: " + file.getName() + " / " + role + " / " + action.getName());
                    action.addOutput(file, role, isTransient);
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    //should be used for jobs running on the webserver.  most of what this does is unnecessary, but it does append outputs to the correct actions
    //and the redundant work is essentially harmless
//    public void cleanup(File wd, HashMap<String, List<Object[]>> inputFiles, HashMap<String, List<Object[]>> outputFiles)
//    {
//        for(File output : wd.listFiles())
//        {
//            processCopiedFile(output);
//        }
//    }

    public List<File> getOutputsFromAction(RecordedAction action, String extension, String role)
    {
        List<File> files = new ArrayList<>();
        FileType fileType = new FileType(extension);

        //iterate over each output created from the first action
        for(String relPath : _outputFiles.keySet())
        {
            for(Object[] array : _outputFiles.get(relPath))
            {

                RecordedAction saved_action = (RecordedAction)array[0];
                String saved_role = (String)array[1];
                File saved_file = (File)array[2];
                if (fileType.isType(saved_file) && (role == null || role.equals(saved_role)) && action == saved_action)
                {
                    files.add(saved_file);
                }
            }
        }

        return files;
    }

    public SequencePipelineSettings getSettings()
    {
        return _settings;
    }

    public static String getExpectedNameForInput(String fn)
    {
        FileType gz = new FileType(".gz");
        if (gz.isType(fn))
            return fn.replaceAll(".gz$", "");
        else
            return fn;
    }

    public boolean isNormalizationRequired(File f)
    {
        if (getSettings().isDoMerge() || getSettings().isDoBarcode())
            return true;

        if (FastqUtils.FqFileType.isType(f))
        {
            if (!FastqUtils.FASTQ_ENCODING.Standard.equals(FastqUtils.inferFastqEncoding(f)))
            {
                getJob().getLogger().debug("fastq file does not appear to use standard encoding: " + f.getPath());
                return true;
            }
            else
            {
                return false;
            }
        }
        else
        {
            return true;
        }
    }

    public List<File> getFilesToNormalize(List<File> files) throws FileNotFoundException
    {
        return getFilesToNormalize(files, false);
    }

    public List<File> getFilesToNormalize(List<File> files, boolean allowMissingFiles) throws FileNotFoundException
    {
        if (getSettings().isDoMerge() || getSettings().isDoBarcode())
            return files;

        List<File> toNormalize = new ArrayList<>();
        for (File f : files)
        {
            if (!f.exists())
            {
                if (allowMissingFiles)
                    continue;

                throw new FileNotFoundException("Missing file: " + f.getPath());
            }

            if (isNormalizationRequired(f))
               toNormalize.add(f);
        }

        return toNormalize;
    }

    public static Integer getExpRunIdForJob(PipelineJob job) throws PipelineJobException
    {
        return getExpRunIdForJob(job, true);
    }

    public static Integer getExpRunIdForJob(PipelineJob job, boolean throwUnlessFound) throws PipelineJobException
    {
        Integer jobId = PipelineService.get().getJobId(job.getUser(), job.getContainer(), job.getJobGUID());
        Integer parentJobId = PipelineService.get().getJobId(job.getUser(), job.getContainer(), job.getParentGUID());

        TableInfo runs = ExperimentService.get().getSchema().getTable("ExperimentRun");
        TableSelector ts = new TableSelector(runs, Collections.singleton("RowId"), new SimpleFilter(FieldKey.fromString("JobId"), jobId), null);
        Map<String, Object>[] rows = ts.getMapArray();

        if (rows.length == 0)
        {
            ts = new TableSelector(runs, Collections.singleton("RowId"), new SimpleFilter(FieldKey.fromString("JobId"), parentJobId), null);
            rows = ts.getMapArray();
        }

        if (rows.length != 1)
        {
            if (throwUnlessFound)
                throw new PipelineJobException("Incorrect row count when querying ExpRuns.  Found: " + rows.length);
            else
                return null;
        }
        Map<String, Object> row = rows[0];
        return (Integer)row.get("rowid");
    }

    public static String getMinimalBaseName(File file)
    {
        return getMinimalBaseName(file.getName());
    }

    public static String getMinimalBaseName(String filename)
    {
        String bn;
        int i = 0;

        if (filename == null)
            return null;

        while (i < 20){
            bn = FilenameUtils.getBaseName(filename);
            if (bn.equals(filename)){
                break;
            }
            filename = bn;
            i++;
        }
        return filename;
    }

    public void swapFiles(WorkDirectory wd, File original, File newFile)
    {
        try
        {
            getJob().getLogger().debug("Swapping output file: ");

            String relPath = FilenameUtils.normalize(wd.getRelativePath(original));
            if (relPath == null)
            {
                relPath = FilenameUtils.normalize(original.getPath());
            }
            String relPath2 = FilenameUtils.normalize(wd.getRelativePath(newFile));
            if (relPath2 == null && newFile.exists())
            {
                relPath2 = FilenameUtils.normalize(newFile.getPath());
            }

            getJob().getLogger().debug("\tNew file: " + newFile.getPath());
            getJob().getLogger().debug("\tRelative path: " + relPath);
            if (_inputFiles.containsKey(relPath))
            {
                if (!_inputFiles.containsKey(relPath2))
                {
                    _inputFiles.put(relPath2, new ArrayList<Object[]>());
                }

                for(Object[] array : _inputFiles.get(relPath))
                {
                    getJob().getLogger().debug("\treplacing input: " + array[2] + " with " + newFile.getPath());
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
                    getJob().getLogger().debug("\treplacing output: " + array[2] + " with " + newFile.getPath());
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

    public void handleInputs(WorkDirectory wd) throws PipelineJobException
    {
        Set<File> inputs = new HashSet<>();
        inputs.addAll(getSupport().getInputFiles());

        getJob().getLogger().info("Cleaning up input files");

        String handling = getSettings().getInputfileTreatment();
        if ("delete".equals(handling))
        {
            for (File input : inputs)
            {
                if (input.exists())
                {
                    if (!_finalOutputs.contains(input))
                    {
                        getJob().getLogger().info("Deleting input file: " + input.getPath());

                        input.delete();
                        if (input.exists())
                        {
                            throw new PipelineJobException("Unable to delete file: " + input.getPath());
                        }
                    }
                    else
                    {
                        //this input file was not altered during normalization.  in this case, we move it into the analysis folder
                        getJob().getLogger().info("File was not altered by normalization.  Copying to analysis folder: " + input.getPath());
                        copyInputToAnalysisDir(input, wd);
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
                        getJob().getLogger().debug("Moving input file to analysis directory: " + input.getPath());
                        copyInputToAnalysisDir(input, wd);
                    }
                    else
                    {
                        getJob().getLogger().info("Compressing/Moving input file to analysis directory: " + input.getPath());
                        File compressed = Compress.compressGzip(input);
                        if (!compressed.exists())
                            throw new PipelineJobException("Unable to compress file: " + input);

                        swapFiles(wd, input, compressed);

                        input.delete();
                        copyInputToAnalysisDir(compressed, wd);
                    }
                }
            }
        }
        else
        {
            getJob().getLogger().info("\tInput files will be left alone");
        }

        deleteIntermediateFiles();
    }

    public void deleteIntermediateFiles() throws PipelineJobException
    {
        getJob().getLogger().info("Cleaning up intermediate files");

        Set<File> inputs = new HashSet<>();
        inputs.addAll(getSupport().getInputFiles());

        Set<String> inputPaths = getInputPaths();

        if (getSettings().isDeleteIntermediateFiles())
        {
            getJob().getLogger().debug("Intermediate files will be removed");

            for (File f : _intermediateFiles)
            {
                getJob().getLogger().debug("\tDeleting intermediate file: " + f.getPath());

                if (inputPaths.contains(f.getPath()))
                {
                    getJob().getLogger().debug("\tpath matches an input, skipping");
                    continue;
                }

                if (!f.exists())
                {
                    getJob().getLogger().debug("\tfile doesnt exist, skipping");
                    continue;
                }

                f.delete();
                if (f.exists())
                {
                    throw new PipelineJobException("Unable to delete file: " + f.getPath());
                }

                if (f.getParentFile().listFiles().length == 0)
                {
                    getJob().getLogger().debug("\talso deleting empty parent folder: " + f.getParentFile().getPath());
                    f.getParentFile().delete();
                }
            }
        }
        else
        {
            getJob().getLogger().debug("Intermediate files will be left alone");
        }
    }

    public File copyInputToAnalysisDir(File input, WorkDirectory wd) throws PipelineJobException
    {
        getJob().getLogger().debug("Copying input file to analysis directory: " + input.getPath());

        try
        {
            //NOTE: we assume the input is gzipped already
            File outputDir = getSupport().getAnalysisDirectory();
            File output = new File(outputDir, input.getName());
            if (output.exists())
            {
                if (_unalteredOutputs.contains(output))
                {
                    getJob().getLogger().debug("\tThis input was unaltered during normalization and a copy already exists in the analysis folder so the original will be discarded");
                    input.delete();
                    swapFiles(wd, input, output);
                    return output;
                }
                else
                {
                    output = new File(outputDir, FileUtil.getBaseName(input.getName()) + ".orig.gz");
                    getJob().getLogger().debug("\tA file with the expected output name already exists, so the original will be renamed: " + output.getPath());
                }
            }

            FileUtils.moveFile(input, output);
            if (!output.exists())
            {
                throw new PipelineJobException("Unable to move file: " + input.getPath());
            }

            swapFiles(wd, input, output);

            return output;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public FileAnalysisJobSupport getSupport()
    {
        return (FileAnalysisJobSupport)_job;
    }

    public File getExtraBarcodesFile()
    {
        return new File(getSupport().getAnalysisDirectory(), "extraBarcodes.txt");
    }

    public List<BarcodeModel> getExtraBarcodesFromFile() throws PipelineJobException
    {
        List<BarcodeModel> models = new ArrayList<>();
        File barcodes = getExtraBarcodesFile();

        if (barcodes.exists())
        {
            getJob().getLogger().debug("\tReading additional barcodes from file");

            CSVReader reader = null;

            try
            {
                reader = new CSVReader(new FileReader(barcodes), '\t');
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
            catch (FileNotFoundException e)
            {
                throw new PipelineJobException(e);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            finally
            {
                try
                {
                    if (reader != null)
                        reader.close();

                    barcodes.delete();
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }

        return models;
    }

    public void writeExtraBarcodes() throws PipelineJobException
    {
        BarcodeModel[] models = getSettings().getAdditionalBarcodes();
        if (models != null && models.length > 0)
        {
            File extraBarcodes = getExtraBarcodesFile();
            getJob().getLogger().debug("\tWriting additional barcodes to file: " + extraBarcodes.getPath());
            CSVWriter writer = null;
            try
            {
                writer = new CSVWriter(new FileWriter(extraBarcodes), '\t', CSVWriter.NO_QUOTE_CHARACTER);
                for (BarcodeModel m : models)
                {
                    writer.writeNext(new String[]{m.getName(), m.getSequence()});
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
            finally
            {
                if (writer != null)
                {
                    try
                    {
                        writer.close();
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
            }
        }
    }

    public Set<String> getInputPaths()
    {
        Set<String> inputPaths = new HashSet<>();
        for (File f : getSupport().getInputFiles())
        {
            inputPaths.add(f.getPath());
        }
        return inputPaths;
    }
}
