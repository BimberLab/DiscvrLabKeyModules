package org.labkey.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.TaskPipelineSettings;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.model.ReferenceLibraryMember;

import java.io.File;
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
    private String _description;
    private List<ReferenceLibraryMember> _libraryMembers;
    private Integer _libraryId = null;
    private ReferenceGenomeImpl _referenceGenome = null;

    public ReferenceLibraryPipelineJob(Container c, User user, PipeRoot pipeRoot, String name, String description, @Nullable List<ReferenceLibraryMember> libraryMembers, @Nullable Integer libraryId) throws IOException
    {
        super(ReferenceLibraryPipelineProvider.NAME, c, user, name, pipeRoot, new JSONObject(), new TaskId(TaskPipelineSettings.class, "referenceLibraryPipeline"), null);
        _description = description;
        _libraryMembers = libraryMembers;
        _libraryId = libraryId;
    }

    @Override
    protected void createLogFile() throws IOException
    {
        File outputDir = createLocalDirectory(getPipeRoot());
        setLogFile(new File(outputDir, FileUtil.makeFileNameWithTimestamp("referenceLibrary", "log")));
    }

    //for recreating an existing library
    public static ReferenceLibraryPipelineJob recreate(Container c, User user, PipeRoot pipeRoot, Integer libraryId)throws IOException
    {
        TableInfo ti = DbSchema.get(SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
        Map rowMap = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowid"), libraryId), null).getObject(Map.class);
        if (rowMap == null)
        {
            throw new IllegalArgumentException("Library not found: " + libraryId);
        }

        return new ReferenceLibraryPipelineJob(c, user, pipeRoot, (String)rowMap.get("name"), (String)rowMap.get("description"), null, libraryId);
    }

    public String getName()
    {
        return getProtocolName();
    }

    @Override
    public String getDescription()
    {
        return "Create Reference Genome";
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(ReferenceLibraryPipelineJob.class));
    }

    public String getLibraryDescription()
    {
        return _description;
    }

    public List<ReferenceLibraryMember> getLibraryMembers()
    {
        return _libraryMembers;
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
        return getLibraryId() == null;
    }

    protected File createLocalDirectory(PipeRoot pipeRoot) throws IOException
    {
        File outputDir = SequenceAnalysisManager.get().getReferenceLibraryDir(getContainer());
        if (outputDir == null)
        {
            throw new IOException("No pipeline directory set for folder: " + getContainer().getPath());
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
}
