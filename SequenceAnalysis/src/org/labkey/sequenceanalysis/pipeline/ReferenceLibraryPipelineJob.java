package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileUrls;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
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
public class ReferenceLibraryPipelineJob extends PipelineJob
{
    private String _name;
    private String _description;
    private List<ReferenceLibraryMember> _libraryMembers;
    private Integer _libraryId = null;

    public ReferenceLibraryPipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, String name, String description, List<ReferenceLibraryMember> libraryMembers) throws IOException
    {
        super(ReferenceLibraryPipelineProvider.NAME, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _name = name;
        _description = description;
        _libraryMembers = libraryMembers;

        File outputDir = getOutputDir();
        setLogFile(new File(outputDir, FileUtil.makeFileNameWithTimestamp("referenceLibrary", "log")));
    }

    //for recreating an existing library
    public ReferenceLibraryPipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, Integer libraryId)throws IOException
    {
        super(null, new ViewBackgroundInfo(c, user, url), pipeRoot);

        TableInfo ti = DbSchema.get(SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
        Map rowMap = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowid"), libraryId), null).getObject(Map.class);
        if (rowMap == null)
        {
            throw new IllegalArgumentException("Library not found: " + libraryId);
        }

        _name = (String)rowMap.get("name");
        _description = (String)rowMap.get("description");
        _libraryMembers = null;
        _libraryId = libraryId;

        File outputDir = getOutputDir();
        setLogFile(new File(outputDir, FileUtil.makeFileNameWithTimestamp("referenceLibrary", "log")));
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

    public String getName()
    {
        return _name;
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

    public File getOutputDir() throws IOException
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
}
