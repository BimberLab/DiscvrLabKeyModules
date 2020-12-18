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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
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
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.FastaDataLoader;
import org.labkey.api.reader.FastaLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.sequenceanalysis.GenomeTrigger;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.Job;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.sequenceanalysis.model.ReferenceLibraryMember;
import org.labkey.sequenceanalysis.pipeline.ReferenceGenomeImpl;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryPipelineJob;
import org.labkey.sequenceanalysis.pipeline.SequenceAnalysisTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

    private static final Logger _log = LogManager.getLogger(SequenceAnalysisManager.class);

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
        return DbSchema.get(SequenceAnalysisSchema.SCHEMA_NAME, DbSchemaType.Module);
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

    private int cascadeDeleteWithQUS(UserSchema us, String tableName, SimpleFilter filter, String pkName) throws Exception
    {
        //first find list of PKs
        List<Integer> pks = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(tableName), PageFlowUtil.set(pkName), filter, null).getArrayList(Integer.class);
        List<Map<String, Object>> toDelete = new ArrayList<>();
        pks.forEach(x -> {
            Map<String, Object> map = new CaseInsensitiveHashMap<>();
            map.put(pkName, x);
            toDelete.add(map);
        });

        Map<String, Object> scriptContext = new HashMap<>();
        scriptContext.put("deleteFromServer", true);  //a flag to make the trigger script accept this
        List<Map<String, Object>> deleted = us.getTable(tableName, null).getUpdateService().deleteRows(us.getUser(), us.getContainer(), toDelete, null, scriptContext);

        return deleted.size();
    }

    public void deleteReadset(List<Integer> rowIds, User user, Container c) throws Exception
    {
        SequenceAnalysisSchema s = SequenceAnalysisSchema.getInstance();
        UserSchema us = QueryService.get().getUserSchema(user, c, SequenceAnalysisSchema.SCHEMA_NAME);
        TableInfo readsets = us.getTable(SequenceAnalysisSchema.TABLE_READSETS, null);

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
                cascadeDeleteWithQUS(us, SequenceAnalysisSchema.TABLE_OUTPUTFILES, new SimpleFilter(FieldKey.fromString("readset"), rowId), "rowid");
                cascadeDeleteWithQUS(us, SequenceAnalysisSchema.TABLE_ANALYSES, new SimpleFilter(FieldKey.fromString("readset"), rowId), "rowid");

                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_QUALITY_METRICS + " WHERE readset = ?", rowId));

                cascadeDeleteWithQUS(us, SequenceAnalysisSchema.TABLE_READ_DATA, new SimpleFilter(FieldKey.fromString("readset"), rowId), "rowid");

                //then the readsets themselves
                List<Map<String, Object>> keysToDelete = new ArrayList<>();
                keysToDelete.add(new CaseInsensitiveHashMap<>(){{put("rowId", rowId);}});

                Map<String, Object> scriptContext = new HashMap<>();
                scriptContext.put("deleteFromServer", true);  //a flag to make the trigger script accept this
                readsets.getUpdateService().deleteRows(user, c, keysToDelete, null, scriptContext);
            }
            transaction.commit();
        }
        catch (InvalidKeyException | BatchValidationException | QueryUpdateServiceException e)
        {
            throw new SQLException(e);
        }
    }

    public void deleteAnalysis(User user, Container container, Collection<Integer> rowIds) throws Exception
    {
        SequenceAnalysisSchema s = SequenceAnalysisSchema.getInstance();

        UserSchema us = QueryService.get().getUserSchema(user, container, SequenceAnalysisSchema.SCHEMA_NAME);
        if (us == null)
        {
            throw new IllegalArgumentException("Unable to find sequenceanalysis user schema");
        }

        try (DbScope.Transaction transaction = s.getSchema().getScope().ensureTransaction())
        {
            for (int rowId : rowIds)
            {

                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION + " WHERE analysis_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY + " WHERE analysis_id = ?", rowId));
                cascadeDeleteWithQUS(us, SequenceAnalysisSchema.TABLE_OUTPUTFILES, new SimpleFilter(FieldKey.fromString("analysis_id"), rowId), "rowid");

                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON + " WHERE analysis_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS + " WHERE analysis_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_COVERAGE + " WHERE analysis_id = ?", rowId));
                new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_QUALITY_METRICS + " WHERE analysis_id = ?", rowId));

                List<Map<String, Object>> keysToDelete = new ArrayList<>();
                keysToDelete.add(new CaseInsensitiveHashMap<Object>(){{put("rowId", rowId);}});

                Map<String, Object> scriptContext = new HashMap<>();
                scriptContext.put("deleteFromServer", true);  //a flag to make the trigger script accept this
                us.getTable(SequenceAnalysisSchema.TABLE_ANALYSES, null).getUpdateService().deleteRows(user, container, keysToDelete, null, scriptContext);
            }
            transaction.commit();
        }
    }

    public Collection<Integer> deleteOutputFiles(List<Integer> outputFileIds, User user, Container container, boolean doDelete) throws Exception
    {
        SequenceAnalysisSchema s = SequenceAnalysisSchema.getInstance();

        try (DbScope.Transaction transaction = s.getSchema().getScope().ensureTransaction())
        {
            UserSchema us = QueryService.get().getUserSchema(user, container, SequenceAnalysisSchema.SCHEMA_NAME);
            if (us == null)
            {
                throw new IllegalArgumentException("Unable to find sequenceanalysis user schema");
            }

            Set<Integer> bamsDeleted = new HashSet<>();
            Set<Integer> outputFilesWithDataNotDeleted = new HashSet<>();
            Set<Integer> expDataDeleted = new HashSet<>();
            List<SequenceOutputFile> files = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES), new SimpleFilter(FieldKey.fromString("rowid"), outputFileIds, CompareType.IN), null).getArrayList(SequenceOutputFile.class);
            for (SequenceOutputFile so : files)
            {
                ExpData d = so.getExpData();
                if (d != null && d.getFile() != null)
                {
                    // account for possibility that another sequence output is using this file.  this would probably be from an error, like a pipeline resume/double import, but even in this case we shouldnt delete it
                    // also check based on filepath
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("rowid"), outputFileIds, CompareType.NOT_IN);
                    filter.addCondition(new SimpleFilter.OrClause(
                            new CompareType.CompareClause(FieldKey.fromString("dataId"), CompareType.EQUAL, so.getDataId()),
                            new CompareType.CompareClause(FieldKey.fromString("dataId/DataFileUrl"), CompareType.EQUAL, so.getExpData().getDataFileUrl())
                    ));

                    if (container.isWorkbook())
                    {
                        container = container.getParent();
                    }

                    if (new TableSelector(us.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES, null), filter, null).exists())
                    {
                        _log.error("outputfile file appears to be in use by another record, will not delete the associated file.  dataId: " + so.getDataId() + ", " + d.getDataFileUrl());
                        outputFilesWithDataNotDeleted.add(so.getRowid());
                    }
                    else if (doDelete)
                    {
                        if (d.getFile().exists())
                            d.getFile().delete();

                        expDataDeleted.add(d.getRowId());
                    }
                }

                //NOTE: move outside block above in case this delete occurred after the actual file was deleted
                if (d != null && SequenceAnalysisTask.ALIGNMENT_CATEGORY.equals(so.getCategory()) && so.getAnalysis_id() != null)
                {
                    bamsDeleted.add(d.getRowId());
                }
            }

            Set<Integer> additionalAnalysisIds = SequenceAnalysisManager.get().getAnalysesAssociatedWithOutputFiles(outputFileIds);

            if (doDelete)
            {
                final List<Map<String, Object>> outputFilesToDelete = new ArrayList<>();
                outputFileIds.forEach(x -> {
                    Map<String, Object> map = new CaseInsensitiveHashMap<>();
                    map.put("rowid", x);
                    outputFilesToDelete.add(map);
                });

                Map<String, Object> scriptContext = new HashMap<>();
                scriptContext.put("deleteFromServer", true);  //a flag to make the trigger script accept this
                List<Map<String, Object>> deleted = us.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES, null).getUpdateService().deleteRows(user, container, outputFilesToDelete, null, scriptContext);
                if (deleted.size() != outputFilesToDelete.size())
                {
                    throw new PipelineJobException("The total files deleted did not match the input.  Was: " + deleted.size() + ", expected: " + outputFilesToDelete.size());
                }

                if (!additionalAnalysisIds.isEmpty())
                    SequenceAnalysisManager.get().deleteAnalysis(user, container, additionalAnalysisIds);

                //also look for orphan quality metrics:
                if (!expDataDeleted.isEmpty())
                {
                    // If the BAM was deleted by the analysis was not, rather than discard all the metrics, set dataId to NULL and keep them attached to the analysis:
                    if (!bamsDeleted.isEmpty())
                    {
                        List<Map<String, Object>> rowsToUpdate = new ArrayList<>();
                        List<Map<String, Object>> oldKeys = new ArrayList<>();
                        new TableSelector(us.getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS), PageFlowUtil.set("rowId", "analysis_id", "container"), new SimpleFilter(FieldKey.fromString("dataId"), bamsDeleted, CompareType.IN), null).forEachResults(rs -> {
                            // The intent of this is to recover rows from any situation where a BAM is deleted but the analysis is not
                            if (rs.getObject(FieldKey.fromString("analysis_id")) != null && !additionalAnalysisIds.contains(rs.getInt(FieldKey.fromString("analysis_id"))))
                            {
                                Map<String, Object> toUpdate = new CaseInsensitiveHashMap<>();
                                toUpdate.put("rowId", rs.getInt(FieldKey.fromString("rowid")));
                                toUpdate.put("dataid", null);
                                toUpdate.put("container", rs.getString(FieldKey.fromString("container")));
                                rowsToUpdate.add(toUpdate);

                                Map<String, Object> toUpdateKey = new CaseInsensitiveHashMap<>();
                                toUpdateKey.put("rowId", rs.getInt(FieldKey.fromString("rowid")));
                                oldKeys.add(toUpdateKey);
                            }
                        });

                        if (!rowsToUpdate.isEmpty())
                        {
                            _log.info("quality metric rows attached to BAMs that will not be deleted: " + rowsToUpdate.size());
                            us.getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS).getUpdateService().updateRows(user, container, rowsToUpdate, oldKeys, null, new HashMap<>());
                        }
                    }

                    List<Integer> metricRowIds = new TableSelector(us.getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS), PageFlowUtil.set("rowId"), new SimpleFilter(FieldKey.fromString("dataId"), expDataDeleted, CompareType.IN), null).getArrayList(Integer.class);
                    if (!metricRowIds.isEmpty())
                    {
                        final List<Map<String, Object>> metricToDelete = new ArrayList<>();
                        metricRowIds.forEach(x -> {
                            Map<String, Object> map = new CaseInsensitiveHashMap<>();
                            map.put("rowid", x);
                            metricToDelete.add(map);
                        });
                        us.getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS).getUpdateService().deleteRows(user, container, metricToDelete, null, scriptContext);
                    }
                }
                
                //Because Alignment files are tagged in outputfiles and analyses, if the former is deleted, automatically update the latter
                if (!bamsDeleted.isEmpty())
                {
                    //Find all analyses using these BAMs:
                    final List<Map<String, Object>> rows = new ArrayList<>();
                    final List<Map<String, Object>> oldKeys = new ArrayList<>();

                    TableSelector ts = new TableSelector(us.getTable(SequenceAnalysisSchema.TABLE_ANALYSES, null), PageFlowUtil.set("rowid", "container"), new SimpleFilter(FieldKey.fromString("alignmentfile"), bamsDeleted, CompareType.IN), null);
                    if (ts.exists())
                    {
                        ts.forEachResults(rs -> {
                            //these were deleted anyway
                            if (additionalAnalysisIds.contains(rs.getInt(FieldKey.fromString("rowid"))))
                            {
                                return;
                            }

                            Map<String, Object> map = new CaseInsensitiveHashMap<>();
                            map.put("rowid", rs.getInt(FieldKey.fromString("rowid")));
                            oldKeys.add(map);

                            map = new CaseInsensitiveHashMap<>();
                            map.put("rowid", rs.getInt(FieldKey.fromString("rowid")));
                            map.put("alignmentfile", null);
                            map.put("container", rs.getString(FieldKey.fromString("container")));
                            rows.add(map);
                        });
                    }

                    if (!rows.isEmpty())
                    {
                        _log.info("Will set alignmentfile to NULL on " + rows.size() + " analyses because the BAM was deleted");
                        us.getTable(SequenceAnalysisSchema.TABLE_ANALYSES, null).getUpdateService().updateRows(user, container, rows, oldKeys, null, scriptContext);
                    }
                }

                transaction.commit();
            }

            return additionalAnalysisIds;
        }
    }

    public Set<Integer> getAnalysesAssociatedWithOutputFiles(List<Integer> outputFileIds)
    {
        Set<Integer> possibleDeletes = new HashSet<>(outputFileIds.isEmpty() ? Collections.emptyList() : new SqlSelector(SequenceAnalysisSchema.getInstance().getSchema(), new SQLFragment("SELECT distinct a.rowid FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_OUTPUTFILES + " o JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSES + " a ON (o.dataId = a.alignmentFile) WHERE o.rowid IN (" + StringUtils.join(outputFileIds, ",") + ")")).getArrayList(Integer.class));

        //make sure these are not in use by other outputfiles not being deleted
        if (!possibleDeletes.isEmpty())
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("rowId"), outputFileIds, CompareType.NOT_IN);
            filter.addCondition(FieldKey.fromString("analysis_id"), possibleDeletes, CompareType.IN);

            List<Integer> inUse = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES), PageFlowUtil.set("analysis_id"), filter, null).getArrayList(Integer.class);
            if (!inUse.isEmpty())
            {
                _log.info("found " + inUse.size() + " analyses associated with outputs being deleted along with another output.  these will not be deleted.");
                possibleDeletes.removeAll(inUse);
            }
        }

        return possibleDeletes;
    }

    public void deleteRefNtSequence(User user, Container container, List<Integer> rowIds) throws Exception
    {
        deleteRefNtSequence(user, container, rowIds, false);
    }

    //Used by upgrade code only
    protected void deleteRefNtSequenceWithoutUserSchema(List<Integer> rowIds) throws Exception
    {
        deleteRefNtSequence(null, null, rowIds, true);
    }

    private void deleteRefNtSequence(User user, Container container, List<Integer> rowIds, boolean useDbLayer) throws Exception
    {
        SequenceAnalysisSchema s = SequenceAnalysisSchema.getInstance();
        UserSchema us = null;
        if (!useDbLayer)
        {
            us = QueryService.get().getUserSchema(user, container, SequenceAnalysisSchema.SCHEMA_NAME);
            if (us == null)
            {
                throw new IllegalArgumentException("Unable to find sequenceanalysis user schema");
            }
        }

        try (DbScope.Transaction transaction = s.getSchema().getScope().ensureTransaction())
        {
            List<Map<String, Object>> toDeleteQus = new ArrayList<>();

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
                if (useDbLayer)
                {
                    new SqlExecutor(s.getSchema()).execute(new SQLFragment("DELETE FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES + " WHERE rowid = ?", rowId));
                }
                else
                {
                    Map<String, Object> map = new CaseInsensitiveHashMap<>();
                    map.put("rowid", rowId);
                    toDeleteQus.add(map);
                }
            }

            if (!toDeleteQus.isEmpty())
            {
                Map<String, Object> scriptContext = new HashMap<>();
                scriptContext.put("deleteFromServer", true);  //a flag to make the trigger script accept this

                QueryUpdateService qus = us.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES).getUpdateService();

                int batchSize = 2500;
                int numBatches = (int)Math.ceil(toDeleteQus.size() / (double)batchSize);

                for (int i = 0; i < numBatches; i++)
                {
                    int start = i * batchSize;
                    List<Map<String, Object>> subset = toDeleteQus.subList(start, Math.min(toDeleteQus.size(), start + batchSize));
                    qus.deleteRows(user, container, subset, null, scriptContext);
                }
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

    public static void deleteReferenceLibraries(User u, List<Integer> rowIds) throws Exception
    {
        for (Integer rowId : rowIds)
        {
            ReferenceGenomeImpl genome = SequenceAnalysisServiceImpl.get().getReferenceGenome(rowId, u);
            if (genome == null)
            {
                throw new IllegalArgumentException("Unable to find genome: " + rowId);
            }

            String containerId = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), PageFlowUtil.set("container")).getObject(rowId, String.class);
            Container c = ContainerManager.getForId(containerId);
            if (!c.hasPermission(u, DeletePermission.class))
            {
                throw new UnauthorizedException("User does not have delete permission in folder: " + c.getPath());
            }

            deleteReferenceLibrary(u, c, rowId);
        }
    }

    private static void deleteReferenceLibrary(User user, Container c, Integer rowId) throws Exception
    {
        cascadeDelete(user.getUserId(), c.getId(), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS, "library_id", rowId);
        cascadeDelete(user.getUserId(), c.getId(), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_LIBRARY_TRACKS, "library_id", rowId);
        cascadeDelete(user.getUserId(), c.getId(), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_CHAIN_FILES, "genomeId1", rowId);
        cascadeDelete(user.getUserId(), c.getId(), SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_CHAIN_FILES, "genomeId2", rowId);

        //then delete files
        File dir = SequenceAnalysisManager.get().getReferenceLibraryDir(c);
        if (dir != null && dir.exists())
        {
            File libraryDir = new File(dir, rowId.toString());
            if (libraryDir.exists())
            {
                _log.info("deleting reference library dir: " + libraryDir.getPath());
                FileUtils.deleteDirectory(libraryDir);
            }
        }

        JobRunner jr = JobRunner.getDefault();
        Set<GenomeTrigger> triggers = SequenceAnalysisServiceImpl.get().getGenomeTriggers();
        for (final GenomeTrigger t : triggers)
        {
            if (t.isAvailable(c))
            {
                _log.info("running genome delete trigger: " + t.getName());
                final int libraryId = rowId;
                jr.execute(new Job()
                {
                    @Override
                    public void run()
                    {
                        t.onDelete(c, user, _log, libraryId);
                    }
                });
            }
        }

        jr.waitForCompletion();

        //finally the record itself
        Map<String, Object> map = new CaseInsensitiveHashMap<>();
        map.put("rowid", rowId);
        List<Map<String, Object>> toDelete = Arrays.asList(map);

        Map<String, Object> scriptContext = new HashMap<>();
        scriptContext.put("deleteFromServer", true);  //a flag to make the trigger script accept this
        UserSchema us = QueryService.get().getUserSchema(user, c, SequenceAnalysisSchema.SCHEMA_NAME);
        us.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES, null).getUpdateService().deleteRows(user, c, toDelete, null, scriptContext);
    }

    public String getNTRefForAARef(Integer refId)
    {
        SQLFragment sql = new SQLFragment("SELECT name FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES + " a " +
                " LEFT JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES + " n ON (n.rowid = a.ref_nt_id) WHERE a.rowid = ?", refId);

        return new SqlSelector(SequenceAnalysisSchema.getInstance().getSchema(), sql).getObject(String.class);
    }

    public ReferenceLibraryPipelineJob createReferenceLibrary(List<Integer> sequenceIds, Container c, User u, String name, String assemblyId, String description, boolean skipCacheIndexes, boolean skipTriggers, @Nullable List<String> unplacedContigPrefixes, Set<GenomeTrigger> extraTriggers) throws IOException
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

        return createReferenceLibrary(c, u, name, assemblyId, description, libraryMembers, skipCacheIndexes, skipTriggers, unplacedContigPrefixes, extraTriggers);
    }

    public ReferenceLibraryPipelineJob createReferenceLibrary(Container c, User u, String name, String assemblyId, String description, List<ReferenceLibraryMember> libraryMembers, boolean skipCacheIndexes, boolean skipTriggers, @Nullable List<String> unplacedContigPrefixes, Set<GenomeTrigger> extraTriggers) throws IOException
    {
        try
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
            ReferenceLibraryPipelineJob job = new ReferenceLibraryPipelineJob(c, u, root, name, assemblyId, description, libraryMembers, null, skipCacheIndexes, skipTriggers, unplacedContigPrefixes, extraTriggers);
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
                        String header = (String) fastaRecord.get("header");
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

    public void addChainFile(Container c, User u, File file, int genomeId1, int genomeId2, String source, Double version) throws Exception
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
        map.put("source", source);
        map.put("version", version);
        map.put("chainFile", chainFile.getRowId());

        map.put("container", c.getId());
        map.put("created", new Date());
        map.put("createdby", u.getUserId());
        map.put("modified", new Date());
        map.put("modifiedby", u.getUserId());
        Table.insert(u, chainTable, map);
    }

    public SequenceOutputHandler getFileHandler(String handlerClass, SequenceOutputHandler.TYPE type)
    {
        if (StringUtils.isEmpty(handlerClass))
        {
            return null;
        }

        for (SequenceOutputHandler handler : SequenceAnalysisServiceImpl.get().getFileHandlers(type))
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
        PipeRoot pr = PipelineService.get().getPipelineRootSetting(c);
        if (pr == null)
        {
            throw new IllegalArgumentException("Pipeline root is null for folder: " + c.getPath());
        }

        File pipelineDir = pr.getRootPath();
        if (pipelineDir == null)
        {
            return null;
        }

        return new File(pipelineDir, ".referenceLibraries");
    }

    public void apppendSequenceLength(User u, Logger log)
    {
        TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);

        TableSelector ts = new TableSelector(ti, PageFlowUtil.set("rowid", "sequenceFile"), new SimpleFilter(FieldKey.fromString("seqLength"), null, CompareType.ISBLANK), null);

        log.info(ts.getRowCount() + " total sequences to migrate");
        ts.forEach(RefNtSequenceModel.class, new Selector.ForEachBlock<>()
        {
            @Override
            public void exec(RefNtSequenceModel nt) throws StopIteratingException
            {
                try (InputStream is = nt.getSequenceInputStream())
                {
                    if (is == null)
                    {
                        _log.error("unable to read NT sequence: " + nt.getRowid());
                        return;
                    }

                    Map<String, Object> toUpdate = new CaseInsensitiveHashMap<>();
                    toUpdate.put("rowid", nt.getRowid());
                    toUpdate.put("seqLength", StringUtil.bytesToString(IOUtils.toByteArray(is)).length());
                    Table.update(u, ti, toUpdate, nt.getRowid());
                }
                catch (IOException e)
                {
                    _log.error(e.getMessage(), e);

                    stopIterating();
                }
            }
        });
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testJars()
        {
            File libDir = new File(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME).getExplodedPath(), "lib");
            assertNotNull("Unable to find SequenceAnalysis lib dir", libDir);
            assertTrue("SequenceAnalysis lib dir does not exist: " + libDir.getPath(), libDir.exists());

            File apiLibDir = new File(ModuleLoader.getInstance().getModule("API").getExplodedPath(), "lib");
            assertNotNull("Unable to find apiLibDir lib dir", apiLibDir);
            assertTrue("apiLibDir lib dir does not exist: " + apiLibDir.getPath(), apiLibDir.exists());

            File fastqcDir = new File(libDir.getParentFile(), "external/fastqc");
            assertNotNull("Unable to find fastqcDir dir", fastqcDir);
            assertTrue("fastqcDir dir does not exist: " + fastqcDir.getPath(), fastqcDir.exists());
        }
    }
}