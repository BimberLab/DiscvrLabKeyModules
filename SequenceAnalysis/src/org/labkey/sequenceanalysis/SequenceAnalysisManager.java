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

import htsjdk.samtools.util.StringUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.StopIteratingException;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.FastaDataLoader;
import org.labkey.api.reader.FastaLoader;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.sequenceanalysis.model.ReferenceLibraryMember;
import org.labkey.sequenceanalysis.pipeline.AlignmentAnalysisJob;
import org.labkey.sequenceanalysis.pipeline.ReadsetImportJob;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryPipelineJob;
import org.labkey.sequenceanalysis.pipeline.SequenceOutputHandlerJob;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_OUTPUTFILES + " WHERE readset = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSES + " WHERE readset = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_QUALITY_METRICS + " WHERE readset = ?", rowId));

                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READ_DATA + " WHERE readset = ?", rowId));

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
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_QUALITY_METRICS + " WHERE analysis_id = ?", rowId));

                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSES + " WHERE rowid = ?", rowId));
            }
            transaction.commit();
        }
    }

    public void deleteOutputFiles(List<Integer> rowIds) throws SQLException
    {
        SequenceAnalysisSchema s = SequenceAnalysisSchema.getInstance();

        try (DbScope.Transaction transaction = s.getSchema().getScope().ensureTransaction())
        {
            List<SequenceOutputFile> files = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES), new SimpleFilter(FieldKey.fromString("rowid"), rowIds, CompareType.IN), null).getArrayList(SequenceOutputFile.class);
            for (SequenceOutputFile so : files)
            {
                ExpData d = so.getExpData();
                if (d != null && d.getFile() != null && d.getFile().exists())
                {
                    d.getFile().delete();
                }
            }

            List<Integer> additionalAnalysisIds = SequenceAnalysisManager.get().getAnalysesAssociatedWithOutputFiles(rowIds);

            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_OUTPUTFILES + " WHERE rowid IN (" + StringUtils.join(rowIds, ",") + ")"));

            if (!additionalAnalysisIds.isEmpty())
                SequenceAnalysisManager.get().deleteAnalysis(additionalAnalysisIds);

            transaction.commit();
        }
    }

    public List<Integer> getAnalysesAssociatedWithOutputFiles(List<Integer> keys)
    {
        return new SqlSelector(SequenceAnalysisSchema.getInstance().getSchema(), new SQLFragment("SELECT distinct a.rowid FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_OUTPUTFILES + " o JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSES + " a ON (o.dataId = a.alignmentFile) WHERE o.rowid IN (" + StringUtils.join(keys, ",") +")")).getArrayList(Integer.class);
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

                //reference libraries
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS + " WHERE ref_nt_id = ?", rowId));

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

        Resource r = ModuleLoader.getInstance().getResource(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME), Path.parse("/external/htsjdk-2.8.1.jar"));
        if (r == null)
        {
            throw new IllegalArgumentException("Unable to find htsjdk JAR file");
        }

        File samJar = ((FileResource)r).getFile();
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

    public String getNTRefForAARef(Integer refId)
    {
        SQLFragment sql = new SQLFragment("SELECT name FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES + " a " +
                " LEFT JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES + " n ON (n.rowid = a.ref_nt_id) WHERE a.rowid = ?", refId);

        return new SqlSelector(SequenceAnalysisSchema.getInstance().getSchema(), sql).getObject(String.class);
    }

    public ReferenceLibraryPipelineJob createReferenceLibrary(List<Integer> sequenceIds, Container c, User u, String name, String description, boolean skipCacheIndexes, @Nullable List<String> unplacedContigPrefixes) throws IOException
    {
        List<ReferenceLibraryMember> libraryMembers = new ArrayList<>();
        for (Integer sequenceId : sequenceIds)
        {
            ReferenceLibraryMember m = new ReferenceLibraryMember();
            if (sequenceId == null)
            {
                throw new IllegalArgumentException("NT ID cannot be null");
            }

            m.setRefNtId(sequenceId);
            libraryMembers.add(m);
        }

        return createReferenceLibrary(c, u, name, description, libraryMembers, skipCacheIndexes, unplacedContigPrefixes);
    }

    public ReferenceLibraryPipelineJob createReferenceLibrary(Container c, User u, String name, String description, List<ReferenceLibraryMember> libraryMembers, boolean skipCacheIndexes, @Nullable List<String> unplacedContigPrefixes) throws IOException
    {
        try
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
            ReferenceLibraryPipelineJob job = new ReferenceLibraryPipelineJob(c, u, root, name, description, libraryMembers, null, skipCacheIndexes, unplacedContigPrefixes);
            PipelineService.get().queueJob(job);

            return job;
        }
        catch (PipelineValidationException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public List<Integer> importRefSequencesFromFasta(Container c, User u, File file, boolean splitWhitespace, Map<String, String> params, Logger log, @Nullable File outDir, @Nullable Integer jobId) throws IOException
    {
        PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
        if (root == null)
        {
            throw new IllegalArgumentException("Pipeline root not defined for container: " + c.getPath());
        }

        TableInfo dnaTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
        List<Integer> sequenceIds = new ArrayList<>();
        int processed = 0;
        try (FastaDataLoader loader = new FastaDataLoader(file, false))
        {
            loader.setCharacterFilter(new FastaLoader.UpperAndLowercaseCharacterFilter());

            try (CloseableIterator<Map<String, Object>> i = loader.iterator())
            {
                while (i.hasNext())
                {
                    processed++;
                    if (log != null && processed % 100 == 0)
                    {
                        log.info("processed " + processed + " sequences");
                    }

                    Map<String, Object> fastaRecord = i.next();
                    CaseInsensitiveHashMap map = new CaseInsensitiveHashMap();
                    if (params != null)
                        map.putAll(params);

                    if (!map.containsKey("name"))
                    {
                        String header = (String)fastaRecord.get("header");
                        if (splitWhitespace && header.contains(" "))
                        {
                            int idx = header.indexOf(" ");
                            map.put("comments", header.substring(idx + 1));
                            map.put("name", header.substring(0, idx));
                        }
                        else
                        {
                            map.put("name", header);
                        }
                    }

                    map.put("container", c.getId());
                    map.put("created", new Date());
                    map.put("createdby", u.getUserId());
                    map.put("modified", new Date());
                    map.put("modifiedby", u.getUserId());

                    if (jobId != null)
                        map.put("jobId", jobId);

                    map = Table.insert(u, dnaTable, map);
                    sequenceIds.add((Integer) map.get("rowid"));

                    RefNtSequenceModel m = new TableSelector(dnaTable, new SimpleFilter(FieldKey.fromString("rowid"), map.get("rowid")), null).getObject(RefNtSequenceModel.class);

                    //to better handle large sequences, write sequence to a gzipped text file
                    m.createFileForSequence(u, (String) fastaRecord.get("sequence"), outDir);
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

        targetDir = new File(targetDir, "chainFiles");
        if (!targetDir.exists())
        {
            targetDir.mkdirs();
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

    public SequenceOutputHandler getFileHandler(String handlerClass)
    {
        if (StringUtils.isEmpty(handlerClass))
        {
            return null;
        }

        for (SequenceOutputHandler handler : SequenceAnalysisServiceImpl.get().getFileHandlers())
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

    private static final Set<String> pipelineDirs = PageFlowUtil.set(ReadsetImportJob.FOLDER_NAME, ReadsetImportJob.FOLDER_NAME + "Pipeline", AlignmentAnalysisJob.FOLDER_NAME, AlignmentAnalysisJob.FOLDER_NAME + "Pipeline", "sequenceOutputs", SequenceOutputHandlerJob.FOLDER_NAME + "Pipeline", "illuminaImport", "analyzeAlignment");
    private static final Set<String> skippedDirs = PageFlowUtil.set(".sequences", ".jbrowse");

    public void getOrphanFilesForContainer(Container c, User u, Set<File> orphanFiles, Set<File> orphanIndexes, Set<PipelineStatusFile> orphanJobs, List<String> messages)
    {
        PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
        if (root == null)
        {
            return;
        }

        messages.add("## processing container: " + c.getPath());

        TableInfo jobsTable = PipelineService.get().getJobsTable(u, c);

        //find known ExpDatas
        Set<Integer> knownExpDatas = new HashSet<>();
        knownExpDatas.addAll(new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READ_DATA), PageFlowUtil.set("fileid1"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null).getArrayList(Integer.class));
        knownExpDatas.addAll(new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READ_DATA), PageFlowUtil.set("fileid2"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null).getArrayList(Integer.class));
        knownExpDatas.addAll(new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_ANALYSES), PageFlowUtil.set("alignmentfile"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null).getArrayList(Integer.class));
        knownExpDatas.addAll(new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES), PageFlowUtil.set("dataId"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null).getArrayList(Integer.class));
        knownExpDatas = Collections.unmodifiableSet(knownExpDatas);
        //messages.add("## total registered sequence ExpData: " + knownExpDatas.size());

        Set<Integer> knownPipelineJobs = new HashSet<>();
        UserSchema us = QueryService.get().getUserSchema(u, c, SequenceAnalysisSchema.SCHEMA_NAME);
        TableInfo rd = us.getTable(SequenceAnalysisSchema.TABLE_READ_DATA);
        knownPipelineJobs.addAll(new TableSelector(rd, new HashSet<ColumnInfo>(QueryService.get().getColumns(rd, PageFlowUtil.set(FieldKey.fromString("runId/jobId"))).values()), new SimpleFilter(FieldKey.fromString("container"), c.getId()).addCondition(FieldKey.fromString("runId/jobId"), null, CompareType.NONBLANK), null).getArrayList(Integer.class));

        TableInfo rs = us.getTable(SequenceAnalysisSchema.TABLE_READSETS);
        knownPipelineJobs.addAll(new TableSelector(rs, new HashSet<ColumnInfo>(QueryService.get().getColumns(rs, PageFlowUtil.set(FieldKey.fromString("runId/jobId"))).values()), new SimpleFilter(FieldKey.fromString("container"), c.getId()).addCondition(FieldKey.fromString("runId/jobId"), null, CompareType.NONBLANK), null).getArrayList(Integer.class));

        TableInfo a = us.getTable(SequenceAnalysisSchema.TABLE_ANALYSES);
        knownPipelineJobs.addAll(new TableSelector(a, new HashSet<ColumnInfo>(QueryService.get().getColumns(a, PageFlowUtil.set(FieldKey.fromString("runId/jobId"))).values()), new SimpleFilter(FieldKey.fromString("container"), c.getId()).addCondition(FieldKey.fromString("runId/jobId"), null, CompareType.NONBLANK), null).getArrayList(Integer.class));

        TableInfo of = us.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);
        knownPipelineJobs.addAll(new TableSelector(of, new HashSet<ColumnInfo>(QueryService.get().getColumns(of, PageFlowUtil.set(FieldKey.fromString("runId/jobId"))).values()), new SimpleFilter(FieldKey.fromString("container"), c.getId()).addCondition(FieldKey.fromString("runId/jobId"), null, CompareType.NONBLANK), null).getArrayList(Integer.class));

        knownPipelineJobs.addAll(new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES), PageFlowUtil.set("jobId"), new SimpleFilter(FieldKey.fromString("container"), c.getId()).addCondition(FieldKey.fromString("jobId"), null, CompareType.NONBLANK), null).getArrayList(Integer.class));
        knownPipelineJobs = Collections.unmodifiableSet(knownPipelineJobs);
        //messages.add("## total expected pipeline job folders: " + knownPipelineJobs.size());

        TableSelector jobTs = new TableSelector(jobsTable, PageFlowUtil.set("FilePath"), new SimpleFilter(FieldKey.fromString("RowId"), knownPipelineJobs, CompareType.IN), null);

        Set<File> knownJobPaths = new HashSet<>();
        for (String filePath : jobTs.getArrayList(String.class))
        {
            File f = new File(filePath).getParentFile();
            if (!f.exists())
            {
                messages.add("## unable to find expected pipeline job folder: " + f.getPath());
            }
            else
            {
                knownJobPaths.add(f);
            }
        }
        //messages.add("## total job paths: " + knownJobPaths.size());

        SimpleFilter dataFilter = new SimpleFilter(FieldKey.fromString("container"), c.getId());
        TableInfo dataTable = ExperimentService.get().getTinfoData();
        TableSelector ts = new TableSelector(dataTable, PageFlowUtil.set("RowId", "DataFileUrl"), dataFilter, null);
        final Map<URI, Set<Integer>> dataMap = new HashMap<>();
        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException, StopIteratingException
            {
                if (rs.getString("DataFileUrl") == null)
                {
                    return;
                }

                try
                {
                    URI uri = new URI(rs.getString("DataFileUrl"));
                    if (!dataMap.containsKey(uri))
                    {
                        dataMap.put(uri, new HashSet<>());
                    }

                    dataMap.get(uri).add(rs.getInt("RowId"));
                }
                catch (URISyntaxException e)
                {
                    _log.error(e.getMessage(), e);
                }
            }
        });
        //messages.add("## total ExpData paths: " + dataMap.size());

        for (String dirName : pipelineDirs)
        {
            File dir = new File(root.getRootPath(), dirName);
            if (dir.exists())
            {
                for (File subdir : dir.listFiles())
                {
                    if (!subdir.isDirectory())
                    {
                        continue;
                    }

                    boolean isOrphanPipelineDir = isOrphanPipelineDir(jobsTable, subdir, c, knownExpDatas, knownJobPaths, orphanJobs, messages);
                    if (!isOrphanPipelineDir)
                    {
                        if (!knownJobPaths.remove(subdir))
                        {
                            messages.add("#pipeline path listed as orphan, but not present in known paths: ");
                            messages.add(subdir.getPath());
                        }

                        getOrphanFilesForDirectory(c, knownExpDatas, dataMap, subdir, orphanFiles, orphanIndexes);
                    }
                }
            }
        }

        // any files remaining in knownJobPaths indicates that we didnt find registered sequence data.  this could be a job
        // that either failed or for whatever reason is no longer important
        if (!knownJobPaths.isEmpty())
        {
            messages.add("## The following directories match existing pipeline jobs, but do not contain registered data:");
            for (File f : knownJobPaths)
            {
                long size = FileUtils.sizeOfDirectory(f);
                //ignore if less than 1mb
                if (size > 1e6)
                {
                    messages.add("## size: " + FileUtils.byteCountToDisplaySize(size));
                    messages.add(f.getPath());
                }
            }
        }

        //TODO: look for .deleted and /archive
        File deletedDir = new File(root.getRootPath().getParentFile(), ".deleted");
        if (deletedDir.exists())
        {
            messages.add("## .deleted dir found: " + deletedDir.getPath());
        }

        File assayData = new File(root.getRootPath(), "assaydata");
        if (assayData.exists())
        {
            File[] bigFiles = assayData.listFiles(new FileFilter()
            {
                @Override
                public boolean accept(File pathname)
                {
                    //50mb
                    return (pathname.length() >= 5e7);
                }
            });

            if (bigFiles != null && bigFiles.length > 0)
            {
                messages.add("## large files in assaydata, might be unnecessary:");
                for (File f : bigFiles)
                {
                    messages.add(f.getPath());
                }
            }

            File archive = new File(assayData, "archive");
            if (archive.exists())
            {
                File[] files = archive.listFiles();
                if (files != null && files.length > 0)
                {
                    messages.add("## the following files are in assaydata/archive, and were probably automatically moved here after delete.  they might be unnecessary:");
                    for (File f : files)
                    {
                        messages.add(f.getPath());
                    }
                }
            }
        }

        for (Container child : ContainerManager.getChildren(c))
        {
            if (child.isWorkbook())
            {
                getOrphanFilesForContainer(child, u, orphanFiles, orphanIndexes, orphanJobs, messages);
            }
        }
    }

    private boolean isOrphanPipelineDir(TableInfo jobsTable, File dir, Container c, Set<Integer> knownExpDataIds, Set<File> knownJobPaths, Set<PipelineStatusFile> orphanJobs, List<String> messages)
    {
        //find statusfile
        List<Integer> jobIds = new TableSelector(jobsTable, PageFlowUtil.set("RowId"), new SimpleFilter(FieldKey.fromString("FilePath"), dir.getPath() + System.getProperty("file.separator"), CompareType.STARTS_WITH), null).getArrayList(Integer.class);
        if (jobIds.isEmpty())
        {
            long size = FileUtils.sizeOfDirectory(dir);
            messages.add("## Unable to find matching job, might be orphan: ");
            messages.add("## size: " + FileUtils.byteCountToDisplaySize(size));
            messages.add(dir.getPath());
            return false;
        }
        else if (jobIds.size() > 1)
        {
            messages.add("## More than one possible job found, this may simply indicate parent/child jobs: " + dir.getPath());
        }

        //this could be a directory from an analysis that doesnt register files, like picard metrics
        if (knownJobPaths.contains(dir))
        {
            return false;
        }

        // NOTE: if this files within a known job path, it still could be an orphan.  first check whether the directory has registered files.
        // If so, remove that path from the set of known job paths
        List<? extends ExpData> dataUnderPath = ExperimentService.get().getExpDatasUnderPath(dir, c);
        Set<Integer> dataIdsUnderPath = new HashSet<>();
        for (ExpData d : dataUnderPath)
        {
            dataIdsUnderPath.add(d.getRowId());
        }

        if (!CollectionUtils.containsAny(dataIdsUnderPath, knownExpDataIds))
        {
            for (int jobId : jobIds)
            {
                PipelineStatusFile sf = PipelineService.get().getStatusFile(jobId);
                orphanJobs.add(sf);
            }

            return true;
        }

        return false;
    }

    private void getOrphanFilesForDirectory(Container c, Set<Integer> knownExpDatas, Map<URI, Set<Integer>> dataMap, File dir, Set<File> orphanSequenceFiles, Set<File> orphanIndexes)
    {
        //skipped for perf reasons.  extremely unlikely
        //if (!dir.exists() || Files.isSymbolicLink(dir.toPath()))
        //{
        //    return;
        //}

        File[] arr = dir.listFiles();
        if (arr == null)
        {
            _log.error("unable to list files: " + dir.getPath());
            return;
        }

        for (File f : arr)
        {
            //skipped for perf reasons.  extremely unlikely
            //if (Files.isSymbolicLink(f.toPath()))
            //{
            //    continue;
            //}

            if (f.isDirectory())
            {
                getOrphanFilesForDirectory(c, knownExpDatas, dataMap, f, orphanSequenceFiles, orphanIndexes);
            }
            else
            {
                //iterate possible issues:

                //orphan index
                if (f.getPath().toLowerCase().endsWith(".bai") || f.getPath().toLowerCase().endsWith(".tbi") || f.getPath().toLowerCase().endsWith(".idx"))
                {
                    if (!new File(FileUtil.getBaseName(f.getPath())).exists())
                    {
                        orphanIndexes.add(f);
                        continue;
                    }
                }

                //sequence files not associated w/ DB records:
                if (SequenceUtil.FILETYPE.fastq.getFileType().isType(f) || SequenceUtil.FILETYPE.bam.getFileType().isType(f))
                {
                    //find all ExpDatas referencing this file
                    Set<Integer> dataIdsForFile = dataMap.get(FileUtil.getAbsoluteCaseSensitiveFile(f).toURI());
                    if (dataIdsForFile == null || !CollectionUtils.containsAny(dataIdsForFile, knownExpDatas))
                    {
                        //a hack, but special-case undetermined/unaligned FASTQ files
                        if (SequenceUtil.FILETYPE.fastq.getFileType().isType(f))
                        {
                            if (f.getPath().contains("/Normalization/") && f.getName().startsWith("Undetermined_"))
                                continue;
                            else if (f.getPath().contains("/Alignment/") && (f.getName().contains("unaligned") || f.getName().contains("unmapped")))
                                continue;
                        }

                        orphanSequenceFiles.add(f);
                    }
                }
            }
        }
    }

    public void apppendSequenceLength(User u, Logger log)
    {
        TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);

        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("seqLength"), null, CompareType.ISBLANK), null);

        log.info(ts.getRowCount() + " total sequences to migrate");
        ts.forEach(new Selector.ForEachBlock<RefNtSequenceModel>()
        {
            @Override
            public void exec(RefNtSequenceModel nt) throws SQLException, StopIteratingException
            {
                try (InputStream is = nt.getSequenceInputStream())
                {
                    if (is == null)
                    {
                        _log.error("unable to read NT sequence: " + nt.getRowid());
                        return;
                    }

                    nt.setSeqLength(StringUtil.bytesToString(IOUtils.toByteArray(is)).length());
                    Table.update(u, ti, nt, nt.getRowid());
                }
                catch (IOException e)
                {
                    _log.error(e.getMessage(), e);

                    stopIterating();
                }
            }
        }, RefNtSequenceModel.class);
    }
}