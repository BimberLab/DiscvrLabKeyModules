package org.labkey.jbrowse.model;

import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.gff.Gff3Codec;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.index.IndexFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.DISCVRSeqRunner;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.jbrowse.JBrowseManager;
import org.labkey.jbrowse.JBrowseSchema;
import org.labkey.sequenceanalysis.run.util.TabixRunner;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 7/14/2014
 * Time: 2:54 PM
 */
public class JsonFile
{
    private static final Logger logger = LogManager.getLogger(JsonFile.class);

    private Integer _trackId;
    private Integer _sequenceId;
    private Integer _outputFile;
    private String _objectId;
    private String _container;
    private String _category = null;
    protected String _label = null;
    private String _trackJson = null;

    public JsonFile()
    {

    }

    public Integer getTrackId()
    {
        return _trackId;
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

    public File getBaseDir()
    {
        File jbrowseDir = new File(JBrowseManager.get().getBaseDir(getContainerObj(), needsProcessing()), "resources");

        return needsProcessing() ? new File(jbrowseDir, getObjectId()) : null;
    }

    public String getLabel()
    {
        if (_label == null)
        {
            if (_trackId != null)
            {
                _label = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("reference_library_tracks"), PageFlowUtil.set("name"), new SimpleFilter(FieldKey.fromString("rowid"), _trackId), null).getObject(String.class);
                if (StringUtils.isEmpty(_label))
                {
                    _label = "Reference Annotation";
                }
            }
            else if (_outputFile != null)
            {
                TableInfo ti = JBrowseManager.get().getSequenceAnalysisTable("outputfiles");
                _label = new TableSelector(ti, PageFlowUtil.set("name"), new SimpleFilter(FieldKey.fromString("rowid"), _outputFile), null).getObject(String.class);
                if (StringUtils.isEmpty(_label))
                {
                    ExpData d = getExpData();
                    if (d != null)
                    {
                        _label = d.getName();
                    }
                }
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
                if (StringUtils.isEmpty(_category))
                {
                    _category = "Reference Annotations";
                }
            }
            else if (_outputFile != null)
            {
                _category = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("outputfiles"), PageFlowUtil.set("category"), new SimpleFilter(FieldKey.fromString("rowid"), _outputFile), null).getObject(String.class);
                if (StringUtils.isEmpty(_category))
                {
                    _category = "Data";
                }
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
                logger.error("Invalid JSON for jsonfile: " + getObjectId(), e);
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
        if (targetFile == null)
        {
            throw new IllegalStateException("Track lacks file associated with it: " + getObjectId());
        }
        else if (!targetFile.getContainer().hasPermission(u, ReadPermission.class))
        {
            //Note: this should never be allowed to get to this point...
            throw new UnauthorizedException("The current user does not have read permission for: " + targetFile.getContainer().getPath());
        }

        JSONObject ret;
        if (TRACK_TYPES.vcf.getFileType().isType(targetFile.getFile()))
        {
            ret = getVcfTrack(log, targetFile, rg);
        }
        else if (TRACK_TYPES.bam.getFileType().isType(targetFile.getFile()) || TRACK_TYPES.cram.getFileType().isType(targetFile.getFile()))
        {
            ret = getBamOrCramTrack(log, targetFile, rg);
        }
        else if (TRACK_TYPES.gff.getFileType().isType(targetFile.getFile()))
        {
            ret = getGxfTrack(log, targetFile, rg);
        }
        else if (TRACK_TYPES.gtf.getFileType().isType(targetFile.getFile()))
        {
            // NOTE: restore this once JB2 officially supports GtfTabixAdapter
            log.info("Unsupported track type: " + targetFile.getFile().getName());
            return null;
            //ret = getGxfTrack(log, targetFile, rg);
        }
        else if (TRACK_TYPES.bed.getFileType().isType(targetFile.getFile()))
        {
            ret = getBedTrack(log, targetFile, rg);
        }
        else if (TRACK_TYPES.bw.getFileType().isType(targetFile.getFile()))
        {
            ret = getBigWigTrack(log, targetFile, rg);
        }
        else
        {
            log.info("Unsupported track type: " + targetFile.getFile().getName());
            return null;
        }

        if (ret == null)
        {
            return null;
        }

        ret = possiblyAddSearchConfig(ret, rg);

        //Note: currently only support limited properties since a blind merge would make it easy to break the UI
        if (getTrackJson() != null)
        {
            JSONObject json = getExtraTrackConfig();
            if (json.has("metadata"))
            {
                ret.put("metadata", json.getJSONObject("metadata"));
            }

            if (json.has("category"))
            {
                ret.put("category", new JSONArray(){{
                    put(json.getString("category"));
                }});
            }
        }

        return ret;
    }

    //TODO: add this
    private @NotNull Map<String, Object> getExtraMetadata(User u, Logger log) throws PipelineJobException
    {
        Container target = getContainerObj();
        target = target.isWorkbook() ? target.getParent() : target;
        UserSchema us = QueryService.get().getUserSchema(u, target, JBrowseManager.SEQUENCE_ANALYSIS);
        TableInfo ti = us.getTable("outputfiles");
        if (ti == null)
        {
            log.error("unable to find outputfiles table:");
            log.error("container: " + target.getPath());
            log.error("user: " + u.getUserId());
            log.error("userName: " + u.getDisplayName(u));
            log.error("has read permission: " + target.hasPermission(u, ReadPermission.class));
            log.error("has home read permission: " + ContainerManager.getHomeContainer().hasPermission(u, ReadPermission.class));
            log.error("has shared read permission: " + ContainerManager.getSharedContainer().hasPermission(u, ReadPermission.class));
            log.error("other tables: " + StringUtils.join(us.getTableNames(), ";"));

            throw new PipelineJobException("Unable to find outputfiles table");
        }

        Set<FieldKey> keys = PageFlowUtil.set(
                FieldKey.fromString("description"),
                FieldKey.fromString("analysis_id"),
                FieldKey.fromString("analysis_id/name"),
                FieldKey.fromString("readset"),
                FieldKey.fromString("readset/name"),
                FieldKey.fromString("readset/subjectid"),
                FieldKey.fromString("readset/sampletype"),
                FieldKey.fromString("readset/platform"),
                FieldKey.fromString("readset/application")
        );

        Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, keys);
        TableSelector outputFileTs = new TableSelector(ti, cols.values(), new SimpleFilter(FieldKey.fromString("rowid"), getOutputFile()), null);
        if (!outputFileTs.exists())
        {
            log.error("unable to find outputfile: " + getOutputFile() + " in container: " + ti.getUserSchema().getContainer().getPath());
            return Collections.emptyMap();
        }

        ExpData d = getExpData();
        if (d == null || !d.getFile().exists())
        {
            log.error("unable to find file for output: " + getOutputFile());
            return Collections.emptyMap();
        }

        Map<String, Object> metadata = new HashMap<>();
        if (outputFileTs.exists())
        {
            try (Results results = outputFileTs.getResults())
            {
                results.next();
                metadata.put("Description", results.getString(FieldKey.fromString("description")));
                metadata.put("Readset", results.getString(FieldKey.fromString("readset/name")));
                metadata.put("Subject Id", results.getString(FieldKey.fromString("readset/subjectid")));
                metadata.put("Sample Type", results.getString(FieldKey.fromString("readset/sampletype")));
                metadata.put("Platform", results.getString(FieldKey.fromString("readset/platform")));
                metadata.put("Application", results.getString(FieldKey.fromString("readset/application")));
            }
            catch (SQLException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return metadata;
    }

    public boolean doIndex()
    {
        String sourceFilename = getSourceFileName();
        if (sourceFilename == null)
        {
            return false;
        }

        // The intent of this is to index GFF/GTF files that are part of the primary genome, not ad hoc resources. These files can opt-in to indexing using
        //TODO: restore this once JB2 support GTF indexing: TRACK_TYPES.gtf.getFileType().isType(sourceFilename)
        boolean doIndex = TRACK_TYPES.gff.getFileType().isType(sourceFilename) && getTrackId() != null;
        JSONObject config = getExtraTrackConfig();
        if (config != null)
        {
            if (config.optBoolean("includeInSearch", false))
            {
                doIndex = true;
            }

            if (config.optBoolean("excludeFromSearch", false))
            {
                doIndex = false;
            }
        }

        return doIndex;
    }

    private JSONObject possiblyAddSearchConfig(JSONObject json, ReferenceGenome rg)
    {
        if (!doIndex())
        {
            return json;
        }

        JSONObject searchConfig = new JSONObject();
        //searchConfig.put("indexingAttributes", Arrays.asList("Name", "ID", "type"));
        //searchConfig.put("indexingFeatureTypesToExclude", Arrays.asList("CDS", "exon"));
        searchConfig.put("textSearchAdapter", new JSONObject(){{
            put("type", "TrixTextSearchAdapter");
            put("textSearchAdapterId", json.get("trackId") + "-index");
            put("ixFilePath", new JSONObject(){{
                put("uri", getWebDavURL(getExpectedLocationOfIndexFile(".ix", true)));
            }});

            put("ixxFilePath", new JSONObject(){{
                put("uri", getWebDavURL(getExpectedLocationOfIndexFile(".ixx", true)));
            }});

            put("metaFilePath", new JSONObject()
            {{
                put("uri", getWebDavURL(getExpectedLocationOfIndexFile("_meta.json", false)));
            }});

            put("assemblyNames", new JSONArray(){{
                put(JBrowseSession.getAssemblyName(rg));
            }});
        }});

        json.put("textSearching", searchConfig);

        return json;
    }

    public boolean doExpectedSearchIndexesExist()
    {
        if (!doIndex())
        {
            return true;
        }

        for (String ext : Arrays.asList(".ix", ".ixx", "_meta.json"))
        {
            if (!getExpectedLocationOfIndexFile(ext, false).exists())
            {
                return false;
            }
        }

        return true;
    }

    private JSONObject getVcfTrack(Logger log, ExpData targetFile, ReferenceGenome rg)
    {
        JSONObject ret = new JSONObject();
        ret.put("type", getTrackType());
        ret.put("trackId", getJsonTrackId());
        ret.put("name", getLabel());
        ret.put("assemblyNames", new JSONArray(){{
            put(JBrowseSession.getAssemblyName(rg));
        }});

        String url = targetFile.getWebDavURL(FileContentService.PathType.full);
        if (url == null)
        {
            log.info("Unable to create WebDav URL for JBrowse resource with path: " + targetFile.getFile());
            return null;
        }

        ret.put("adapter", new JSONObject(){{
            put("type", "ExtendedVariantAdapter");
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

        ret.put("displays", new JSONArray(){{
            put(new JSONObject(){{
                put("type", "ExtendedVariantDisplay");
                put("displayId", getJsonTrackId() + "-ExtendedVariantDisplay");
                put("maxDisplayedBpPerPx", 2000);
                put("mouseover", "jexl:'Position: ' + formatWithCommas(get(feature,'POS'))");
                put("renderer", new JSONObject(){{
                    put("type", "ExtendedVariantRenderer");
                    //put("showLabels", false);
                    //put("labels", new JSONObject(){{
                    //    put("description", "jexl:get(feature,'POS')");
                    //}});
                }});

                JSONObject json = getExtraTrackConfig();
                if (json != null && json.has("additionalFeatureMsg"))
                {
                    getJSONObject("renderer").put("message", json.getString("additionalFeatureMsg"));
                }
            }});
        }});

        return ret;
    }

    public boolean matchesTrackSelector(List<String> toTest)
    {
        if (toTest == null)
        {
            return false;
        }

        return toTest.contains(getObjectId()) || toTest.contains(getLabel()) || toTest.contains(getJsonTrackId());
    }

    public String getJsonTrackId()
    {
        final File finalLocation = getLocationOfProcessedTrack(false);
        return finalLocation == null ? null : finalLocation.getName();
    }

    private JSONObject getBamOrCramTrack(Logger log, ExpData targetFile, ReferenceGenome rg)
    {
        JSONObject ret = new JSONObject();
        ret.put("type", getTrackType());
        ret.put("trackId", getJsonTrackId());
        ret.put("name", getLabel());
        ret.put("category", new JSONArray(){{
            put(getCategory());
        }});
        ret.put("assemblyNames", new JSONArray(){{
            put(JBrowseSession.getAssemblyName(rg));
        }});

        String url = targetFile.getWebDavURL(FileContentService.PathType.full);
        if (url == null)
        {
            log.info("Unable to create WebDav URL for JBrowse resource with path: " + targetFile.getFile());
            return null;
        }

        String type = TRACK_TYPES.bam.getFileType().isType(targetFile.getFile()) ? "BamAdapter" : "CramAdapter";
        boolean isBam = "BamAdapter".equals(type);
        if (isBam)
        {
            ret.put("adapter", new JSONObject()
            {{
                put("type", type);
                put("bamLocation", new JSONObject()
                {{
                    put("uri", url);
                }});

                put("index", new JSONObject()
                {{
                    put("location", new JSONObject()
                    {{
                        put("uri", url + ".bai");
                    }});
                    put("indexType", "BAI");
                }});

                put("sequenceAdapter", JBrowseSession.getIndexedFastaAdapter(rg));
            }});
        }
        else
        {
            ret.put("adapter", new JSONObject()
            {{
                put("type", type);
                put("cramLocation", new JSONObject()
                {{
                    put("uri", url);
                }});
                put("craiLocation", new JSONObject()
                {{
                    put("uri", url + ".crai");
                }});
                put("sequenceAdapter", JBrowseSession.getBgZippedIndexedFastaAdapter(rg));
            }});
        }
        return ret;
    }

    private JSONObject getBedTrack(Logger log, ExpData targetFile, ReferenceGenome rg) throws PipelineJobException
    {
        String adapterType = "BedTabixAdapter";
        String prefix = "bed";

        return getTabixTrack(log, targetFile, rg, adapterType, prefix);
    }

    private JSONObject getBigWigTrack(Logger log, ExpData targetFile, ReferenceGenome rg) throws PipelineJobException
    {
        JSONObject ret = new JSONObject();
        ret.put("type", getTrackType());
        ret.put("trackId", getJsonTrackId());
        ret.put("name", getLabel());
        ret.put("assemblyNames", new JSONArray(){{
            put(JBrowseSession.getAssemblyName(rg));
        }});
        ret.put("category", new JSONArray(){{
            put(getCategory());
        }});

        String url = targetFile.getWebDavURL(FileContentService.PathType.full);
        if (url == null)
        {
            log.info("Unable to create WebDav URL for JBrowse resource with path: " + targetFile.getFile());
            return null;
        }

        ret.put("adapter", new JSONObject(){{
            put("type", "BigWigAdapter");
            put("bigWigLocation", new JSONObject(){{
                put("uri", url);
                put("locationType", "UriLocation");
            }});
        }});

        return ret;
    }

    private JSONObject getGxfTrack(Logger log, ExpData targetFile, ReferenceGenome rg) throws PipelineJobException
    {
        String adapterType = TRACK_TYPES.gff.getFileType().isType(targetFile.getFile()) ? "Gff3TabixAdapter" : "GtfTabixAdapter";
        String prefix = TRACK_TYPES.gff.getFileType().isType(targetFile.getFile()) ? "gff" : "gtf";

        return getTabixTrack(log, targetFile, rg, adapterType, prefix);
    }

    public String getTrackType()
    {
        JSONObject extraConfig = getExtraTrackConfig();
        if (extraConfig != null && extraConfig.has("type"))
        {
            return extraConfig.getString("type");
        }

        ExpData targetFile = getExpData();
        if (TRACK_TYPES.vcf.getFileType().isType(targetFile.getFile()))
        {
            return "ExtendedVariantTrack";
        }
        else if (TRACK_TYPES.bam.getFileType().isType(targetFile.getFile()) || TRACK_TYPES.cram.getFileType().isType(targetFile.getFile()))
        {
            return "AlignmentsTrack";
        }
        else if (TRACK_TYPES.gff.getFileType().isType(targetFile.getFile()))
        {
            return "FeatureTrack";
        }
        else if (TRACK_TYPES.gtf.getFileType().isType(targetFile.getFile()))
        {
            return "FeatureTrack";
        }
        else if (TRACK_TYPES.bed.getFileType().isType(targetFile.getFile()))
        {
            return "FeatureTrack";
        }
        else if (TRACK_TYPES.bw.getFileType().isType(targetFile.getFile()))
        {
            return "QuantitativeTrack";
        }
        else
        {
            throw new IllegalArgumentException("Unsupported track type: " + targetFile.getFile().getName());
        }
    }

    private JSONObject getTabixTrack(Logger log, ExpData targetFile, ReferenceGenome rg, String adapterType, String prefix) throws PipelineJobException
    {
        JSONObject ret = new JSONObject();
        ret.put("type", getTrackType());
        ret.put("trackId", getJsonTrackId());
        ret.put("name", getLabel());
        ret.put("category", new JSONArray(){{
            put(getCategory());
        }});
        ret.put("assemblyNames", new JSONArray(){{
            put(JBrowseSession.getAssemblyName(rg));
        }});

        // if not gzipped, we need to process it:
        File gzipped = prepareResource(log, true, false);
        final String url;
        if (!getExpData().getFile().equals(gzipped))
        {
            url = getWebDavURL(gzipped);
        }
        else
        {
            url = targetFile.getWebDavURL(FileContentService.PathType.full);
        }

        if (url == null)
        {
            log.info("Unable to create WebDav URL for JBrowse resource with path: " + targetFile.getFile());
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

    public boolean needsProcessing()
    {
        return (needsGzip() && !isGzipped()) || doIndex() || shouldHaveFreeTextSearch();
    }

    public boolean isGzipped()
    {
        String fn = getSourceFileName();
        if (fn == null)
        {
            throw new IllegalStateException("JsonFile lacks ExpData or lacks file");
        }

        return fn.toLowerCase().endsWith(".gz");
    }

    protected boolean needsGzip()
    {
        String fn = getSourceFileName();
        if (fn == null)
        {
            return false;
        }

        return !isGzipped() && (TRACK_TYPES.gff.getFileType().isType(fn) || TRACK_TYPES.gtf.getFileType().isType(fn) || TRACK_TYPES.bed.getFileType().isType(fn));
    }

    public File prepareResource(Logger log, boolean throwIfNotPrepared, boolean forceReprocess) throws PipelineJobException
    {
        ExpData expData = getExpData();
        if (expData == null)
        {
            throw new PipelineJobException("No ExpData for JsonFile: " + getObjectId());
        }

        File targetFile = expData.getFile();
        if (needsGzip() && !isGzipped())
        {
            //need to gzip and tabix index:
            final File finalLocation = getLocationOfProcessedTrack(true);
            if (finalLocation.exists() && !SequencePipelineService.get().hasMinLineCount(finalLocation, 1))
            {
                log.info("File exists but is zero-length, deleting and re-processing:");
                forceReprocess = true;
            }

            File idx = new File(finalLocation.getPath() + ".tbi");
            if (finalLocation.exists() && forceReprocess && !targetFile.equals(finalLocation))
            {
                finalLocation.delete();
                if (idx.exists())
                {
                    idx.delete();
                }
            }

            if (!finalLocation.exists())
            {
                if (throwIfNotPrepared)
                {
                    throw new IllegalStateException("This track should have been previously gzipped: " + expData.getFile().getName());
                }

                if (!targetFile.exists())
                {
                    throw new PipelineJobException("Source file does not exist: " + targetFile.getPath());
                }

                try
                {
                    if (!targetFile.getParentFile().equals(finalLocation.getParentFile()))
                    {
                        log.debug("Creating local copy of: " + targetFile.getPath());
                        File local = new File(finalLocation.getParentFile(), targetFile.getName());
                        if (local.exists())
                        {
                            local.delete();
                        }

                        FileUtils.copyFile(targetFile, local);
                        targetFile = local;
                    }

                    File bgZipped = SequenceAnalysisService.get().bgzipFile(targetFile, log);
                    FileUtils.moveFile(bgZipped, finalLocation);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            targetFile = finalLocation;
        }

        // Ensure index check runs even if file was already gzipped:
        File idx = new File(targetFile.getPath() + ".tbi");
        if (targetFile.getName().toLowerCase().endsWith(".gz") && (forceReprocess || !idx.exists()))
        {
            createIndex(targetFile, log, idx, throwIfNotPrepared);
        }

        if (doIndex())
        {
            File trixDir = new File(targetFile.getParentFile(), "trix");
            if (forceReprocess && trixDir.exists())
            {
                try
                {
                    FileUtils.deleteDirectory(trixDir);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            File ixx = getExpectedLocationOfIndexFile(".ixx", false);
            if (ixx.exists() && !SequencePipelineService.get().hasMinLineCount(ixx, 1))
            {
                log.info("ixx exists but is zero-length, deleting and re-processing: " + ixx.getPath());
                ixx.delete();
            }

            if (!ixx.exists())
            {
                if (throwIfNotPrepared)
                {
                    throw new IllegalStateException("This track should have been previously indexed: " + expData.getFile().getName());
                }

                List<String> attributes = Arrays.asList("Name", "ID", "gene_id", "gene_name");

                File exe = JBrowseManager.get().getJbrowseCli();
                SimpleScriptWrapper wrapper = new SimpleScriptWrapper(log);
                wrapper.setWorkingDir(targetFile.getParentFile());
                wrapper.setThrowNonZeroExits(true);

                wrapper.execute(Arrays.asList(exe.getPath(), "text-index", "--force", "--quiet", "--attributes", StringUtils.join(attributes, ","), "--prefixSize", "5", "--file", targetFile.getPath()));
                if (!ixx.exists())
                {
                    throw new PipelineJobException("Unable to find expected index file: " + ixx.getPath());
                }

                // See here: https://github.com/GMOD/jbrowse-components/issues/2344
                // for background. this hackery is needed to ensure the trackId listed in the ix matches the GUID, not the filename:
                log.info("Updating trackId in trix ix file");
                File ix = getExpectedLocationOfIndexFile(".ix", false);
                if (!ix.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + ix.getPath());
                }
            }
        }

        if (shouldHaveFreeTextSearch())
        {
            File luceneDir = getExpectedLocationOfLuceneIndex(throwIfNotPrepared);
            if (forceReprocess && luceneDir.exists())
            {
                try
                {
                    FileUtils.deleteDirectory(luceneDir);
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            if (forceReprocess || !doesLuceneIndexExist())
            {
                prepareLuceneIndex(log);
            }
            else
            {
                log.debug("Existing lucene index found, will not re-create: " + luceneDir.getPath());
            }
        }

        return targetFile;
    }

    private boolean doesLuceneIndexExist()
    {
        File luceneDir = getExpectedLocationOfLuceneIndex(false);
        if (luceneDir == null)
        {
            return false;
        }

        // NOTE: is this the best file to test?
        luceneDir = new File(luceneDir, "write.lock");
        return luceneDir.exists();
    }

    public @NotNull List<String> getInfoFieldsToIndex(@Nullable String... defaults)
    {
        JSONObject config = getExtraTrackConfig();
        String rawFields = config == null ? null : StringUtils.trimToNull(config.optString("infoFieldsForFullTextSearch"));
        if (rawFields == null)
        {
            return defaults == null ? Collections.emptyList() : Arrays.asList(defaults);
        }

        return Arrays.asList(rawFields.split(","));
    }

    private void prepareLuceneIndex(Logger log) throws PipelineJobException
    {
        log.debug("Generating VCF full text index for file: " + getExpData().getFile().getName());

        DISCVRSeqRunner runner = new DISCVRSeqRunner(log);
        if (!runner.jarExists())
        {
            log.error("Unable to find DISCVRSeq.jar, skiping lucene index creation");
            return;
        }

        List<String> args = runner.getBaseArgs("VcfToLuceneIndexer");
        args.add("-V");
        args.add(getExpData().getFile().getPath());

        args.add("-O");
        args.add(getExpectedLocationOfLuceneIndex(false).getPath());

        List<String> infoFieldsForFullTextSearch = getInfoFieldsToIndex("AF");
        for (String field : infoFieldsForFullTextSearch)
        {
            args.add("-IF");
            args.add(field);
        }

        runner.execute(args);
    }

    protected void createIndex(File finalLocation, Logger log, File idx, boolean throwIfNotPrepared) throws PipelineJobException
    {
        if (throwIfNotPrepared)
        {
            throw new IllegalStateException("This track should have been previously indexed: " + finalLocation.getPath());
        }
        else
        {
            if (SystemUtils.IS_OS_WINDOWS)
            {
                try
                {
                    if (TRACK_TYPES.bed.getFileType().isType(finalLocation))
                    {
                        Index index = IndexFactory.createIndex(finalLocation.toPath(), new BEDCodec(), IndexFactory.IndexType.TABIX);
                        index.write(idx);
                    }
                    else if (TRACK_TYPES.gtf.getFileType().isType(finalLocation) || TRACK_TYPES.gff.getFileType().isType(finalLocation))
                    {
                        Index index = IndexFactory.createIndex(finalLocation.toPath(), new Gff3Codec(), IndexFactory.IndexType.TABIX);
                        index.write(idx);
                    }
                    else if (TRACK_TYPES.vcf.getFileType().isType(finalLocation))
                    {
                        SequenceAnalysisService.get().ensureVcfIndex(finalLocation, log);
                    }
                    else
                    {
                        log.warn("Cannot create tabix index on windows!");
                    }
                }
                catch (IOException e)
                {
                    log.error("Error creating tabix index!", e);
                }
            }
            else
            {
                //Ensure sorted:
                if (TRACK_TYPES.bed.getFileType().isType(finalLocation))
                {
                    try
                    {
                        SequencePipelineService.get().sortROD(finalLocation, log, 2);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }
                else if (TRACK_TYPES.gff.getFileType().isType(finalLocation) || TRACK_TYPES.gtf.getFileType().isType(finalLocation))
                {
                    SequenceAnalysisService.get().sortGxf(log, finalLocation, null);
                }

                TabixRunner tabix = new TabixRunner(log);
                tabix.execute(finalLocation);
            }
        }
    }

    public File getLocationOfProcessedTrack(boolean createDir)
    {
        ExpData expData = getExpData();
        if (expData == null || expData.getFile() == null)
        {
            return null;
        }

        File trackDir = getBaseDir();
        if (createDir && !trackDir.exists())
        {
            trackDir.mkdirs();
        }

        return new File(trackDir, FileUtil.makeLegalName(getSourceFileName()).replaceAll(" ", "_") + (needsGzip() && !isGzipped() ? ".gz" : ""));
    }

    protected String getSourceFileName()
    {
        ExpData expData = getExpData();
        if (expData == null)
        {
            throw new IllegalStateException("expData should not be null: " + getObjectId());
        }

        return getObjectId() + expData.getName();
    }

    public File getExpectedLocationOfIndexFile(String extension, boolean throwIfNotFound)
    {
        File basedir = getLocationOfProcessedTrack(false);
        if (basedir == null)
        {
            return null;
        }

        File ret = new File(basedir.getParentFile(), "trix");
        ret = new File(ret, basedir.getName() + extension);

        if (throwIfNotFound && !ret.exists())
        {
            throw new IllegalStateException("File not found: " + ret.getPath());
        }

        return ret;
    }

    private String getWebDavURL(File input)
    {
        java.nio.file.Path path = input.toPath();
        if (getContainerObj() == null)
        {
            return null;
        }

        PipeRoot root = PipelineService.get().getPipelineRootSetting(getContainerObj());
        if (root == null)
            return null;

        path = path.toAbsolutePath();
        if (root.isUnderRoot(path))
        {
            String relPath = root.relativePath(path);
            if (relPath == null)
                return null;

            if(!FileContentService.get().isCloudRoot(getContainerObj()))
            {
                relPath = Path.parse(FilenameUtils.separatorsToUnix(relPath)).encode();
            }
            else
            {
                // Do not encode path from S3 folder.  It is already encoded.
                relPath = Path.parse(FilenameUtils.separatorsToUnix(relPath)).toString();
            }

            return AppProps.getInstance().getBaseServerUrl() + root.getWebdavURL() + relPath;
        }

        return null;
    }

    public boolean isVisibleByDefault()
    {
        JSONObject json = getExtraTrackConfig();
        if (json == null || json.get("visibleByDefault") == null)
            return false;

        return Boolean.parseBoolean(json.get("visibleByDefault").toString());
    }

    public static JsonFile prepareJsonFileRecordForOutputFile(User u, Integer outputFileId, JSONObject additionalConfig, Logger log)
    {
        log.debug("preparing outputfile: " + outputFileId);

        //find existing resource
        TableInfo jsonFiles = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
        TableSelector ts1 = new TableSelector(jsonFiles, new SimpleFilter(FieldKey.fromString("outputfile"), outputFileId), null);
        if (ts1.exists())
        {
            return ts1.getObject(JsonFile.class);
        }

        SequenceOutputFile so = SequenceOutputFile.getForId(outputFileId);
        if (so == null)
        {
            throw new IllegalArgumentException("Unable to find outputfile: " + outputFileId);
        }

        Container fileContainer = ContainerManager.getForId(so.getContainer());
        if (fileContainer == null)
        {
            throw new IllegalArgumentException("Unable to find container with Id: " + so.getContainer());
        }

        //else create
        log.debug("adding new jsonfile record");

        Map<String, Object> jsonRecord = new CaseInsensitiveHashMap<>();
        jsonRecord.put("outputfile", outputFileId);
        jsonRecord.put("container", fileContainer.getId());
        jsonRecord.put("created", new Date());
        jsonRecord.put("createdby", u.getUserId());
        jsonRecord.put("modified", new Date());
        jsonRecord.put("modifiedby", u.getUserId());
        jsonRecord.put("objectid", new GUID().toString().toUpperCase());
        if (additionalConfig != null)
        {
            jsonRecord.put("trackJson", additionalConfig.toString());
        }

        Table.insert(u, jsonFiles, jsonRecord);

        return ts1.getObject(JsonFile.class);
    }

    public static JsonFile prepareJsonFileForGenomeTrack(User u, Integer trackId)
    {
        //validate track exists
        TableInfo ti = JBrowseManager.get().getSequenceAnalysisTable("reference_library_tracks");
        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowid"), trackId), null);
        Map<String, Object> trackRowMap = ts.getMap();

        if (trackRowMap.get("fileid") == null)
        {
            throw new IllegalArgumentException("Track does not have a file: " + trackId);
        }

        ExpData data = ExperimentService.get().getExpData((int)trackRowMap.get("fileid"));
        if (data == null || data.getFile() == null)
        {
            throw new IllegalArgumentException("Unable to find ExpData: " + (data == null ? null : data.getFile()));
        }

        if (!data.getFile().exists())
        {
            throw new IllegalArgumentException("File does not exist: " + data.getFile().getPath());
        }

        //find existing resource
        TableInfo jsonFiles = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
        TableSelector ts1 = new TableSelector(jsonFiles, new SimpleFilter(FieldKey.fromString("trackid"), trackId), null);
        JsonFile jsonFile;
        if (ts1.exists())
        {
            return ts1.getObject(JsonFile.class);
        }

        //else create
        Map<String, Object> jsonRecord = new CaseInsensitiveHashMap<>();
        jsonRecord.put("trackid", trackId);
        jsonRecord.put("container", data.getContainer().getId());
        jsonRecord.put("created", new Date());
        jsonRecord.put("createdby", u.getUserId());
        jsonRecord.put("modified", new Date());
        jsonRecord.put("modifiedby", u.getUserId());
        jsonRecord.put("objectid", new GUID().toString().toUpperCase());

        TableInfo jsonTable = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
        Table.insert(u, jsonTable, jsonRecord);

        jsonFile = ts1.getObject(JsonFile.class);

        //Map<String, Object> metadata = new HashMap<>();
        //metadata.put("Description", trackRowMap.get("description"));
        //jsonRecord.put("metadata", metadata);

        return jsonFile;
    }

    public String getDisplayType()
    {
        ExpData targetFile = getExpData();
        if (TRACK_TYPES.vcf.getFileType().isType(targetFile.getFile()))
        {
            return "ExtendedVariantDisplay";
        }
        else if (TRACK_TYPES.bam.getFileType().isType(targetFile.getFile()) || TRACK_TYPES.cram.getFileType().isType(targetFile.getFile()))
        {
            return "LinearAlignmentsDisplay";
        }
        else if (TRACK_TYPES.gff.getFileType().isType(targetFile.getFile()))
        {
            return "LinearBasicDisplay";
        }
        else if (TRACK_TYPES.gtf.getFileType().isType(targetFile.getFile()))
        {
            return "LinearBasicDisplay";
        }
        else if (TRACK_TYPES.bed.getFileType().isType(targetFile.getFile()))
        {
            return "LinearBasicDisplay";
        }
        else if (TRACK_TYPES.bw.getFileType().isType(targetFile.getFile()))
        {
            //TODO: Quantitative??
            return "LinearBasicDisplay";
        }
        else
        {
            throw new IllegalArgumentException("Unsupported track type: " + targetFile.getFile().getName());
        }
    }

    public enum TRACK_TYPES
    {
        bam(".bam", false),
        cram(".cram", false),
        gtf(".gtf", true),
        gff(Arrays.asList(".gff", ".gff3"), true),
        bed(".bed", true),
        bw(".bw", false),
        vcf(List.of(".vcf"), true);

        private final List<String> _extensions;
        private final boolean _allowGzip;

        TRACK_TYPES(String extension, boolean allowGzip)
        {
            this(Collections.singletonList(extension), allowGzip);
        }

        TRACK_TYPES(List<String> extensions, boolean allowGzip)
        {
            _extensions = extensions;
            _allowGzip = allowGzip;
        }

        public FileType getFileType()
        {
            return new FileType(_extensions, _extensions.get(0), false, _allowGzip ? FileType.gzSupportLevel.SUPPORT_GZ : FileType.gzSupportLevel.NO_GZ);
        }

        public String getPrimaryExtension()
        {
            return _extensions.get(0);
        }
    }

    public boolean shouldHaveFreeTextSearch()
    {
        ExpData targetFile = getExpData();
        if (!TRACK_TYPES.vcf.getFileType().isType(targetFile.getFile()))
        {
            return false;
        }

        JSONObject json = getExtraTrackConfig();
        return json != null && json.optBoolean("createFullTextIndex", false);
    }

    public File getExpectedLocationOfLuceneIndex(boolean throwIfNotFound)
    {
        File basedir = getLocationOfProcessedTrack(false);
        if (basedir == null)
        {
            return null;
        }

        File ret = new File(basedir.getParentFile(), "lucene");
        if (throwIfNotFound && !ret.exists())
        {
            throw new IllegalStateException("Expected search index not found: " + ret.getPath());
        }

        return ret;
    }
}
