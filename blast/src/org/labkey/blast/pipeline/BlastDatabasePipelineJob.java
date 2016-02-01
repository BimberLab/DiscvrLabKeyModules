package org.labkey.blast.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileUrls;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.blast.BLASTManager;
import org.labkey.blast.BLASTSchema;

import java.io.File;

/**
 * User: bimber
 * Date: 7/21/2014
 * Time: 10:33 AM
 */
public class BlastDatabasePipelineJob extends PipelineJob
{
    private Integer _libraryId;
    private String _databaseGuid;

    public BlastDatabasePipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, Integer libraryId)
    {
        super(BlastDatabasePipelineProvider.NAME, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _libraryId = libraryId;
        _databaseGuid = new GUID().toString().toUpperCase();
        setLogFile(new File(BLASTManager.get().getDatabaseDir(c, true), "blast-" + _databaseGuid + ".log"));
    }

    public BlastDatabasePipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, String databaseGuid) throws PipelineValidationException
    {
        super(null, new ViewBackgroundInfo(c, user, url), pipeRoot);

        //find library based on DB
        TableInfo databases = DbSchema.get(BLASTSchema.NAME).getTable(BLASTSchema.TABLE_DATABASES);
        _libraryId = new TableSelector(databases, PageFlowUtil.set("libraryid"), new SimpleFilter(FieldKey.fromString("objectid"), databaseGuid), null).getObject(Integer.class);
        if (_libraryId == null)
        {
            throw new PipelineValidationException("Unknown BLAST database: " + databaseGuid);
        }

        _databaseGuid = databaseGuid;
        setLogFile(new File(BLASTManager.get().getDatabaseDir(c, true), "blast-" + _databaseGuid + ".log"));
    }

    @Override
    public String getDescription()
    {
        return "Create BLAST Database";
    }

    @Override
    public ActionURL getStatusHref()
    {
        return PageFlowUtil.urlProvider(FileUrls.class).urlBegin(getContainer());
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(BlastDatabasePipelineJob.class));
    }

    public Integer getLibraryId()
    {
        return _libraryId;
    }

    public String getDatabaseGuid()
    {
        return _databaseGuid;
    }

    public File getDatabaseDir()
    {
        return BLASTManager.get().getDatabaseDir(getContainer(), true);
    }
}
