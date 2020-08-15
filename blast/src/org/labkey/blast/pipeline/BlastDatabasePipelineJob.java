package org.labkey.blast.pipeline;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileUrls;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.blast.BLASTManager;
import org.labkey.blast.BLASTSchema;

import java.io.File;
import java.util.Date;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/21/2014
 * Time: 10:33 AM
 */
public class BlastDatabasePipelineJob extends PipelineJob
{
    private Integer _libraryId;
    private String _databaseGuid;
    private File _fasta;
    private File _databaseDir;

    // Default constructor for serialization
    protected BlastDatabasePipelineJob()
    {
    }

    public BlastDatabasePipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, Integer libraryId) throws PipelineJobException
    {
        super(BlastDatabasePipelineProvider.NAME, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _libraryId = libraryId;
        _databaseGuid = new GUID().toString().toUpperCase();
        setLogFile(new File(BLASTManager.get().getDatabaseDir(c, true), "blast-" + _databaseGuid + ".log"));

        ReferenceGenome rg = SequenceAnalysisService.get().getReferenceGenome(_libraryId, user);
        _fasta = rg.getSourceFastaFile();
        _databaseDir = BLASTManager.get().getDatabaseDir(getContainer(), true);
        possiblyCreateDbRecord();
    }

    public static BlastDatabasePipelineJob recreate(Container c, User user, ActionURL url, PipeRoot pipeRoot, String databaseGuid) throws PipelineValidationException, PipelineJobException
    {
        //find library based on DB
        TableInfo databases = BLASTSchema.getInstance().getSchema().getTable(BLASTSchema.TABLE_DATABASES);
        Integer libraryId = new TableSelector(databases, PageFlowUtil.set("libraryid"), new SimpleFilter(FieldKey.fromString("objectid"), databaseGuid), null).getObject(Integer.class);
        if (libraryId == null)
        {
            throw new PipelineValidationException("Unknown BLAST database: " + databaseGuid);
        }

        return new BlastDatabasePipelineJob(c, user, url, pipeRoot, libraryId);
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

    public File getFasta()
    {
        return _fasta;
    }

    public File getDatabaseDir()
    {
        return _databaseDir;
    }

    private void possiblyCreateDbRecord()
    {
        getLogger().info("Possibly creating database record for library: " + getLibraryId());

        TableInfo referenceLibraries = DbSchema.get(BLASTManager.SEQUENCE_ANALYSIS).getTable("reference_libraries");
        TableSelector ts = new TableSelector(referenceLibraries, PageFlowUtil.set("name", "description", "fasta_file"), new SimpleFilter(FieldKey.fromString("rowid"), getLibraryId()), null);
        Map<String, Object> libraryMap = ts.getMap();
        Integer dataId = (Integer)libraryMap.get("fasta_file");
        if (dataId == null)
        {
            throw new IllegalArgumentException("Library lacks FASTA file: " + getLibraryId());
        }

        //it is possible to recreate the files for an existing DB.  check to make sure this doesnt already exist
        TableInfo databases = BLASTSchema.getInstance().getSchema().getTable(BLASTSchema.TABLE_DATABASES);
        boolean dbExists = new TableSelector(databases, new SimpleFilter(FieldKey.fromString("objectid"), getDatabaseGuid()), null).exists();
        if (dbExists)
        {
            getLogger().info("database record already exists");
        }
        else
        {
            //create the database record
            CaseInsensitiveHashMap<Object> databaseRecord = new CaseInsensitiveHashMap<>();
            databaseRecord.put("name", libraryMap.get("name"));
            databaseRecord.put("description", libraryMap.get("description"));
            databaseRecord.put("libraryid", getLibraryId());
            databaseRecord.put("objectid", getDatabaseGuid());

            databaseRecord.put("container", getContainer().getId());
            databaseRecord.put("created", new Date());
            databaseRecord.put("createdby", getUser().getUserId());
            databaseRecord.put("modified", new Date());
            databaseRecord.put("modifiedby", getUser().getUserId());

            Table.insert(getUser(), databases, databaseRecord);
        }
    }
}
