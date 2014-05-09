package org.labkey.mergesync;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 10/10/13
 * Time: 12:26 PM
 */
public class MergeSyncRunner implements Job
{
    private static final Logger _log = Logger.getLogger(MergeSyncRunner.class);
    protected final static SimpleDateFormat _dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd kk:mm");
    protected final static SimpleDateFormat _dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final Set<String> EXCLUDED_RESULTS = PageFlowUtil.set("received", "recieved", ".", "SENT TO RHEIN CONSULTING LAB");

    private Map<String, TableInfo> _cachedResultTables = new HashMap<>();
    private Map<String, Integer> _cachedProjectNames = new HashMap<>();
    private Map<String, String> _cachedTasks = new HashMap<>();
    private Map<String, String> _cachedRuns = new HashMap<>();

    public MergeSyncRunner()
    {

    }

    /**
     * pulls data from Merge back to EHR
     */
    public void execute(JobExecutionContext context) throws JobExecutionException
    {
        if (!MergeSyncManager.get().isPullEnabled())
            return;

        pullResultsFromMerge();
    }

    /**
     * When a request is sent to merge, a record is added to mergesync.orderssynced
     * This inspects any records in this table and checks whether merge has results available for that run
     */
    public void pullResultsFromMerge()
    {
        Date lastRun = MergeSyncManager.get().getLastRun();
        _log.info("Pulling results from merge verified since: " + (lastRun == null ? "never run before" : _dateTimeFormat.format(lastRun)));

        if (lastRun == null)
        {
            lastRun = new Date();
        }

        TableInfo mergeOrders = MergeSyncManager.get().getMergeSchema().getTable(MergeSyncManager.TABLE_MERGE_ORDERS);
        if (mergeOrders == null)
        {
            _log.warn("Unable to find merge orders table, aborting");
            return;
        }

        User u = MergeSyncManager.get().getLabKeyUser();
        Container c = MergeSyncManager.get().getLabKeyContainer();
        if (u == null || c == null)
        {
            _log.error("Unknown user or container, cannot pull results from merge");
            return;
        }

        DbSchema mergeSchema = MergeSyncManager.get().getMergeSchema();
        if (mergeSchema == null)
        {
            _log.error("Unable to find merge schema, aborting");
            return;
        }

        TableInfo resultTable = MergeSyncUserSchema.getMergeDataTable(mergeSchema);
        if (mergeSchema == null)
        {
            _log.error("Unable to create merge results table");
            return;
        }

        int stackSize = -1;

        try
        {
            // Push a fake ViewContext onto the HttpView stack
            stackSize = HttpView.getStackSize();
            ViewContext.getMockViewContext(u, c, new ActionURL("onprc_ehr", "fake.view", c), true);

            QueryService.get().setEnvironment(QueryService.Environment.USER, u);

            pullResults(u, c, mergeSchema, lastRun);

            MergeSyncManager.get().setLastRun(new Date());
        }
        finally
        {
            QueryService.get().clearEnvironment();
            if (stackSize > -1)
                HttpView.resetStackSize(stackSize);
        }
    }

    private void pullResults(final User u, final Container c, DbSchema mergeSchema, Date minDate)
    {
        TableInfo clinpathRuns = QueryService.get().getUserSchema(u, c, "study").getTable("clinpathRuns");
        if (clinpathRuns == null)
        {
            _log.error("Unable to find study.clinpathRuns, aborting");
            return;
        }

        TableInfo runsTable = MergeSyncUserSchema.getMergeRunsTable(mergeSchema);
        final TableInfo mergeResultTable = MergeSyncUserSchema.getMergeDataTable(mergeSchema);

        SimpleFilter runFilter = new SimpleFilter(FieldKey.fromString("dateVerified"), minDate, CompareType.DATE_GTE);
        runFilter.addCondition(FieldKey.fromString("status"), "V");
        runFilter.addCondition(FieldKey.fromString("numericLastName"), true, CompareType.EQUAL);

        TableSelector runTs = new TableSelector(runsTable, runFilter, null);
        Long count = runTs.getRowCount();
        if (count > 0)
        {
            _log.info("found " + count + " new merge runs to sync");
            processRuns(c, u, runTs, mergeResultTable);
            _log.info("finished pulling results from merge");
        }
        else
        {
            _log.info("no merge runs found, nothing to do");
        }
    }

    private TableInfo getClinpathRuns(Container c, User u)
    {
        TableInfo clinpathRuns = QueryService.get().getUserSchema(u, c, "study").getTable("clinpathRuns");
        if (clinpathRuns == null)
        {
            _log.error("Unable to find study.clinpathRuns, aborting");
            return null;
        }

        return clinpathRuns;
    }

