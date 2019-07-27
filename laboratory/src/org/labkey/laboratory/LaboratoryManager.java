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
package org.labkey.laboratory;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.Portal;
import org.labkey.laboratory.query.ContainerIncrementingTable;
import org.labkey.laboratory.query.LaboratoryWorkbooksTable;
import org.labkey.laboratory.query.WorkbookModel;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 9/26/12
 * Time: 7:59 AM
 */
public class LaboratoryManager
{
    private static final LaboratoryManager _instance = new LaboratoryManager();
    public static final String DEFAULT_WORKBOOK_FOLDERTYPE_PROPNAME = "DefaultWorkbookFolderType";
    private static final Logger _log = Logger.getLogger(LaboratoryManager.class);

    private LaboratoryManager()
    {

    }

    public static LaboratoryManager get()
    {
        return _instance;
    }

    public void initWorkbooksForContainer(User u, Container c) throws Exception
    {
        if (c.isWorkbookOrTab())
            return;

        boolean labModuleEnabled = c.getActiveModules().contains(ModuleLoader.getInstance().getModule(LaboratoryModule.class));

        List<Container> containers = c.getChildren();
        sortContainers(containers);
        for (Container child : containers)
        {
            if (labModuleEnabled && child.isWorkbook())
            {
                initLaboratoryWorkbook(child, u);
            }

            initWorkbooksForContainer(u, child);
        }
    }

