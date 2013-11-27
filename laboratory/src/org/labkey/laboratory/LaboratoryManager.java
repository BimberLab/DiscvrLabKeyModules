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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.Portal;
import org.labkey.laboratory.query.ContainerIncrementingTable;
import org.labkey.laboratory.query.LaboratoryWorkbooksTable;
import org.labkey.laboratory.query.WorkbookModel;

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
    private static LaboratoryManager _instance = new LaboratoryManager();
    public static final String DEFAULT_WORKBOOK_FOLDERTYPE_PROPNAME = "DefaultWorkbookFolderType";

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

                List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
                Map<String, Object> row = new CaseInsensitiveHashMap<Object>();
                rows.add(row);

                BatchValidationException errors = new BatchValidationException();

                TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString(LaboratoryWorkbooksTable.WORKBOOK_COL), c.getId()), null);
                if (ts.getRowCount() == 0)
                    ti.getUpdateService().insertRows(u, c, rows, errors, new HashMap<String, Object>());
            }
        }
    }

    private void sortContainers(List<Container> containers)
    {
        Collections.sort(containers, new Comparator<Container>()
        {
            @Override
            public int compare(Container o1, Container o2)
            {
                return (new Integer(o1.getRowId())).compareTo(o2.getRowId());
            }
        });
    }

    public Integer getNextWorkbookId(Container c)
    {
        Container target = c.isWorkbook() ? c.getParent() : c;
        TableInfo ti = LaboratorySchema.getInstance().getSchema().getTable(LaboratorySchema.TABLE_WORKBOOKS);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString(LaboratoryWorkbooksTable.PARENT_COL), target.getId(), CompareType.EQUAL);

        TableSelector ts = new TableSelector(ti, Collections.singleton("workbookId"), filter, null);
        Map<String, List<Aggregate.Result>> aggs = ts.getAggregates(Collections.singletonList(new Aggregate(FieldKey.fromString("workbookId"), Aggregate.Type.MAX)));
        for (List<Aggregate.Result> ag : aggs.values())
        {
            for (Aggregate.Result r : ag)
            {
                //did the return type of aggregates change between 12.3 -> 13.1??
                Integer rowId;
                if (r.getValue() instanceof Long)
                {
                    Long val = (Long)r.getValue();
                    rowId = val == null ? null : val.intValue();
                }
                else
                {
                    rowId = (Integer)r.getValue();
                }

                if (rowId == null)
                    rowId = 0;

                rowId++;
                return rowId;
            }
        }

        return 1;
    }

    public Integer getWorkbookId(Container c)
    {
        TableInfo ti = LaboratorySchema.getInstance().getSchema().getTable(LaboratorySchema.TABLE_WORKBOOKS);
        TableSelector ts = new TableSelector(ti, Collections.singleton("workbookId"), new SimpleFilter(FieldKey.fromString(LaboratoryWorkbooksTable.WORKBOOK_COL), c.getId()), null);
        Integer[] arr = ts.getArray(Integer.class);
        return arr.length == 0 ? null : arr[0];
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
        FolderType lab = ModuleLoader.getInstance().getFolderType("Laboratory Folder");
        String folderType = LaboratoryService.get().getDefaultWorkbookFolderType(c);
        FolderType expt = ModuleLoader.getInstance().getFolderType(folderType);
        FolderType custom = ModuleLoader.getInstance().getFolderType("None");
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
                List<Portal.WebPart> parts = new ArrayList<Portal.WebPart>();
                parts.addAll(c.getFolderType().getRequiredWebParts());
                parts.addAll(c.getFolderType().getPreferredWebParts());

                Portal.saveParts(c, pageId, parts);
            }

            Portal.resetPages(c, c.getFolderType().getDefaultTabs(), true);
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
        try
        {
            Table.update(u, ti, model, model.getContainer());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void updateWorkbookTags(User u, Container c, Collection<String> tags)
    {
        updateWorkbookTags(u, c, tags, false);
    }

    public void updateWorkbookTags(User u, Container c, Collection<String> tags, boolean doMerge)
    {
        assert u != null : "No user provided";

        TableInfo ti = LaboratorySchema.getInstance().getTable(LaboratorySchema.TABLE_WORKBOOK_TAGS);
        try
        {
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
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
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

    private void populateDefaultDataForTable(User u, Container c, String schema, String query, final Set<String> columns) throws BatchValidationException
    {
        assert u != null : "No user provided";

        UserSchema us = QueryService.get().getUserSchema(u, c, schema);
        if (us == null)
            throw new IllegalArgumentException("Schema " + schema + " not found");

        UserSchema sharedSchema = QueryService.get().getUserSchema(u, ContainerManager.getSharedContainer(), schema);
        if (sharedSchema == null)
            throw new IllegalArgumentException("Schema " + schema + " not found in /shared");

        TableInfo ti = us.getTable(query);
        final QueryUpdateService qus = ti.getUpdateService();

        TableInfo sharedTable = sharedSchema.getTable(query);

        final List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        TableSelector ts = new TableSelector(sharedTable, columns, null, null);
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
            qus.insertRows(u, c, rows, errors, new HashMap<String, Object>());
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
        Map<String, List<Aggregate.Result>> aggs = ts.getAggregates(Collections.singletonList(new Aggregate(table.getColumn(colName), Aggregate.Type.MAX)));
        Integer max = (Integer)aggs.get(colName).get(0).getValue();

        SimpleFilter filter2 = new SimpleFilter(FieldKey.fromString(colName), null, CompareType.ISBLANK);
        TableSelector ts2 = new TableSelector(ct, toSelect, filter2, sort);
        Map<String, Object>[] rows = ts2.getMapArray();
        Integer incValue = max == null ? 0 : max;

        ExperimentService.get().ensureTransaction();
        try
        {
            for (Map<String, Object> row : rows)
            {
                incValue++;
                row.put(colName, incValue);
                Table.update(u, ct.getRealTable(), row, row.get(pkCol));
            }

            ExperimentService.get().commitTransaction();
            ct.saveId(c, incValue);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }
    }
}