    private void processRuns(final Container c, final User u, TableSelector runTs, final TableInfo mergeResultTable)
    {
        runTs.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet runRs) throws SQLException
            {
                processSingleRun(c, u, mergeResultTable, runRs);
            }
        });
    }

    private void processSingleRun(final Container c, final User u, TableInfo mergeResultTable, ResultSet mergeRunRs) throws SQLException
    {
        Integer accession = mergeRunRs.getInt("accession");
        Integer panelId = mergeRunRs.getInt("panelid");

        SimpleFilter resultFilter = new SimpleFilter(FieldKey.fromString("accession"), accession);
        resultFilter.addCondition(FieldKey.fromString("panelid"), panelId);
        resultFilter.addCondition(FieldKey.fromString("isFinal"), "Y");
        resultFilter.addCondition(FieldKey.fromString("status"), "V");
        resultFilter.addCondition(FieldKey.fromString("text_result"), EXCLUDED_RESULTS, CompareType.NOT_IN);

        final Map<String, List<Map<String, Object>>> resultsToCreate = new HashMap<>();
        final Map<String, TableInfo> resultDatasets = new HashMap<>();

        TableInfo tableOrdersSynced = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_ORDERSSYNCED);
        Map<String, Object> existingRequest = getExistingRequest(c, u, accession, panelId);
        final String existingRunId = existingRequest == null ? null : (String)existingRequest.get("runid");
        _log.info("processing order: " + accession);
        _log.info("existingRunId: " + existingRunId);

        TableSelector resultsTs = new TableSelector(mergeResultTable, resultFilter, null);
        resultsTs.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                Map<String, Object> resultRow = processResultSet(c, u, rs, existingRunId);
                if (resultRow == null)
                {
                    return;  //this could occur if the result is simply a placeholder like 'RECEIVED'
                }

                TableInfo dataset = getResultDataSet(c, u, rs, existingRunId);
                if (dataset == null)
                {
                    return; //count on getResultDataSet() for error reporting
                }

                resultDatasets.put(dataset.getName(), dataset);

                List<Map<String, Object>> list = resultsToCreate.get(dataset.getName());
                if (list == null)
                    list = new ArrayList<>();

                list.add(resultRow);

                resultsToCreate.put(dataset.getName(), list);
            }
        });

        DbScope scope = DbScope.getLabkeyScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            String taskId = null;
            String runId = null;
            String orderObjectId = null;
            if (existingRequest != null)
            {
                taskId = (String)existingRequest.get("taskid");
                runId = (String)existingRequest.get("runid");
                orderObjectId = (String)existingRequest.get("objectid");
                Boolean resultsReceived = (Boolean)existingRequest.get("resultsreceived");

                if (resultsReceived)
                {
                    _log.info("This order already has results imported, skipping: " + accession + "/" + panelId);
                    return;
                }
            }

            String mergeService = mergeRunRs.getString("servicename_abbr");
            String labkeyServiceName = resolveServiceName(c, u, mergeService, runId);
            boolean expectResults = shouldSyncResults(labkeyServiceName);

            //NOTE: only put this into a task if we expect to sync results.  otherwise leave it in the queue and manual entry
            if (taskId == null && expectResults)
            {
                taskId = createTask(c, u, mergeRunRs.getString("animalId"), mergeRunRs.getDate("date"));
            }

            if (runId == null)
            {
                runId = createRun(c, u, mergeRunRs, taskId, (expectResults && resultsToCreate.isEmpty()));
            }

            String requestId = null;
            if (orderObjectId == null)
            {
                orderObjectId = createOrderRecord(c, u, taskId, runId, accession, panelId, mergeRunRs.getDate("date"));
            }
            else
            {
                requestId = getRequestForOrder(c, u, orderObjectId);
            }

            if (expectResults)
            {
                if (resultsToCreate.isEmpty())
                {
                    _log.error("Merge request for " + mergeService + " was expected to have results, but does not.  Accession: " + accession + ", panelId: " + panelId);
                }

                for (String datasetName : resultsToCreate.keySet())
                {
                    List<Map<String, Object>> rows = resultsToCreate.get(datasetName);
                    for (Map<String, Object> row : rows)
                    {
                        row.put("taskid", taskId);
                        row.put("runid", runId);
                        row.put("requestid", requestId);
                    }

                    TableInfo ds = resultDatasets.get(datasetName);

                    _log.info("creating " + datasetName + " results: " + resultsToCreate.get(datasetName).size());

                    try
                    {
                        BatchValidationException errors = new BatchValidationException();
                        ds.getUpdateService().insertRows(u, c, resultsToCreate.get(datasetName), errors, new HashMap<String, Object>());
                    }
                    catch (BatchValidationException | DuplicateKeyException | QueryUpdateServiceException e)
                    {
                        _log.error(e.getMessage(), e);
                        throw new RuntimeException(e.getMessage(), e);
                    }
                }
            }
            else
            {
                if (!resultsToCreate.isEmpty())
                {
                    _log.error("Merge request for " + mergeService + " was not expected to have results, but does.  Accession: " + accession + ", panelId: " + panelId);
                    for (String tableName : resultsToCreate.keySet())
                    {
                        _log.error(tableName + ": " + resultsToCreate.get(tableName).size());
                    }
                }
            }

            //update the LK table to indicate results are received
            Map<String, Object> toUpdate = new CaseInsensitiveHashMap<>();
            toUpdate.put("resultsreceived", true);
            toUpdate.put("objectid", orderObjectId);
            toUpdate.put("taskid", taskId);
            toUpdate.put("requestid", requestId);
            Table.update(u, tableOrdersSynced, toUpdate, orderObjectId);

            transaction.commit();
        }
    }

    private boolean shouldSyncResults(String labkeyServiceName)
    {
        if (labkeyServiceName ==null)
            return false;

        String cacheKey = this.getClass().getName() + "||" + "||resultsExpectedMap";
        if (CacheManager.getSharedCache().get(cacheKey) == null)
        {
            TableInfo ti = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_TESTNAMEMAPPING);
            TableSelector ts = new TableSelector(ti);
            Map<String, Boolean> ret = new HashMap<>();
            for (Map<String, Object> row : ts.getMapArray())
            {
                ret.put((String)row.get("servicename"), (Boolean)row.get("automaticresults"));
            }

            CacheManager.getSharedCache().put(cacheKey, ret);
        }

        Map<String, Boolean> map = (Map)CacheManager.getSharedCache().get(cacheKey);

        return map.get(labkeyServiceName) != null ? map.get(labkeyServiceName) : false;
    }

    private String createTask(Container c, User u, String animalId, Date date) throws SQLException
    {
        String key = animalId + " " + _dateFormat.format(date);
        if (_cachedTasks.containsKey(key))
        {
            return _cachedTasks.get(key);
        }

        String taskId = new GUID().toString();
        Map<String, Object> taskRecord = new CaseInsensitiveHashMap<>();
        taskRecord.put("taskid", taskId);
        taskRecord.put("title", key);
        taskRecord.put("category", "Task");
        taskRecord.put("formType", "Labwork");
        taskRecord.put("assignedto", u.getUserId());
        taskRecord.put("container", c.getId());
        taskRecord.put("created", new Date());
        taskRecord.put("createdby", u.getUserId());
        taskRecord.put("modified", new Date());
        taskRecord.put("modifiedby", u.getUserId());

        TableInfo taskTable = DbSchema.get("ehr").getTable("tasks");

        _log.info("Creating task for merge sync: " + key);
        Table.insert(u, taskTable, taskRecord);

        _cachedTasks.put(key, taskId);

        return taskId;
    }

    private Map<String, Object> getExistingRequest(Container c, User u, Integer accession, Integer panelId)
    {
        TableInfo tableOrdersSynced = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_ORDERSSYNCED);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("order_accession"), accession);
        filter.addCondition(FieldKey.fromString("container"), c.getId());

        // this is designed to handle legacy data and newer data.  the legacy data has only been tracked by accession.
        // data synced via LK are tracked using accession and panelId
        filter.addClause(new SimpleFilter.OrClause(
                new CompareType.CompareClause(FieldKey.fromString("test_accession"), CompareType.EQUAL, panelId),
                new CompareType.CompareClause(FieldKey.fromString("test_accession"), CompareType.ISBLANK, null)
        ));

        TableSelector ts = new TableSelector(tableOrdersSynced, PageFlowUtil.set("objectid", "resultsreceived", "runid", "taskid"), filter, null);
        List<Map> ret = ts.getArrayList(Map.class);
        if (ret.isEmpty())
            return null;

        return new CaseInsensitiveHashMap<Object>(ret.get(0));
    }

    private Map<String, Object> processResultSet(Container c, User u, ResultSet rs, @Nullable String runId) throws SQLException
    {
        String animalId = rs.getString("animalId");
        Date date = convertGMTToLocal(rs.getDate("date"));
        String projectName = rs.getString("projectName");
        //Date datecollected = convertGMTToLocal(rs.getDate("datecollected"));
        String servicename_abbr = rs.getString("servicename_abbr");
        String testid_abbr = rs.getString("testid_abbr");

        String text_result = rs.getString("text_result");
        Double numeric_result = rs.getDouble("numeric_result");
        String remark = rs.getString("remark");
        if (rs.getString("runRemark") != null)
        {
            if (remark == null)
                remark = rs.getString("runRemark");
            else
                remark = remark + "\n" + rs.getString("runRemark");
        }
        Integer project = resolveProject(c, u, projectName);

        String servicename = resolveServiceName(c, u, servicename_abbr, null);
        if (servicename == null)
        {
            _log.error("Unable to resolve merge servicename: " + servicename_abbr);
            return null;
        }

        Map<String, Object> resultRow = new CaseInsensitiveHashMap<>();
        resultRow.put("Id", animalId);
        resultRow.put("date", date);
        resultRow.put("project", project);
        resultRow.put("testid", testid_abbr);
        resultRow.put("remark", remark);
        resultRow.put("units", resolveUnits(c, u, null, testid_abbr));

        resultRow.put("result", numeric_result);
        if (numeric_result == null)
        {
            resultRow.put("qualresult", text_result);
        }

        return resultRow;
    }

    private TableInfo getResultDataSet(Container c, User u, ResultSet rs, @Nullable String runId) throws SQLException
    {
        String servicename = resolveServiceName(c, u, rs.getString("servicename_abbr"), runId);
        if (servicename == null)
        {
            return null;
        }

        TableInfo resultTable = resolveServiceResultTable(c, u, servicename);
        if (resultTable == null)
        {
            _log.error("Unable to find result table matching: " + servicename);
            return null;
        }

        return resultTable;
    }

    private Date convertGMTToLocal(Date d)
    {
        if (d == null)
            return null;

        //NOTE: this is handled at the UserSchema level now
        return d;
    }

    private String createRun(Container c, User u, ResultSet mergeRunRs, String taskId, boolean hasResults) throws SQLException
    {
        Integer accessionId = mergeRunRs.getInt("accession");
        Integer panelId = mergeRunRs.getInt(("panelid"));

        String key = accessionId + "||" + panelId;
        if (_cachedRuns.containsKey(key))
        {
            return _cachedRuns.get(key);
        }

        String animalId = mergeRunRs.getString("animalId");
        Date date = convertGMTToLocal(mergeRunRs.getDate("date"));
        Integer project = resolveProject(c, u, mergeRunRs.getString("projectName"));
        String servicename = resolveServiceName(c, u, mergeRunRs.getString("servicename_abbr"), null);

        String runId = new GUID().toString();
        Map<String, Object> runRow = new CaseInsensitiveHashMap<>();
        runRow.put("Id", animalId);
        runRow.put("date", date);
        runRow.put("project", project);
        runRow.put("objectid", runId);
        runRow.put("taskid", taskId);
        runRow.put("servicerequested", servicename);
        runRow.put("QCStateLabel", hasResults ? EHRService.QCSTATES.Completed.getLabel() : EHRService.QCSTATES.RequestApproved.getLabel());

        TableInfo clinpathRuns = getClinpathRuns(c, u);
        try
        {
            _log.info("creating clinpath run for merge data: " + key);
            clinpathRuns.getUpdateService().insertRows(u, c, Arrays.asList(runRow), new BatchValidationException(), new HashMap<String, Object>());

            _cachedRuns.put(key, runId);

            return runId;
        }
        catch (BatchValidationException | DuplicateKeyException | QueryUpdateServiceException | SQLException e)
        {
            _log.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private String getRequestForOrder(Container c, User u, String objectId)
    {
        TableInfo ti = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_ORDERSSYNCED);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("objectid"), objectId);
        TableSelector ts = new TableSelector(ti, Collections.singleton("requestid"), filter, null);
        List<String> ret = ts.getArrayList(String.class);

        return ret.isEmpty() ? null : ret.get(0);
    }

    private String createOrderRecord(Container c, User u, String taskId, String runId, Integer accession, Integer panelId, Date date) throws SQLException
    {
        String objectId = new GUID().toString();
        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
        toInsert.put("resultsreceived", false);
        toInsert.put("taskid", taskId);
        toInsert.put("runid", runId);
        toInsert.put("order_accession", accession);
        toInsert.put("test_accession", panelId);
        toInsert.put("merge_datecreated", date);
        toInsert.put("merge_dateposted", new Date());
        toInsert.put("objectid", objectId);
        toInsert.put("container", c.getId());
        toInsert.put("created", new Date());
        toInsert.put("createdby", u.getUserId());
        toInsert.put("modified", new Date());
        toInsert.put("modifiedby", u.getUserId());

        _log.info("creating order record for merge data: " + accession + "/" + panelId);
        toInsert = Table.insert(u, MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_ORDERSSYNCED), toInsert);

        return objectId;
    }

    private String resolveServiceName(Container c, User u, String mergeServiceName, @Nullable String runId)
    {
        if (runId != null)
        {
            TableInfo clinpathRuns = getClinpathRuns(c, u);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("objectId"), runId);
            TableSelector ts = new TableSelector(clinpathRuns, Collections.singleton("servicerequested"), filter, null);
            List<String> ret = ts.getArrayList(String.class);
            if (ret != null && !ret.isEmpty())
            {
                return ret.get(0);
            }
        }

        String cacheKey = this.getClass().getName() + "||" + c.getId() + "||testNameMap";
        if (CacheManager.getSharedCache().get(cacheKey) == null)
        {
            TableInfo ti = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_MERGE_TO_LK_MAPPING);
            TableSelector ts = new TableSelector(ti);
            Map<String, String> ret = new HashMap<>();
            for (Map<String, Object> row : ts.getMapArray())
            {
                ret.put((String)row.get("mergetestname"), (String)row.get("servicename"));
            }

            CacheManager.getSharedCache().put(cacheKey, ret);
        }

        Map<String, String> map = (Map)CacheManager.getSharedCache().get(cacheKey);

        return map.get(mergeServiceName) == null ? mergeServiceName : map.get(mergeServiceName);
    }

    private String resolveUnits(Container c, User u, String category, String testid)
    {
        String cacheKey = this.getClass().getName() + "||" + c.getId() + "||testUnitMap";
        Map<String, Map<String, Object>> ret = (Map)CacheManager.getSharedCache().get(cacheKey);
        if (ret == null)
        {
            TableInfo ti = QueryService.get().getUserSchema(u, c, "ehr_lookups").getTable("lab_tests");
            TableSelector ts = new TableSelector(ti);
            final Map<String, Map<String, Object>> results = new HashMap<>();
            ts.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    String key = rs.getString("type") + "||" + rs.getString("testid");
                    Map<String, Object> map = new HashMap<>();
                    map.put("category", rs.getString("type"));
                    map.put("testid", rs.getString("testid"));
                    map.put("units", rs.getString("units"));

                    results.put(key, map);
                }
            });

            ret = results;
            CacheManager.getSharedCache().put(cacheKey, ret);
        }

        String key = category + "||" + testid;
        Map<String, Object> map = ret.get(key);

        return map == null ? null : (String)map.get("units");
    }

    private TableInfo resolveServiceResultTable(Container c, User u, String servicename)
    {
        if (_cachedResultTables.containsKey(servicename))
        {
            return _cachedResultTables.get(servicename);
        }

        TableInfo ti = QueryService.get().getUserSchema(u, c, "ehr_lookups").getTable("labwork_services");
        Set<FieldKey> fks = PageFlowUtil.set(FieldKey.fromString("dataset/category"));
        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, fks);
        TableSelector ts = new TableSelector(ti, cols.values(), new SimpleFilter(FieldKey.fromString("servicename"), servicename), null);
        List<String> datasetNames = ts.getArrayList(String.class);
        String datasetName = datasetNames == null || datasetNames.isEmpty() ? null : datasetNames.get(0);
        if (datasetName == null)
        {
            _cachedResultTables.put(servicename, null);
            return null;
        }

        TableInfo ret = null;
        int datasetId = StudyService.get().getDatasetIdByName(c, datasetName);
        if (datasetId > -1)
        {
            DataSet ds = StudyService.get().getDataSet(c, datasetId);
            ret = QueryService.get().getUserSchema(u, c, "study").getTable(ds.getName());
        }

        _cachedResultTables.put(servicename, ret);
        return ret;
    }

    private Integer resolveProject(Container c, User u, String projectName)
    {
        if (_cachedProjectNames.containsKey(projectName))
        {
            return _cachedProjectNames.get(projectName);
        }

        TableInfo ti = QueryService.get().getUserSchema(u, c, "ehr").getTable("project");
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("displayName"), projectName);
        TableSelector ts = new TableSelector(ti, Collections.singleton("project"), filter, null);
        List<Integer> results = ts.getArrayList(Integer.class);
        Integer ret = results.isEmpty() ? null : results.get(0);
        _cachedProjectNames.put(projectName, ret);

        return ret;
    }
}