    public synchronized void initLaboratoryWorkbook(Container c, User u) throws Exception
    {
        if (c.isWorkbook())
        {
            UserSchema us = QueryService.get().getUserSchema(u, c, LaboratoryModule.SCHEMA_NAME);
            if (us != null)
            {
                TableInfo ti = us.getTable(LaboratorySchema.TABLE_WORKBOOKS);
                TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString(LaboratoryWorkbooksTable.WORKBOOK_COL), c.getId()), null);
                if (!ts.exists())
                {
                    List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
                    Map<String, Object> row = new CaseInsensitiveHashMap<>();
                    rows.add(row);

                    ti.getUpdateService().insertRows(u, c, rows, new BatchValidationException(), null, new HashMap<>());
                }
            }
        }
    }

    private void sortContainers(List<Container> containers)
    {
        containers.sort(Comparator.comparingInt(Container::getRowId));
    }

    public WorkbookModel getWorkbookModel(Container c)
    {
        TableInfo ti = LaboratorySchema.getInstance().getSchema().getTable(LaboratorySchema.TABLE_WORKBOOKS);
        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString(LaboratoryWorkbooksTable.WORKBOOK_COL), c.getId()), null);
        WorkbookModel[] arr = ts.getArray(WorkbookModel.class);
        WorkbookModel m =  arr.length == 0 ? null : arr[0];
        if (m == null)
            return null;

        if (m.getContainer() == null)
        {
            m.setContainer(c.getId());
        }

        TableInfo ti2 = LaboratorySchema.getInstance().getSchema().getTable(LaboratorySchema.TABLE_WORKBOOK_TAGS);
        TableSelector ts2 = new TableSelector(ti2, PageFlowUtil.set("tag"), new SimpleFilter(FieldKey.fromString(LaboratoryWorkbooksTable.WORKBOOK_COL), c.getId()), null);
        String[] arr2 = ts2.getArray(String.class);
        m.setTags(arr2);

        return m;
    }

    /**
     * This is designed to iterate a folder and children, resetting the webparts and tabs
     * @param c
     */
    public void resetLaboratoryFolderTypes(User u, Container c, boolean includeChildren)
    {
        FolderType lab = FolderTypeManager.get().getFolderType("Laboratory Folder");
        String folderType = LaboratoryService.get().getDefaultWorkbookFolderType(c);
        FolderType expt = FolderTypeManager.get().getFolderType(folderType);
        FolderType custom = FolderTypeManager.get().getFolderType("None");
        assert lab != null && expt != null && custom != null;

        if (c.isWorkbook() && c.getParent().getFolderType().equals(lab))
        {
            c.setFolderType(expt, u);
        }

        if (lab.equals(c.getFolderType()) || expt.equals(c.getFolderType()))
        {
            //force tabs to reset
            if (lab.equals(c.getFolderType()))
            {
                c.setFolderType(custom, u);
                c.setFolderType(lab, u);
            }
            else if (expt.equals(c.getFolderType()))
            {
                c.setFolderType(custom, u);
                c.setFolderType(expt, u);
            }

            for (String pageId : Portal.getPages(c).keySet())
            {
                List<Portal.WebPart> parts = new ArrayList<>();
                parts.addAll(c.getFolderType().getRequiredWebParts());
                parts.addAll(c.getFolderType().getPreferredWebParts());

                Portal.saveParts(c, pageId, parts);
            }

            Portal.resetPages(c, new ArrayList<>(c.getFolderType().getDefaultTabs()), true);
        }

        if (includeChildren)
        {
            for (Container child : c.getChildren())
            {
                resetLaboratoryFolderTypes(u, child, includeChildren);
            }
        }
    }

    public void updateWorkbook(User u, WorkbookModel model)
    {
        TableInfo ti = LaboratorySchema.getInstance().getTable(LaboratorySchema.TABLE_WORKBOOKS);
        Table.update(u, ti, model, model.getContainer());
    }

    public void updateWorkbookTags(User u, Container c, Collection<String> tags)
    {
        updateWorkbookTags(u, c, tags, false);
    }

    public void updateWorkbookTags(User u, Container c, Collection<String> tags, boolean doMerge)
    {
        assert u != null : "No user provided";

        TableInfo ti = LaboratorySchema.getInstance().getTable(LaboratorySchema.TABLE_WORKBOOK_TAGS);
        Set<String> newTags = new HashSet<String>();
        newTags.addAll(tags);

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("container"), c.getId());
        TableSelector ts = new TableSelector(ti, Collections.singleton("tag"), filter, null);
        List<String> existingTags = Arrays.asList(ts.getArray(String.class));

        if (doMerge)
        {
            newTags.addAll(existingTags);
        }

        List<String> toDelete = new ArrayList<String>(existingTags);
        toDelete.removeAll(newTags);

        if (toDelete.size() > 0)
        {
            SimpleFilter filter1 = new SimpleFilter(FieldKey.fromString("tag"), toDelete, CompareType.IN);
            filter1.addCondition(FieldKey.fromString("container"), c.getId());
            Table.delete(ti, filter1);
        }

        List<String> toAdd = new ArrayList<String>(newTags);
        toAdd.removeAll(existingTags);

        Date created = new Date();
        for (String tag : toAdd)
        {
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("tag", tag);
            row.put("created", created);
            row.put("createdby", u.getUserId());
            row.put("container", c.getId());
            Table.insert(u, ti, row);
        }
    }

    //pass null to populate all supported tables
    public void populateDefaultData(User u, Container c, @Nullable List<String> tableNames) throws BatchValidationException
    {
        if (tableNames == null)
        {
            tableNames = new ArrayList<>();
            tableNames.add(LaboratorySchema.TABLE_SAMPLE_TYPE);
        }

        for (String name : tableNames)
        {
            if (LaboratorySchema.TABLE_SAMPLE_TYPE.equalsIgnoreCase(name))
            {
                populateDefaultDataForTable(u, c, "laboratory", LaboratorySchema.TABLE_SAMPLE_TYPE, PageFlowUtil.set("type"));
            }
        }
    }

    /**
     * NOTE: this makes the assumption that the schema/query match a physical table
     */
    private void populateDefaultDataForTable(User u, Container c, String schema, String query, final Set<String> columns) throws BatchValidationException
    {
        assert u != null : "No user provided";

        UserSchema us = QueryService.get().getUserSchema(u, c, schema);
        if (us == null)
            throw new IllegalArgumentException("Schema " + schema + " not found");


        DbSchema sourceSchema = DbSchema.get(schema);
        if (sourceSchema == null)
            throw new IllegalArgumentException("Schema " + schema + " not found in /shared");

        TableInfo ti = us.getTable(query);
        final QueryUpdateService qus = ti.getUpdateService();

        TableInfo sourceTable = sourceSchema.getTable(query);
        if (sourceTable.getColumn(FieldKey.fromString("container")) == null)
        {
            throw new IllegalArgumentException("Table " + schema + "." + query + " does not have a container column");
        }

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("container"), ContainerManager.getSharedContainer().getId());

        final List<Map<String, Object>> rows = new ArrayList<>();
        TableSelector ts = new TableSelector(sourceTable, columns, filter, null);
        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                Map<String, Object> row = new CaseInsensitiveHashMap<>();
                for (String col : columns)
                {
                    row.put(col, rs.getObject(col));
                }

                rows.add(row);
            }
        });

        BatchValidationException errors = new BatchValidationException();

        try
        {
            qus.insertRows(u, c, rows, errors, null, new HashMap<String, Object>());
        }
        catch (QueryUpdateServiceException e)
        {
            throw new RuntimeException(e);
        }
        catch (DuplicateKeyException e)
        {
            //ignore
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (BatchValidationException e)
        {
            //ignore
        }
    }

    public void initContainerIncrementingTableIds(Container c, User u, String schemaName, String queryName)
    {
        if (c.isWorkbook())
            return;

        UserSchema us = QueryService.get().getUserSchema(u, c, schemaName);
        if (us == null)
            throw new IllegalArgumentException("Unknown schema: " + schemaName);

        TableInfo table = us.getTable(queryName);
        if (table == null)
            throw new IllegalArgumentException("Unknown table: " + schemaName + "." + queryName);

        if (!(table instanceof ContainerIncrementingTable))
            throw new IllegalArgumentException("Table is not a ContainerIncrementingTable: " + schemaName + "." + queryName);

        if (table.getPkColumnNames().size() != 1)
            throw new IllegalArgumentException("Table does not have a single PK");

        ContainerIncrementingTable ct = (ContainerIncrementingTable)table;
        QueryUpdateService qus = ct.getUpdateService();
        String colName = ct.getIncrementingCol();
        Set<String> toSelect = new HashSet<String>();
        toSelect.add(colName);
        for (String ci : ct.getPkColumnNames())
        {
            toSelect.add(ci);
        }

        String pkCol = ct.getPkColumnNames().get(0);
        Sort sort = new Sort(pkCol);

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString(colName), null, CompareType.NONBLANK);
        TableSelector ts = new TableSelector(ct, PageFlowUtil.set(colName), filter, null);
        Map<String, List<Aggregate.Result>> aggs = ts.getAggregates(Collections.singletonList(new Aggregate(table.getColumn(colName), Aggregate.BaseType.MAX)));
        Integer max = (Integer)aggs.get(colName).get(0).getValue();

        SimpleFilter filter2 = new SimpleFilter(FieldKey.fromString(colName), null, CompareType.ISBLANK);
        TableSelector ts2 = new TableSelector(ct, toSelect, filter2, sort);
        Map<String, Object>[] rows = ts2.getMapArray();
        Integer incValue = max == null ? 0 : max;


        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            for (Map<String, Object> row : rows)
            {
                incValue++;
                row.put(colName, incValue);
                Table.update(u, ct.getRealTable(), row, row.get(pkCol));
            }

            transaction.commit();
            ct.saveId(c, incValue);
        }
    }

    public List<String> createIndexes(User u, boolean commitChanges, boolean rebuildIndexes)
    {
        List<String> messages = new ArrayList<>();

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            //add indexes to assays first
            Map<String, List<List<String>>> assayIndexes = ((LaboratoryServiceImpl)LaboratoryServiceImpl.get()).getAssayIndexes();
            DbSchema assayResultSchema = DbSchema.get("assayresult");
            Set<String> distinctIndexes = new HashSet<>();
            for (ExpProtocol p : ExperimentService.get().getAllExpProtocols())
            {
                AssayProvider ap = AssayService.get().getProvider(p);
                if (ap == null)
                    continue;

                if (!assayIndexes.containsKey(ap.getName()))
                    continue;

                List<List<String>> indexes = assayIndexes.get(ap.getName());
                Domain d = ap.getResultsDomain(p);
                String tableName = d.getStorageTableName();
                TableInfo realTable = assayResultSchema.getTable(tableName);
                if (realTable == null)
                {
                    messages.add("Unable to find table for assay protocol: " + p.getName());
                    continue;
                }

                processIndexes(assayResultSchema, realTable, indexes, distinctIndexes, messages, commitChanges, rebuildIndexes);
            }

            Map<String, Map<String, List<List<String>>>> tableIndexes = ((LaboratoryServiceImpl)LaboratoryServiceImpl.get()).getTableIndexes();
            for (String schemaName : tableIndexes.keySet())
            {
                DbSchema schema = DbSchema.get(schemaName);
                if (schema == null)
                {
                    messages.add("Unable to find schema: " + schemaName);
                    continue;
                }

                for (String queryName : tableIndexes.get(schemaName).keySet())
                {
                    TableInfo ti = schema.getTable(queryName);
                    if (ti == null)
                    {
                        messages.add("Unable to find table: " + queryName);
                        continue;
                    }

                    processIndexes(schema, ti, tableIndexes.get(schemaName).get(queryName), distinctIndexes, messages, commitChanges, rebuildIndexes);
                }
            }

            transaction.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return messages;
    }

    private void processIndexes(DbSchema schema, TableInfo realTable, List<List<String>> indexes, Set<String> distinctIndexes, List<String> messages, boolean commitChanges, boolean rebuildIndexes) throws SQLException
    {
        for (List<String> indexCols : indexes)
        {
            boolean missingCols = false;

            List<String> cols = new ArrayList<>();
            String[] includedCols = null;
            Map<String, String> directionMap = new HashMap<>();

            for (String name : indexCols)
            {
                String[] tokens = name.split(":");
                if (tokens[0].equalsIgnoreCase("include"))
                {
                    if (tokens.length > 1)
                    {
                        includedCols = tokens[1].split(",");
                    }
                }
                else
                {
                    cols.add(tokens[0]);
                    if (tokens.length > 1)
                        directionMap.put(tokens[0], tokens[1]);
                }
            }

            for (String col : cols)
            {
                if (realTable.getColumn(col) == null)
                {
                    messages.add("Table: " + realTable.getName() + " does not have column " + col + ", so indexing will be skipped");
                    missingCols = true;
                }
            }

            if (missingCols)
                continue;

            String idxPrefix = "LABORATORY_IDX_";
            String indexName = idxPrefix + realTable.getName() + "_" + StringUtils.join(cols, "_");
            if (includedCols != null)
            {
                indexName += "_include_" + StringUtils.join(includedCols, "_");
            }

            if (distinctIndexes.contains(indexName))
                throw new RuntimeException("An index has already been created with the name: " + indexName);
            distinctIndexes.add(indexName);

            Set<String> indexNames = new CaseInsensitiveHashSet();
            DatabaseMetaData meta = schema.getScope().getConnection().getMetaData();
            ResultSet rs = null;
            try
            {
                rs = meta.getIndexInfo(schema.getScope().getDatabaseName(), schema.getName(), realTable.getName(), false, false);
                while (rs.next())
                {
                    indexNames.add(rs.getString("INDEX_NAME"));
                }
            }
            finally
            {
                ResultSetUtil.close(rs);
            }

            boolean exists = indexNames.contains(indexName);
            if (exists && rebuildIndexes)
            {
                if (commitChanges)
                {
                    dropIndex(schema, realTable, indexName, cols, realTable.getName(), messages);
                }
                else
                {
                    messages.add("Will drop/recreate index on column(s): " + StringUtils.join(cols, ", ") + " for table: " + schema.getName() + "." + realTable.getName());
                }
                exists = false;
            }

            if (!exists)
            {
                if (commitChanges)
                {
                    List<String> columns = new ArrayList<>();
                    for (String name : cols)
                    {
                        if (schema.getSqlDialect().isSqlServer() && directionMap.containsKey(name))
                            name += " " + directionMap.get(name);

                        columns.add(name);
                    }

                    createIndex(schema, realTable, realTable.getName(), indexName, columns, includedCols, messages);
                }
                else
                {
                    messages.add("Missing index on column(s): " + StringUtils.join(indexCols, ", ") + (includedCols != null ? " include: " + StringUtils.join(includedCols, ",") : "") + " for table: " + schema.getName() + "." + realTable.getName());
                }
            }
        }
    }

    private void createIndex(DbSchema schema, TableInfo realTable, String tableName, String indexName, List<String> columns, String[] includedCols, List<String> messages)
    {
        messages.add("Creating index on column(s): " + StringUtils.join(columns, ", ") + " for table: " + schema.getName() + "." + tableName);
        String sqlString = "CREATE INDEX " + indexName + " ON " + realTable.getSelectName() + "(" + StringUtils.join(columns, ", ") + ")";
        if (schema.getSqlDialect().isSqlServer())
        {
            if (includedCols != null)
                sqlString += " INCLUDE (" + StringUtils.join(includedCols, ", ") + ") ";

            sqlString += " WITH (DATA_COMPRESSION = ROW)";
        }
        SQLFragment sql = new SQLFragment(sqlString);
        SqlExecutor se = new SqlExecutor(schema);
        se.execute(sql);
    }

    private void dropIndex(DbSchema schema, TableInfo realTable, String indexName, List<String> cols, String tableName, List<String> messages)
    {
        messages.add("Dropping index on column(s): " + StringUtils.join(cols, ", ") + " for table: " + schema.getName() + "." + tableName);
        String sqlString = "DROP INDEX " + indexName + " ON " + realTable.getSelectName();
        SQLFragment sql = new SQLFragment(sqlString);
        SqlExecutor se = new SqlExecutor(schema);
        se.execute(sql);
    }
}
