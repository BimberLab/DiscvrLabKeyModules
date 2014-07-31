package org.labkey.jbrowse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.jbrowse.model.JsonFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/14/2014
 * Time: 4:09 PM
 */
public class JBrowseRoot
{
    private static final Logger _log = Logger.getLogger(JBrowseRoot.class);
    private Logger _customLogger = null;
    private File _root;

    private JBrowseRoot(File root)
    {
        _root = root;
    }

    public static JBrowseRoot getRoot() throws IllegalArgumentException
    {
        File root = JBrowseManager.get().getJBrowseRoot();
        if (root == null || !root.exists() || !root.canRead() || !root.canWrite())
        {
            throw new IllegalArgumentException("JBrowse root is not valid");
        }

        return new JBrowseRoot(root);
    }

    private Logger getLogger()
    {
        return _customLogger == null ? _log : _customLogger;
    }

    public void setLogger(Logger log)
    {
        _customLogger = log;
    }

    public File getRootDir()
    {
        return _root;
    }

    public File getReferenceDir()
    {
        return new File(_root, "references");
    }

    public File getTracksDir()
    {
        return new File(_root, "tracks");
    }

    public File getDatabaseDir()
    {
        return new File(_root, "databases");
    }

    public void ensureFolders()
    {
        if (!getReferenceDir().exists())
        {
            getReferenceDir().mkdirs();
        }

        if (!getTracksDir().exists())
        {
            getTracksDir().mkdirs();
        }


        if (!getDatabaseDir().exists())
        {
            getDatabaseDir().mkdirs();
        }
    }

    private boolean runScript(String scriptName, List<String> args) throws IOException
    {
        File scriptFile = new File(JBrowseManager.get().getJBrowseBinDir(), scriptName);
        if (!scriptFile.exists())
        {
            getLogger().error("Unable to find jbrowse script: " + scriptFile.getPath());
            return false;
        }

        args.add(0, scriptFile.getPath());

        getLogger().info("preparing jbrowse resource:");
        getLogger().info(StringUtils.join(args, " "));

        StringBuffer output = new StringBuffer();

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);

        Process p = null;
        try
        {
            p = pb.start();
            try (BufferedReader procReader = new BufferedReader(new InputStreamReader(p.getInputStream())))
            {
                String line;
                while ((line = procReader.readLine()) != null)
                {
                    output.append(line);
                    output.append(System.getProperty("line.separator"));
                }
            }

            p.waitFor();
        }
        catch (IOException | InterruptedException e)
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

    public JsonFile prepareRefSeq(Container c, User u, Integer ntId) throws IOException
    {
        //validate
        TableInfo ti = QueryService.get().getUserSchema(u, c, JBrowseManager.SEQUENCE_ANALYSIS).getTable("ref_nt_sequences");
        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowid"), ntId), null);
        RefNtSequenceModel refSeqMap = ts.getObject(RefNtSequenceModel.class);

        if (refSeqMap == null)
        {
            throw new IllegalArgumentException("Unable to find sequence: " + ntId);
        }

        //find existing resource
        TableInfo jsonFiles = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_JSONFILES);
        TableSelector ts1 = new TableSelector(jsonFiles, new SimpleFilter(FieldKey.fromString("sequenceid"), ntId), null);
        if (ts1.exists())
        {
            return ts1.getObject(JsonFile.class);
        }

        File outDir = new File(getReferenceDir(), ntId.toString());

        //else create
        Map<String, Object> jsonRecord = new CaseInsensitiveHashMap();
        jsonRecord.put("sequenceid", ntId);
        jsonRecord.put("relPath", FileUtil.relativePath(_root.getPath(), outDir.getPath()));
        jsonRecord.put("container", c.getId());
        jsonRecord.put("created", new Date());
        jsonRecord.put("createdby", u.getUserId());
        jsonRecord.put("modified", new Date());
        jsonRecord.put("modifiedby", u.getUserId());
        jsonRecord.put("objectid", new GUID().toString().toUpperCase());
        Table.insert(u, jsonFiles, jsonRecord);

        if (outDir.exists())
        {
            outDir.delete();
        }

        outDir.mkdirs();

