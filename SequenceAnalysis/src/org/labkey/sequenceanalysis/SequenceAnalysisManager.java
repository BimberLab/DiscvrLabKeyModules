/*
 * Copyright (c) 2009-2012 LabKey Corporation
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.sequenceanalysis;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.FastaDataLoader;
import org.labkey.api.reader.FastaLoader;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.SequenceFileHandler;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.sequenceanalysis.model.ReferenceLibraryMember;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryPipelineJob;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SequenceAnalysisManager
{
    private static final SequenceAnalysisManager _instance = new SequenceAnalysisManager();

    private static final Logger _log = Logger.getLogger(SequenceAnalysisManager.class);

    public static final String OUTPUT_XML_EXTENSION = ".seqout.xml.gz";
    public static final String FASTQC_REPORT_EXTENSION = "_fastqc.html";
    private List<String> _platforms = null;

    private SequenceAnalysisManager()
    {
        // prevent external construction with a private default constructor
    }

    public static SequenceAnalysisManager get()
    {
        return _instance;
    }

    public String getSchemaName()
    {
        return SequenceAnalysisSchema.SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SequenceAnalysisSchema.SCHEMA_NAME);
    }

    public SchemaTableInfo getTable(String tablename)
    {
        return getSchema().getTable(tablename);
    }

    /**
     * Returns a string containing all errors from a SQLException, which may contain many messages
     *
     * @param e The SQLException object
     * @return A string containing all the error messages
     */
    public String getAllErrors(SQLException e)
    {
        StringBuilder sb = new StringBuilder(e.toString());
        while (null != (e = e.getNextException()))
        {
            sb.append("; ");
            sb.append(e.toString());
        }
        return sb.toString();
    }

    // Return number of runs in the specified container
    public int getRunCount(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        return (int) new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ANALYSES), filter, null).getRowCount();
    }

    // Return number of runs in the specified container
    public int getReadsetCount(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        return (int) new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_READSETS), filter, null).getRowCount();
    }

    public List<String> getSequencePlatforms()
    {
        if (_platforms == null)
        {
            _platforms = new ArrayList<>();
            TableSelector ts = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_SEQUENCE_PLATFORMS));
            ts.forEach(new TableSelector.ForEachBlock<ResultSet>()
            {
                public void exec(ResultSet rs) throws SQLException
                {
                    _platforms.add(rs.getString("platform"));
                }
            });
        }
        return _platforms;
    }

    public void deleteReadset(List<Integer> rowIds) throws SQLException
    {
        SequenceAnalysisSchema s = SequenceAnalysisSchema.getInstance();

        try (DbScope.Transaction transaction = s.getSchema().getScope().ensureTransaction())
        {
            for (int rowId : rowIds)
            {
                String subselect = " analysis_id IN (select rowid from " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSES + " WHERE readset = ?)";

                //delete all analyses and associated records
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION + " WHERE " + subselect, rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY + " WHERE " + subselect, rowId));

                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON + " WHERE " + subselect, rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS + " WHERE " + subselect, rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_COVERAGE + " WHERE " + subselect, rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSES + " WHERE readset = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_QUALITY_METRICS + " WHERE readset = ?", rowId));

                //then the readsets themselves
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READSETS + " WHERE rowId = ?", rowId));
            }
            transaction.commit();
        }
    }

    public void deleteAnalysis(List<Integer> rowIds) throws SQLException
    {
        SequenceAnalysisSchema s = SequenceAnalysisSchema.getInstance();

        try (DbScope.Transaction transaction = s.getSchema().getScope().ensureTransaction())
        {
            for (int rowId : rowIds)
            {

                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION + " WHERE analysis_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY + " WHERE analysis_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_OUTPUTFILES + " WHERE analysis_id = ?", rowId));

                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON + " WHERE analysis_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS + " WHERE analysis_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_COVERAGE + " WHERE analysis_id = ?", rowId));

                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSES + " WHERE rowid = ?", rowId));
            }
            transaction.commit();
        }
    }

    public void deleteRefNtSequence(List<Integer> rowIds) throws SQLException
    {
        SequenceAnalysisSchema s = SequenceAnalysisSchema.getInstance();

        try (DbScope.Transaction transaction = s.getSchema().getScope().ensureTransaction())
        {
            for (int rowId : rowIds)
            {
                //first data from analyses
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON + " WHERE ref_nt_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS + " WHERE ref_nt_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_COVERAGE + " WHERE ref_nt_id = ?", rowId));

                //alignment summary
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION + " WHERE ref_nt_id = ?", rowId));

                //then other reference data
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_FEATURES + " WHERE ref_nt_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_FEATURES + " WHERE ref_nt_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_DRUG_RESISTANCE + " WHERE ref_nt_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES + " WHERE ref_nt_id = ?", rowId));

                //delete file on the filesystem, if present
                RefNtSequenceModel ref = RefNtSequenceModel.getForRowId(rowId);
                if (ref.getSequenceFile() != null)
                {
                    ExpData d = ExperimentService.get().getExpData(ref.getSequenceFile());
                    if (d != null && d.getFile().exists())
                    {
                        d.getFile().delete();
                    }
                }

                //finally the sequence itself
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES + " WHERE rowid = ?", rowId));
            }
            transaction.commit();
        }
    }

    public void deleteRefAaSequence(List<Integer> rowIds) throws SQLException
    {
        SequenceAnalysisSchema s = SequenceAnalysisSchema.getInstance();

        try (DbScope.Transaction transaction = s.getSchema().getScope().ensureTransaction())
        {
            for (int rowId : rowIds)
            {
                //first data from analyses
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON + " WHERE ref_aa_id = ?", rowId));

                //then other reference data
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_FEATURES + " WHERE ref_aa_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_DRUG_RESISTANCE + " WHERE ref_aa_id = ?", rowId));

                //then the sequence itself
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES + " WHERE rowid = ?", rowId));
            }
            transaction.commit();
        }
    }

    public void deleteContainer(Container c)
    {
        SequenceAnalysisSchema s = SequenceAnalysisSchema.getInstance();

        try (DbScope.Transaction transaction = s.getSchema().getScope().ensureTransaction())
        {
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION + " WHERE alignment_id IN (select rowid from " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY + " WHERE container = ?)", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_COVERAGE + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSES + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_INSTRUMENT_RUNS + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_QUALITY_METRICS + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READSETS + " WHERE container = ?", c.getEntityId()));
            transaction.commit();
        }
    }

    public static File getHtsJdkJar()
    {
        File webappDir = ModuleLoader.getInstance().getWebappDir();

        //NOTE: webappdir is null on remote servers
        if (webappDir == null)
        {
            webappDir = new File(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class).getExplodedPath(), "../../labkeywebapp");
            if (!webappDir.exists())
            {
                throw new RuntimeException("Unable to find JAR root.");
            }
        }

        File samJar = new File(webappDir, "WEB-INF/lib");
        samJar = new File(samJar, "htsjdk-1.118.jar");
        if (!samJar.exists())
            throw new RuntimeException("Not found: " + samJar.getPath());

        return samJar;
    }

    public static void cascadeDelete(int userId, String containerId, String schemaName, String queryName, String keyField, Object keyValue) throws SQLException
    {
        cascadeDelete(userId, containerId, schemaName, queryName, keyField, keyValue, null);
    }

    public static void cascadeDelete(int userId, String containerId, String schemaName, String queryName, String keyField, Object keyValue, String sql) throws SQLException
    {
        User u = UserManager.getUser(userId);
        if (u == null)
            throw new RuntimeException("User does not exist: " + userId);

        Container c = ContainerManager.getForId(containerId);
        if (c == null)
            throw new RuntimeException("Container does not exist: " + containerId);

        DbSchema schema = QueryService.get().getUserSchema(u, c, schemaName).getDbSchema();
        if (schema == null)
            throw new RuntimeException("Unknown schema: " + schemaName);

        TableInfo table = schema.getTable(queryName);
        if (table == null)
            throw new RuntimeException("Unknown table: " + schemaName + "." + queryName);

        if (!c.hasPermission(u, DeletePermission.class))
            throw new UnauthorizedException("User does not have permission to delete from the table: " + table.getPublicName());

        SimpleFilter filter;
        if (sql == null)
        {
            filter = new SimpleFilter(FieldKey.fromString(keyField), keyValue);
        }
        else
        {
            filter = new SimpleFilter();
            filter.addWhereClause(sql, new Object[]{keyValue}, FieldKey.fromParts(keyField));
        }
        Table.delete(table, filter);
    }

    public static void deleteReferenceLibrary(int userId, String containerId, Integer rowId) throws SQLException, IOException
    {
        Container c = ContainerManager.getForId(containerId);
        if (c == null)
        {
            return;

        }

        cascadeDelete(userId, containerId, "sequenceanalysis", "reference_library_members", "library_id", rowId);
        cascadeDelete(userId, containerId, "sequenceanalysis", "reference_library_tracks", "library_id", rowId);

        //then delete files
        File dir = SequenceAnalysisManager.get().getReferenceLibraryDir(c);
        if (dir != null && dir.exists())
        {
            File libraryDir = new File(dir, rowId.toString());
            if (libraryDir != null && libraryDir.exists())
            {
                _log.info("deleting reference library dir: " + libraryDir.getPath());
                FileUtils.deleteDirectory(libraryDir);
            }
        }
    }

    public File findResource(String path) throws FileNotFoundException
    {
        Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
        MergedDirectoryResource resource = (MergedDirectoryResource) module.getModuleResolver().lookup(Path.parse(path));
        File file = null;
        for (Resource r : resource.list())
        {
            if (r instanceof FileResource)
            {
                file = ((FileResource) r).getFile().getParentFile();
                break;
            }
        }

        if (file == null)
            throw new FileNotFoundException("Not found: " + path);

        if (!file.exists())
            throw new FileNotFoundException("Not found: " + file.getPath());

        return file;
    }

    public String getNTRefForAARef(Integer refId)
    {
        SQLFragment sql = new SQLFragment("SELECT name FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES + " a " +
                " LEFT JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES + " n ON (n.rowid = a.ref_nt_id) WHERE a.rowid = ?", refId);

        return new SqlSelector(SequenceAnalysisSchema.getInstance().getSchema(), sql).getObject(String.class);
    }

    public ReferenceLibraryPipelineJob createReferenceLibrary(List<Integer> sequenceIds, Container c, User u, String name, String description) throws Exception
    {
        List<ReferenceLibraryMember> libraryMembers = new ArrayList<>();
        for (Integer sequenceId : sequenceIds)
        {
            ReferenceLibraryMember m = new ReferenceLibraryMember();
            m.setRef_nt_id(sequenceId);
            libraryMembers.add(m);
        }

        return createReferenceLibrary(c, u, name, description, libraryMembers);
    }

    public ReferenceLibraryPipelineJob createReferenceLibrary(Container c, User u, String name, String description, List<ReferenceLibraryMember> libraryMembers) throws Exception
    {
        try
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
            ReferenceLibraryPipelineJob job = new ReferenceLibraryPipelineJob(c, u, null, root, name, description, libraryMembers);
            PipelineService.get().queueJob(job);

            return job;
        }
        catch (PipelineValidationException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public List<Integer> importRefSequencesFromFasta(Container c, User u, File file, Map<String, String> params) throws Exception
    {
        PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
        if (root == null)
        {
            throw new IllegalArgumentException("Pipeline root not defined for container: " + c.getPath());
        }

        TableInfo dnaTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
        List<Integer> sequenceIds = new ArrayList<>();
        try (FastaDataLoader loader = new FastaDataLoader(file, false))
        {
            loader.setCharacterFilter(new FastaLoader.UpperAndLowercaseCharacterFilter());

            try (CloseableIterator<Map<String, Object>> i = loader.iterator())
            {
                while (i.hasNext())
                {
                    Map<String, Object> fastaRecord = i.next();
                    CaseInsensitiveHashMap map = new CaseInsensitiveHashMap();
                    if (params != null)
                        map.putAll(params);

                    map.put("name", fastaRecord.get("header"));
                    map.put("container", c.getId());
                    map.put("created", new Date());
                    map.put("createdby", u.getUserId());
                    map.put("modified", new Date());
                    map.put("modifiedby", u.getUserId());

                    map = Table.insert(u, dnaTable, map);
                    sequenceIds.add((Integer) map.get("rowid"));

                    RefNtSequenceModel m = new TableSelector(dnaTable, new SimpleFilter(FieldKey.fromString("rowid"), map.get("rowid")), null).getObject(RefNtSequenceModel.class);

                    //to better handle large sequences, write sequence to a gzipped text file
                    m.createFileForSequence(u, (String) fastaRecord.get("sequence"));
                }
            }
        }

        return sequenceIds;
    }

    public void addChainFile(Container c, User u, File file, int genomeId1, int genomeId2, Double version) throws Exception
    {
        TableInfo libraryTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
        TableInfo chainTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_CHAIN_FILES);

        Integer fastaId = new TableSelector(libraryTable, Collections.singleton("fasta_file"), new SimpleFilter(FieldKey.fromString("rowid"), genomeId1), null).getObject(Integer.class);
        if (fastaId == null)
        {
            throw new IllegalArgumentException("Unable to find FASTA for library: " + genomeId1);
        }

        ExpData data = ExperimentService.get().getExpData(fastaId);
        if (data == null)
        {
            throw new IllegalArgumentException("Unable to find FASTA for library: " + genomeId1);
        }

        File targetDir = data.getFile().getParentFile();
        if (!targetDir.exists())
        {
            throw new IllegalArgumentException("Unable to find expected FASTA location: " + targetDir.getPath());
        }

        //create file
        String expectedName = "chain-" + genomeId1 + "to" + genomeId2 + ".chain";
        AssayFileWriter writer = new AssayFileWriter();
        File outputFile = writer.findUniqueFileName(expectedName, targetDir);

        FileUtils.moveFile(file, outputFile);
        ExpData chainFile = ExperimentService.get().createData(c, new DataType("Sequence Track"));
        chainFile.setName(outputFile.getName());
        chainFile.setDataFileURI(outputFile.toURI());
        chainFile.save(u);

        //create row
        CaseInsensitiveHashMap map = new CaseInsensitiveHashMap();
        map.put("genomeId1", genomeId1);
        map.put("genomeId2", genomeId2);
        map.put("version", version);
        map.put("chainFile", chainFile.getRowId());

        map.put("container", c.getId());
        map.put("created", new Date());
        map.put("createdby", u.getUserId());
        map.put("modified", new Date());
        map.put("modifiedby", u.getUserId());
        Table.insert(u, chainTable, map);
    }

    public void addTrackForLibrary(Container c, User u, File file, int libraryId, String trackName, String trackDescription, String type) throws Exception
    {
        TableInfo libraryTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
        TableInfo trackTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_LIBRARY_TRACKS);

        Integer fastaId = new TableSelector(libraryTable, Collections.singleton("fasta_file"), new SimpleFilter(FieldKey.fromString("rowid"), libraryId), null).getObject(Integer.class);
        if (fastaId == null)
        {
            throw new IllegalArgumentException("Unable to find FASTA for library: " + libraryId);
        }

        ExpData data = ExperimentService.get().getExpData(fastaId);
        if (data == null)
        {
            throw new IllegalArgumentException("Unable to find FASTA for library: " + libraryId);
        }

        File targetDir = data.getFile().getParentFile();
        if (!targetDir.exists())
        {
            throw new IllegalArgumentException("Unable to find expected FASTA location: " + targetDir.getPath());
        }

        //create file
        String expectedName = trackName + "." + FileUtil.getExtension(file);
        AssayFileWriter writer = new AssayFileWriter();
        File outputFile = writer.findUniqueFileName(expectedName, targetDir);

        FileUtils.moveFile(file, outputFile);
        ExpData trackData = ExperimentService.get().createData(c, new DataType("Sequence Track"));
        trackData.setName(outputFile.getName());
        trackData.setDataFileURI(outputFile.toURI());
        trackData.save(u);

        //create row
        CaseInsensitiveHashMap map = new CaseInsensitiveHashMap();
        map.put("name", trackName);
        map.put("description", trackDescription);
        map.put("library_id", libraryId);
        map.put("fileid", trackData.getRowId());
        map.put("container", c.getId());
        map.put("created", new Date());
        map.put("createdby", u.getUserId());
        map.put("modified", new Date());
        map.put("modifiedby", u.getUserId());
        Table.insert(u, trackTable, map);
    }

    public SequenceFileHandler getFileHandler(String handlerClass)
    {
        if (StringUtils.isEmpty(handlerClass))
        {
            return null;
        }

        for (SequenceFileHandler handler : SequenceAnalysisServiceImpl.get().getFileHandlers())
        {
            if (handler.getClass().getName().equals(handlerClass))
            {
                return handler;
            }
        }

        return null;
    }

    public File getReferenceLibraryDir(Container c)
    {
        File pipelineDir = PipelineService.get().getPipelineRootSetting(c).getRootPath();
        if (pipelineDir == null)
        {
            return null;
        }

        return new File(pipelineDir, ".referenceLibraries");
    }
}