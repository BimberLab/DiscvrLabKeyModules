package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.model.ReferenceLibraryMember;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/21/2014
 * Time: 10:33 AM
 */
public class ReferenceLibraryPipelineJob extends SequenceJob
{
    public static final String FOLDER_NAME = "referenceLibrary";

    private String _description;
    private Integer _libraryId = null;
    private ReferenceGenomeImpl _referenceGenome = null;
    private boolean _isNew;
    private boolean _skipCacheIndexes = false;

    public ReferenceLibraryPipelineJob(Container c, User user, PipeRoot pipeRoot, String name, String description, @Nullable List<ReferenceLibraryMember> libraryMembers, @Nullable Integer libraryId, boolean skipCacheIndexes) throws IOException
    {
        super(ReferenceLibraryPipelineProvider.NAME, c, user, name, pipeRoot, new JSONObject(), new TaskId(ReferenceLibraryPipelineJob.class), FOLDER_NAME);
        _description = description;
        _libraryId = libraryId;
        _isNew = libraryId == null;
        _skipCacheIndexes = skipCacheIndexes;

        saveLibraryMembersToFile(libraryMembers);
    }

    private void saveLibraryMembersToFile(List<ReferenceLibraryMember> libraryMembers) throws IOException
    {
        if (libraryMembers != null)
        {
            try (XMLEncoder encoder = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(getSerializedLibraryMembersFile()))))
            {
                getLogger().info("writing libraryMembers to XML: " + libraryMembers.size());
                encoder.writeObject(libraryMembers);
            }
        }
    }

    private List<ReferenceLibraryMember> readLibraryMembersFromFile() throws IOException
    {
        File xml = getSerializedLibraryMembersFile();
        if (xml.exists())
        {
            try (XMLDecoder decoder = new XMLDecoder(new BufferedInputStream(new FileInputStream(xml))))
            {
                List<ReferenceLibraryMember> ret = (List<ReferenceLibraryMember>) decoder.readObject();
                getLogger().debug("read libraryMembers from file: " + ret.size());

                return ret;
            }
        }

        return null;
    }

    @Override
    protected void writeParameters(JSONObject params) throws IOException
    {
        //no need to write params
    }

    public File getSerializedLibraryMembersFile()
    {
        File outputDir = createLocalDirectory(getPipeRoot());

        return new File(outputDir, FileUtil.makeLegalName(getName()) + ".xml");
    }

    @Override
    protected void createLogFile() throws IOException
    {
        File outputDir = createLocalDirectory(getPipeRoot());
        setLogFile(new File(outputDir, FileUtil.makeFileNameWithTimestamp("referenceLibrary", "log")));
    }

    //for recreating an existing library
    public static ReferenceLibraryPipelineJob recreate(Container c, User user, PipeRoot pipeRoot, Integer libraryId, boolean skipCacheIndexes)throws IOException
    {
        TableInfo ti = DbSchema.get(SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
        Map rowMap = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowid"), libraryId), null).getObject(Map.class);
        if (rowMap == null)
        {
            throw new IllegalArgumentException("Library not found: " + libraryId);
        }

        return new ReferenceLibraryPipelineJob(c, user, pipeRoot, (String)rowMap.get("name"), (String)rowMap.get("description"), null, libraryId, skipCacheIndexes);
    }

    public String getName()
    {
        return getProtocolName();
    }

    @Override
    public String getDescription()
    {
        return (isCreateNew() ? "Create" : "Recreate") + " Reference Genome";
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        TaskPipeline taskPipeline = PipelineJobService.get().getTaskPipeline(new TaskId(ReferenceLibraryPipelineJob.class));
        if (taskPipeline != null)
        {
            getLogger().debug("active task: " + getActiveTaskId().getNamespaceClass());
            for (TaskId taskId : taskPipeline.getTaskProgression())
            {
                getLogger().debug("task: " + taskId.getNamespaceClass());
                TaskFactory taskFactory = PipelineJobService.get().getTaskFactory(taskId);
                if (taskFactory == null)
                    getLogger().warn("Task '" + taskId + "' not found");
                else
                    getLogger().debug("location: " + taskFactory.getExecutionLocation());
            }
        }
        else
        {
            getLogger().warn("taskPipeline is null");
        }

        return taskPipeline;
    }

    public String getLibraryDescription()
    {
        return _description;
    }

    public List<ReferenceLibraryMember> getLibraryMembers() throws PipelineJobException
    {
        try
        {
            return readLibraryMembersFromFile();
        }
        catch (IOException e)
        {
            throw new PipelineJobException("Unable to read library file: " + getSerializedLibraryMembersFile().getPath(), e);
        }
    }

    @Override
    public JSONObject getParameterJson()
    {
        return new JSONObject();
    }

    public Integer getLibraryId()
    {
        return _libraryId;
    }

    public void setLibraryId(Integer libraryId)
    {
        _libraryId = libraryId;
    }

    public boolean isCreateNew()
    {
        return _isNew;
    }

    protected File createLocalDirectory(PipeRoot pipeRoot)
    {
        File outputDir = SequenceAnalysisManager.get().getReferenceLibraryDir(getContainer());
        if (outputDir == null)
        {
            throw new RuntimeException("No pipeline directory set for folder: " + getContainer().getPath());
        }

        if (!outputDir.exists())
        {
            outputDir.mkdirs();
        }

        return outputDir;
    }

    @Override
    public ActionURL getStatusHref()
    {
        if (_libraryId != null)
        {
            ActionURL ret = QueryService.get().urlFor(getUser(), getContainer(), QueryAction.executeQuery, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
            ret.addParameter("query.rowid~eq", _libraryId);

            return ret;
        }
        return null;
    }

    public ReferenceGenomeImpl getReferenceGenome()
    {
        return _referenceGenome;
    }

    public void setReferenceGenome(ReferenceGenomeImpl referenceGenome)
    {
        _referenceGenome = referenceGenome;
    }

    public boolean skipCacheIndexes()
    {
        return _skipCacheIndexes;
    }
}
