package org.labkey.jbrowse;

import htsjdk.samtools.util.BlockCompressedOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Results;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.Readers;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.jbrowse.model.Database;
import org.labkey.jbrowse.model.JsonFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 7/14/2014
 * Time: 4:09 PM
 */
public class JBrowseRoot
{
    private static final Logger _log = LogManager.getLogger(JBrowseRoot.class);
    private Logger _customLogger = null;

    public JBrowseRoot(@Nullable Logger log)
    {
        _customLogger = log;
    }

    private Logger getLogger()
    {
        return _customLogger == null ? _log : _customLogger;
    }

    public static File getBaseDir(Container c)
    {
        return getBaseDir(c, true);
    }

    @Nullable
    public static File getBaseDir(Container c, boolean doCreate)
    {
        FileContentService fileService = FileContentService.get();
        File fileRoot = fileService == null ? null : fileService.getFileRoot(c, FileContentService.ContentType.files);
        if (fileRoot == null || !fileRoot.exists())
        {
            return null;
        }

        File jbrowseDir = new File(fileRoot, ".jbrowse");
        if (!jbrowseDir.exists())
        {
            if (!doCreate)
            {
                return null;
            }

            jbrowseDir.mkdirs();
        }

        return jbrowseDir;
    }

    public File getReferenceDir(Container c)
    {
        return new File(getBaseDir(c), "references");
    }

    public File getTracksDir(Container c)
    {
        return new File(getBaseDir(c), "tracks");
    }

    public File getDatabaseDir(Container c)
    {
        return new File(getBaseDir(c), "databases");
    }

    private boolean runScript(String scriptName, List<String> args) throws IOException
    {
        return runScript(scriptName, args, null);
    }

    private String getPerlLocation()
    {
        LabkeyScriptEngineManager svc = ServiceRegistry.get().getService(LabkeyScriptEngineManager.class);
        for (ExternalScriptEngineDefinition def : svc.getEngineDefinitions())
        {
            if (def.getExtensions() != null && Arrays.stream(def.getExtensions()).anyMatch("pl"::equals))
            {
                getLogger().debug("using perl engine path");
                return def.getExePath();
            }
        }

        String path = PipelineJobService.get().getConfigProperties().getSoftwarePackagePath("PERL_HOME");
        if (path == null)
        {
            path = System.getProperty("perl.home", System.getenv("PERL_HOME"));
        }

        return path == null ? "perl" : new File(path, "perl").getPath();
    }

    private boolean runScript(String scriptName, List<String> args, @Nullable File workingDir) throws IOException
    {
        File scriptFile = new File(JBrowseManager.get().getJBrowseBinDir(), scriptName);
        if (!scriptFile.exists())
        {
            getLogger().error("Unable to find jbrowse script: " + scriptFile.getPath());
            return false;
        }

        if (workingDir != null && !workingDir.exists())
        {
            workingDir.mkdirs();
        }

        args.add(0, getPerlLocation());
        args.add(1, scriptFile.getPath());

        getLogger().info("preparing jbrowse resource:");
        getLogger().info(StringUtils.join(args, " "));

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);

        if (workingDir != null)
        {
            getLogger().info("using working directory: " + workingDir.getPath());
            pb.directory(workingDir);
        }

        String path = System.getenv("PATH");
        if (path != null)
        {
            getLogger().debug("using PATH: " + path);
        }

        Process p = null;
        try
        {
            p = pb.start();
            try (BufferedReader procReader = Readers.getReader(p.getInputStream()))
            {
                String line;
                while ((line = procReader.readLine()) != null)
                {
                    getLogger().debug(line);
                }
            }

            p.waitFor();
        }
        catch (Exception e)
        {
            getLogger().error(e.getMessage(), e);
            return false;
        }
        finally
        {
            if (p != null)
            {
                p.destroy();
            }
        }

