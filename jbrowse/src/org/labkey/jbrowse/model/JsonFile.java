package org.labkey.jbrowse.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.services.ServiceRegistry;
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
    private Integer _outputFile;
    private String _relpath;
    private String _objectId;
    private String _container;
    private String _category = null;
    private String _label = null;
    private String _trackJson = null;

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

        TableInfo ti = JBrowseManager.get().getSequenceAnalysisTable("reference_library_tracks");
        TableSelector ts = new TableSelector(ti, PageFlowUtil.set("name"), new SimpleFilter(FieldKey.fromString("rowid"), _trackId), null);

        return ts.getObject(String.class);
    }

    public void setTrackId(Integer trackId)
    {
        _trackId = trackId;
    }

    public Integer getOutputFile()
    {
        return _outputFile;
    }

    public void setOutputFile(Integer outputFile)
    {
        _outputFile = outputFile;
    }

    public ExpData getExpData()
    {
        if (_outputFile == null)
            return null;

        Integer dataId = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("outputfiles"), PageFlowUtil.set("dataid"), new SimpleFilter(FieldKey.fromString("rowid"), _outputFile), null).getObject(Integer.class);
        if (dataId == null)
            return null;

        return ExperimentService.get().getExpData(dataId);
    }

    public ExpData getRefLibraryData()
    {
        if (_outputFile == null)
            return null;

        Integer libraryId = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("outputfiles"), PageFlowUtil.set("library_id"), new SimpleFilter(FieldKey.fromString("rowid"), _outputFile), null).getObject(Integer.class);
        if (libraryId == null)
            return null;

        Integer dataId = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("reference_libraries"), PageFlowUtil.set("fasta_file"), new SimpleFilter(FieldKey.fromString("rowid"), libraryId), null).getObject(Integer.class);
        if (dataId == null)
            return null;

        return ExperimentService.get().getExpData(dataId);
    }

    public String getContainer()
    {
        return _container;
    }

    public Container getContainerObj()
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
            return new File("tracks", "track-" + _trackId.toString()).toString();
        }
        else if (_outputFile != null)
        {
            return new File("tracks", "data-" + _outputFile.toString()).toString();
        }

        return null;
    }

    public File getBaseDir()
    {
        Container c = ContainerManager.getForId(_container);
        if (c == null)
        {
            return null;
        }

        FileContentService fileService = ServiceRegistry.get().getService(FileContentService.class);
        File fileRoot = fileService == null ? null : fileService.getFileRoot(c, FileContentService.ContentType.files);
        if (fileRoot == null || !fileRoot.exists())
        {
            return null;
        }

        File jbrowseDir = new File(fileRoot, ".jbrowse");
        if (!jbrowseDir.exists())
        {
            jbrowseDir.mkdirs();
        }

        return getRelativePath() == null ? null : new File(jbrowseDir, getRelativePath());
    }

    public File getTrackRootDir()
    {
        File ret = getBaseDir();
        if (getRelativePath() == null)
            return null;

        if (getTrackId() != null)
        {
            ret = new File(ret, "data");
        }

        return ret;
    }

    public File getTrackListFile()
    {
        File ret = new File(getTrackRootDir(), "trackList.json");
        return ret.exists() ? ret : null;

    }

    public File getTracksJsonFile()
    {
        if (getRelativePath() == null)
            return null;

        File ret = new File(getTrackRootDir(), "tracks.json");
        return ret.exists() ? ret : null;
    }

    public File getRefSeqsFile()
    {
        if (getRelativePath() == null)
            return null;

        File ret = new File(getBaseDir(), "seq/refSeqs.json");
        return ret.exists() ? ret : null;
    }

    public String getLabel()
    {
        if (_label == null)
        {
            if (_trackId != null)
            {
                _label = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("reference_library_tracks"), PageFlowUtil.set("category"), new SimpleFilter(FieldKey.fromString("rowid"), _trackId), null).getObject(String.class);
                if (_label == null)
                {
                    _label = "Reference Annotation";
                }
            }
            else if (_outputFile != null)
            {
                TableInfo ti = JBrowseManager.get().getSequenceAnalysisTable("outputfiles");
                _label = new TableSelector(ti, PageFlowUtil.set("name"), new SimpleFilter(FieldKey.fromString("rowid"), _outputFile), null).getObject(String.class);
                if (_label == null)
                {
                    ExpData d = getExpData();
                    if (d != null)
                    {
                        _label = d.getName();
                    }
                }
            }
            else if (_sequenceId != null)
            {
                _label = "Reference sequences";
            }
        }

        return _label == null ? "" : _label;
    }

    public String getCategory()
    {
        if (_category == null)
        {
            if (_trackId != null)
            {
                _category = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("reference_library_tracks"), PageFlowUtil.set("category"), new SimpleFilter(FieldKey.fromString("rowid"), _trackId), null).getObject(String.class);
                if (_category == null)
                {
                    _category = "Reference Annotations";
                }
            }
            else if (_outputFile != null)
            {
                _category = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("outputfiles"), PageFlowUtil.set("category"), new SimpleFilter(FieldKey.fromString("rowid"), _outputFile), null).getObject(String.class);
                if (_category == null)
                {
                    _category = "Data";
                }
            }
            else if (_sequenceId != null)
            {
                _category = "Reference sequences";
            }
        }

        return _category == null ? "" : _category;
    }

    public void setCategory(String category)
    {
        _category = category;
    }

    public String getTrackJson()
    {
        return _trackJson;
    }

    public void setTrackJson(String trackJson)
    {
        _trackJson = trackJson;
    }

    public JSONObject getExtraTrackConfig()
    {
        if (_trackJson != null)
        {
            try
            {
                return new JSONObject(_trackJson);
            }
            catch (JSONException e)
            {
                //ignore?
            }
        }

        return null;
    }
}
