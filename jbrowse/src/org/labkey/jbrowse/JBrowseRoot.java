package org.labkey.jbrowse;

import htsjdk.samtools.BAMIndexer;
import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.SequenceUtil;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.variant.vcf.VCF3Codec;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.ReferenceLibraryHelper;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.jbrowse.model.JsonFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

    public File getDataDir()
    {
        return new File(_root, "data");
    }

    private boolean runScript(String scriptName, List<String> args) throws IOException
    {
        File scriptFile = new File(JBrowseManager.get().getJBrowseBinDir(), scriptName);
        if (!scriptFile.exists())
        {
            getLogger().error("Unable to find jbrowse script: " + scriptFile.getPath());
            return false;
        }

        args.add(0, "perl");
        args.add(1, scriptFile.getPath());

        getLogger().info("preparing jbrowse resource:");
        getLogger().info(StringUtils.join(args, " "));

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
                    getLogger().debug(line);
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

    public JsonFile prepareFile(Container c, User u, Integer dataId, boolean forceRecreateJson) throws IOException
    {
        //validate
        ExpData d = ExperimentService.get().getExpData(dataId);
        if (d == null || d.getFile() == null || !d.getFile().exists())
        {
            throw new IllegalArgumentException("Unable to find file for data: " + dataId);
        }

        //find existing resource
        TableInfo jsonFiles = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_JSONFILES);
        TableSelector ts1 = new TableSelector(jsonFiles, new SimpleFilter(FieldKey.fromString("dataid"), dataId), null);
        JsonFile jsonFile = null;
        if (ts1.exists())
        {
            jsonFile = ts1.getObject(JsonFile.class);
            if (!forceRecreateJson)
            {
                return jsonFile;
            }
        }

        //else create
        if (jsonFile == null)
        {
            Map<String, Object> jsonRecord = new CaseInsensitiveHashMap();
            jsonRecord.put("dataid", dataId);
            jsonRecord.put("container", c.getId());
            jsonRecord.put("created", new Date());
            jsonRecord.put("createdby", u.getUserId());
            jsonRecord.put("modified", new Date());
            jsonRecord.put("modifiedby", u.getUserId());
            jsonRecord.put("objectid", new GUID().toString().toUpperCase());
            Table.insert(u, jsonFiles, jsonRecord);

            jsonFile = ts1.getObject(JsonFile.class);
        }

        return jsonFile;
    }

    public JsonFile prepareRefSeq(Container c, User u, Integer ntId, boolean forceRecreateJson) throws IOException
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
        JsonFile jsonFile = null;
        if (ts1.exists())
        {
            jsonFile = ts1.getObject(JsonFile.class);
            if (!forceRecreateJson)
            {
                return jsonFile;
            }
        }

        File outDir = new File(getReferenceDir(), ntId.toString());
        if (outDir.exists())
        {
            FileUtils.deleteDirectory(outDir);
        }
        outDir.mkdirs();

        //else create
        if (jsonFile == null)
        {
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

            jsonFile = ts1.getObject(JsonFile.class);
        }

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

        return jsonFile;
    }

    public void prepareDatabase(Container c, User u, String databaseId) throws IOException
    {
        File outDir = new File(getDatabaseDir(), databaseId.toString());

        //Note: delete entire directory to ensure we recreate symlinks, etc.
        if (outDir.exists())
        {
            getLogger().info("deleting existing directory");
            FileUtil.deleteDir(outDir);
        }

        outDir.mkdirs();

        File seqDir = new File(outDir, "seq");
        seqDir.mkdirs();

        File trackDir = new File(outDir, "tracks");
        trackDir.mkdirs();

        //TODO: handle libraries
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

        //also add library members:
        Integer libraryId = new TableSelector(JBrowseSchema.getInstance().getSchema().getTable(JBrowseSchema.TABLE_DATABASES), PageFlowUtil.set("libraryId"), new SimpleFilter(FieldKey.fromString("objectid"), databaseId), null).getObject(Integer.class);
        if (libraryId != null)
        {
            List<Integer> refNts = new TableSelector(DbSchema.get("sequenceanalysis").getTable("reference_library_members"), PageFlowUtil.set("ref_nt_id"), new SimpleFilter(FieldKey.fromString("library_id"), libraryId), null).getArrayList(Integer.class);
            for (Integer refNtId : refNts)
            {
                JsonFile f = prepareRefSeq(c, u, refNtId, false);
                if (f != null)
                {
                    jsonGuids.add(f.getObjectId());
                }
            }

            List<Integer> trackIds = new TableSelector(DbSchema.get("sequenceanalysis").getTable("reference_library_tracks"), PageFlowUtil.set("rowid"), new SimpleFilter(FieldKey.fromString("library_id"), libraryId), null).getArrayList(Integer.class);
            for (Integer trackId : trackIds)
            {
                JsonFile f = prepareFeatureTrack(c, u, trackId, false);
                if (f != null)
                {
                    jsonGuids.add(f.getObjectId());
                }
            }
        }

        JSONArray refSeq = new JSONArray();
        JSONObject trackList = new JSONObject();
        trackList.put("formatVersion", 1);

        JSONObject tracks = new JSONObject();

        TableInfo tableJsonFiles = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_JSONFILES);
        Sort sort = new Sort("sequenceid/name,trackid/name");
        TableSelector ts2 = new TableSelector(tableJsonFiles, new SimpleFilter(FieldKey.fromString("objectid"), jsonGuids, CompareType.IN), sort);
        List<JsonFile> jsonData = ts2.getArrayList(JsonFile.class);
        int compressedRefs = 0;
        int totalRefs = 0;

        for (JsonFile f : jsonData)
        {
            getLogger().info("processing JSON file: " + f.getObjectId());
            if (f.getSequenceId() != null)
            {
                getLogger().info("adding ref seq: " + f.getSequenceId());
                if (f.getRefSeqsFile() == null)
                {
                    prepareRefSeq(c, u, f.getSequenceId(), true);
                }

                totalRefs++;
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
                    prepareFeatureTrack(c, u, f.getTrackId(), true);
                    if (f.getTrackListFile() == null)
                    {
                        getLogger().error("this track lacks a trackList.conf file.  this probably indicates a problem when this resource was originally processed.  you should try to re-process it.");
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
                        existingTracks.put(arr.get(i));
                    }

                    trackList.put("tracks", existingTracks);
                }

                //even through we're loading the raw data based on urlTemplate, make a symlink from this location into our DB so help generate-names.pl work properly
                Path sourceFilePath = new File(trackDir, f.getTrackName()).toPath();
                File targetFile = new File(outDir, "tracks/" + f.getTrackName());
                Path symLinkPath = targetFile.toPath();

                getLogger().info("creating sym link to track: " + f.getTrackName());
                Files.createSymbolicLink(symLinkPath, sourceFilePath);
            }
            else if (f.getDataId() != null)
            {
                getLogger().info("processing ad hoc file: " + f.getDataId());
                ExpData d = ExperimentService.get().getExpData(f.getDataId());
                if (d == null || !d.getFile().exists())
                {
                    getLogger().error("unable to find file for Data Id: " + f.getDataId());
                    continue;
                }

                File dataDir = new File(getDataDir(), ((Integer)d.getRowId()).toString());
                JSONObject o = processFile(d, dataDir, d.getName(), d.getName(), null, "Category");
                if (o != null)
                {
                    JSONArray existingTracks = trackList.containsKey("tracks") ? trackList.getJSONArray("tracks") : new JSONArray();
                    existingTracks.put(o);
                    trackList.put("tracks", existingTracks);
                }
            }
            else
            {
                getLogger().error("json file lacks refSeq and trackList, cannot be included.  this might indicate it is an unsupported file type, or that the file should be re-processed");
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
        o.put("showTranslation", false);

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

        List<String> args = new ArrayList<>(Arrays.asList("--out", outDir.getPath()));
        if (JBrowseManager.get().compressJSON())
        {
            args.add("--compress");
        }

        runScript("generate-names.pl", args);
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
            else
            {
                getLogger().info("will not process file: " + f.getName());
            }
        }
    }

    public JsonFile prepareFeatureTrack(Container c, User u, Integer trackId, boolean forceRecreateJson) throws IOException
    {
        //validate track exists
        TableInfo ti = QueryService.get().getUserSchema(u, c, JBrowseManager.SEQUENCE_ANALYSIS).getTable("reference_library_tracks");
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

        //find existing resource
        TableInfo jsonFiles = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_JSONFILES);
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

        File outDir = new File(getTracksDir(), trackId.toString());
        if (outDir.exists())
        {
            FileUtils.deleteDirectory(outDir);
        }
        outDir.mkdirs();

        //else create
        if (jsonFile == null)
        {
            Map<String, Object> jsonRecord = new CaseInsensitiveHashMap();
            jsonRecord.put("trackid", trackId);
            jsonRecord.put("relPath", FileUtil.relativePath(_root.getPath(), outDir.getPath()));
            jsonRecord.put("container", c.getId());
            jsonRecord.put("created", new Date());
            jsonRecord.put("createdby", u.getUserId());
            jsonRecord.put("modified", new Date());
            jsonRecord.put("modifiedby", u.getUserId());
            String guid = new GUID().toString().toUpperCase();
            jsonRecord.put("objectid", guid);

            TableInfo jsonTable = DbSchema.get(JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_JSONFILES);
            Table.insert(u, jsonTable, jsonRecord);

            jsonFile = ts1.getObject(JsonFile.class);
        }

        processFile(data, outDir, (trackRowMap.get("rowid") + " " + trackRowMap.get("name")), (String)trackRowMap.get("name"), (String)trackRowMap.get("description"), (String)trackRowMap.get("category"));

        return jsonFile;
    }

    private JSONObject processFile(ExpData data, File outDir, String featureName, String featureLabel, String description, String category) throws IOException
    {
        FileType bamType = new FileType("bam", false);
        FileType vcfType = new FileType("vcf", true);
        File input = data.getFile();

        String ext = FileUtil.getExtension(data.getFile());
        if ("gff3".equalsIgnoreCase(ext) || "gff".equalsIgnoreCase(ext) || "gtf".equalsIgnoreCase(ext))
        {
            return processFlatFile(data, outDir, "--gff", featureName, featureLabel, description, category);
        }
        else if ("bed".equalsIgnoreCase(ext))
        {
            return processFlatFile(data, outDir, "--bed", featureName, featureLabel, description, category);
        }
        else if ("gbk".equalsIgnoreCase(ext))
        {
            return processFlatFile(data, outDir, "--gbk", featureName, featureLabel, description, category);
        }
        else if ("bigwig".equalsIgnoreCase(ext) || "bw".equalsIgnoreCase(ext))
        {
            return processBigWig(data, outDir, featureName, featureLabel, description, category);
        }
        else if (bamType.isType(input))
        {
            return processBam(data, outDir, featureName, featureLabel, description, category);
        }
        else if (vcfType.isType(input))
        {
            return processVCF(data, outDir, featureName, featureLabel, description, category);
        }
        else
        {
            getLogger().error("Unknown extension, skipping: " + ext);
            return null;
        }
    }

    private JSONObject processBam(ExpData data, File outDir, String featureName, String featureLabel, String description, String category) throws IOException
    {
        getLogger().info("processing BAM file");
        String guid = new GUID().toString().toUpperCase();
        File targetFile = new File(outDir, guid + ".bam");

        //check for BAI
        File indexFile = new File(data.getFile().getPath() + ".bai");
        if (!indexFile.exists())
        {
            getLogger().info("unable to find index file for BAM, creating");
            try (SAMFileReader reader = new SAMFileReader(data.getFile()))
            {
                reader.setValidationStringency(ValidationStringency.SILENT);

                BAMIndexer.createIndex(reader, indexFile);
            }
        }

        //make sym link
        getLogger().info("creating sym link to file: " + targetFile.getPath());
        Files.createSymbolicLink(targetFile.toPath(), data.getFile().toPath());
        Files.createSymbolicLink(new File(targetFile.getPath() + ".bai").toPath(), indexFile.toPath());

        //create track JSON
        JSONObject o = new JSONObject();
        o.put("category", "Alignments");
        o.put("storeClass", "JBrowse/Store/SeqFeature/BAM");
        o.put("label", guid);
        o.put("type", "JBrowse/View/Track/Alignments2");
        o.put("key", featureLabel);

        //TODO: right path?
        o.put("urlTemplate", "data/" + targetFile.getName());
        if (description != null)
            o.put("description", description);
        if (category != null)
            o.put("category", category);

        return o;
    }

    private JSONObject processVCF(ExpData data, File outDir, String featureName, String featureLabel, String description, String category) throws IOException
    {
        getLogger().info("processing VCF file: " + data.getFile().getName());

        String guid = new GUID().toString().toUpperCase();

        File inputFile = data.getFile();
        //if file is not bgziped, do so
        if (!"gz".equals(FileUtil.getExtension(inputFile)))
        {
            getLogger().info("bgzipping VCF file");
            File compressed = new File(inputFile.getPath() + ".gz");
            bgzip(inputFile, compressed);

            inputFile = compressed;
        }

        //check for index
        File indexFile = new File(inputFile.getPath() + ".tbi");
        if (!indexFile.exists())
        {
            getLogger().info("unable to find index file for VCF, creating");
            IndexFactory.createTabixIndex(inputFile, new VCF3Codec(), TabixFormat.VCF, null);
        }

        File targetFile = new File(outDir, guid + "." + FileUtil.getExtension(inputFile));

        //make sym link
        getLogger().info("creating sym link to file: " + targetFile.getPath());
        Files.createSymbolicLink(targetFile.toPath(), inputFile.toPath());
        File targetIndex = new File(targetFile.getPath() + ".bai");
        Files.createSymbolicLink(targetIndex.toPath(), indexFile.toPath());

        //create track JSON
        JSONObject o = new JSONObject();
        o.put("category", "Variants");
        o.put("storeClass", "JBrowse/Store/SeqFeature/VCFTabix");
        o.put("label", guid);
        o.put("type", "JBrowse/View/Track/HTMLVariants");
        o.put("key", featureLabel);
        o.put("urlTemplate", "data/" + targetFile.getName());
        o.put("tbiUrlTemplate", "data/" + targetIndex.getName());
        if (description != null)
            o.put("description", description);
        if (category != null)
            o.put("category", category);

        return o;
    }

    private JSONObject processBigWig(ExpData data, File outDir, String featureName, String featureLabel, String description, String category) throws IOException
    {
        String guid = new GUID().toString().toUpperCase();
        File targetFile = new File(outDir, guid + "." + FileUtil.getExtension(data.getFile()));
        Files.createSymbolicLink(new File(targetFile.getPath() + ".bai").toPath(), data.getFile().toPath());

        //create track JSON
        JSONObject o = new JSONObject();
        o.put("category", "BigWig");
        o.put("storeClass", "JBrowse/Store/SeqFeature/BigWig");
        o.put("label", guid);
        o.put("type", "JBrowse/View/Track/Wiggle/XYPlot");
        o.put("key", featureLabel);
        o.put("urlTemplate", "data/" + targetFile.getName());
        if (description != null)
            o.put("description", description);
        if (category != null)
            o.put("category", category);

        return o;
    }

    private JSONObject processFlatFile(ExpData data, File outDir, String typeArg, String featureName, String featureLabel, String description, String category) throws IOException
    {
        List<String> args = new ArrayList<>();

        args.add(typeArg);
        args.add(data.getFile().getPath());

        args.add("--trackLabel");
        args.add(featureLabel);

        args.add("--key");
        args.add(featureName);

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
            String relPath = FileUtil.relativePath(_root.getPath(), outDir.getPath());
            urlTemplate = "/" + JBrowseManager.get().getJBrowseDbPrefix() + relPath + "/" + urlTemplate;
            obj.getJSONArray("tracks").getJSONObject(0).put("urlTemplate", urlTemplate);

            if (description != null)
                obj.getJSONArray("tracks").getJSONObject(0).put("description", description);
            if (category != null)
                obj.getJSONArray("tracks").getJSONObject(0).put("category", category);

            writeJsonToFile(trackList, obj.toString(1));

            return obj.getJSONArray("tracks").getJSONObject(0);
        }

        return null;
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

    private void bgzip(File input, File output)
    {
        try (FileInputStream i = new FileInputStream(input); BlockCompressedOutputStream o = new BlockCompressedOutputStream(new FileOutputStream(output), output))
        {
            FileUtil.copyData(i, o);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