        return true;
    }

    public JsonFile prepareOutputFile(User u, Integer outputFileId, boolean forceRecreateJson) throws IOException
    {
        return prepareOutputFile(u, outputFileId, forceRecreateJson, null);
    }

    private File getOutDirForOutputFile(JsonFile jsonFile)
    {
        return new File(getTracksDir(jsonFile.getContainerObj()), "data-" + jsonFile.getOutputFile().toString());
    }

    public JsonFile prepareOutputFile(User u, Integer outputFileId, boolean forceRecreateJson, JSONObject additionalConfig) throws IOException
    {
        getLogger().debug("preparing outputfile: " + outputFileId);

        //find existing resource
        TableInfo jsonFiles = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
        TableSelector ts1 = new TableSelector(jsonFiles, new SimpleFilter(FieldKey.fromString("outputfile"), outputFileId), null);
        JsonFile jsonFile = null;
        if (ts1.exists())
        {
            jsonFile = ts1.getObject(JsonFile.class);
            File outDir = getOutDirForOutputFile(jsonFile);
            if (outDir.exists())
            {
                getLogger().debug("Inspecting directory for broken symlinks: " + outDir.getPath());
                for (File f : outDir.listFiles())
                {
                    if (Files.isSymbolicLink(f.toPath()) && !f.exists())
                    {
                        getLogger().info("found broken symlink: " + f.getPath());
                        getLogger().debug("deleting existing directory: " + outDir.getPath());
                        forceRecreateJson = true;
                        safeDeleteDiretory(outDir);
                        break;
                    }
                }
            }

            if (!forceRecreateJson)
            {
                return jsonFile;
            }
        }

        //in case we have a cached version, re-create
        if (forceRecreateJson && jsonFile != null)
        {
            File outDir = getOutDirForOutputFile(jsonFile);
            if (outDir.exists())
            {
                getLogger().debug("deleting existing directory: " + outDir.getPath());
                safeDeleteDiretory(outDir);
            }
            outDir.mkdirs();
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
        if (jsonFile == null)
        {
            getLogger().debug("adding new jsonfile record");

            Map<String, Object> jsonRecord = new CaseInsensitiveHashMap<>();
            jsonRecord.put("outputfile", outputFileId);

            File outDir = new File(getTracksDir(fileContainer), "data-" + outputFileId.toString());
            jsonRecord.put("relPath", FileUtil.relativePath(getBaseDir(fileContainer).getPath(), outDir.getPath()));
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

            jsonFile = ts1.getObject(JsonFile.class);
        }

        if (jsonFile != null)
        {
            File outDir = getOutDirForOutputFile(jsonFile);
            if (!outDir.exists())
            {
                outDir.mkdirs();
            }

            processFile(so.getExpData(), outDir, "data-" + jsonFile.getOutputFile(), jsonFile.getLabel(), additionalConfig, jsonFile.getCategory(), jsonFile.getRefLibraryData());
            if (getTrackListForOutputFile(jsonFile) == null)
            {
                getLogger().error("this outputfile/track lacks a trackList.conf file.  this probably indicates a problem when this resource was originally processed.  you should try to re-process it." + (jsonFile.getTrackRootDir() == null ? "" : "  expected to find file in: " + jsonFile.getTrackRootDir()));
                return null;
            }
        }

        return jsonFile;
    }

    public JsonFile prepareRefSeq(User u, Integer ntId, boolean forceRecreateJson) throws IOException
    {
        //validate
        TableInfo ti = JBrowseManager.get().getSequenceAnalysisTable("ref_nt_sequences");
        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowid"), ntId), null);
        RefNtSequenceModel model = ts.getObject(RefNtSequenceModel.class);

        if (model == null)
        {
            throw new IllegalArgumentException("Unable to find sequence: " + ntId);
        }

        //find existing resource
        TableInfo jsonFiles = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
        TableSelector ts1 = new TableSelector(jsonFiles, new SimpleFilter(FieldKey.fromString("sequenceid"), ntId), null);
        JsonFile jsonFile = null;
        if (ts1.exists())
        {
            jsonFile = ts1.getObject(JsonFile.class);
            if (!forceRecreateJson)
            {
                return jsonFile;
            }
        }

        File outDir = new File(getReferenceDir(ContainerManager.getForId(model.getContainer())), ntId.toString());
        if (outDir.exists())
        {
            safeDeleteDiretory(outDir);
        }
        outDir.mkdirs();

        //else create
        if (jsonFile == null)
        {
            Map<String, Object> jsonRecord = new CaseInsensitiveHashMap<>();
            jsonRecord.put("sequenceid", ntId);
            jsonRecord.put("relPath", FileUtil.relativePath(getBaseDir(ContainerManager.getForId(model.getContainer())).getPath(), outDir.getPath()));
            jsonRecord.put("container", model.getContainer());
            jsonRecord.put("created", new Date());
            jsonRecord.put("createdby", u.getUserId());
            jsonRecord.put("modified", new Date());
            jsonRecord.put("modifiedby", u.getUserId());
            jsonRecord.put("objectid", new GUID().toString().toUpperCase());
            Table.insert(u, jsonFiles, jsonRecord);

            jsonFile = ts1.getObject(JsonFile.class);
        }

        File fasta = AssayFileWriter.findUniqueFileName(FileUtil.makeLegalName(model.getName()) + ".fasta", outDir);

        try (PrintWriter writer = PrintWriters.getPrintWriter(fasta))
        {
            writer.write(">" + model.getName() + "\n");

            String seq = model.getSequence();
            int len = seq == null ? 0 : seq.length();
            int partitionSize = 60;
            for (int i=0; i<len; i+=partitionSize)
            {
                writer.write(seq.substring(i, Math.min(len, i + partitionSize)) + "\n");
            }
        }

        List<String> args = new ArrayList<>();

        //TODO: consider switching to:
        //args.add("--indexed_fasta");
        //faiUrlTemplate
        //urlTemplate

        args.add("--fasta");
        args.add(fasta.getPath());

        args.add("--trackLabel");
        args.add(model.getRowid() + "_" + model.getName());

        args.add("--key");
        args.add(model.getName());

        if (JBrowseManager.get().compressJSON())
        {
            args.add("--compress");
        }

        args.add("--out");
        args.add(outDir.getPath());

        boolean success = runScript("prepare-refseqs.pl", args);
        if (success)
        {
            fasta.delete();
        }

        File trackList = new File(outDir, "trackList.json");
        if (trackList.exists())
        {
            JSONObject obj = readFileToJson(trackList);
            String urlTemplate = obj.getJSONArray("tracks").getJSONObject(0).getString("urlTemplate");
            urlTemplate = "references/" + ntId.toString() + "/" + urlTemplate;
            obj.getJSONArray("tracks").getJSONObject(0).put("urlTemplate", urlTemplate);

            writeJsonToFile(trackList, obj.toString(1));
        }

        return jsonFile;
    }

    public void prepareDatabase(Database database, User u, @Nullable PipelineJob job) throws IOException, PipelineJobException
    {
        File outDir = new File(getDatabaseDir(database.getContainerObj()), database.getObjectId());

        //Note: delete entire directory to ensure we recreate symlinks, etc.
        if (outDir.exists())
        {
            getLogger().info("deleting existing directory");
            safeDeleteDiretory(outDir);
        }

        outDir.mkdirs();

        File seqDir = new File(outDir, "seq");
        seqDir.mkdirs();

        File trackDir = new File(outDir, "tracks");
        trackDir.mkdirs();

        TableInfo tableMembers = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASE_MEMBERS);
        TableSelector ts = new TableSelector(tableMembers, new SimpleFilter(FieldKey.fromString("database"), database.getObjectId()), null);
        final List<String> jsonGuids = new ArrayList<>();
        ts.forEach(rs ->
        {
            String jsonGuid = rs.getString("jsonfile");
            if (jsonGuid != null)
            {
                jsonGuids.add(jsonGuid);
            }
        });

        List<JsonFile> jsonFiles = new ArrayList<>();
        TableInfo tableJsonFiles = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
        Sort sort = new Sort("sequenceid/name,trackid/name");
        TableSelector ts2 = new TableSelector(tableJsonFiles, new SimpleFilter(FieldKey.fromString("objectid"), jsonGuids, CompareType.IN), sort);
        jsonFiles.addAll(ts2.getArrayList(JsonFile.class));

        //also add library members:
        Database db = new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES), new SimpleFilter(FieldKey.fromString("objectid"), database.getObjectId()), null).getObject(Database.class);
        if (db != null)
        {
            getLogger().info("adding library: " + db.getLibraryId());
            List<Integer> refNts = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("reference_library_members"), PageFlowUtil.set("ref_nt_id"), new SimpleFilter(FieldKey.fromString("library_id"), db.getLibraryId()), null).getArrayList(Integer.class);

            getLogger().info("total ref sequences: " + refNts.size());
            int j = 0;
            for (Integer refNtId : refNts)
            {
                //NOTE: this should also have the effect of allowing the job to cancel if it has been cancelled by the user
                if (job != null && j % 100 == 0)
                {
                    job.setStatus(PipelineJob.TaskStatus.running, "Processing sequence " + (j+1) + " of " + refNts.size());
                }
                j++;

                JsonFile f = prepareRefSeq(u, refNtId, false);
                if (f != null && !jsonGuids.contains(f.getObjectId()))
                {
                    jsonFiles.add(f);
                    jsonGuids.add(f.getObjectId());
                }
            }

            if (job != null)
            {
                job.setStatus(PipelineJob.TaskStatus.running, "Processing tracks");
            }

            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("library_id"), db.getLibraryId());
            filter.addCondition(FieldKey.fromString("datedisabled"), null, CompareType.ISBLANK);

            List<Integer> trackIds = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("reference_library_tracks"), PageFlowUtil.set("rowid"), filter, null).getArrayList(Integer.class);
            getLogger().info("total tracks: " + trackIds.size());
            j = 0;
            for (Integer trackId : trackIds)
            {
                //NOTE: this should also have the effect of allowing the job to cancel if it has been cancelled by the user
                if (job != null && j % 10 == 0)
                {
                    job.setStatus(PipelineJob.TaskStatus.running, "Processing track " + (j+1) + " of " + trackIds.size());
                }
                j++;

                JsonFile f = prepareFeatureTrack(u, trackId, "Reference Annotations", false);
                if (f != null && !jsonGuids.contains(f.getObjectId()))
                {
                    f.setCategory("Reference Annotations");
                    jsonFiles.add(f);
                    jsonGuids.add(f.getObjectId());
                }
            }

            if (job != null)
            {
                job.setStatus(PipelineJob.TaskStatus.running, null);
            }
        }

        JSONArray refSeq = new JSONArray();
        JSONObject trackList = new JSONObject();
        List<String> defaultTrackLabels = new ArrayList<>();
        trackList.put("formatVersion", 1);

        JSONObject tracks = new JSONObject();

        jsonFiles.sort((o1, o2) ->
        {
            int ret = o1.getCategory().compareTo(o2.getCategory());
            if (ret != 0)
            {
                return ret;
            }

            return o1.getLabel().compareTo(o2.getLabel());
        });

        int compressedRefs = 0;
        Set<Integer> referenceIds = new HashSet<>();

        getLogger().info("total JSON files: " + jsonFiles.size());
        int j = 0;
        for (JsonFile f : jsonFiles)
        {
            JSONObject config = f.getExtraTrackConfig(job.getLogger());
            //NOTE: this should also have the effect of allowing the job to cancel if it has been cancelled by the user
            if (job != null && j % 100 == 0)
            {
                job.setStatus(PipelineJob.TaskStatus.running, "Processing file " + (j+1) + " of " + jsonFiles.size());
            }
            j++;

            getLogger().info("processing JSON file: " + f.getObjectId());
            if (config != null && config.optBoolean("omitTrack", false))
            {
                job.getLogger().info("skipping track because omitTrack=true in extra config");
                continue;
            }

            if (f.getSequenceId() != null)
            {
                getLogger().info("adding ref seq: " + f.getSequenceId());
                if (f.getRefSeqsFile() == null)
                {
                    prepareRefSeq(u, f.getSequenceId(), true);
                }

                if (f.getRefSeqsFile() == null)
                {
                    throw new IOException("There was an error preparing ref seq file for sequence: " + f.getSequenceId());
                }

                referenceIds.add(f.getSequenceId());
                JSONArray arr = readFileToJsonArray(f.getRefSeqsFile());
                for (int i = 0; i < arr.length(); i++)
                {
                    refSeq.put(arr.get(i));
                }

                //inspect for compression
                if (hasFilesWithExtension(new File(f.getBaseDir(), "seq"), "txtz"))
                {
                    getLogger().info("reference is compressed");
                    compressedRefs++;
                }
                else
                {
                    getLogger().info("reference is not compressed");
                }

                //also add sym link to raw json
                mergeSequenceDirs(seqDir, new File(f.getBaseDir(), "seq"));
            }
            else if (f.getTrackId() != null)
            {
                getLogger().info("adding track: " + f.getTrackId());

                //try to recreate if it does not exist
                if (f.getTrackListFile() == null)
                {
                    prepareFeatureTrack(u, f.getTrackId(), f.getCategory(), true);
                    if (f.getTrackListFile() == null)
                    {
                        getLogger().error("this track lacks a trackList.conf file.  this probably indicates a problem when this resource was originally processed.  you should try to re-process it." + (f.getTrackRootDir() == null ? "" : "  expected to find file in: " + f.getTrackRootDir()));
                        continue;
                    }
                }

                JSONObject json = readFileToJson(f.getTrackListFile());
                if (json.containsKey("tracks"))
                {
                    JSONArray existingTracks = trackList.containsKey("tracks") ? trackList.getJSONArray("tracks") : new JSONArray();
                    JSONArray arr = json.getJSONArray("tracks");
                    for (int i = 0; i < arr.length(); i++)
                    {
                        JSONObject o = arr.getJSONObject(i);
                        if (f.getCategory() != null)
                        {
                            o.put("category", f.getCategory());
                        }

                        if (f.getExtraTrackConfig(getLogger()) != null)
                        {
                            getLogger().debug("adding extra track config");
                            o.putAll(f.getExtraTrackConfig(getLogger()));

                            if (isVisible(f))
                            {
                                getLogger().debug("adding default visible track: " + o.getString("label"));
                                defaultTrackLabels.add(o.getString("label"));
                            }
                        }

                        if (o.get("urlTemplate") != null)
                        {
                            getLogger().debug("updating urlTemplate");
                            getLogger().debug("old: " + o.getString("urlTemplate"));
                            o.put("urlTemplate", o.getString("urlTemplate").replaceAll("^tracks/track-" + f.getTrackId() + "/data/tracks", "tracks"));
                            getLogger().debug("new: " + o.getString("urlTemplate"));
                        }

                        existingTracks.put(o);
                    }

                    trackList.put("tracks", existingTracks);
                }

                //even through we're loading the raw data based on urlTemplate, make a symlink from this location into our DB so generate-names.pl works properly
                File sourceFile = f.expectDataSubdirForTrack() ? new File(f.getTrackRootDir(), "tracks/track-" + f.getTrackId()) : f.getTrackRootDir();
                File targetFile = new File(outDir, "tracks/track-" + f.getTrackId());

                createSymlink(targetFile, sourceFile);
            }
            else if (f.getOutputFile() != null)
            {
                getLogger().info("processing output file: " + f.getOutputFile());

                Container target = f.getContainerObj();
                target = target.isWorkbook() ? target.getParent() : target;
                UserSchema us = QueryService.get().getUserSchema(u, target, JBrowseManager.SEQUENCE_ANALYSIS);
                TableInfo ti = us.getTable("outputfiles");
                if (ti == null)
                {
                    getLogger().error("unable to find outputfiles table:");
                    getLogger().error("container: " + target.getPath());
                    getLogger().error("user: " + u.getUserId());
                    getLogger().error("userName: " + u.getDisplayName(u));
                    getLogger().error("has read permission: " + target.hasPermission(u, ReadPermission.class));
                    getLogger().error("has home read permission: " + ContainerManager.getHomeContainer().hasPermission(u, ReadPermission.class));
                    getLogger().error("has shared read permission: " + ContainerManager.getSharedContainer().hasPermission(u, ReadPermission.class));
                    getLogger().error("other tables: " + StringUtils.join(us.getTableNames(), ";"));

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
                TableSelector outputFileTs = new TableSelector(ti, cols.values(), new SimpleFilter(FieldKey.fromString("rowid"), f.getOutputFile()), null);
                if (!outputFileTs.exists())
                {
                    getLogger().error("unable to find outputfile: " + f.getOutputFile() + " in container: " + ti.getUserSchema().getContainer().getPath());
                    continue;
                }

                ExpData d = f.getExpData();
                if (d == null || !d.getFile().exists())
                {
                    getLogger().error("unable to find file for output: " + f.getOutputFile());
                    continue;
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
                    catch(SQLException e)
                    {
                        throw new IOException(e);
                    }
                }

                File trackFile = getTrackListForOutputFile(f);
                boolean doCreate = trackFile == null;
                if (trackFile != null && trackFile.getParentFile().exists())
                {
                    getLogger().debug("Inspecting existing directory for broken symlinks: " + trackFile.getParentFile().getPath());
                    for (File file : trackFile.getParentFile().listFiles())
                    {
                        if (Files.isSymbolicLink(file.toPath()) && !file.exists())
                        {
                            getLogger().info("found broken symlink: " + file.getPath());
                            getLogger().debug("deleting existing directory: " + outDir.getPath());
                            doCreate = true;
                            safeDeleteDiretory(outDir);
                            break;
                        }
                    }
                }

                //try to recreate if it does not exist
                if (doCreate)
                {
                    getLogger().info("existing trackList not found, creating");
                    File outputDir = new File(getTracksDir(f.getContainerObj()), "data-" + f.getOutputFile().toString());
                    if (outputDir.exists())
                    {
                        getLogger().info("deleting existing json directory");
                        safeDeleteDiretory(outputDir);
                    }
                    outputDir.mkdirs();

                    processFile(d, outputDir, "data-" + f.getOutputFile(), f.getLabel(), metadata, f.getCategory(), f.getRefLibraryData());
                    if (getTrackListForOutputFile(f) == null)
                    {
                        getLogger().error("this outputfile/track lacks a trackList.conf file.  this probably indicates a problem when this resource was originally processed.  you should try to re-process it." + (f.getTrackRootDir() == null ? "" : "  expected to find file in: " + f.getTrackRootDir()));
                        continue;
                    }
                }
                else
                {
                    //double check that VCF index exists
                    File input = d.getFile();
                    if (input.exists() && vcfType.isType(input))
                    {
                        SequenceAnalysisService.get().ensureVcfIndex(input, getLogger());
                    }
                }

                JSONArray existingTracks = trackList.containsKey("tracks") ? trackList.getJSONArray("tracks") : new JSONArray();
                JSONObject json = readFileToJson(getTrackListForOutputFile(f));
                if (json != null && !json.isEmpty())
                {
                    JSONArray outputFileTracks = json.containsKey("tracks") ? json.getJSONArray("tracks") : new JSONArray();
                    for (JSONObject o : outputFileTracks.toJSONObjectArray())
                    {
                        if (f.getCategory() != null)
                        {
                            o.put("category", f.getCategory());
                        }

                        if (f.getExtraTrackConfig(getLogger()) != null)
                        {
                            getLogger().debug("adding extra track config");
                            o.putAll(f.getExtraTrackConfig(getLogger()));

                            if (isVisible(f))
                            {
                                getLogger().debug("adding default visible track: " + o.getString("label"));
                                defaultTrackLabels.add(o.getString("label"));
                            }
                        }

                        String outDirPrefix = "tracks/data-" + f.getOutputFile().toString();
                        if (o.get("urlTemplate") != null)
                        {
                            getLogger().debug("updating urlTemplate");
                            getLogger().debug("old: " + o.getString("urlTemplate"));
                            o.put("urlTemplate", o.getString("urlTemplate").replaceAll("^tracks/data-" + f.getOutputFile() + "/data/", ""));
                            getLogger().debug("new: " + o.getString("urlTemplate"));
                        }

                        existingTracks.put(o);
                        trackList.put("tracks", existingTracks);

                        // Note: this previously used this logic:
                        File sourceFile = f.expectDataSubdirForTrack() ? new File(f.getTrackRootDir(), "tracks/data-" + f.getOutputFile()) : f.getTrackRootDir();
                        File targetFile = new File(outDir, outDirPrefix);
                        if (!targetFile.getParentFile().exists())
                        {
                            targetFile.getParentFile().mkdirs();
                        }

                        createSymlink(targetFile, sourceFile);
                    }
                }
            }
            else
            {
                getLogger().error("json file lacks refSeq and trackList, cannot be included.  this might indicate it is an unsupported file type, or that the file should be re-processed");
            }
        }

        if (job != null)
        {
            job.setStatus(PipelineJob.TaskStatus.running, null);
        }

        if (referenceIds.size() != compressedRefs && compressedRefs > 0)
        {
            getLogger().error("Some references are compressed and some are not.  Total ref: " + referenceIds.size() + ", compressed: " + compressedRefs + ".  This will cause rendering problems.");
        }

        //add single track for reference sequence
        //combine ref seqs into a single track
        JSONArray existingTracks = trackList.containsKey("tracks") ? trackList.getJSONArray("tracks") : new JSONArray();
        JSONObject o = new JSONObject();
        o.put("category", "Reference Annotations");
        o.put("storeClass", "JBrowse/Store/Sequence/StaticChunked");
        o.put("chunkSize", 20000);
        o.put("label", "reference_sequence");
        o.put("key", "Reference sequence");
        o.put("type", "SequenceTrack");
        o.put("showTranslation", false);

        defaultTrackLabels.add(0, o.getString("label"));

        //NOTE: this isnt perfect.  these tracks might have been created in the past with a different setting, and in fact some might be compressed and some not.
        if (compressedRefs > 0 && compressedRefs == referenceIds.size())
        {
            o.put("compress", 1);
        }
        o.put("urlTemplate", "seq/{refseq_dirpath}/{refseq}-");
        existingTracks.put(o);
        trackList.put("tracks", existingTracks);

        //look in reference_aa_sequences and ref_nt_features and create track of these if they exist
        JSONObject codingRegionTrack = createCodingRegionTrack(db.getContainerObj(), referenceIds, trackDir);
        if (codingRegionTrack != null)
        {
            JSONArray existingTracks2 = trackList.containsKey("tracks") ? trackList.getJSONArray("tracks") : new JSONArray();
            existingTracks2.put(codingRegionTrack);
            trackList.put("tracks", existingTracks2);

            defaultTrackLabels.add(1, o.getString("label"));
        }


        JSONObject featureTrack = createFeatureTrack(db.getContainerObj(), referenceIds, trackDir);
        if (featureTrack != null)
        {
            JSONArray existingTracks2 = trackList.containsKey("tracks") ? trackList.getJSONArray("tracks") : new JSONArray();
            existingTracks2.put(featureTrack);
            trackList.put("tracks", existingTracks2);

            defaultTrackLabels.add(1, o.getString("label"));
        }

        JSONObject nameJson = new JSONObject();
        nameJson.put("url", "names/");
        nameJson.put("type", "Hash");
        trackList.put("names", nameJson);

        if (!defaultTrackLabels.isEmpty())
        {
            trackList.put("alwaysOnTracks", StringUtils.join(defaultTrackLabels, ","));
        }

        if (database.getJsonConfig() != null)
        {
            try
            {
                trackList.putAll(new JSONObject(database.getJsonConfig()));
            }
            catch (JSONException e)
            {
                _log.error("error parsing jsonConfig", e);
            }
        }

        writeJsonToFile(new File(seqDir, "refSeqs.json"), refSeq.toString(1));
        writeJsonToFile(new File(outDir, "trackList.json"), trackList.toString(1));
        writeJsonToFile(new File(outDir, "tracks.json"), tracks.toString(1));
        writeJsonToFile(new File(outDir, "tracks.conf"), "");

        File namesDir = new File(outDir, "names");
        if (!shouldCreateOwnIndex(database.getObjectId()))
        {
            File existingNamesDir = getPreviouslyCreatedIndex(database.getObjectId());
            if (existingNamesDir == null)
            {
                getLogger().error("Unable to find expected search index.  this may mean the parent database needs to be regenerated");
            }
            else
            {
                getLogger().info("reusing existing search index: " + existingNamesDir.getPath());
                if (namesDir.exists())
                {
                    safeDeleteDiretory(namesDir);
                }

                if (existingNamesDir.exists())
                {
                    createSymlink(namesDir, existingNamesDir);
                }
                else
                {
                    getLogger().error("Unable to find expected search index.  this may mean the parent database needs to be regenerated: " + existingNamesDir.getPath());
                }
            }
        }
        else
        {
            getLogger().info("will attempt to generate search index");
            if (namesDir.exists() && namesDir.list() != null && namesDir.list().length > 0)
            {
                getLogger().info("existing search index dir found, will not recreate: " + namesDir.getPath());
            }
            else
            {
                getLogger().info("creating search index: " + namesDir.getPath());
                if (!namesDir.exists())
                {
                    namesDir.mkdirs();
                }

                List<String> args = new ArrayList<>(Arrays.asList("--out", outDir.getPath()));
                if (JBrowseManager.get().compressJSON())
                {
                    args.add("--compress");
                }

                args.add("--verbose");
                args.add("--mem");
                args.add("536870912");

                args.add("--completionLimit");
                args.add("20");

                //args.add("--hashBits");
                //args.add("16");

                Set<String> tracksToIndex = new HashSet<>();
                for (JSONObject track : trackList.getJSONArray("tracks").toJSONObjectArray())
                {
                    if (!track.containsKey("doIndex") || Boolean.parseBoolean(track.get("doIndex").toString()))
                    {
                        tracksToIndex.add(track.getString("label"));
                    }
                    else
                    {
                        getLogger().debug("skipping indexing of track: " + track.getString("label"));
                    }
                }

                if (!tracksToIndex.isEmpty())
                {
                    args.add("--tracks");
                    args.add(StringUtils.join(tracksToIndex, ","));

                    if (job != null)
                    {
                        job.setStatus(PipelineJob.TaskStatus.running, "Preparing search index");
                    }

                    runScript("generate-names.pl", args);
                }
                else
                {
                    getLogger().debug("no tracks to index, skipping generate-names.pl");
                }
            }
        }
    }

    private boolean shouldCreateOwnIndex(String databaseId) throws PipelineJobException
    {
        TableSelector ts = new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES), PageFlowUtil.set("libraryId", "createOwnIndex", "primaryDb"), new SimpleFilter(FieldKey.fromString("objectid"), databaseId), null);
        Map<String, Object> map = ts.getMap();
        if (map == null)
        {
            throw new PipelineJobException("Unable to find record of database: " + databaseId);
        }

        if (Boolean.TRUE.equals(map.get("createOwnIndex")) || Boolean.TRUE.equals(map.get("primaryDb")))
        {
            return true;
        }

        getLogger().debug("this session will attempt to use the previously cached index from the parent session for this genome");
        return false;
    }

    private boolean isVisible(JsonFile f)
    {
        JSONObject json = f.getExtraTrackConfig(getLogger());
        if (json == null || json.get("visibleByDefault") == null)
            return false;

        return Boolean.parseBoolean(json.get("visibleByDefault").toString());
    }

    private File getPreviouslyCreatedIndex(String databaseId) throws PipelineJobException
    {
        Integer libraryId = new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES), PageFlowUtil.set("libraryId"), new SimpleFilter(FieldKey.fromString("objectid"), databaseId), null).getObject(Integer.class);
        if (libraryId == null)
        {
            throw new PipelineJobException("Unable to find libary Id for session");
        }

        SimpleFilter referenceFilter = new SimpleFilter(FieldKey.fromString("libraryId"), libraryId);
        referenceFilter.addCondition(FieldKey.fromString("primarydb"), true);
        TableSelector referenceTs = new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES), PageFlowUtil.set("objectid", "container"), referenceFilter, null);
        Map<String, Object> refMap = referenceTs.getObject(Map.class);
        if (refMap == null)
        {
            throw new PipelineJobException("unable to find record of parent DB for this genome: " + libraryId);
        }

        Container refContainer = ContainerManager.getForId((String)refMap.get("container"));
        if (refContainer == null)
        {
            throw new PipelineJobException("unable to find container matching: " + refMap.get("container"));
        }

        File ret = new File(getDatabaseDir(refContainer), refMap.get("objectid") + "/names");
        if (!ret.exists())
        {
            getLogger().error("expected index directory does not exist: " + ret.getPath());
        }

        return ret;
    }

    private File getTrackListForOutputFile(JsonFile json)
    {
        //first try at the top-level
        File ret = new File(json.getTrackRootDir(), "trackList.json");
        if (ret.exists())
        {
            return ret;
        }

        ret = new File(ret.getParentFile(), "data");
        ret = new File(ret, "trackList.json");
        if (ret.exists())
        {
            return ret;
        }

        return null;
    }

    private JSONObject createCodingRegionTrack(Container c, Set<Integer> referenceIds, File databaseTrackDir) throws IOException
    {
        //first add coding regions
        TableSelector ts = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("ref_aa_sequences"), new SimpleFilter(FieldKey.fromString("ref_nt_id"), referenceIds, CompareType.IN), new Sort("ref_nt_id,start_location"));
        if (!ts.exists())
        {
            return null;
        }

        File aaFeaturesOutFile = new File(databaseTrackDir, "aaFeatures.gff");
        try (final PrintWriter writer = PrintWriters.getPrintWriter(aaFeaturesOutFile))
        {
            //first find ref_aa_sequences
            ts.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    String name = rs.getString("name");
                    Integer refNtId = rs.getInt("ref_nt_id");
                    Boolean isComplement = rs.getObject("isComplement") != null && rs.getBoolean("isComplement");
                    String exons = StringUtils.trimToNull(rs.getString("exons"));
                    if (exons == null)
                    {
                        return;
                    }

                    RefNtSequenceModel ref = RefNtSequenceModel.getForRowId(refNtId);
                    String refName = ref.getName();

                    String[] tokens = StringUtils.split(exons, ";");

                    String featureId = refName + "_" + name;
                    String strand = isComplement ? "-" : "+";
                    String[] lastExon = StringUtils.split(tokens[tokens.length - 1], "-");
                    if (lastExon.length != 2)
                    {
                        return;
                    }

                    writer.write(StringUtils.join(new String[]{refName, "ReferenceAA", "gene", rs.getString("start_location"), lastExon[1], ".", strand, ".", "ID=" + featureId + ";Note="}, '\t') + System.getProperty("line.separator"));

                    for (String exon : tokens)
                    {
                        String[] borders = StringUtils.split(exon, "-");
                        if (borders.length != 2)
                        {
                            getLogger().error("improper exon: " + exon);
                            return;
                        }

                        writer.write(StringUtils.join(new String[]{refName, "ReferenceAA", "CDS", borders[0], borders[1], ".", strand, ".", "Parent=" + featureId}, '\t') + System.getProperty("line.separator"));
                    }
                }
            });
        }

        //now process track
        String featureName = "CodingRegions";
        File outDir = new File(databaseTrackDir, "tmpTrackDir");
        if (outDir.exists())
        {
            safeDeleteDiretory(outDir);
        }

        outDir.mkdirs();

        JSONObject ret = processFlatFile(c, aaFeaturesOutFile, outDir, "--gff", featureName, "Coding Regions", null, "Reference Annotations", "JBrowse/View/Track/CanvasFeatures", null);

        //move file, so name parsing works properly
        File source = new File(databaseTrackDir, "tmpTrackDir/data/tracks/" + featureName);
        File dest = new File(databaseTrackDir, featureName);
        FileUtils.moveDirectory(source, dest);
        safeDeleteDiretory(outDir);

        //update urlTemplate
        String relPath = FileUtil.relativePath(getBaseDir(c).getPath(), databaseTrackDir.getPath());
        getLogger().debug("using relative path: " + relPath);
        ret.put("urlTemplate", "tracks/" + featureName + "/{refseq}/trackData.json" + (JBrowseManager.get().compressJSON() ? "z" : ""));

        aaFeaturesOutFile.delete();

        return ret;
    }

    private JSONObject createFeatureTrack(Container c, Set<Integer> referenceIds, File databaseTrackDir) throws IOException
    {
        //first add coding regions
        TableSelector ts = new TableSelector(JBrowseManager.get().getSequenceAnalysisTable("ref_nt_features"), new SimpleFilter(FieldKey.fromString("ref_nt_id"), referenceIds, CompareType.IN), new Sort("ref_nt_id,nt_start"));
        if (!ts.exists())
        {
            return null;
        }

        File aaFeaturesOutFile = new File(databaseTrackDir, "ntFeatures.gff");
        try (final PrintWriter writer = PrintWriters.getPrintWriter(aaFeaturesOutFile))
        {
            //first find ref_aa_sequences
            ts.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    String name = rs.getString("name");
                    Integer refNtId = rs.getInt("ref_nt_id");
                    RefNtSequenceModel ref = RefNtSequenceModel.getForRowId(refNtId);
                    String refName = ref.getName();

                    String featureId = refName + "_" + name;
                    writer.write(StringUtils.join(new String[]{refName, "ReferenceNTFeatures", rs.getString("category"), rs.getString("nt_start"), rs.getString("nt_stop"), ".", "+", ".", "ID=" + featureId + ";Note="}, '\t') + System.getProperty("line.separator"));
                }
            });
        }

        //now process track
        String featureName = "SequenceFeatures";
        File outDir = new File(databaseTrackDir, "tmpTrackDir");
        if (outDir.exists())
        {
            safeDeleteDiretory(outDir);
        }

        outDir.mkdirs();

        JSONObject ret = processFlatFile(c, aaFeaturesOutFile, outDir, "--gff", featureName, "Coding Regions", null, "Reference Annotations", "JBrowse/View/Track/CanvasFeatures", null);

        //move file, so name parsing works properly
        File source = new File(databaseTrackDir, "tmpTrackDir/data/tracks/" + featureName);
        File dest = new File(databaseTrackDir, featureName);
        FileUtils.moveDirectory(source, dest);
        safeDeleteDiretory(outDir);

        //update urlTemplate
        String relPath = FileUtil.relativePath(getBaseDir(c).getPath(), databaseTrackDir.getPath());
        getLogger().debug("using relative path: " + relPath);
        ret.put("urlTemplate", featureName + "/{refseq}/trackData.json" + (JBrowseManager.get().compressJSON() ? "z" : ""));

        aaFeaturesOutFile.delete();

        return ret;
    }

    private boolean hasFilesWithExtension(File root, String ext)
    {
        for (File f : root.listFiles())
        {
            if (f.isDirectory())
            {
                if (hasFilesWithExtension(f, ext))
                {
                    return true;
                }
            }
            else if (ext.equals(FileUtil.getExtension(f)))
            {
                return true;
            }
        }

        return false;
    }

    private void mergeSequenceDirs(File targetDir, File sourceDir)
    {
        for (File f : sourceDir.listFiles())
        {
            if (f.isDirectory())
            {
                processDir(f, targetDir, f.getName(), 1);
            }
        }
    }

    private void createSymlink(File targetFile, File sourceFile) throws IOException
    {
        getLogger().info("creating sym link");
        getLogger().debug("source: " + sourceFile.getPath());
        getLogger().debug("target: " + targetFile.getPath());

        if (!sourceFile.exists())
        {
            getLogger().error("unable to find file: " + sourceFile.getPath());
        }

        if (targetFile.exists())
        {
            getLogger().warn("target of symlink already exists: " + targetFile.getPath());
            if (FileUtils.isSymlink(targetFile))
            {
                targetFile.delete();
            }
        }

        if (!targetFile.exists())
        {
            Files.createSymbolicLink(targetFile.toPath(), sourceFile.toPath());
        }
        else
        {
            getLogger().info("symlink target already exists, skipping: " + targetFile.getPath());
        }
    }

    private void processDir(File sourceDir, File targetDir, String relPath, int depth)
    {
        File newFile = new File(targetDir, relPath);
        if (!newFile.exists())
        {
            newFile.mkdirs();
        }

        for (File f : sourceDir.listFiles())
        {
            String ext = FileUtil.getExtension(f);
            if (f.isDirectory() && depth < 2)
            {
                processDir(f, targetDir, relPath + "/" + f.getName(), (depth + 1));
            }
            else if (depth >= 2 || "txtz".equals(ext) || "txt".equals(ext) || "jsonz".equals(ext) || "json".equals(ext) || ".htaccess".equals(f.getName()))
            {
                try
                {
                    File sourceFile = f;
                    File targetFile = new File(targetDir, relPath + "/" + f.getName());

                    createSymlink(targetFile, sourceFile);
                }
                catch (UnsupportedOperationException | IOException e)
                {
                    getLogger().error(e);
                }
            }
            else
            {
                getLogger().info("will not process file: " + f.getName());
            }
        }
    }

    public JsonFile prepareFeatureTrack(User u, Integer trackId, @Nullable String category, boolean forceRecreateJson) throws IOException
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
        if (!data.getFile().exists())
        {
            throw new IllegalArgumentException("File does not exist: " + data.getFile().getPath());
        }

        //Note: it is possible for this to be a non-supported filetype

        //find existing resource
        TableInfo jsonFiles = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
        TableSelector ts1 = new TableSelector(jsonFiles, new SimpleFilter(FieldKey.fromString("trackid"), trackId), null);
        JsonFile jsonFile = null;
        if (ts1.exists())
        {
            jsonFile = ts1.getObject(JsonFile.class);
            if (!forceRecreateJson)
            {
                return jsonFile;
            }
        }

        File outDir = new File(getTracksDir(data.getContainer()), "track-" + trackId.toString());
        if (outDir.exists())
        {
            safeDeleteDiretory(outDir);
        }
        outDir.mkdirs();

        //else create
        if (jsonFile == null)
        {
            Map<String, Object> jsonRecord = new CaseInsensitiveHashMap<>();
            jsonRecord.put("trackid", trackId);
            jsonRecord.put("relPath", FileUtil.relativePath(getBaseDir(data.getContainer()).getPath(), outDir.getPath()));
            jsonRecord.put("container", data.getContainer().getId());
            jsonRecord.put("created", new Date());
            jsonRecord.put("createdby", u.getUserId());
            jsonRecord.put("modified", new Date());
            jsonRecord.put("modifiedby", u.getUserId());
            jsonRecord.put("objectid", new GUID().toString().toUpperCase());

            TableInfo jsonTable = JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES);
            Table.insert(u, jsonTable, jsonRecord);

            jsonFile = ts1.getObject(JsonFile.class);
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("Description", trackRowMap.get("description"));

        processFile(data, outDir, "track-" + (trackRowMap.get("rowid")).toString(), (String)trackRowMap.get("name"), metadata, category == null ? (String)trackRowMap.get("category") : category, null);

        return jsonFile;
    }

    private File convertGtfToGff(File gtf, File gff)
    {
        getLogger().info("attempting to convert GTF to GFF: " + gtf.getName());
        File gffread = SequencePipelineService.get().getExeForPackage("GFFREADPATH", "gffread");
        if (gffread == null || !gffread.exists())
        {
            getLogger().warn("unable to find gffread");
            return null;
        }

        try
        {
            SimpleScriptWrapper wrapper = new SimpleScriptWrapper(getLogger());
            wrapper.execute(Arrays.asList(gffread.getPath(), gtf.getPath(), "-E", "-F", "-O", "-o", gff.getPath()));

            return gff;
        }
        catch (PipelineJobException e)
        {
            getLogger().error(e.getMessage(), e);
        }

        return null;
    }

    private final FileType bamType = new FileType("bam", FileType.gzSupportLevel.NO_GZ);
    private final FileType vcfType = new FileType("vcf", FileType.gzSupportLevel.SUPPORT_GZ);

    private List<JSONObject> processFile(ExpData data, File outDir, String featureName, String featureLabel, Map<String, Object> metadata, String category, @Nullable ExpData refGenomeData) throws IOException
    {
        File input = data.getFile();

        String ext = FileUtil.getExtension(data.getFile());
        if ("gff3".equalsIgnoreCase(ext) || "gff".equalsIgnoreCase(ext) || "gtf".equalsIgnoreCase(ext))
        {
            File temp = null;
            Set<String> nameAttrs = new HashSet<>();
            nameAttrs.add("gene_id");
            nameAttrs.add("gene_name");
            nameAttrs.add("ncbi_geneid");
            nameAttrs.add("ensembl_geneid");
            if ("gtf".equalsIgnoreCase(ext))
            {
                getLogger().info("converting GTF to GFF");
                temp = convertGtfToGff(data.getFile(), File.createTempFile("temp", ".gff"));
            }

            JSONObject ret = processFlatFile(data.getContainer(), (temp == null ? data.getFile() : temp), outDir, "--gff", featureName, featureLabel, metadata, category, "JBrowse/View/Track/CanvasFeatures", nameAttrs);
            writeTrackList(outDir, ret);

            if (temp != null)
            {
                temp.delete();
            }

            return ret == null ? null : Arrays.asList(ret);
        }
        else if ("bed".equalsIgnoreCase(ext) || "bedgraph".equalsIgnoreCase(ext))
        {
            JSONObject ret = processFlatFile(data.getContainer(), data.getFile(), outDir, "--bed", featureName, featureLabel, metadata, category, null, null);
            writeTrackList(outDir, ret);
            return ret == null ? null : Arrays.asList(ret);
        }
        else if ("gbk".equalsIgnoreCase(ext))
        {
            JSONObject ret = processFlatFile(data.getContainer(), data.getFile(), outDir, "--gbk", featureName, featureLabel, metadata, category, null, null);
            writeTrackList(outDir, ret);
            return ret == null ? null : Arrays.asList(ret);
        }
        else if ("bigwig".equalsIgnoreCase(ext) || "bw".equalsIgnoreCase(ext))
        {
            JSONObject ret = processBigWig(data, outDir, featureName, featureLabel, metadata, category);
            writeTrackList(outDir, ret);
            return ret == null ? null : Arrays.asList(ret);
        }
        else if (bamType.isType(input))
        {
            List<JSONObject> ret = processBam(data, outDir, featureName, featureLabel, metadata, category);
            writeTrackList(outDir, ret);

            return ret != null && !ret.isEmpty() ? ret :  null;
        }
        else if (vcfType.isType(input))
        {
            JSONObject ret = processVCF(data, outDir, featureName, featureLabel, metadata, category, refGenomeData);
            writeTrackList(outDir, ret);

            return ret == null ? null : Arrays.asList(ret);
        }
        else
        {
            getLogger().warn("Unknown extension, skipping: " + ext);
            return null;
        }
    }

    private void writeTrackList(File outDir, JSONObject trackJson) throws IOException
    {
        if (trackJson == null)
        {
            return;
        }

        writeTrackList(outDir, Arrays.asList(trackJson));
    }

    private void writeTrackList(File outDir, List<JSONObject> tracks) throws IOException
    {
        if (tracks == null || tracks.isEmpty())
        {
            return;
        }

        JSONObject ret = new JSONObject();
        ret.put("formatVersion", 1);
        ret.put("refSeqSelectorMaxSize", 200);
        ret.put("tracks", tracks);

        writeJsonToFile(new File(outDir, "trackList.json"), ret.toString(1));
    }

    private List<JSONObject> processBam(ExpData data, File outDir, String featureName, String featureLabel, Map<String, Object> metadata, String category) throws IOException
    {
        getLogger().info("processing BAM file");
        List<JSONObject> ret = new ArrayList<>();

        if (outDir.exists())
        {
            outDir.delete();
        }
        outDir.mkdirs();

        File targetFile = new File(outDir, data.getFile().getName());

        //check for BAI
        File indexFile = new File(data.getFile().getPath() + ".bai");
        if (!indexFile.exists())
        {
            getLogger().info("unable to find index file for BAM, creating");
            try
            {
                SequencePipelineService.get().ensureBamIndex(data.getFile(), getLogger(), false);
            }
            catch (PipelineJobException e)
            {
                throw new IOException(e);
            }
        }

        //make sym link
        createSymlink(targetFile, data.getFile());
        createSymlink(new File(targetFile.getPath() + ".bai"), indexFile);

        //create track JSON
        JSONObject o = new JSONObject();
        o.put("category", "Alignments");
        o.put("storeClass", "JBrowse/Store/SeqFeature/BAM");
        o.put("label", featureName);
        o.put("doIndex", false);
        o.put("type", "JBrowse/View/Track/Alignments2");
        o.put("maxHeight", 1200);   //extend maxHeight
        o.put("key", featureLabel);

        String relPath = FileUtil.relativePath(getBaseDir(data.getContainer()).getPath(), outDir.getPath());
        getLogger().debug("using relative path: " + relPath);
        o.put("urlTemplate", relPath + "/" + targetFile.getName());

        if (metadata != null)
            o.put("metadata", metadata);

        if (category != null)
            o.put("category", category);

        ret.add(o);

        //add coverage track
        JSONObject coverage = new JSONObject();
        coverage.putAll(o);
        coverage.put("label", featureName + "_coverage");
        coverage.put("key", featureLabel + " Coverage");
        coverage.put("type", "JBrowse/View/Track/SNPCoverage");
        ret.add(coverage);

        return ret;
    }

    private JSONObject processVCF(ExpData data, File outDir, String featureName, String featureLabel, Map<String, Object> metadata, String category, @Nullable ExpData refGenomeData) throws IOException
    {
        getLogger().info("processing VCF file: " + data.getFile().getName());
        FileType vcfType = new FileType("vcf", FileType.gzSupportLevel.SUPPORT_GZ);
        FileType bcfType = new FileType("bcf", FileType.gzSupportLevel.SUPPORT_GZ);

        if (outDir.exists())
        {
            outDir.delete();
        }
        outDir.mkdirs();

        File inputFile = data.getFile();

        if (vcfType.isType(inputFile))
        {
            //if file is not bgziped, do so
            if (!"gz".equals(FileUtil.getExtension(inputFile)))
            {
                File compressed = new File(inputFile.getPath() + ".gz");
                if (!compressed.exists())
                {
                getLogger().info("bgzipping VCF file");
                bgzip(inputFile, compressed);
                }
                else
                {
                    getLogger().info("there is already a bgzipped file present, no need to repeat");
                }

                inputFile = compressed;
            }
        }
        else if (bcfType.isType(inputFile))
        {
            getLogger().info("file is already BCF, no action needed");
        }

        //check for index.  should be made on compressed file
        File indexFile = new File(inputFile.getPath() + ".idx");
        if (!indexFile.exists())
        {
            indexFile = new File(inputFile.getPath() + ".tbi");
            if (!indexFile.exists())
            {
                getLogger().info("unable to find index file for VCF, creating");
                indexFile = SequenceAnalysisService.get().ensureVcfIndex(inputFile, getLogger());
            }
        }

        File targetFile = new File(outDir, inputFile.getName());

        //make sym link
        createSymlink(targetFile, inputFile);
        File targetIndex = new File(targetFile.getPath() + ".tbi"); //note: for the time being work with the extension expected by JBrowse, even though GATK/Tabix will produce .idx
        createSymlink(targetIndex, indexFile);

        //create track JSON
        JSONObject o = new JSONObject();
        o.put("category", "Variants");
        o.put("storeClass", "JBrowse/Store/SeqFeature/VCFTabix");
        o.put("label", featureName);
        o.put("doIndex", false);
        //o.put("type", "JBrowse/View/Track/CanvasVariants");
        o.put("type", "AnnotatedVariants/View/Track/VCFVariants");
        o.put("key", featureLabel);
        o.put("hideNotFilterPass", true);
        o.put("chunkSizeLimit", 10000000);

        String relPath = FileUtil.relativePath(getBaseDir(data.getContainer()).getPath(), outDir.getPath());
        getLogger().debug("using relative path: " + relPath);
        if (relPath == null)
        {
            getLogger().debug("source container: " + data.getContainer().getPath());
            getLogger().debug("outDir: " + outDir.getPath());
        }
        o.put("urlTemplate", relPath + "/" + targetFile.getName());

        if (metadata != null)
            o.put("metadata", metadata);

        if (category != null)
            o.put("category", category);

        return o;
    }

    private JSONObject processBigWig(ExpData data, File outDir, String featureName, String featureLabel, Map<String, Object> metadata, String category) throws IOException
    {
        if (outDir.exists())
        {
            outDir.delete();
        }
        outDir.mkdirs();

        File targetFile = new File(outDir, data.getFile().getName());
        createSymlink(targetFile, data.getFile());

        //create track JSON
        JSONObject o = new JSONObject();
        o.put("category", "BigWig");
        o.put("storeClass", "JBrowse/Store/SeqFeature/BigWig");
        o.put("label", featureName);
        o.put("type", "JBrowse/View/Track/Wiggle/XYPlot");
        o.put("key", featureLabel);
        String relPath = FileUtil.relativePath(getBaseDir(data.getContainer()).getPath(), outDir.getPath());
        getLogger().debug("using relative path: " + relPath);
        o.put("urlTemplate", relPath + "/" + targetFile.getName());

        if (metadata != null)
            o.put("metadata", metadata);

        if (category != null)
            o.put("category", category);

        return o;
    }

    private JSONObject processFlatFile(Container c, File inputFile, File outDir, String typeArg, String featureName, String featureLabel, Map<String, Object> metadata, String category, String trackType, Collection<String> nameAttributes) throws IOException
    {
        List<String> args = new ArrayList<>();

        args.add(typeArg);
        args.add(inputFile.getPath());

        //NOTE: this oddity is a quirk of jbrowse.  label is the background name.  'key' is the user-facing label.
        args.add("--trackLabel");
        args.add(featureName);

        args.add("--key");
        args.add(featureLabel);

        if (JBrowseManager.get().compressJSON())
        {
            args.add("--compress");
        }

        if (nameAttributes != null && !nameAttributes.isEmpty())
        {
            args.add("--nameAttributes");
            Set<String> attrs = new HashSet<>();
            attrs.add("name");
            attrs.add("alias");
            attrs.add("id");
            attrs.addAll(nameAttributes);

            args.add(StringUtils.join(attrs, ","));
        }

        //to avoid issues w/ perl and escaping characters, just set the working directory to the output folder
        //args.add("--out");
        //args.add(outDir.getPath());

        runScript("flatfile-to-json.pl", args, outDir);

        File trackList = new File(outDir, "data/trackList.json");
        if (trackList.exists())
        {
            JSONObject obj = readFileToJson(trackList);
            String urlTemplate = obj.getJSONArray("tracks").getJSONObject(0).getString("urlTemplate");
            String relPath = FileUtil.relativePath(getBaseDir(c).getPath(), new File(outDir, "data").getPath());
            getLogger().debug("using relative path: " + relPath);
            getLogger().debug("original urlTemplate: " + urlTemplate);
            urlTemplate = relPath + "/" + urlTemplate;
            getLogger().debug("new urlTemplate: " + urlTemplate);
            obj.getJSONArray("tracks").getJSONObject(0).put("urlTemplate", urlTemplate);

            JSONObject metadataObj = obj.getJSONArray("tracks").getJSONObject(0).containsKey("metadata") ? obj.getJSONArray("tracks").getJSONObject(0).getJSONObject("metadata") : new JSONObject();
            if (metadata != null)
                metadataObj.putAll(metadata);

            obj.getJSONArray("tracks").getJSONObject(0).put("metadata", metadataObj);

            if (category != null)
                obj.getJSONArray("tracks").getJSONObject(0).put("category", category);

            if (trackType != null)
            {
                obj.getJSONArray("tracks").getJSONObject(0).put("trackType", trackType);
                obj.getJSONArray("tracks").getJSONObject(0).put("type", trackType);
            }

            writeJsonToFile(trackList, obj.toString(1));

            return obj.getJSONArray("tracks").getJSONObject(0);
        }
        else
        {
            getLogger().info("track list file does not exist, expected: " + trackList.getPath());
        }

        return null;
    }

    private String readFile(File file) throws IOException
    {
        try (BufferedReader reader = Readers.getReader(file))
        {
            String line;
            StringBuilder stringBuilder = new StringBuilder();
            String ls = System.getProperty("line.separator");

            while ((line = reader.readLine()) != null)
            {
                stringBuilder.append(line);
                stringBuilder.append(ls);
            }

            return stringBuilder.toString();
        }
    }

    private JSONObject readFileToJson(File file) throws IOException
    {
        String contents = readFile(file);

        return new JSONObject(contents);
    }

    private JSONArray readFileToJsonArray(File file) throws IOException
    {
        String contents = readFile(file);

        return new JSONArray(contents);
    }

    private void writeJsonToFile(File output, String json) throws IOException
    {
        getLogger().debug("writing json to file: " + output.getPath());
        try (PrintWriter writer = PrintWriters.getPrintWriter(output))
        {
            writer.write(json.equals("{}") ? "" : json);
        }
    }

    private File bgzip(File input, File output)
    {
        try (FileInputStream i = new FileInputStream(input); BlockCompressedOutputStream o = new BlockCompressedOutputStream(new FileOutputStream(output), output))
        {
            FileUtil.copyData(i, o);

            return output;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    // NOTE: there is a bug in FileUtils causing it to delete the contents of directories that are symlinks, instead of just deleting the symlink on windows machines
    private void safeDeleteDiretory(File directory) throws IOException
    {
        getLogger().debug("deleting directory: "+ directory.getPath());
        if (SystemUtils.IS_OS_WINDOWS)
        {
            FileUtil.deleteDir(directory);
        }
        //this seems to be much, much faster and aware of symlinks
        else if (SystemUtils.IS_OS_LINUX)
        {
            try
            {
                new SimpleScriptWrapper(getLogger()).execute(Arrays.asList("rm", "-Rf", directory.getPath()));
            }
            catch (PipelineJobException e)
            {
                throw new IOException(e);
            }
        }
        else
        {
            FileUtils.deleteDirectory(directory);
        }
    }
}
