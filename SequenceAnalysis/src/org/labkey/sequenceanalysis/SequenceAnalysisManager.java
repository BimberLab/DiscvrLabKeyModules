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
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.util.Path;
import org.labkey.api.view.UnauthorizedException;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SequenceAnalysisManager
{
    private static final SequenceAnalysisManager _instance = new SequenceAnalysisManager();

    public static final String SEQUENCE_PIPELINE_LOCATION = "sequenceAnalysis";
    public static final String OUTPUT_XML_EXTENSION = ".seqout.xml.gz";
    public static final String FASTQC_REPORT_EXTENSION = "_fastqc.html";
    public static final String REF_DB_FASTA_NAME = "Ref_DB.fasta";
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
     * @param e The SQLException object
     * @return A string containing all the error messages
     */
    public String getAllErrors(SQLException e)
    {
        StringBuilder sb = new StringBuilder(e.toString());
        while(null != (e = e.getNextException()))
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
        return (int)new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ANALYSES), filter, null).getRowCount();
    }

    // Return number of runs in the specified container
    public int getReadsetCount(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        return (int)new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_READSETS), filter, null).getRowCount();
    }

    public List<String> getSequencePlatforms()
    {
        if(_platforms == null)
        {
            _platforms = new ArrayList<>();
            TableSelector ts = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_SEQUENCE_PLATFORMS));
            ts.forEach(new TableSelector.ForEachBlock<ResultSet>(){
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

                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNPS + " WHERE " + subselect, rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON + " WHERE " + subselect, rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNPS + " WHERE " + subselect, rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS + " WHERE " + subselect, rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENTS + " WHERE " + subselect, rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READS + " WHERE " + subselect, rowId));
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

                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNPS + " WHERE analysis_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON + " WHERE analysis_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNPS + " WHERE analysis_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS + " WHERE analysis_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENTS + " WHERE analysis_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READS + " WHERE analysis_id = ?", rowId));
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
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNPS + " WHERE ref_nt_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON + " WHERE ref_nt_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNPS + " WHERE ref_nt_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS + " WHERE ref_nt_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENTS + " WHERE ref_nt_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_COVERAGE + " WHERE ref_nt_id = ?", rowId));

                //alignment summary
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION + " WHERE ref_nt_id = ?", rowId));

                //then other reference data
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_FEATURES + " WHERE ref_nt_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES + " WHERE ref_nt_id = ?", rowId));

                //then the sequence itself
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
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNPS + " WHERE ref_aa_id = ?", rowId));
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
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNPS + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNPS + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENTS + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READS + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_COVERAGE + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSES + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_INSTRUMENT_RUNS + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_QUALITY_METRICS + " WHERE container = ?", c.getEntityId()));
            new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READSETS + " WHERE container = ?", c.getEntityId()));
            transaction.commit();
        }
    }

    public static File getSamJar()
    {
        File samJar = new File(ModuleLoader.getInstance().getWebappDir(), "WEB-INF/lib");
        samJar = new File(samJar, "sam-1.96.jar");
        if(!samJar.exists())
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
        if(sql == null)
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

    public File findResource(String path) throws FileNotFoundException
    {
        Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
        MergedDirectoryResource resource = (MergedDirectoryResource)module.getModuleResolver().lookup(Path.parse(path));
        File file = null;
        for (Resource r : resource.list())
        {
            if(r instanceof FileResource)
            {
                file = ((FileResource) r).getFile().getParentFile();
                break;
            }
        }

        if(file == null)
            throw new FileNotFoundException("Not found: " + path);

        if(!file.exists())
            throw new FileNotFoundException("Not found: " + file.getPath());

        return file;
    }

    public String getNTRefForAARef(Integer refId)
    {
        SQLFragment sql = new SQLFragment("SELECT name FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES + " a " +
            " LEFT JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES + " n ON (n.rowid = a.ref_nt_id) WHERE a.rowid = ?", refId);

        return new SqlSelector(SequenceAnalysisSchema.getInstance().getSchema(), sql).getObject(String.class);
    }
}
