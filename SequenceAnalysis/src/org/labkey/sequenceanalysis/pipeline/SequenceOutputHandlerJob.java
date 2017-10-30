package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.HasJobParams;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 1/16/2015.
 */
public class SequenceOutputHandlerJob extends SequenceJob implements HasJobParams
{
    public static final String FOLDER_NAME = "sequenceOutput";

    private String _handlerClassName;

    public SequenceOutputHandlerJob(Container c, User user, @Nullable String jobName, PipeRoot pipeRoot, SequenceOutputHandler handler, List<SequenceOutputFile> files, JSONObject jsonParams) throws IOException
    {
        super(SequenceOutputHandlerPipelineProvider.NAME, c, user, jobName, pipeRoot, jsonParams, null, FOLDER_NAME);

        _handlerClassName = handler.getClass().getName();

        //for the purpose of caching files:
        for (SequenceOutputFile o : files)
        {
            o.cacheForRemoteServer();
        }

        saveFiles(files);
    }

    protected void saveFiles(List<SequenceOutputFile> files) throws IOException
    {
        if (files != null && !files.isEmpty())
        {
            try (XMLEncoder encoder = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(getSerializedOutputFilesFile()))))
            {
                getLogger().info("writing SequenceOutputFiles to XML: " + files.size());
                encoder.writeObject(new ArrayList<>(files));
            }
        }
        else
        {
            getLogger().debug("no output files to write to XML");
        }
    }

    protected List<SequenceOutputFile> readOutputFilesFromFile() throws PipelineJobException, IOException
    {
        File xml = getSerializedOutputFilesFile();
        if (xml.exists() && SequenceUtil.hasLineCount(xml))
        {
            try (XMLDecoder decoder = new XMLDecoder(new BufferedInputStream(new FileInputStream(xml))))
            {
                List<SequenceOutputFile> ret = (List<SequenceOutputFile>) decoder.readObject();
                getLogger().debug("read SequenceOutputFiles from file: " + ret.size());

                for (SequenceOutputFile so : ret)
                {
                    //rely on cached ExpData for filepaths in case there is path translation
                    SequenceJobSupportImpl support = getSequenceSupport();
                    if (so.getDataId() != null)
                    {
                        File f = support.getCachedData(so.getDataId());
                        if (f != null)
                        {
                            so.setFile(f);
                        }
                    }
                }

                return ret;
            }
            catch (Exception e)
            {
                getLogger().error(e.getMessage(), e);
                getLogger().error("contents of XML file: " + xml.getPath());
                try (BufferedReader reader = Readers.getReader(xml))
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        getLogger().error(line);
                    }
                }
            }
        }
        else
        {
            getLogger().debug("serialized XML file not found or is empty: " + xml.getPath());
        }

        return null;
    }

    public File getSerializedOutputFilesFile()
    {
        return new File(getDataDirectory(), FileUtil.getBaseName(getLogFile()) + ".outputs.xml");
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return  PipelineJobService.get().getTaskPipeline(new TaskId(SequenceOutputHandlerJob.class));
    }

    public SequenceOutputHandler getHandler()
    {
        SequenceOutputHandler handler = SequenceAnalysisManager.get().getFileHandler(_handlerClassName);
        if (handler == null)
        {
            throw new IllegalArgumentException("Unable to find handler: " + _handlerClassName);
        }

        return handler;
    }

    public List<SequenceOutputFile> getFiles() throws PipelineJobException
    {
        try
        {
            List<SequenceOutputFile> ret =  readOutputFilesFromFile();

            return ret == null ? Collections.emptyList() : ret;
        }
        catch (IOException e)
        {
            throw new PipelineJobException("Unable to file: " + getSerializedOutputFilesFile().getPath(), e);
        }
    }

    @Override
    public List<File> getInputFiles()
    {
        try
        {
            List<File> ret = new ArrayList<>();
            for (SequenceOutputFile o : getFiles())
            {
                ret.add(o.getFile());
            }

            return ret;
        }
        catch (Exception e)
        {
            _logger.error(e);
        }

        return null;
    }
}