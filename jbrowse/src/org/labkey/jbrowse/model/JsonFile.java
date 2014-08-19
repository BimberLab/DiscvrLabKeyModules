package org.labkey.jbrowse.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.jbrowse.JBrowseManager;

import java.io.File;

/**
 * User: bimber
 * Date: 7/14/2014
 * Time: 2:54 PM
 */
public class JsonFile
{
    private Integer _trackId;
    private Integer _sequenceId;
    private Integer _dataId;
    private String _relpath;
    private String _objectId;
    private String _container;

    public JsonFile()
    {

    }

    public Integer getTrackId()
    {
        return _trackId;
    }

    public String getTrackName()
    {
        if (_trackId == null)
            return null;

        TableInfo ti = DbSchema.get("sequenceanalysis").getTable("reference_library_tracks");
        TableSelector ts = new TableSelector(ti, PageFlowUtil.set("name"), new SimpleFilter(FieldKey.fromString("rowid"), _trackId), null);

        return ts.getObject(String.class);
    }

    public void setTrackId(Integer trackId)
    {
        _trackId = trackId;
    }

    public Integer getDataId()
    {
        return _dataId;
    }

    public void setDataId(Integer dataId)
    {
        _dataId = dataId;
    }

    public String getContainerId()
    {
        return _container;
    }

    public Container getContainer()
    {
        return ContainerManager.getForId(_container);
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public String getObjectId()
    {
        return _objectId;
    }

    public void setObjectId(String objectId)
    {
        _objectId = objectId;
    }

    public Integer getSequenceId()
    {
        return _sequenceId;
    }

    public void setSequenceId(Integer sequenceId)
    {
        _sequenceId = sequenceId;
    }

    public String getRelpath()
    {
        return _relpath;
    }

    public void setRelpath(String relpath)
    {
        _relpath = relpath;
    }

    public String getRelativePath()
    {
        if (_sequenceId != null)
        {
            return new File("references", _sequenceId.toString()).toString();
        }
        else if (_trackId != null)
        {
            return new File("tracks", _trackId.toString()).toString();
        }

        return null;
    }

    public File getBaseDir()
    {
        return getRelativePath() == null ? null : new File(JBrowseManager.get().getJBrowseRoot(), getRelativePath());
    }

    public File getTrackListFile()
    {
        if (getRelativePath() == null)
            return null;

        File ret = new File(getBaseDir(), "trackList.json");
        return ret.exists() ? ret : null;
    }

    public File getTracksJsonFile()
    {
        if (getRelativePath() == null)
            return null;

        File ret = new File(getBaseDir(), "tracks.json");
        return ret.exists() ? ret : null;
    }

    public File getRefSeqsFile()
    {
        if (getRelativePath() == null)
            return null;

        File ret = new File(getBaseDir(), "seq/refSeqs.json");
        return ret.exists() ? ret : null;
    }
}
