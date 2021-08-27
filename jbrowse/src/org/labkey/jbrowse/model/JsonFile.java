package org.labkey.jbrowse.model;

import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.JSONArray;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.jbrowse.JBrowseManager;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;

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
        Integer dataId = null;
        if (_outputFile != null)
        {
            dataId = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("outputfiles"), PageFlowUtil.set("dataid"), new SimpleFilter(FieldKey.fromString("rowid"), _outputFile), null).getObject(Integer.class);
        }
        else if (_trackId != null)
        {
            dataId = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("reference_library_tracks"), PageFlowUtil.set("fileid"), new SimpleFilter(FieldKey.fromString("rowid"), _trackId), null).getObject(Integer.class);
        }

        if (dataId == null)
            return null;

        return ExperimentService.get().getExpData(dataId);
    }

    public File getTrackFile()
    {
        ExpData d = getExpData();
        if (d == null || d.getFile() == null)
            return null;

        return d.getFile();
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

        FileContentService fileService = FileContentService.get();
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

    private static final FileType _ft = new FileType(Arrays.asList(".bam", ".vcf"), ".vcf", FileType.gzSupportLevel.SUPPORT_GZ);

    //NOTE: if the track file isnt parsed to JSON (VCF or BAM), then the file will be in getBaseDir()
    public boolean expectDataSubdirForTrack()
    {
        File f = getTrackFile();
        if (f == null)
        {
            throw new IllegalArgumentException("Unable to find file for JSONFile: " + _objectId);
        }

        return !_ft.isType(f);
    }

    public File getTrackRootDir()
    {
        File ret = getBaseDir();
        if (ret == null)
            return null;

        if (expectDataSubdirForTrack())
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

    public JSONObject getExtraTrackConfig(Logger log)
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
                log.error(e.getMessage(), e);
            }
        }

        return null;
    }

    public @Nullable
    JSONObject toTrackJson(User u, ReferenceGenome rg, Logger log) throws PipelineJobException
    {
        // For legacy reasons we used to map individual seqs. No longer needed:
        if (getSequenceId() != null)
        {
            return null;
        }

        ExpData targetFile = getExpData();
        if (!targetFile.getContainer().hasPermission(u, ReadPermission.class))
        {
            //TODO: this should never be allowed to get to this point...
            throw new UnauthorizedException("The current user does not have read permission for: " + targetFile.getContainer().getPath());
        }

        JSONObject ret;
        if (SequenceUtil.FILETYPE.vcf.getFileType().isType(targetFile.getFile()))
        {
            ret = getVcfTrack(targetFile, rg);
        }
        else if (SequenceUtil.FILETYPE.bam.getFileType().isType(targetFile.getFile()))
        {
            ret = getBamTrack(targetFile, rg);
        }
        else if (SequenceUtil.FILETYPE.gff.getFileType().isType(targetFile.getFile()))
        {
            ret = getGxfTrack(targetFile, rg);
        }
        else if (SequenceUtil.FILETYPE.gtf.getFileType().isType(targetFile.getFile()))
        {
            return null;
        }
        else if (SequenceUtil.FILETYPE.bed.getFileType().isType(targetFile.getFile()))
        {
            return null;
        }
        else
        {
            //TODO:
            log.error("Unsupported track type: " + targetFile.getFile().getName());
            return null;
        }

        //TODO: Search config

        //TODO: additional properties:
        if (getTrackJson() != null)
        {
            JSONObject json = getExtraTrackConfig(log);
            ret.putAll(json);
        }

        return ret;
    }

    private JSONObject getVcfTrack(ExpData targetFile, ReferenceGenome rg)
    {
        JSONObject ret = new JSONObject();
        ret.put("type", "VariantTrack");
        ret.put("trackId", _objectId);
        ret.put("name", getLabel());
        ret.put("assemblyNames", new JSONArray(){{
            add(JBrowseSession.getAssemblyName(rg));
        }});

        String url = targetFile.getWebDavURL(ExpData.PathType.full);
        ret.put("adapter", new JSONObject(){{
            put("type", "VcfTabixAdapter");
            put("vcfGzLocation", new JSONObject(){{
                put("uri", url);
            }});

            put("index", new JSONObject(){{
                put("location", new JSONObject(){{
                    put("uri", url + ".tbi");
                }});
                put("indexType", "TBI");
            }});
        }});

        return ret;
    }

    private JSONObject getBamTrack(ExpData targetFile, ReferenceGenome rg) throws PipelineJobException
    {
        JSONObject ret = new JSONObject();
        ret.put("type", "AlignmentsTrack");
        ret.put("trackId", _objectId);
        ret.put("name", getLabel());
        ret.put("assemblyNames", new JSONArray(){{
            add(JBrowseSession.getAssemblyName(rg));
        }});

        String url = targetFile.getWebDavURL(ExpData.PathType.full);
        ret.put("adapter", new JSONObject(){{
            put("type", "BamAdapter");
            put("bamLocation", new JSONObject(){{
                put("uri", url);
            }});

            put("index", new JSONObject(){{
                put("location", new JSONObject(){{
                    put("uri", url + ".bai");
                }});
                put("indexType", "BAI");
            }});

            put("sequenceAdapter", JBrowseSession.getIndexedFastaAdapter(rg));
        }});

        return ret;
    }

    private JSONObject getGxfTrack(ExpData targetFile, ReferenceGenome rg)
    {
        JSONObject ret = new JSONObject();
        ret.put("type", "FeatureTrack");
        ret.put("trackId", _objectId);
        ret.put("name", getLabel());
        ret.put("category", new JSONArray(){{
            add(getCategory());
        }});
        ret.put("assemblyNames", new JSONArray(){{
            add(JBrowseSession.getAssemblyName(rg));
        }});

        //TODO: if not gzipped, we need to process it:

        String adapterType = SequenceUtil.FILETYPE.gff.getFileType().isType(targetFile.getFile()) ? "Gff3TabixAdapter" : "GtfTabixAdapter";;
        String prefix = SequenceUtil.FILETYPE.gff.getFileType().isType(targetFile.getFile()) ? "gff" : "gtf";

        String url = targetFile.getWebDavURL(ExpData.PathType.full);
        if (url == null)
        {
            //TODO:
            return null;
        }

        ret.put("adapter", new JSONObject(){{
            put("type", adapterType);
            put(prefix + "GzLocation", new JSONObject(){{
                put("uri", url);
            }});

            put("index", new JSONObject(){{
                put("location", new JSONObject(){{
                    put("uri", url + ".tbi");
                }});
                put("indexType", "TBI");
            }});
        }});

        return ret;
    }
}
