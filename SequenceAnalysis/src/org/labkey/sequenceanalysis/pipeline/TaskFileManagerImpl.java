package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepOutput;
import org.labkey.sequenceanalysis.api.pipeline.TaskFileManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
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
    private List<File> _finalOutputs = new ArrayList<>();
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
                    File f = new File(_workLocation, line);
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
                        f.delete();

                        File parentDir = f.getParentFile();
                        if (parentDir.list().length == 0)
                        {
                            parentDir.delete();
                        }
                    }
                    else
                    {
                        _job.getLogger().warn("could not find file to delete");
                    }
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
    public void addIntermediateFile(File f)
    {
        _intermediateFiles.add(f);
    }

    @Override
    public void addIntermediateFiles(Collection<File> files)
    {
        _intermediateFiles.addAll(files);
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

                f.delete();
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
            for(File f : file.listFiles())
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
        try
        {
            // Get rid of any copied input files.
            _wd.discardCopiedInputs();

            //NOTE: preserving relative locations is a pain.  therefore we copy all outputs, including directories
            //then sort out which files were specified as named outputs later
            for(File input : _wd.getDir().listFiles())
            {
                String path = _wd.getRelativePath(input);
                File dest = new File(getSupport().getAnalysisDirectory(), path);
                dest = _wd.outputFile(input, dest);

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
                    action.addOutput(file, role, isTransient);
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    @Override
    public void addFinalOutputFile(File f)
    {
        _finalOutputs.add(f);
    }

    @Override
    public void addFinalOutputFiles(Collection<File> files)
    {
        _finalOutputs.addAll(files);
    }

    @Override
    public void addUnalteredOutput(File file)
    {
        _unalteredOutputs.add(file);
    }

    @Override
    public List<File> getFinalOutputFiles()
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
    public void decompressInputFiles(List<File> inputFiles, List<RecordedAction> actions)
    {
        FileType gz = new FileType(".gz");

        for (File i : inputFiles)
        {
            //NOTE: because we can initate runs on readsets from different containers, we cannot rely on dataDirectory() to be consistent
            //b/c inputs are always copied to the root of the analysis folder, we will use relative paths
            File unzipped = null;
            if (gz.isType(i))
            {
                //NOTE: we use relative paths in all cases here
                _job.getLogger().debug("Decompressing file: " + i.getPath());

                unzipped = new File(_wd.getDir(), i.getName().replaceAll(".gz$", ""));
                _job.getLogger().debug("\tunzipped: " + unzipped.getPath());
                unzipped = Compress.decompressGzip(i, unzipped);

                _unzippedMap.put(i, unzipped);

                RecordedAction action = new RecordedAction(SequenceAlignmentTask.DECOMPRESS_ACTIONNAME);
                action.addInput(i, "Compressed File");
                action.addOutput(unzipped, "Decompressed File", true);
                actions.add(action);
            }
        }

        if (_unzippedMap.size() > 0)
        {
            //swap unzipped files
            for (File f : _unzippedMap.keySet())
            {
                inputFiles.remove(f);
                inputFiles.add(_unzippedMap.get(f));
            }
        }
    }
}