        AssayFileWriter afw = new AssayFileWriter();
        File fasta = afw.findUniqueFileName(refSeqMap.getName() + ".fasta", outDir);
        fasta.createNewFile();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fasta)))
        {
            writer.write(">" + refSeqMap.getName() + "\n");

            String seq = refSeqMap.getSequence();
            int len = seq == null ? 0 : seq.length();
            int partitionSize = 60;
            for (int i=0; i<len; i+=partitionSize)
            {
                writer.write(seq.substring(i, Math.min(len, i + partitionSize)) + "\n");
            }
        }

        List<String> args = new ArrayList<>();

        args.add("--fasta");
        args.add(fasta.getPath());

        args.add("--trackLabel");
        args.add(refSeqMap.getRowid() + "_" + refSeqMap.getName());

        args.add("--key");
        args.add(refSeqMap.getName());

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
            urlTemplate = "/" + JBrowseManager.get().getJBrowseDbPrefix() + "references/" + ntId.toString() + "/" + urlTemplate;
            obj.getJSONArray("tracks").getJSONObject(0).put("urlTemplate", urlTemplate);

            writeJsonToFile(trackList, obj.toString(1));
        }

        return ts1.getObject(JsonFile.class);
    }

    public void prepareDatabase(Container c, User u, String databaseId) throws IOException
    {
        File outDir = new File(getDatabaseDir(), databaseId.toString());

        //Note: delete entire directory to ensure we recreate symlinks, etc.
        if (outDir.exists())
        {
            FileUtils.deleteDirectory(outDir);
        }

        outDir.mkdirs();

        File seqDir = new File(outDir, "seq");
        seqDir.mkdirs();

        TableInfo tableMembers = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_DATABASE_MEMBERS);
        TableSelector ts = new TableSelector(tableMembers, new SimpleFilter(FieldKey.fromString("database"), databaseId), null);
        final List<String> jsonGuids = new ArrayList<>();
        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                String jsonGuid = rs.getString("jsonfile");
                if (jsonGuid != null)
                {
                    jsonGuids.add(jsonGuid);
                }
            }
        });

        JSONArray refSeq = new JSONArray();
        JSONObject trackList = new JSONObject();
        trackList.put("formatVersion", 1);

        JSONObject tracks = new JSONObject();

        TableInfo tableJsonFiles = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_JSONFILES);
        TableSelector ts2 = new TableSelector(tableJsonFiles, new SimpleFilter(FieldKey.fromString("objectid"), jsonGuids, CompareType.IN), null);
        List<JsonFile> jsonData = ts2.getArrayList(JsonFile.class);
        int compressedRefs = 0;
        int totalRefs = 0;

        for (JsonFile f : jsonData)
        {
            getLogger().info("processing JSON file: " + f.getObjectId());
            if (f.getRefSeqsFile() != null)
            {
                getLogger().info("adding ref seq: " + f.getSequenceId());
                totalRefs++;
                JSONArray arr = readFileToJsonArray(f.getRefSeqsFile());
                for (int i = 0; i < arr.length(); i++) {
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
            else if (f.getTrackListFile() != null)
            {
                getLogger().info("adding track: " + f.getTrackId());
                JSONObject json = readFileToJson(f.getTrackListFile());
                if (json.containsKey("tracks"))
                {
                    JSONArray existingTracks = trackList.containsKey("tracks") ? trackList.getJSONArray("tracks") : new JSONArray();
                    JSONArray arr = json.getJSONArray("tracks");
                    for (int i = 0; i < arr.length(); i++)
                    {
                        existingTracks.put(arr.get(i));
                    }

                    trackList.put("tracks", existingTracks);
                }
            }
            else
            {
                getLogger().error("json file lacks refSeq and trackList, cannot be included");
            }
        }

        if (totalRefs != compressedRefs)
        {
            getLogger().error("Some references are compressed and some are not.  Total ref: " + totalRefs + ", compressed: " + compressedRefs + ".  This will cause rendering problems.");
        }

        //add single track for reference sequence
        //combine ref seqs into a single track
        JSONArray existingTracks = trackList.containsKey("tracks") ? trackList.getJSONArray("tracks") : new JSONArray();
        JSONObject o = new JSONObject();
        o.put("category", "Reference sequences");
        o.put("storeClass", "JBrowse/Store/Sequence/StaticChunked");
        o.put("chunkSize", 20000);
        o.put("label", "Reference sequences");
        o.put("key", "Reference sequences");
        o.put("type", "SequenceTrack");

        //NOTE: this isnt perfect.  these tracks might have been created in the past with a different setting, and in fact some might be compressed and some not.
        if (compressedRefs > 0 && compressedRefs == totalRefs)
        {
            o.put("compress", 1);
        }
        o.put("urlTemplate", "/" + JBrowseManager.get().getJBrowseDbPrefix() + "databases/" + databaseId + "/seq/{refseq_dirpath}/{refseq}-");
        existingTracks.put(o);
        trackList.put("tracks", existingTracks);

        writeJsonToFile(new File(seqDir, "refSeqs.json"), refSeq.toString(1));
        writeJsonToFile(new File(outDir, "trackList.json"), trackList.toString(1));
        writeJsonToFile(new File(outDir, "tracks.json"), tracks.toString(1));
        writeJsonToFile(new File(outDir, "tracks.conf"), "");

        File namesDir = new File(outDir, "names");
        if (!namesDir.exists())
            namesDir.mkdirs();

        //TODO
        writeJsonToFile(new File(outDir, "names/root.json"), "");

        runScript("generate-names.pl", new ArrayList<>(Arrays.asList("--out", namesDir.getPath())));
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
                processDir(f, targetDir, f.getName(), 0);
            }
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
                processDir(f, targetDir, relPath + "/" + f.getName(), depth++);
            }
            else if (depth == 2 || "txtz".equals(ext) || "txt".equals(ext) || "jsonz".equals(ext) || "json".equals(ext) || ".htaccess".equals(f.getName()))
            {
                try
                {
                    Path sourceFilePath = f.toPath();
                    File targetFile = new File(targetDir, relPath + "/" + f.getName());
                    Path symLinkPath = targetFile.toPath();

                    getLogger().info("creating sym link to resource: " + relPath + "/" + f.getName());
                    Files.createSymbolicLink(symLinkPath, sourceFilePath);
                }
                catch (UnsupportedOperationException | IOException e)
                {
                    getLogger().error(e);
                }
            }
        }
    }

    public JsonFile prepareTrack(Container c, User u, Integer trackId) throws IOException
    {
        //validate
        TableInfo ti = QueryService.get().getUserSchema(u, c, JBrowseManager.SEQUENCE_ANALYSIS).getTable("reference_library_tracks");
        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowid"), trackId), null);
        Map<String, Object> rowMap = ts.getMap();

        if (rowMap.get("fileid") == null)
        {
            throw new IllegalArgumentException("Track does not have a file: " + trackId);
        }

        ExpData data = ExperimentService.get().getExpData((int)rowMap.get("fileid"));
        if (!data.getFile().exists())
        {
            throw new IllegalArgumentException("File does not exist: " + data.getFile().getPath());
        }

        //find existing resource
        TableInfo jsonFiles = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_JSONFILES);
        TableSelector ts1 = new TableSelector(jsonFiles, new SimpleFilter(FieldKey.fromString("trackid"), trackId), null);
        if (ts1.exists())
        {
            return ts1.getObject(JsonFile.class);
        }

        File outDir = new File(getTracksDir(), trackId.toString());
        outDir.mkdirs();

        //else create
        Map<String, Object> jsonRecord = new CaseInsensitiveHashMap();
        jsonRecord.put("trackid", trackId);
        jsonRecord.put("relPath", FileUtil.relativePath(_root.getPath(), outDir.getPath()));
        jsonRecord.put("container", c.getId());
        jsonRecord.put("created", new Date());
        jsonRecord.put("createdby", u.getUserId());
        jsonRecord.put("modified", new Date());
        jsonRecord.put("modifiedby", u.getUserId());
        jsonRecord.put("objectid", new GUID().toString().toUpperCase());

        TableInfo jsonTable = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_JSONFILES);
        Table.insert(u, jsonTable, jsonRecord);

        List<String> args = new ArrayList<>();
        String ext = FileUtil.getExtension(data.getFile());
        if ("gff".equalsIgnoreCase(ext) || "gtf".equalsIgnoreCase(ext))
        {
            args.add("--gff");
        }
        else if ("bed".equalsIgnoreCase(ext))
        {
            args.add("--bed");
        }
        else if ("gbk".equalsIgnoreCase(ext))
        {
            args.add("--gbk");
        }
        else
        {
            getLogger().error("Unknown extension, skipping: " + ext);
            return null;
        }
        args.add(data.getFile().getPath());

        args.add("--trackLabel");
        args.add(rowMap.get("rowid") + " " + rowMap.get("name"));

        args.add("--key");
        args.add((String)rowMap.get("name"));

        //TODO
        //args.add("--trackType");
        //args.add("--className");
        //args.add("--type");

        if (JBrowseManager.get().compressJSON())
        {
            args.add("--compress");
        }

        args.add("--out");
        args.add(outDir.getPath());

        runScript("flatfile-to-json.pl", args);

        File trackList = new File(outDir, "trackList.json");
        if (trackList.exists())
        {
            JSONObject obj = readFileToJson(trackList);
            String urlTemplate = obj.getJSONArray("tracks").getJSONObject(0).getString("urlTemplate");
            urlTemplate = "/" + JBrowseManager.get().getJBrowseDbPrefix() + "tracks/" + trackId.toString() + "/" + urlTemplate;
            obj.getJSONArray("tracks").getJSONObject(0).put("urlTemplate", urlTemplate);

            writeJsonToFile(trackList, obj.toString(1));
        }

        return ts1.getObject(JsonFile.class);
    }

    private String readFile(File file) throws IOException
    {
        try (BufferedReader reader = new BufferedReader( new FileReader(file)))
        {
            String line = null;
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
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output)))
        {
            writer.write(json.equals("{}") ? "" : json);
        }
    }
}
