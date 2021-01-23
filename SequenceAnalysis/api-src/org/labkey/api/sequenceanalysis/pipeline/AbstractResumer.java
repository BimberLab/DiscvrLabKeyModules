package org.labkey.api.sequenceanalysis.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.util.Pair;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 4/2/2017.
 */
abstract public class AbstractResumer implements Serializable
{
    transient Logger _log;
    transient File _localWorkDir;

    protected TaskFileManager _fileManager;
    protected LinkedHashSet<RecordedAction> _recordedActions = null;
    protected boolean _isResume = false;
    protected Map<File, File> _copiedInputs = new HashMap<>();

    protected List<Pair<File, File>> _filesCopiedLocally = new ArrayList<>();

    //for serialization
    protected AbstractResumer()
    {

    }

    protected AbstractResumer(File localWorkDir, Logger log, TaskFileManager fileManager)
    {
        _localWorkDir = localWorkDir;
        _log = log;
        _fileManager = fileManager;
        _recordedActions = new LinkedHashSet<>();
        _filesCopiedLocally = new ArrayList<>();
    }

    public static File getSerializedJson(File outdir, String jsonName)
    {
        return new File(outdir, jsonName);
    }

    public static <RESUMER extends AbstractResumer> RESUMER readFromJson(File json, Class<RESUMER> clazz) throws PipelineJobException
    {
        try (BufferedInputStream bus = new BufferedInputStream(new FileInputStream(json)))
        {
            ObjectMapper objectMapper = PipelineJob.createObjectMapper();
            return objectMapper.readValue(bus, clazz);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    protected void writeToJson() throws PipelineJobException
    {
        writeToJson(_localWorkDir);
    }

    abstract protected String getJsonName();

    protected void logInfoBeforeSave()
    {
        _log.debug("total actions: " + _recordedActions.size());
        _log.debug("total sequence outputs: " + getFileManager().getOutputsToCreate().size());
    }

    public void writeToJson(File outDir) throws PipelineJobException
    {
        _log.debug("saving job checkpoint to file");
        logInfoBeforeSave();

        if (outDir == null)
        {
            throw new PipelineJobException("output directory was null");
        }

        File output = getSerializedJson(outDir, getJsonName());
        _log.debug("using file: " + output.getPath());
        try
        {
            ObjectMapper objectMapper = PipelineJob.createObjectMapper();
            objectMapper.writeValue(output, this);
        }
        catch (Throwable e)
        {
            throw new PipelineJobException(e);
        }
    }

    public void markComplete()
    {
        markComplete(true);
    }

    public void markComplete(boolean deleteFile)
    {
        File file = getSerializedJson(_localWorkDir, getJsonName());
        if (file.exists())
        {
            _log.info("closing job resumer");
            if (deleteFile)
                file.delete();
            else
                _log.debug("delete of file will be deferred: " + file.getPath());
        }
    }

    public void saveState() throws PipelineJobException
    {
        writeToJson();
    }

    public TaskFileManager getFileManager()
    {
        return _fileManager;
    }

    public void setFileManager(TaskFileManager fileManager)
    {
        _fileManager = fileManager;
    }

    public boolean isResume()
    {
        return _isResume;
    }

    public void setResume(boolean resume)
    {
        _isResume = resume;
    }

    public LinkedHashSet<RecordedAction> getRecordedActions()
    {
        return _recordedActions;
    }

    public void setRecordedActions(LinkedHashSet<RecordedAction> recordedActions)
    {
        _recordedActions = recordedActions;
    }

    public File getLocalWorkDir()
    {
        return _localWorkDir;
    }

    public void setLocalWorkDir(File localWorkDir)
    {
        _localWorkDir = localWorkDir;
    }

    public Logger getLogger()
    {
        return _log;
    }

    public void setLogger(Logger log)
    {
        _log = log;
    }

    public Map<File, File> getCopiedInputs()
    {
        return _copiedInputs;
    }

    public void setCopiedInputs(Map<File, File> copiedInputs)
    {
        _copiedInputs = copiedInputs;
    }

    public List<Pair<File, File>> getFilesCopiedLocally()
    {
        return _filesCopiedLocally;
    }

    public void setFilesCopiedLocally(List<Pair<File, File>> filesCopiedLocally)
    {
        _filesCopiedLocally = filesCopiedLocally;
    }

    public void addFileCopiedLocally(File orig, File copied)
    {
        _filesCopiedLocally.add(Pair.of(orig, copied));
    }

    public static <T extends AbstractResumer> T create(SequenceOutputHandler.JobContext ctx, String jsonName, Class<T> clazz) throws PipelineJobException
    {
        if (!(ctx instanceof SequenceOutputHandler.MutableJobContext))
        {
            throw new IllegalArgumentException("Expected JobContext to be instance of MutableJobContext");
        }

        T ret;
        File json = getSerializedJson(ctx.getSourceDirectory(), jsonName);
        if (!json.exists())
        {
            try
            {
                ret = clazz.getDeclaredConstructor(SequenceOutputHandler.JobContext.class).newInstance(ctx);
            }
            catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e)
            {
                throw new PipelineJobException(e);
            }
        }
        else
        {
            ret = readFromJson(json, clazz);
            ret.setResume(true);
            ret.setLogger(ctx.getLogger());
            ret.setLocalWorkDir(ctx.getWorkDir().getDir());
            ret.getFileManager().onResume(ctx.getJob(), ctx.getWorkDir());

            ctx.getLogger().debug("FileManagers initially equal: " + ctx.getFileManager().equals(ret.getFileManager()));

            ctx.getLogger().debug("Replacing fileManager on JobContext");
            ((SequenceOutputHandler.MutableJobContext)ctx).setFileManager(ret.getFileManager());
            try
            {
                if (!ret.getCopiedInputs().isEmpty())
                {
                    for (File orig : ret.getCopiedInputs().keySet())
                    {
                        ctx.getWorkDir().inputFile(orig, ret._copiedInputs.get(orig), false);
                    }
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            //debugging:
            ctx.getLogger().debug("loaded from file.  total recorded actions: " + ret.getRecordedActions().size());
            ctx.getLogger().debug("total sequence outputs: " + ret.getFileManager().getOutputsToCreate().size());
            ctx.getLogger().debug("total intermediate files: " + ret.getFileManager().getIntermediateFiles().size());
            for (RecordedAction a : ret.getRecordedActions())
            {
                ctx.getLogger().debug("action: " + a.getName() + ", inputs: " + a.getInputs().size() + ", outputs: " + a.getOutputs().size());
            }

            if (ret._recordedActions == null)
            {
                throw new PipelineJobException("Job read from file, but did not have any saved actions.  This indicates a problem w/ serialization.");
            }
        }

        if (ret.isResume())
        {
            ctx.getLogger().info("resuming previous job");

        }

        boolean fmEqual = ctx.getFileManager().equals(ret._fileManager);
        ctx.getLogger().debug("FileManagers on resumer and JobContext equal: " + fmEqual);

        return ret;
    }

    public void markComplete(SequenceOutputHandler.JobContext ctx)
    {
        // NOTE: due to the way the resumer is set up, the FileManager used by the Resumer is a different
        // instance than the JobContext, meaning we need to manually pass information back to the primary FileManager
        ctx.getLogger().debug("total sequence outputs tracked in resumer: " + getFileManager().getOutputsToCreate().size());
        for (SequenceOutputFile so : getFileManager().getOutputsToCreate())
        {
            ctx.addSequenceOutput(so);
        }

        ctx.getLogger().debug("total actions tracked in resumer: " + getRecordedActions().size());
        for (RecordedAction a : getRecordedActions())
        {
            ctx.addActions(a);
        }

        ctx.getFileManager().addIntermediateFiles(getFileManager().getIntermediateFiles());

        markComplete();
    }
}
