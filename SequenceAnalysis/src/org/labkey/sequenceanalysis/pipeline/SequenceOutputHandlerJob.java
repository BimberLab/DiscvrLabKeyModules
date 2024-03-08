package org.labkey.sequenceanalysis.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import htsjdk.samtools.util.IOUtil;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.HasJobParams;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    // Default constructor for serialization
    protected SequenceOutputHandlerJob()
    {
    }

    protected SequenceOutputHandlerJob(SequenceOutputHandlerJob parentJob, String jobName, String subfolder) throws IOException
    {
        super(parentJob, jobName, subfolder);
        _handlerClassName = parentJob._handlerClassName;
    }

    public SequenceOutputHandlerJob(Container c, User user, @Nullable String jobName, PipeRoot pipeRoot, SequenceOutputHandler<?> handler, List<SequenceOutputFile> files, JSONObject jsonParams) throws IOException
    {
        super(SequenceOutputHandlerPipelineProvider.NAME, c, user, jobName, pipeRoot, jsonParams, null, FOLDER_NAME);

        _handlerClassName = handler.getClass().getName();

        //for the purpose of caching files:
        for (SequenceOutputFile o : files)
        {
            o.cacheForRemoteServer();
        }

        saveFiles(files);
        writeSupportToDisk();
    }

    protected void saveFiles(List<SequenceOutputFile> files) throws IOException
    {
        if (files != null && !files.isEmpty())
        {
            File xml = getSerializedOutputFilesFile();

            //remove legacy non-gz file if exists.  remove code eventually
            File legacyXml = new File(FileUtil.getBaseName(xml));
            if (legacyXml.exists())
            {
                legacyXml.delete();
            }

            try (OutputStream output = IOUtil.maybeBufferOutputStream(IOUtil.openFileForWriting(xml)))
            {
                getLogger().info("writing SequenceOutputFiles to XML: " + files.size());
                ObjectMapper objectMapper = createObjectMapper();
                objectMapper.writeValue(output, new ArrayList<>(files));
            }
        }
        else
        {
            getLogger().debug("no output files to write to XML");
        }
    }

    @Override
    protected boolean shouldAllowArchivedReadsets()
    {
        return true;
    }

    protected List<SequenceOutputFile> readOutputFilesFromFile() throws PipelineJobException, IOException
    {
        File xml = getSerializedOutputFilesFile();

        //legacy support for non-gz version.  remove eventually
        if (!xml.exists())
        {
            xml = new File(FileUtil.getBaseName(xml));
        }

        if (xml.exists() && SequenceUtil.hasLineCount(xml))
        {
            try (InputStream is = IOUtil.maybeBufferInputStream(IOUtil.openFileForReading(xml)))
            {
                ObjectMapper objectMapper = createObjectMapper();
                List<SequenceOutputFile> ret = objectMapper.readValue(is, new TypeReference<List<SequenceOutputFile>>(){});
                getLogger().debug("read SequenceOutputFiles from file, total: " + ret.size());

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
                getLogger().debug("contents of XML file: " + xml.getPath());
                try (BufferedReader reader = IOUtil.openFileForBufferedReading(xml))
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        getLogger().debug(line);
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
        if (isSplitJob())
        {
            String logName = FileUtil.getBaseName(getLogFile());
            logName = logName.substring(0, logName.lastIndexOf("-Job"));

            return new File(getWebserverDir(true), logName + ".outputs.json.gz");
        }

        return new File(getDataDirectory(), FileUtil.getBaseName(getLogFile()) + ".outputs.json.gz");
    }

    @Override
    public TaskPipeline<?> getTaskPipeline()
    {
        return  PipelineJobService.get().getTaskPipeline(new TaskId(SequenceOutputHandlerJob.class));
    }

    public SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> getHandler()
    {
        SequenceOutputHandler handler = SequenceAnalysisManager.get().getFileHandler(_handlerClassName, SequenceOutputHandler.TYPE.OutputFile);
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
            throw new PipelineJobException("Unable to find file: " + getSerializedOutputFilesFile().getPath(), e);
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