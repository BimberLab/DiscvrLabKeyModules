package org.labkey.jbrowse.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.jbrowse.JBrowseRoot;
import org.labkey.jbrowse.JBrowseSchema;

import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/21/2014
 * Time: 10:33 AM
 */
public class JBrowseSessionPipelineJob extends PipelineJob
{
    private Integer _libraryId = null;
    private List<Integer> _trackIds = null;
    private List<Integer> _outputFileIds = null;
    private String _databaseGuid = null;
    private String _name = null;
    private String _description = null;
    private List<String> _jsonFiles = null;
    private Mode _mode;

    public static JBrowseSessionPipelineJob addMembers(Container c, User user, PipeRoot pipeRoot, String databaseGuid, List<Integer> trackIds, List<Integer> outputFileIds)
    {
        //find existing record
        TableSelector ts = new TableSelector(DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_DATABASES), new SimpleFilter(FieldKey.fromString("objectid"), databaseGuid), null);
        Map<String, Object> existingRow = ts.getObject(Map.class);
        if (existingRow == null)
        {
            throw new IllegalArgumentException("Unknown database: " + databaseGuid);
        }

        return new JBrowseSessionPipelineJob(c, user, pipeRoot, (String)existingRow.get("name"), (String)existingRow.get("description"), null, trackIds, outputFileIds, databaseGuid);
    }

    public static JBrowseSessionPipelineJob refreshResources(Container c, User user, PipeRoot pipeRoot, List<String> jsonFiles)
    {
        return new JBrowseSessionPipelineJob(c, user, pipeRoot, jsonFiles, null, Mode.ReprocessResources);
    }

    public static JBrowseSessionPipelineJob recreateDatabase(Container c, User user, PipeRoot pipeRoot, String databaseGuid)
    {
        return new JBrowseSessionPipelineJob(c, user, pipeRoot, null, databaseGuid, Mode.RecreateDatabase);
    }

    public static JBrowseSessionPipelineJob createNewDatabase(Container c, User user, PipeRoot pipeRoot, String name, String description, Integer libraryId, List<Integer> trackIds, List<Integer> outputFileIds)
    {
        return new JBrowseSessionPipelineJob(c, user, pipeRoot, name, description, libraryId, trackIds, outputFileIds, null);
    }

    private JBrowseSessionPipelineJob(Container c, User user, PipeRoot pipeRoot, List<String> jsonFiles, String databaseGuid, Mode mode)
    {
        super(JBrowseSessionPipelineProvider.NAME, new ViewBackgroundInfo(c, user, null), pipeRoot);
        _jsonFiles = jsonFiles;
        _databaseGuid = databaseGuid;
        _mode = mode;

        AssayFileWriter writer = new AssayFileWriter();
        JBrowseRoot root = new JBrowseRoot(null);
        setLogFile(writer.findUniqueFileName("jbrowse-" + (new GUID().toString()) + ".log", root.getBaseDir(c)));
    }

    private JBrowseSessionPipelineJob(Container c, User user, PipeRoot pipeRoot, String name, String description, Integer libraryId, List<Integer> trackIds, List<Integer> outputFileIds, @Nullable String existingDatabaseGuid)
    {
        super(JBrowseSessionPipelineProvider.NAME, new ViewBackgroundInfo(c, user, null), pipeRoot);
        _libraryId = libraryId;
        _trackIds = trackIds;
        _outputFileIds = outputFileIds;

        _name = name;
        _description = description;
        _mode = existingDatabaseGuid == null ? Mode.CreateNew : Mode.AddToExisting;
        _databaseGuid = existingDatabaseGuid == null ? new GUID().toString().toUpperCase() : existingDatabaseGuid;

        AssayFileWriter writer = new AssayFileWriter();
        JBrowseRoot root = new JBrowseRoot(getLogger());
        setLogFile(writer.findUniqueFileName("jbrowse-" + _databaseGuid + ".log", root.getBaseDir(c)));
    }

    @Override
    public String getDescription()
    {
        return _mode.getDescription();
    }


    @Override
    public ActionURL getStatusHref()
    {
        if (getDatabaseGuid() != null)
        {
            return DetailsURL.fromString("jbrowse/browser.view?database=" + getDatabaseGuid(), getContainer()).getActionURL();
        }

        return null;
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

    public Integer getLibraryId()
    {
        return _libraryId;
    }

    public List<Integer> getTrackIds()
    {
        return _trackIds;
    }

    public List<String> getJsonFiles()
    {
        return _jsonFiles;
    }

    public List<Integer> getOutputFileIds()
    {
        return _outputFileIds;
    }

    public Mode getMode()
    {
        return _mode;
    }

    public enum Mode
    {
        CreateNew("Create New Session"),
        AddToExisting("Add To Existing Session"),
        ReprocessResources("Recreating Resources"),
        RecreateDatabase("Recreate Session");

        private String _description;

        Mode(String description)
        {
            _description = description;
        }

        public String getDescription()
        {
            return _description;
        }
    }
}
