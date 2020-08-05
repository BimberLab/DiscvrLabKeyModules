package org.labkey.sequenceanalysis.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.util.Pair;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
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

    protected TaskFileManagerImpl _fileManager;
    protected LinkedHashSet<RecordedAction> _recordedActions = null;
    protected boolean _isResume = false;
    protected Map<File, File> _copiedInputs = new HashMap<>();

    protected List<Pair<File, File>> _filesCopiedLocally = new ArrayList<>();

    //for serialization
    protected AbstractResumer()
    {

    }

    protected AbstractResumer(File localWorkDir, Logger log, TaskFileManagerImpl fileManager)
    {
        _localWorkDir = localWorkDir;
        _log = log;
        _fileManager = fileManager;
        _recordedActions = new LinkedHashSet<>();
        _filesCopiedLocally = new ArrayList<>();
    }

    protected static File getSerializedJson(File outdir, String jsonName)
    {
        return new File(outdir, jsonName);
    }

    protected static <RESUMER extends AbstractResumer> RESUMER readFromJson(File json, Class<RESUMER> clazz) throws PipelineJobException
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

    protected void writeToJson(File outDir) throws PipelineJobException
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

    public TaskFileManagerImpl getFileManager()
    {
        return _fileManager;
    }

    public void setFileManager(TaskFileManagerImpl fileManager)
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
}
