/*
 * Copyright (c) 2012 LabKey Corporation
 *
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

package org.labkey.GeneticsCore;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.Queryable;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GeneticsCoreManager
{
    private static final GeneticsCoreManager _instance = new GeneticsCoreManager();

    private static final Logger _log = Logger.getLogger(GeneticsCoreManager.class);

    @Queryable
    public static final String DNA_DRAW_COLLECTED = "DNA Bank Blood Draw Collected";
    @Queryable
    public static final String DNA_DRAW_NEEDED = "DNA Bank Blood Draw Needed";
    @Queryable
    public static final String DNA_NOT_NEEDED = "DNA Bank Not Needed";
    @Queryable
    public static final String PARENTAGE_DRAW_COLLECTED = "Parentage Blood Draw Collected";
    @Queryable
    public static final String PARENTAGE_DRAW_NEEDED = "Parentage Blood Draw Needed";
    @Queryable
    public static final String PARENTAGE_NOT_NEEDED = "Parentage Not Needed";
    @Queryable
    public static final String MHC_DRAW_COLLECTED = "MHC Blood Draw Collected";
    @Queryable
    public static final String MHC_DRAW_NEEDED = "MHC Blood Draw Needed";
    @Queryable
    public static final String MHC_NOT_NEEDED = "MHC Typing Not Needed";

    public static final String SEQUENCEANALYSIS_SCHEMA = "sequenceanalysis";
    public static final String TABLE_SEQUENCE_ANALYSES = "sequence_analyses";
    public static final String GENOTYPE_ASSAY_PROVIDER = "Genotype Assay";

    private GeneticsCoreManager()
    {
        // prevent external construction with a private default constructor
    }

    public static GeneticsCoreManager get()
    {
        return _instance;
    }

    public Pair<List<Integer>, List<Integer>> cacheAnalyses(final ViewContext ctx, final ExpProtocol protocol, String[] pks) throws IllegalArgumentException
    {
        final User u = ctx.getUser();
        final List<Integer> runsCreated = new ArrayList<>();
        final List<Integer> runsDeleted = new ArrayList<>();

        try (DbScope.Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
        {
            //next identify a build up the results
            TableInfo tableAlignments = QueryService.get().getUserSchema(u, ctx.getContainer(), SEQUENCEANALYSIS_SCHEMA).getTable("alignment_summary_by_lineage");
            if (tableAlignments == null)
            {
                throw new IllegalArgumentException("Unable to find alignment_summary_by_lineage query");
            }

            Set<FieldKey> fieldKeys = new HashSet<>();
            fieldKeys.add(FieldKey.fromString("key"));
            fieldKeys.add(FieldKey.fromString("analysis_id"));
            fieldKeys.add(FieldKey.fromString("analysis_id/readset/subjectid"));
            fieldKeys.add(FieldKey.fromString("analysis_id/readset/sampledate"));
            fieldKeys.add(FieldKey.fromString("lineages"));
            fieldKeys.add(FieldKey.fromString("total_reads"));
            fieldKeys.add(FieldKey.fromString("percent"));

            final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(tableAlignments, fieldKeys);

            final AssayProvider ap = AssayService.get().getProvider(GENOTYPE_ASSAY_PROVIDER);
            AssayProtocolSchema schema = ap.createProtocolSchema(u, ctx.getContainer(), protocol, null);
            final TableInfo assayDataTable = schema.getTable(AssayProtocolSchema.DATA_TABLE_NAME);

            final Map<Integer, List<Map<String, Object>>> rowHash = new HashMap<>();
            final Map<Integer, Set<Integer>> toDeleteByAnalysis = new HashMap<>();

            TableSelector tsAlignments = new TableSelector(tableAlignments, cols.values(), new SimpleFilter(FieldKey.fromString("key"), Arrays.asList(pks), CompareType.IN), null);
            tsAlignments.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet object) throws SQLException
                {
                    Results rs = new ResultsImpl(object, cols);

                    int analysisId = rs.getInt(FieldKey.fromString("analysis_id"));
                    String lineages = rs.getString(FieldKey.fromString("lineages"));

                    //identify existing rows in this assay
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("analysisId"), analysisId);
                    filter.addCondition(FieldKey.fromString("marker"), lineages);
                    filter.addCondition(FieldKey.fromString("analysisId"), null, CompareType.NONBLANK);
                    filter.addCondition(FieldKey.fromString("Run/assayType"), SBT_LINEAGE_ASSAY_TYPE);

                    TableSelector ts = new TableSelector(assayDataTable, PageFlowUtil.set("RowId"), filter, null);
                    Set<Integer> existing = new HashSet<>(ts.getArrayList(Integer.class));
                    if (!existing.isEmpty())
                    {
                        Set<Integer> toDelete = toDeleteByAnalysis.containsKey(analysisId) ? toDeleteByAnalysis.get(analysisId) : new HashSet<Integer>();
                        toDelete.addAll(existing);
                        toDeleteByAnalysis.put(analysisId, toDelete);
                    }

                    Map<String, Object> rowMap = new CaseInsensitiveHashMap<>();
                    rowMap.put("subjectid", rs.getString(FieldKey.fromString("analysis_id/readset/subjectid")));
                    rowMap.put("date", rs.getDate(FieldKey.fromString("analysis_id/readset/sampledate")));
                    rowMap.put("marker", rs.getString(FieldKey.fromString("lineages")));
                    rowMap.put("result", rs.getDouble(FieldKey.fromString("percent")));
                    rowMap.put("qual_result", "POS");
                    rowMap.put("analysisid", rs.getInt(FieldKey.fromString("analysis_id")));

                    if (rowMap.get("subjectid") == null)
                    {
                        throw new IllegalArgumentException("One or more rows is missing a subjectId");
                    }

                    List<Map<String, Object>> rows = rowHash.containsKey(analysisId) ? rowHash.get(analysisId) : new ArrayList<Map<String, Object>>();
                    rows.add(rowMap);

                    rowHash.put(analysisId, rows);
                }
            });

            if (!rowHash.isEmpty())
            {
                processSet(SBT_LINEAGE_ASSAY_TYPE, rowHash, assayDataTable, u, ctx, toDeleteByAnalysis, ap, protocol, runsCreated);
            }

            transaction.commit();

            return Pair.of(runsCreated, runsDeleted);
        }
    }

    public static final String HAPLOTYPE_ASSAY_TYPE = "SBT Haplotypes";
    public static final String SBT_LINEAGE_ASSAY_TYPE = "SBT";

    public Pair<List<Integer>, List<Integer>> cacheHaplotypes(final ViewContext ctx, final ExpProtocol protocol, JSONArray data) throws IllegalArgumentException
    {
        final User u = ctx.getUser();
        final List<Integer> runsCreated = new ArrayList<>();
        final List<Integer> runsDeleted = new ArrayList<>();

        //next identify a build up the results
        TableInfo tableAnalyses = QueryService.get().getUserSchema(u, ctx.getContainer(), SEQUENCEANALYSIS_SCHEMA).getTable("sequence_analyses");
        if (tableAnalyses == null)
        {
            throw new IllegalArgumentException("Unable to find sequence_analyses table");
        }

        Set<FieldKey> fieldKeys = new HashSet<>();
        fieldKeys.add(FieldKey.fromString("rowid"));
        fieldKeys.add(FieldKey.fromString("readset/subjectid"));
        fieldKeys.add(FieldKey.fromString("readset/sampledate"));

        Set<Integer> analysisIds = new HashSet<>();
        final Map<Integer, List<JSONObject>> haploMap = new HashMap<>();
        for (JSONObject row : data.toJSONObjectArray())
        {
            Integer analysisId = row.getInt("analysisId");
            analysisIds.add(analysisId);

            if (!haploMap.containsKey(analysisId))
            {
                haploMap.put(analysisId, new ArrayList<>());
            }

            haploMap.get(analysisId).add(row);
        }

        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(tableAnalyses, fieldKeys);

        final AssayProvider ap = AssayService.get().getProvider(GENOTYPE_ASSAY_PROVIDER);
        AssayProtocolSchema schema = ap.createProtocolSchema(u, ctx.getContainer(), protocol, null);
        final TableInfo assayDataTable = schema.getTable(AssayProtocolSchema.DATA_TABLE_NAME);

        final Map<Integer, List<Map<String, Object>>> rowHash = new HashMap<>();
        final Map<Integer, Set<Integer>> toDeleteByAnalysis = new HashMap<>();

        TableSelector tsAlignments = new TableSelector(tableAnalyses, cols.values(), new SimpleFilter(FieldKey.fromString("rowid"), analysisIds, CompareType.IN), null);
        tsAlignments.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet object) throws SQLException
            {
                Results rs = new ResultsImpl(object, cols);

                int analysisId = rs.getInt(FieldKey.fromString("rowid"));

                //identify existing rows in this assay
                for (JSONObject row : haploMap.get(analysisId))
                {
                    String haplotype = row.getString("haplotype");
                    Double pct = row.optDouble("pct");
                    String comments = row.optString("comments");
                    String category = row.optString("category");

                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("analysisId"), analysisId);
                    filter.addCondition(FieldKey.fromString("marker"), haplotype);
                    filter.addCondition(FieldKey.fromString("analysisId"), null, CompareType.NONBLANK);
                    filter.addCondition(FieldKey.fromString("Run/assayType"), HAPLOTYPE_ASSAY_TYPE);

                    TableSelector ts = new TableSelector(assayDataTable, PageFlowUtil.set("RowId"), filter, null);
                    Set<Integer> existing = new HashSet<>(ts.getArrayList(Integer.class));
                    if (!existing.isEmpty())
                    {
                        Set<Integer> toDelete = toDeleteByAnalysis.containsKey(analysisId) ? toDeleteByAnalysis.get(analysisId) : new HashSet<>();
                        toDelete.addAll(existing);
                        toDeleteByAnalysis.put(analysisId, toDelete);
                    }

                    Map<String, Object> rowMap = new CaseInsensitiveHashMap<>();
                    rowMap.put("subjectid", rs.getString(FieldKey.fromString("readset/subjectid")));
                    rowMap.put("date", rs.getDate(FieldKey.fromString("readset/sampledate")));
                    rowMap.put("marker", haplotype);
                    rowMap.put("qual_result", "POS");
                    rowMap.put("result", pct);
                    rowMap.put("comment", comments);
                    rowMap.put("category", category);
                    rowMap.put("analysisid", analysisId);

                    if (rowMap.get("subjectid") == null)
                    {
                        throw new IllegalArgumentException("One or more rows is missing a subjectId");
                    }

                    List<Map<String, Object>> rows = rowHash.containsKey(analysisId) ? rowHash.get(analysisId) : new ArrayList<>();
                    rows.add(rowMap);

                    rowHash.put(analysisId, rows);
                }
            }
        });

        if (!rowHash.isEmpty())
        {
            try (DbScope.Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
            {
                processSet(HAPLOTYPE_ASSAY_TYPE, rowHash, assayDataTable, u, ctx, toDeleteByAnalysis, ap, protocol, runsCreated);

                transaction.commit();
            }
        }

        return Pair.of(runsCreated, runsDeleted);
    }

    private void processSet(String assayType, Map<Integer, List<Map<String, Object>>> rowHash, TableInfo assayDataTable, User u, ViewContext ctx, Map<Integer, Set<Integer>> toDeleteByAnalysis, AssayProvider ap, ExpProtocol protocol, List<Integer> runsCreated)
    {
        for (Integer analysisId : rowHash.keySet())
        {
            TableSelector ts = new TableSelector(DbSchema.get(SEQUENCEANALYSIS_SCHEMA).getTable(TABLE_SEQUENCE_ANALYSES), PageFlowUtil.set("container"), new SimpleFilter(FieldKey.fromString("rowId"), analysisId), null);
            String analysisContainerId = ts.getObject(String.class);
            Container analysisContainer = ContainerManager.getForId(analysisContainerId);

            if (toDeleteByAnalysis.containsKey(analysisId))
            {
                List<Map<String, Object>> rowsToDelete = new ArrayList<>();
                for (Integer rowId : toDeleteByAnalysis.get(analysisId))
                {
                    rowsToDelete.add(PageFlowUtil.mapInsensitive("RowId", rowId));
                }

                try
                {
                    assayDataTable.getUpdateService().deleteRows(u, analysisContainer, rowsToDelete, null, new HashMap<>());
                }
                catch (Exception e)
                {
                    _log.error(e);
                    throw new IllegalArgumentException(e.getMessage());
                }
            }

            List<Map<String, Object>> rows = rowHash.get(analysisId);
            if (!rows.isEmpty())
            {
                JSONObject json = new JSONObject();
                Map<String, Object> batchProps = new HashMap<>();
                batchProps.put("Name", "Analysis Id: " + analysisId);
                json.put("Batch", batchProps);

                Map<String, Object> runProps = new HashMap<>();
                runProps.put("Name", "Analysis Id: " + analysisId);
                runProps.put("assayType", assayType);
                runProps.put("runDate", new Date());
                runProps.put("performedby", u.getDisplayName(u));
                json.put("Run", runProps);

                try
                {
                    ViewContext ctxCopy = new ViewContext(ctx);
                    ctxCopy.setContainer(analysisContainer);

                    _log.info("created assay run for analysis " + analysisId + " as part of caching sequence results");
                    Pair<ExpExperiment, ExpRun> ret = LaboratoryService.get().saveAssayBatch(rows, json, "sbt_cache_" + analysisId + "_" + FileUtil.getTimestamp(), ctxCopy, ap, protocol);
                    runsCreated.add(ret.second.getRowId());
                }
                catch (ValidationException e)
                {
                    throw new IllegalArgumentException(e.getMessage());
                }
            }
        }
    }
}
