package org.labkey.blast.model;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.blast.BLASTManager;
import org.labkey.blast.BLASTSchema;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/20/2014
 * Time: 3:51 PM
 */
public class BlastJob    
{
    private int _rowid;
    private String _databaseId;
    private String _title;
    private Map<String, Object> _params;
    private boolean _saveResults;
    private boolean _hasRun;
    private String _objectid;
    private String _container;
    private int _createdBy;
    private Date _created;
    private int _modifiedBy;
    private Date _modified;
    private String _jobId;
    private Integer _htmlFile;
    
    public BlastJob()
    {
        
    }

    public int getRowid()
    {
        return _rowid;
    }

    public void setRowid(int rowid)
    {
        _rowid = rowid;
    }

    public String getDatabaseId()
    {
        return _databaseId;
    }

    public void setDatabaseId(String databaseId)
    {
        _databaseId = databaseId;
    }

    public String getTitle()
    {
        return _title;
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public Map<String, Object> getParams()
    {
        return _params;
    }

    public void setParams(String params)
    {
        if (params == null)
            _params = null;

        _params = new HashMap<>(new JSONObject(params));
    }

    public void addParam(String name, String value)
    {
        if (_params == null)
            _params = new HashMap<>();

        _params.put(name, value);
    }

    public Map<String, Object> getParamMap()
    {
        return _params;
    }

    public boolean isSaveResults()
    {
        return _saveResults;
    }

    public void setSaveResults(boolean saveResults)
    {
        _saveResults = saveResults;
    }

    public boolean getHasRun()
    {
        return _hasRun;
    }

    public String getObjectid()
    {
        return _objectid;
    }

    public void setObjectid(String objectid)
    {
        _objectid = objectid;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public int getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public boolean isHasRun()
    {
        return _hasRun;
    }

    public void setHasRun(boolean hasRun)
    {
        _hasRun = hasRun;
    }

    public String getJobId()
    {
        return _jobId;
    }

    public void setJobId(String jobId)
    {
        _jobId = jobId;
    }

    public Integer getHtmlFile()
    {
        return _htmlFile;
    }

    public void setHtmlFile(Integer htmlFile)
    {
        _htmlFile = htmlFile;
    }

    public File getOutputDir()
    {
        Container c = ContainerManager.getForId(_container);
        if (c == null)
        {
            return null;
        }

        File outputDir = BLASTManager.get().getBlastRoot(c, true);
        if (!outputDir.exists())
        {
            outputDir.mkdirs();
        }

        return outputDir;
    }

    public File getExpectedOutputFile()
    {
        return new File(getOutputDir(), "blast-" + _objectid + ".html");
    }

    public File getExpectedInputFile()
    {
        return new File(getOutputDir(), "blast-" + _objectid + ".input");
    }

    public void setComplete(User u, @Nullable PipelineJob job)
    {
        setHasRun(true);

        if (!isSaveResults())
        {
            if (getExpectedInputFile().exists())
            {
                getExpectedInputFile().delete();
            }
        }
        else
        {
            File output = getExpectedOutputFile();
            Container c = ContainerManager.getForId(_container);
            ExpData data = ExperimentService.get().createData(c, new DataType("BLAST Output"));
            data.setName(output.getName());
            data.setDataFileURI(output.toURI());
            data.save(u);
            setHtmlFile(data.getRowId());
        }

        if (job != null)
        {
            setJobId(job.getJobGUID());
        }
        TableInfo jobs = DbSchema.get(BLASTSchema.NAME).getTable(BLASTSchema.TABLE_BLAST_JOBS);
        Table.update(u, jobs, this, getObjectid());
    }

    public JSONObject toJSON(User u, boolean includeHTML)
    {
        JSONObject ret = new JSONObject();
        ret.put("databaseId", _databaseId);
        ret.put("title", _title);
        ret.put("container", _container);
        ret.put("params", getParamMap());
        ret.put("jobId", _jobId);
        ret.put("htmlFile", _htmlFile);
        ret.put("created", _created);
        ret.put("createdBy", _createdBy);
        User createdUser = UserManager.getUser(_createdBy);
        if (createdUser != null)
        {
            ret.put("createdByDisplayName", createdUser.getDisplayName(u));
        }

        ret.put("hasRun", _hasRun);

        File expectedOutput = getExpectedOutputFile();
        ret.put("hasResults", expectedOutput.exists());

        if (includeHTML)
        {
            if (!getHasRun())
            {
                ret.put("html", "The job has not yet finished.  The page will refresh every 5 seconds until the job is complete.");
            }
            else if (!expectedOutput.exists())
            {
                ret.put("html", "Output file not found");
            }
            else
            {
                try
                {
                    String html = readFile(expectedOutput);
                    ret.put("html", html == null || html.isEmpty() ? "BLAST did not produce any output, see the log file for more information" : html);
                }
                catch (IOException e)
                {
                    //ignore
                }
            }
        }

        return ret;
    }

    //TODO: consider streaming
    private String readFile(File file) throws IOException
    {
        return new String(Files.readAllBytes(Paths.get(file.toURI())));
    }
}
