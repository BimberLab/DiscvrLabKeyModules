package org.labkey.jbrowse.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.jbrowse.JBrowseManager;
import org.labkey.jbrowse.JBrowseSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 7/14/2014
 * Time: 2:54 PM
 */
public class JBrowseSession
{
    private static final Logger _log = LogManager.getLogger(JBrowseSession.class);

    private int _rowId;
    private String _name;
    private String _description;
    private String _jobid;
    private Integer _libraryId;
    private String _jsonConfig;
    private String _objectId;
    private String _container;
    private List<DatabaseMember> _members = null;

    public JBrowseSession()
    {

    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public Container getContainerObj()
    {
        return _container == null ? null : ContainerManager.getForId(_container);
    }

    public String getObjectId()
    {
        return _objectId;
    }

    public void setObjectId(String objectId)
    {
        _objectId = objectId;
    }

    public String getJobid()
    {
        return _jobid;
    }

    public void setJobid(String jobid)
    {
        _jobid = jobid;
    }

    public Integer getLibraryId()
    {
        return _libraryId;
    }

    public void setLibraryId(Integer libraryId)
    {
        _libraryId = libraryId;
    }

    public String getJsonConfig()
    {
        return _jsonConfig;
    }

    public void setJsonConfig(String jsonConfig)
    {
        _jsonConfig = jsonConfig;
    }

    public List<DatabaseMember> getMembers()
    {
        if (_members == null)
        {
            TableInfo ti = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASE_MEMBERS);
            _members = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("database"), _objectId), null).getArrayList(DatabaseMember.class);
        }

        return _members;
    }

    public static void onDatabaseDelete(String containerId, final String databaseId)
    {
        Container c = ContainerManager.getForId(containerId);
        if (c == null)
        {
            return;
        }

        //delete children
        TableInfo ti = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASE_MEMBERS);
        Table.delete(ti, new SimpleFilter(FieldKey.fromString("database"), databaseId, CompareType.EQUAL));
    }

    public static JBrowseSession getForId(String objectId)
    {
        return new TableSelector(JBrowseSchema.getInstance().getSchema().getTable(JBrowseSchema.TABLE_DATABASES)).getObject(objectId, JBrowseSession.class);
    }

    public List<JsonFile> getJsonFiles(User u, boolean createDatabaseRecordsIfNeeded)
    {
        // Start with all tracks from this genome.
        List<JsonFile> toAdd = new ArrayList<>(getGenomeTracks(u, createDatabaseRecordsIfNeeded));
        toAdd.forEach(x -> x.setCategory("Reference Annotations"));

        // Add DB-driven ones:
        RefAaJsonFile refAa = new RefAaJsonFile(getLibraryId(), createDatabaseRecordsIfNeeded, u);
        if (refAa.shouldExist())
        {
            toAdd.add(refAa);
        }

        RefNtFeaturesJsonFile refNt = new RefNtFeaturesJsonFile(getLibraryId(), createDatabaseRecordsIfNeeded, u);
        if (refNt.shouldExist())
        {
            toAdd.add(refNt);
        }

        // Then resources specific to this session:
        for (DatabaseMember m : getMembers())
        {
            JsonFile jsonFile = m.getJson();
            if (jsonFile == null)
            {
                continue;
            }

            toAdd.add(jsonFile);
        }

        return toAdd;
    }

    public void ensureJsonFilesPrepared(User u, Logger log) throws PipelineJobException
    {
        for (JsonFile x : getJsonFiles(u, true))
        {
            try
            {
                x.prepareResource(log, false, false);
            }
            catch (Exception e)
            {
                log.error("Unable to process JsonFile: " + getObjectId(), e);
            }
        }
    }

    public JSONObject getConfigJson(User u, Logger log, @Nullable List<String> additionalActiveTracks) throws PipelineJobException
    {
        JSONObject ret = new JSONObject();

        ReferenceGenome rg = SequenceAnalysisService.get().getReferenceGenome(_libraryId, u);
        if (rg == null)
        {
            throw new IllegalArgumentException("Unable to find genome: " + _libraryId);
        }

        ret.put("assembly", getAssemblyJson(rg));
        ret.put("configuration", new JSONObject());
        ret.put("connections", new JSONArray());

        JSONArray tracks = new JSONArray();
        List<JsonFile> jsonFiles = getJsonFiles(u, false);
        List<JsonFile> visibleTracks = new ArrayList<>();
        for (JsonFile jsonFile : jsonFiles)
        {
            JSONObject o = jsonFile.toTrackJson(u, rg, log);
            if (o != null)
            {
                tracks.put(o);
                visibleTracks.add(jsonFile);
            }
        }

        ret.put("tracks", tracks);
        ret.put("defaultSession", getDefaultSessionJson(visibleTracks, additionalActiveTracks));

        if (getJsonConfig() != null)
        {
            JSONObject json = new JSONObject(getJsonConfig());
            if (json.has("defaultLocation"))
            {
                ret.put("location", json.get("defaultLocation"));
            }
        }

        return ret;
    }

    private List<JsonFile> getGenomeTracks(User u, boolean createInDatabaseIfMissing)
    {
        // Find active tracks:
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("library_id"), getLibraryId(), CompareType.IN);
        filter.addCondition(FieldKey.fromString("datedisabled"), null, CompareType.ISBLANK);
        List<Integer> trackIds = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("reference_library_tracks"), PageFlowUtil.set("rowid"), filter, null).getArrayList(Integer.class);

        if (trackIds.isEmpty())
        {
            return Collections.emptyList();
        }

        List<JsonFile> ret = new ArrayList<>();
        for (int trackId : trackIds)
        {
            TableSelector ts = new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES), new SimpleFilter(FieldKey.fromString("trackid"), trackId, CompareType.EQUAL), null);
            if (!ts.exists())
            {
                if (createInDatabaseIfMissing)
                {
                    ret.add(JsonFile.prepareJsonFileForGenomeTrack(u, trackId));
                }
                else
                {
                    throw new IllegalStateException("Missing JsonFile for track: " + trackId);
                }
            }
            else
            {
                ret.add(ts.getObject(JsonFile.class));
            }
        }

        return ret;
    }

    public JSONObject getDefaultSessionJson(List<JsonFile> tracks, @Nullable List<String> additionalActiveTracks)
    {
        JSONObject ret = new JSONObject();
        ret.put("name", getName());

        JSONObject viewJson = new JSONObject();
        viewJson.put("id", _objectId);
        viewJson.put("type", "LinearGenomeView");

        JSONArray defaultTracks = new JSONArray();
        for (JsonFile jf : tracks)
        {
            boolean visibleByDefault = jf.isVisibleByDefault() || jf.matchesTrackSelector(additionalActiveTracks);
            if (visibleByDefault) {
                defaultTracks.put(new JSONObject(){{
                    put("type", jf.getTrackType());
                    put("configuration", jf.getJsonTrackId());
                    JSONArray displaysArr = new JSONArray();
                    displaysArr.put(new JSONObject(){{
                        String displayType = jf.getDisplayType();
                        put("type", displayType);
                        put("configuration", jf.getJsonTrackId() + "-" + displayType);
                    }});
                    put("displays", displaysArr);
                }});
            }
        }
        viewJson.put("tracks", defaultTracks);

        ret.put("view", viewJson);

        return ret;
    }

    public static JSONObject getAssemblyJson(ReferenceGenome rg)
    {
        JSONObject ret = new JSONObject();

        ret.put("name", rg.getName());
        ret.put("sequence", new JSONObject(){{
            put("type", "ReferenceSequenceTrack");
            put("trackId", getAssemblyTrackId(rg));
            put("adapter", getIndexedFastaAdapter(rg));
        }});

        return ret;
    }

    public static String getAssemblyTrackId(ReferenceGenome rg)
    {
        return FileUtil.makeLegalName(rg.getName()) + "-ReferenceSequenceTrack";
    }

    public static String getAssemblyName(ReferenceGenome rg)
    {
        return FileUtil.makeLegalName(rg.getName());
    }

    public static JSONObject getIndexedFastaAdapter(ReferenceGenome rg)
    {
        ExpData d = ExperimentService.get().getExpData(rg.getFastaExpDataId());
        String url = d.getWebDavURL(FileContentService.PathType.full);

        JSONObject ret = new JSONObject();
        ret.put("type", "IndexedFastaAdapter");
        ret.put("fastaLocation", new JSONObject(){{
            put("uri", url);
        }});
        ret.put("faiLocation", new JSONObject(){{
            put("uri", url + ".fai");
        }});

        return ret;
    }

    public static JSONObject getBgZippedIndexedFastaAdapter(ReferenceGenome rg)
    {
        ExpData d = ExperimentService.get().getExpData(rg.getFastaExpDataId());
        String url = d.getWebDavURL(FileContentService.PathType.full);

        JSONObject ret = new JSONObject();
        ret.put("type", "BgzipFastaAdapter");
        ret.put("fastaLocation", new JSONObject(){{
            put("uri", url + ".gz");
        }});
        ret.put("faiLocation", new JSONObject(){{
            put("uri", url + ".fai");
        }});
        ret.put("gziLocation", new JSONObject(){{
            put("uri", url + ".gz.gzi");
        }});

        return ret;
    }

    public static JBrowseSession getGenericGenomeSession(int genomeId)
    {
        Container genomeContainer = ContainerManager.getForId(new TableSelector(DbSchema.get(JBrowseManager.SEQUENCE_ANALYSIS, DbSchemaType.Module).getTable("reference_libraries"), PageFlowUtil.set("container"), new SimpleFilter(FieldKey.fromString("rowid"), genomeId), null).getObject(String.class));

        JBrowseSession session = new JBrowseSession();
        session.setContainer(genomeContainer.getId());
        session.setLibraryId(genomeId);
        session.setObjectId(new GUID().toString());

        return session;
    }

    public JsonFile getTrack(User u, String trackGUID)
    {
        for (JsonFile jf : getJsonFiles(u, false))
        {
            if (trackGUID.equalsIgnoreCase(jf.getObjectId()))
            {
                return jf;
            }
        }

        return null;
    }
}
