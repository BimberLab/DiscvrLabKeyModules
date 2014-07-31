package org.labkey.jbrowse.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileUrls;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.jbrowse.JBrowseManager;
import org.labkey.jbrowse.JBrowseSchema;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/21/2014
 * Time: 10:33 AM
 */
public class JBrowseSessionPipelineJob extends PipelineJob
{
    private List<Integer> _libraryIds;
    private List<Integer> _ntIds;
    private List<Integer> _trackIds;
    private String _databaseGuid;
    private String _name;
    private String _description;
    private boolean _createNew;

    public static JBrowseSessionPipelineJob addMembers(Container c, User user, PipeRoot pipeRoot, String databaseGuid, List<Integer> libraryIds, List<Integer> ntIds, List<Integer> trackIds)
    {
        //find existing record
        TableSelector ts = new TableSelector(DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_DATABASES), new SimpleFilter(FieldKey.fromString("objectid"), databaseGuid), null);
        Map<String, Object> existingRow = ts.getObject(Map.class);
        if (existingRow == null)
        {
            throw new IllegalArgumentException("Unknown database: " + databaseGuid);
        }

        return new JBrowseSessionPipelineJob(c, user, pipeRoot, (String)existingRow.get("name"), (String)existingRow.get("description"), libraryIds, ntIds, trackIds, databaseGuid);
    }

    public static JBrowseSessionPipelineJob createNewDatabase(Container c, User user, PipeRoot pipeRoot, String name, String description, List<Integer> libraryIds, List<Integer> ntIds, List<Integer> trackIds)
    {
        return new JBrowseSessionPipelineJob(c, user, pipeRoot, name, description, libraryIds, ntIds, trackIds, null);
    }

    private JBrowseSessionPipelineJob(Container c, User user, PipeRoot pipeRoot, String name, String description, List<Integer> libraryIds, List<Integer> ntIds, List<Integer> trackIds, @Nullable String existingDatabaseGuid)
    {
        super(null, new ViewBackgroundInfo(c, user, null), pipeRoot);
        _libraryIds = libraryIds;
        _trackIds = trackIds;
        _ntIds = ntIds;

        _name = name;
        _description = description;
        _createNew = existingDatabaseGuid == null;
        _databaseGuid = existingDatabaseGuid == null ? new GUID().toString().toUpperCase() : existingDatabaseGuid;

        AssayFileWriter writer = new AssayFileWriter();
        setLogFile(writer.findUniqueFileName("jbrowse-" + _databaseGuid + ".log", JBrowseManager.get().getJBrowseRoot()));
    }

    @Override
    public String getDescription()
    {
        return "Create JBrowse Session";
    }

    @Override
    public ActionURL getStatusHref()
    {
        return PageFlowUtil.urlProvider(FileUrls.class).urlBegin(getContainer());
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(JBrowseSessionPipelineJob.class));
    }

    public String getDatabaseGuid()
    {
        return _databaseGuid;
    }

    public String getName()
    {
        return _name;
    }

    public String getDatabaseDescription()
    {
        return _description;
    }

    public List<Integer> getLibraryIds()
    {
        return _libraryIds;
    }

    public List<Integer> getNtIds()
    {
        return _ntIds;
    }

    public List<Integer> getTrackIds()
    {
        return _trackIds;
    }

    public boolean isCreateNew()
    {
        return _createNew;
    }
}
