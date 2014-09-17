package org.labkey.sequenceanalysis.model;

import org.apache.commons.beanutils.ConvertUtils;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by bimber on 8/8/2014.
 */
public class AnalysisModelImpl implements AnalysisModel
{
    private Integer _rowId;
    private String _type;
    private Integer _runId;
    private Integer _createdby;
    private Date _created;
    private Integer _modifiedby;
    private Date _modified;
    private String _container;
    private Integer _readset;
    private Integer _alignmentFile;
    private Integer _reference_library;
    private Integer _library_id;
    private String _description;
    private String _synopsis;
    private Map<Integer, String> _cachedFilePaths = new HashMap<>();

    public AnalysisModelImpl()
    {

    }

    public static AnalysisModel getFromDb(int analysisId, User u)
    {
        TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ANALYSES);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("rowid"), analysisId);
        TableSelector ts = new TableSelector(ti, filter, null);
        AnalysisModel[] rows = ts.getArray(AnalysisModelImpl.class);
        if (rows.length != 1)
            throw new RuntimeException("Unable to find analysis: " + analysisId);

        AnalysisModel model = rows[0];
        Container c = ContainerManager.getForId(model.getContainer());
        if (!c.hasPermission(u, ReadPermission.class))
            throw new UnauthorizedException("User : " + analysisId);

        return model;
    }

    public Integer getAnalysisId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public void setRunId(Integer runId)
    {
        _runId = runId;
    }

    public void setCreatedby(Integer createdby)
    {
        _createdby = createdby;
    }

    public void setModifiedby(Integer modifiedby)
    {
        _modifiedby = modifiedby;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public void setReadset(Integer readset)
    {
        _readset = readset;
    }

    public void setAlignmentFile(Integer alignmentFile)
    {
        _alignmentFile = alignmentFile;
    }

    public void setReference_library(Integer reference_library)
    {
        _reference_library = reference_library;
    }

    public void setReferenceLibrary(Integer reference_library)
{
    _reference_library = reference_library;
}

    public void setCreated(Date created)
    {
        _created = created;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public Integer getRunId()
    {
        return _runId;
    }

    public String getContainer()
    {
        return _container;
    }

    public Integer getReadset()
    {
        return _readset;
    }

    public Integer getAlignmentFile()
    {
        return _alignmentFile;
    }

    public ExpData getAlignmentData()
    {
        return getData(_alignmentFile);
    }

    private ExpData getData(Integer dataId)
    {
        if (dataId == null)
        {
            return null;
        }

        return ExperimentService.get().getExpData(dataId);
    }

    public File getAlignmentFileObject()
    {
        ExpData d = getAlignmentData();
        if (d != null)
        {
            return d.getFile();
        }

        return null;
    }

    public Integer getReferenceLibrary()
    {
        return _reference_library;
    }

    public Integer getReference_Library()
    {
        return _reference_library;
    }

    public ExpData getReferenceLibraryData()
    {
        return getData(_reference_library);
    }

    public File getReferenceLibraryFile()
    {
        ExpData d = getReferenceLibraryData();
        if (d != null)
        {
            return d.getFile();
        }

        return null;
    }

    public String getType()
    {
        return _type;
    }

    public Date getModified()
    {
        return _modified;
    }

    public Integer getModifiedby()
    {
        return _modifiedby;
    }

    public Date getCreated()
    {
        return _created;
    }

    public Integer getCreatedby()
    {
        return _createdby;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getSynopsis()
    {
        return _synopsis;
    }

    public void setSynopsis(String synopsis)
    {
        _synopsis = synopsis;
    }

    public Integer getLibraryId()
    {
        return _library_id;
    }

    public Integer getLibrary_Id()
    {
        return _library_id;
    }

    public void setLibrary_id(Integer library_id)
    {
        _library_id = library_id;
    }

    public void setLibraryId(Integer library_id)
    {
        _library_id = library_id;
    }

    public ReadsetModel getReadsetModel()
    {
        if (_readset == null)
            return null;

        return new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READSETS)).getObject(_readset, ReadsetModel.class);
    }

    private String getFilePath(Integer dataId)
    {
        if (dataId == null || PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer)
        {
            return null;
        }

        ExpData data = getData(dataId);
        if (data.getFile() != null && data.getFile().exists())
        {
            return data.getFile().getPath();
        }

        return null;
    }

    public JSONObject toJSON()
    {
        JSONObject ret = new JSONObject();
        ret.put("rowId", _rowId);
        ret.put("type", _type);
        ret.put("runId", _runId);

        ret.put("createdby", _createdby);
        ret.put("created", _created);
        ret.put("modifiedby", _modifiedby);
        ret.put("modified", _modified);
        ret.put("container", _container);
        ret.put("readset", _readset);

        JSONObject filePaths = new JSONObject();
        ret.put("alignmentFile", _alignmentFile);
        if (_alignmentFile != null)
            filePaths.put(_alignmentFile.toString(), getFilePath(_alignmentFile));


        ret.put("referenceLibrary", _reference_library);
        if (_reference_library != null)
            filePaths.put(_reference_library.toString(), getFilePath(_reference_library));

        ret.put("filePaths", filePaths);
        ret.put("library_id", _library_id);
        ret.put("description", _description);
        ret.put("synopsis", _synopsis);

        return ret;
    }

    public static AnalysisModel fromJson(JSONObject o)
    {
        AnalysisModelImpl ret = new AnalysisModelImpl();
        ret.setRowId(o.optInt("rowId"));
        ret.setType(o.optString("type"));
        ret.setRunId(o.optInt("type"));
        ret.setCreatedby(o.optInt("createdby"));
        ret.setCreated(ConvertHelper.convert(o.optString("created"), Date.class));
        ret.setModifiedby(o.optInt("modifiedby"));
        ret.setModified(ConvertHelper.convert(o.optString("modified"), Date.class));
        ret.setContainer(o.optString("container"));
        ret.setReadset(o.optInt("readset"));
        ret.setAlignmentFile(o.optInt("alignmentFile"));
        ret.setReferenceLibrary(o.optInt("referenceLibrary"));
        ret.setLibraryId(o.optInt("library_id"));
        ret.setDescription(o.optString("description"));
        ret.setSynopsis(o.optString("synopsis"));

        if (o.containsKey("filePaths"))
        {
            JSONObject paths = o.getJSONObject("filePaths");
            for (String dataId : paths.keySet())
            {
                ret._cachedFilePaths.put(Integer.parseInt(dataId), paths.getString(dataId));
            }
        }

        return ret;
    }
}
