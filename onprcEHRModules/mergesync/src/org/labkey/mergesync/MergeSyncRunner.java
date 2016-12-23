package org.labkey.mergesync;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
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
import java.util.HashSet;
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
    private Map<String, Map<String, Object>> _cachedRuns = new HashMap<>();
    private Map<String, String> _cachedMethods = new HashMap<>();
    private Map<String, String> _cachedTissues = new HashMap<>();

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
        if (resultTable == null)
        {
            _log.error("Unable to create merge results table");
            return;
        }

        try (ViewContext.StackResetter ignored = ViewContext.pushMockViewContext(u, c, new ActionURL("onprc_ehr", "fake.view", c)))
        {
            QueryService.get().setEnvironment(QueryService.Environment.USER, u);

            Date syncStart = new Date();
            pullResults(u, c, mergeSchema, lastRun);

            MergeSyncManager.get().setLastRun(syncStart);

            validateRuns(c, u, mergeSchema, 30, syncStart);

            if (MergeSyncManager.get().doSyncAnimalsAndProjects())
            {
                syncAnimalsToMerge(c, u, mergeSchema);
                syncProjectsToMerge(c, u, mergeSchema);
            }
        }
        finally
        {
            QueryService.get().clearEnvironment();
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

        //sync on date/time in order to avoid additional work
        SimpleFilter runFilter = new SimpleFilter(FieldKey.fromString("dateVerified"), minDate, CompareType.GTE);
        runFilter.addCondition(FieldKey.fromString("status"), "V");

        //Modified: 4-11-2016 R.Blasa allow both numeric and non-numeric ids
        //runFilter.addCondition(FieldKey.fromString("numericLastName"), true, CompareType.EQUAL);

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
                processSingleRun(c, u, mergeResultTable, runRs, false);
            }
        });
    }

    private void validateRuns(final Container c, final User u, DbSchema mergeSchema, int offset, final Date syncStart)
    {
        TableInfo runsTable = MergeSyncUserSchema.getMergeRunsTable(mergeSchema);

        Calendar minDate = Calendar.getInstance();
        minDate.setTime(new Date());
        minDate.add(Calendar.DATE, -1 * offset);

        SimpleFilter runFilter = new SimpleFilter(FieldKey.fromString("dateVerified"), minDate, CompareType.GTE);
        runFilter.addCondition(FieldKey.fromString("dateVerified"), null, CompareType.NONBLANK);
        runFilter.addCondition(FieldKey.fromString("status"), "V");

        //Removed 4-12-2016 R.Blasa  allow both nuemric and non-numeric ids
       //  runFilter.addCondition(FieldKey.fromString("numericLastName"), true, CompareType.EQUAL);

        final TableSelector runTs = new TableSelector(runsTable, runFilter, null);
        if (runTs.exists())
        {
            _log.info("verifying previous " + offset + " days of merge runs are present");
            runTs.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet mergeRunRs) throws SQLException
                {
                    Integer accession = mergeRunRs.getInt("accession");
                    Integer panelId = mergeRunRs.getInt("panelid");

                    Map<String, Object> existingRequest = getExistingRequest(c, u, accession, panelId, false);
                    if (existingRequest == null || existingRequest.get("runLsid") == null)  //proxy for whether this record existing in clinpath runs
                    {
                        if (existingRequest == null || existingRequest.get("objectid") == null)  //proxy for whether a record existing in orders synced.  this might
                        {
                            //NOTE: if the dateVerified is greater than the sync start, it was most likely verified during this sync operation and we'll pick it up during the next sync anyway
                            String msg = "merge run missing: " + accession + " / " + panelId + ".  No record of previous sync.  Date verified: " + _dateTimeFormat.format(mergeRunRs.getDate("dateVerified"));
                            if (mergeRunRs.getDate("dateVerified").getTime() <= syncStart.getTime())
                            {
                                _log.error(msg);
                            }
                            else
                            {
                                _log.warn(msg);
                            }
                            //processSingleRun(c, u, mergeResultTable, mergeRunRs, false);
                        }
                        else if (existingRequest.get("deletedate") != null)
                        {
                            //run was deleted from LabKey, ignore
                        }
                        else if (existingRequest.get("runLsid") == null)
                        {
                            _log.error("merge run missing: " + accession + " / " + panelId + ".  Record of previous sync, but no clinpathRun record.  Date verified: " + _dateTimeFormat.format(mergeRunRs.getDate("dateVerified")));
                            //processSingleRun(c, u, mergeResultTable, mergeRunRs, false);
                        }
                    }
                }
            });
        }
    }

    public void syncSingleRun(final Container c, final User u, final Integer mergeAccession, final Integer mergeTestId) throws SQLException
    {
        DbSchema mergeSchema = MergeSyncManager.get().getMergeSchema();
        if (mergeSchema == null)
            return;

        _log.info("attempting to resync single run: " + mergeAccession + " / " + mergeTestId);

        TableInfo runsTable = MergeSyncUserSchema.getMergeRunsTable(mergeSchema);
        final TableInfo mergeResultTable = MergeSyncUserSchema.getMergeDataTable(mergeSchema);

        SimpleFilter runFilter = new SimpleFilter(FieldKey.fromString("accession"), mergeAccession, CompareType.EQUAL);
        runFilter.addCondition(FieldKey.fromString("panelid"), mergeTestId);
        runFilter.addCondition(FieldKey.fromString("status"), "V");

        //Modified: 4-11-2016 R.Blasa allow both numeric and non-numeric ids
       // runFilter.addCondition(FieldKey.fromString("numericLastName"), true, CompareType.EQUAL);




        TableSelector runTs = new TableSelector(runsTable, runFilter, null);
        runTs.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet runRs) throws SQLException
            {
                //delete results if we expect automatic results
                String mergeService = runRs.getString("servicename_abbr");
                String labkeyServiceName = resolveServiceName(c, u, mergeService, null);
                if (shouldSyncResults(labkeyServiceName))
                {
                    deleteExistingResults(c, u, mergeAccession, mergeTestId);
                }
                else
                {
                    _log.info("this service type does not have automatic results, so we will not delete any existing results");
                }

                try
                {
                    Map<String, Object> existingRequest = getExistingRequest(c, u, mergeAccession, mergeTestId, false);
                    if (existingRequest != null)
                    {
                        TableSelector runsTs = new TableSelector(getClinpathRuns(c, u), PageFlowUtil.set("lsid", "Id", "project", "date"), new SimpleFilter(FieldKey.fromString("objectid"), existingRequest.get("runid")), null);
                        Map<String, Object> runRow = runsTs.getMap();
                        if (runRow != null)
                        {
                            runRow = new CaseInsensitiveHashMap<>(runRow);

                            boolean changed = false;
                            if (!runRow.get("Id").equals(runRs.getString("animalId")))
                            {
                                runRow.put("Id", runRs.getString("Id"));
                                changed = true;
                            }

                            if (!DateUtils.isSameInstant((Date)runRow.get("date"), runRs.getDate("date")))
                            {
                                runRow.put("date", runRs.getDate("date"));
                                changed = true;
                            }

                            Integer project = resolveProject(c, u, runRs.getString("projectName"));
                            if (project != null && !project.equals(runRow.get("project")))
                            {
                                runRow.put("project", project);
                                changed = true;
                            }

                            if (changed)
                            {
                                Map<String, Object> keys = new CaseInsensitiveHashMap<>();
                                keys.put("lsid", runRow.get("lsid"));
                                getClinpathRuns(c, u).getUpdateService().updateRows(u, c, Collections.singletonList(runRow), Collections.singletonList(keys), null, getExtraContext());
                            }
                        }
                    }

                    processSingleRun(c, u, mergeResultTable, runRs, true);
                }
                catch (InvalidKeyException | BatchValidationException | QueryUpdateServiceException e)
                {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        });
    }

    public void deleteExistingResults(final Container c, final User u, Integer mergeAccession, Integer mergeTestId) throws SQLException
    {
        Map<String, Object> request = getExistingRequest(c, u, mergeAccession, mergeTestId, false);
        if (request == null)
        {
            return;
        }

        String runId = (String)request.get("runid");
        if (runId == null)
        {
            return;
        }

        TableSelector ts = new TableSelector(getClinpathRuns(c, u), Collections.singleton("servicerequested"), new SimpleFilter(FieldKey.fromString("objectid"), runId), null);
        String servicename = ts.getObject(String.class);
        if (servicename == null)
        {
            return;
        }

        try
        {
            TableInfo resultTable = resolveServiceResultTable(c, u, servicename);
            if (resultTable == null)
            {
                _log.error("Unable to find result table matching: " + servicename);
                return;
            }

            List<String> lsids = new TableSelector(resultTable, Collections.singleton("lsid"), new SimpleFilter(FieldKey.fromString("runid"), runId), null).getArrayList(String.class);
            if (lsids != null && !lsids.isEmpty())
            {
                List<Map<String, Object>> toDelete = new ArrayList<>();
                for (String lsid : lsids)
                {
                    Map<String, Object> row = new CaseInsensitiveHashMap<>();
                    row.put("lsid", lsid);

                    toDelete.add(row);
                }

                List deleted = resultTable.getUpdateService().deleteRows(u, c, toDelete, null, getExtraContext());
                _log.info("pre-deleted " + deleted.size() + " rows from table: " + resultTable.getName());
            }
            else
            {
                _log.info("no existing results found, nothing to delete");
            }
        }
        catch (InvalidKeyException | BatchValidationException | QueryUpdateServiceException e)
        {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void processSingleRun(final Container c, final User u, TableInfo mergeResultTable, ResultSet mergeRunRs, final boolean forceSyncResults) throws SQLException
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
        Map<String, Object> existingRequest = getExistingRequest(c, u, accession, panelId, true);
        final String existingRunId = existingRequest == null ? null : (String)existingRequest.get("runid");
        _log.info("processing order: " + accession);
        _log.info("existingRunId: " + existingRunId);

        Set<String> completedTasks = new HashSet<>();
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

                TableInfo dataset = getResultDataset(c, u, rs.getString("servicename_abbr"), existingRunId);
                if (dataset == null)
                {
                    return; //count on getResultDataset() for error reporting
                }

                resultDatasets.put(dataset.getName(), dataset);

                List<Map<String, Object>> list = resultsToCreate.get(dataset.getName());
                if (list == null)
                    list = new ArrayList<>();

                list.add(resultRow);

                resultsToCreate.put(dataset.getName(), list);
            }
        });

        DbScope scope = DbScope.getLabKeyScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            String taskId = null;
            String runId = null;
            String runLsid = null;
            String runTaskId = null;
            String orderObjectId = null;
            if (existingRequest != null)
            {
                taskId = (String)existingRequest.get("taskid");
                runId = (String)existingRequest.get("runid");
                runLsid = (String)existingRequest.get("runLsid");
                orderObjectId = (String)existingRequest.get("objectid");
                runTaskId = (String)existingRequest.get("taskid");
                Boolean resultsReceived = (Boolean)existingRequest.get("resultsreceived");

                if (resultsReceived && !forceSyncResults)
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
                Map<String, Object> map = createRun(c, u, mergeRunRs, taskId, (expectResults && resultsToCreate.isEmpty()));
                runId = map == null ? null : (String)map.get("runid");
            }

            if (runLsid == null && runId != null)
            {
                runLsid = getRunLsid(c, u, accession, panelId, runId);
                runTaskId = getRunTaskId(c, u, accession, panelId, runId);
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

                    BatchValidationException errors = new BatchValidationException();
                    ds.getUpdateService().insertRows(u, c, resultsToCreate.get(datasetName), errors, null, getExtraContext());
                }

                //if successful, we need to mark the run as completed
                _log.info("marking clinpath run as complete: " + runId);
                Map<String, Object> toUpdate = new CaseInsensitiveHashMap<>();
                toUpdate.put("lsid", runLsid);
                toUpdate.put("QCStateLabel", EHRService.QCSTATES.Completed.getLabel());

                Map<String, Object> toUpdateKeys = new CaseInsensitiveHashMap<>();
                toUpdateKeys.put("lsid", runLsid);

                getClinpathRuns(c, u).getUpdateService().updateRows(u, c, Collections.singletonList(toUpdate), Collections.singletonList(toUpdateKeys), null, getExtraContext());
                completedTasks.add(taskId);
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
                else
                {
                    //note: use whether this record is part of a task as a proxy for whether someone has worked with it or not
                    if (runTaskId == null)
                    {
                        Map<String, Object> toUpdate = new CaseInsensitiveHashMap<>();
                        toUpdate.put("lsid", runLsid);
                        toUpdate.put("QCStateLabel", EHRService.QCSTATES.RequestSampleDelivered.getLabel());

                        Map<String, Object> toUpdateKeys = new CaseInsensitiveHashMap<>();
                        toUpdateKeys.put("lsid", runLsid);

                        _log.info("marking clinpath run as delivered: " + runId);
                        getClinpathRuns(c, u).getUpdateService().updateRows(u, c, Collections.singletonList(toUpdate), Collections.singletonList(toUpdateKeys), null, getExtraContext());
                    }
                    else
                    {
                        _log.info("run is already part of a task, will not set qcstate");
                    }
                }
            }

            for (String t : completedTasks)
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString("taskid"), t);
                filter.addCondition(FieldKey.fromString("qcstate"), EHRService.QCSTATES.Completed.getQCState(c).getRowId(), CompareType.NEQ_OR_NULL);
                TableSelector ts = new TableSelector(getClinpathRuns(c, u), filter, null);
                if (!ts.exists())
                {
                    _log.info("task has no non-completed runs, marking complete: " + t);
                    TableInfo taskTable = DbSchema.get("ehr").getTable("tasks");
                    Map<String, Object> toUpdate = new CaseInsensitiveHashMap<>();
                    toUpdate.put("taskid", t);
                    toUpdate.put("qcstate", EHRService.QCSTATES.Completed.getQCState(c).getRowId());
                    Table.update(u, taskTable, toUpdate, t);
                }
                else
                {
                    _log.info("task still has non-completed runs, will not mark complete: " + t);
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
        catch (BatchValidationException | DuplicateKeyException | InvalidKeyException | QueryUpdateServiceException e)
        {
            _log.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
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

        String taskId = new GUID().toString().toUpperCase();
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
        taskRecord.put("QCStateLabel", EHRService.QCSTATES.Completed.getLabel());

        TableInfo taskTable = DbSchema.get("ehr").getTable("tasks");

        _log.info("Creating task for merge sync: " + key);
        Table.insert(u, taskTable, taskRecord);

        _cachedTasks.put(key, taskId);

        return taskId;
    }

    private Map<String, Object> getExistingRequest(Container c, User u, Integer accession, Integer panelId, boolean validateRunId)
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

        TableSelector ts = new TableSelector(tableOrdersSynced, PageFlowUtil.set("objectid", "resultsreceived", "runid", "taskid", "deletedate"), filter, new Sort("-created"));
        List<Map> ret = ts.getArrayList(Map.class);
        if (ret.isEmpty())
            return null;

        Map<String, Object> map = new CaseInsensitiveHashMap<Object>(ret.get(0));

        //make sure this runId actually exists
        String runId = (String)map.get("runid");
        if (runId != null)
        {
            TableInfo ti = getClinpathRuns(c, u);
            TableSelector ts2 = new TableSelector(ti, PageFlowUtil.set("lsid", "qcstate", "taskid"), new SimpleFilter(FieldKey.fromString("objectid"), runId), null);
            Map<String, Object> row = ts2.getObject(Map.class);
            if (row != null)
            {
                map.put("runLsid", row.get("lsid"));
                map.put("qcstate", row.get("qcstate"));
                map.put("taskid", row.get("taskid"));

                //NOTE: only return the map if this run is really found
                return map;
            }
            else if (!validateRunId)
            {
                return map;
            }
            else
            {
                _log.error("There is a record of a synced merge request (" + accession + "), but the runId was not found: " + runId + ". the request will be recreated.");
            }
        }

        return validateRunId ? null : map;
    }

    private Map<String, Object> processResultSet(Container c, User u, ResultSet rs, @Nullable String runId) throws SQLException
    {
        String animalId = rs.getString("animalId");
        Date date = convertGMTToLocal(rs.getTimestamp("date"));
      //  _log.info("Show Lab Record date: " + date);    //Removed 3-24-2016 Blasa

        String projectName = rs.getString("projectName");
        //Date datecollected = convertGMTToLocal(rs.getDate("datecollected"));
        String servicename_abbr = rs.getString("servicename_abbr");
        String testid_abbr = rs.getString("testid_abbr");

        String text_result = rs.getString("text_result");
        Double numeric_result = rs.getDouble("numeric_result");
        numeric_result = rs.wasNull() ? null : numeric_result;

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

        //NOTE: when a result is flagged as an error, merge seems to store 0 in the numeric result, while keeping the original value as the text result
        //i dont like this behavior, but if there is a remark (a proxy for having an error), the numeric result is 0, and text result is numeric, then defer to the latter
        if (!StringUtils.isEmpty(remark) && (numeric_result == null || numeric_result == 0.0) && !StringUtils.isEmpty(text_result) && text_result.matches("[-+]?\\d*\\.?\\d+"))
        {
            _log.info("deferring to text result instead of numeric result: " + text_result);
            numeric_result = ConvertHelper.convert(text_result, Double.class);
        }

        resultRow.put("result", numeric_result);
        if (numeric_result == null)
        {
            resultRow.put("qualresult", text_result);
        }

        return resultRow;
    }

    private TableInfo getResultDataset(Container c, User u, String serviceName, @Nullable String runId) throws SQLException
    {
        String servicename = resolveServiceName(c, u, serviceName, runId);
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

    private String getRunLsid(Container c, User u, Integer accessionId, Integer panelId, String runId) throws SQLException
    {
        String key = accessionId + "||" + panelId;
        if (_cachedRuns.containsKey(key))
        {
            return (String)_cachedRuns.get(key).get("lsid");
        }

        Map<String, Object> map = cacheRun(c, u, key, runId);

        return (String)map.get("lsid");
    }

    private String getRunTaskId(Container c, User u, Integer accessionId, Integer panelId, String runId) throws SQLException
    {
        String key = accessionId + "||" + panelId;
        if (_cachedRuns.containsKey(key))
        {
            return (String)_cachedRuns.get(key).get("taskid");
        }

        Map<String, Object> map = cacheRun(c, u, key, runId);

        return (String)map.get("taskid");
    }

    private Map<String, Object> cacheRun(Container c, User u, String key, String runId)
    {
        TableInfo ti = getClinpathRuns(c, u);
        TableSelector ts = new TableSelector(ti, PageFlowUtil.set("lsid", "qcstate", "taskid"), new SimpleFilter(FieldKey.fromString("objectid"), runId), null);
        Map<String, Object> found = ts.getObject(Map.class);
        Map<String, Object> map = new CaseInsensitiveHashMap<>();
        if (found != null)
        {
            map.put("lsid", found.get("lsid"));
            map.put("runid", runId);
            map.put("qcstate", found.get("qcstate"));
            map.put("taskid", found.get("taskid"));
        }

        _cachedRuns.put(key, map);

        return map;
    }

    private Map<String, Object> createRun(Container c, User u, ResultSet mergeRunRs, String taskId, boolean hasResults) throws SQLException
    {
        Integer accessionId = mergeRunRs.getInt("accession");
        Integer panelId = mergeRunRs.getInt(("panelid"));

        String key = accessionId + "||" + panelId;
        if (_cachedRuns.containsKey(key))
        {
            return _cachedRuns.get(key);
        }

        String animalId = mergeRunRs.getString("animalId");
        Date date = convertGMTToLocal(mergeRunRs.getTimestamp("date"));
       // _log.info("Show clinPath Record date: " + date);    //Removed 3-24-2016 Blasa

        Integer project = resolveProject(c, u, mergeRunRs.getString("projectName"));
        String servicename = resolveServiceName(c, u, mergeRunRs.getString("servicename_abbr"), null);

        String runId = new GUID().toString().toUpperCase();
        Map<String, Object> runRow = new CaseInsensitiveHashMap<>();
        runRow.put("Id", animalId);
        runRow.put("date", date);
        runRow.put("project", project);
        runRow.put("objectid", runId);
        runRow.put("taskid", taskId);
        runRow.put("servicerequested", servicename);

        String tissue = resolveTissueForService(servicename, u, c);
        if (tissue != null)
            runRow.put("tissue", tissue);

        String method = resolveMethodForService(servicename, u, c);
        if (method != null)
            runRow.put("method", method);

        EHRService.QCSTATES qc = hasResults ? EHRService.QCSTATES.Completed : EHRService.QCSTATES.RequestSampleDelivered;

        runRow.put("QCStateLabel", qc.getLabel());

        TableInfo clinpathRuns = getClinpathRuns(c, u);
        try
        {
            _log.info("creating clinpath run for merge data: " + key);
            BatchValidationException errors = new BatchValidationException();
            List<Map<String, Object>> createdRunRows = clinpathRuns.getUpdateService().insertRows(u, c, Arrays.asList(runRow), errors, null, getExtraContext());
            if (errors.hasErrors())
            {
                throw errors;
            }

            if (createdRunRows == null)
            {
                throw new RuntimeException("Unable to create clinpath run record for: " + key);
            }

            if (!createdRunRows.isEmpty())
            {
                Map<String, Object> map = new CaseInsensitiveHashMap<>();
                map.put("runid", runId);
                map.put("lsid", createdRunRows.get(0).get("lsid"));
                map.put("taskid", createdRunRows.get(0).get("taskid"));
                map.put("qcstate", qc.getQCState(c).getRowId());

                _cachedRuns.put(key, map);
            }

            return _cachedRuns.get(key);
        }
        catch (BatchValidationException | DuplicateKeyException | QueryUpdateServiceException | SQLException e)
        {
            _log.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public Map<String, Object> getExtraContext()
    {
        Map<String, Object> map = new HashMap<>();
        map.put("quickValidation", true);
        map.put("generatedByServer", true);

        return map;
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
        String objectId = new GUID().toString().toUpperCase();
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
        Map<String, String> map = (Map)CacheManager.getSharedCache().get(cacheKey);
        if (map == null)
        {
            TableInfo ti = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_MERGE_TO_LK_MAPPING);
            TableSelector ts = new TableSelector(ti);
            map = new HashMap<>();
            for (Map<String, Object> row : ts.getMapArray())
            {
                map.put((String)row.get("mergetestname"), (String)row.get("servicename"));
            }

            CacheManager.getSharedCache().put(cacheKey, map);
        }

        return map.get(mergeServiceName) == null ? mergeServiceName : map.get(mergeServiceName);
    }

    private String resolveTissueForService(String servicename, User u, Container c)
    {
        if (_cachedTissues.containsKey(servicename))
        {
            return _cachedTissues.get(servicename);
        }

        TableInfo ti = QueryService.get().getUserSchema(u, c, "ehr_lookups").getTable("labwork_services");
        TableSelector ts = new TableSelector(ti, PageFlowUtil.set("tissue"), new SimpleFilter(FieldKey.fromString("servicename"), servicename), null);
        _cachedTissues.put(servicename, ts.getObject(String.class));

        return _cachedTissues.get(servicename);
    }

    private String resolveMethodForService(String servicename, User u, Container c)
    {
        if (_cachedMethods.containsKey(servicename))
        {
            return _cachedMethods.get(servicename);
        }

        TableInfo ti = QueryService.get().getUserSchema(u, c, "ehr_lookups").getTable("labwork_services");
        TableSelector ts = new TableSelector(ti, PageFlowUtil.set("method"), new SimpleFilter(FieldKey.fromString("servicename"), servicename), null);
        _cachedMethods.put(servicename, ts.getObject(String.class));

        return _cachedMethods.get(servicename);
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
            Dataset ds = StudyService.get().getDataset(c, datasetId);
            ret = QueryService.get().getUserSchema(u, c, "study").getTable(ds.getName());
        }

        _cachedResultTables.put(servicename, ret);
        return ret;
    }

    private void syncAnimalsToMerge(Container c, User u, DbSchema mergeSchema)
    {
        try
        {
            int datasetId = StudyService.get().getDatasetIdByName(c, "demographics");
            if (datasetId > -1)
            {
                Dataset ds = StudyService.get().getDataset(c, datasetId);
                String realTableName = ds.getDomain().getStorageTableName();
                TableInfo realTable = DbSchema.get("studydataset", DbSchemaType.Provisioned).getTable(realTableName);

                TableInfo patients = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_PATIENTS);


                //first find IDs we need to add.  even though the 2 DBs are on the same server instance for us, dont make that assumption and compare lists in java
                List<String> livingAnimals = new TableSelector(realTable, PageFlowUtil.set("participantid"), new SimpleFilter(FieldKey.fromString("calculated_status"), "Alive"), null).getArrayList(String.class);
                int start = 0;
                int batchSize = 500;
                while (start < livingAnimals.size())
                {
                    List<String> sublist = livingAnimals.subList(start, Math.min(livingAnimals.size(), start + batchSize));
                    start = start + batchSize;

                    List<String> idsPresent = new TableSelector(patients, PageFlowUtil.set("PT_LNAME"), new SimpleFilter(FieldKey.fromString("PT_LNAME"), sublist, CompareType.IN), null).getArrayList(String.class);
                    sublist.removeAll(idsPresent);
                    if (!sublist.isEmpty())
                    {
                        _log.info("creating " + sublist.size() + " animals in merge");
                        for (String id : sublist)
                        {
                            RequestSyncHelper.createPatient(mergeSchema, c, u, id);
                        }
                    }
                }

                //then find any IDs we need to mark as dead in merge
                List<String> deadAnimals = new TableSelector(realTable, PageFlowUtil.set("participantid"), new SimpleFilter(FieldKey.fromString("calculated_status"), "Dead", CompareType.EQUAL), null).getArrayList(String.class);
                start = 0;
                while (start < deadAnimals.size())
                {
                    List<String> sublist = deadAnimals.subList(start, Math.min(deadAnimals.size(), start + batchSize));
                    start = start + batchSize;

                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("PT_LNAME"), sublist, CompareType.IN);
                    filter.addCondition(FieldKey.fromString("PT_FNAME"), "Deceased", CompareType.DOES_NOT_CONTAIN);
                    List<String> idsToChange = new TableSelector(patients, PageFlowUtil.set("PT_NUM"), filter, null).getArrayList(String.class);
                    if (!idsToChange.isEmpty())
                    {
                        _log.info("marking " + idsToChange.size() + " animals as deceased in merge");
                        for (String ptNum : idsToChange)
                        {
                            SQLFragment sql = new SQLFragment("UPDATE dbo.patients SET pt_fname = left(rtrim(pt_fname), 5) + ' ' + '*Deceased*' WHERE PT_NUM = ?", ptNum);
                            new SqlExecutor(mergeSchema).execute(sql);
                        }
                    }
                }

                //then shipped
                List<String> shippedAnimals = new TableSelector(realTable, PageFlowUtil.set("participantid"), new SimpleFilter(FieldKey.fromString("calculated_status"), "Shipped", CompareType.EQUAL), null).getArrayList(String.class);
                start = 0;
                while (start < shippedAnimals.size())
                {
                    List<String> sublist = shippedAnimals.subList(start, Math.min(shippedAnimals.size(), start + batchSize));
                    start = start + batchSize;

                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("PT_LNAME"), sublist, CompareType.IN);
                    filter.addCondition(FieldKey.fromString("PT_FNAME"), "Sold", CompareType.DOES_NOT_CONTAIN);
                    List<String> idsToChange = new TableSelector(patients, PageFlowUtil.set("PT_NUM"), filter, null).getArrayList(String.class);
                    if (!idsToChange.isEmpty())
                    {
                        _log.info("marking " + idsToChange.size() + " animals as shipped in merge");
                        for (String ptNum : idsToChange)
                        {
                            SQLFragment sql = new SQLFragment("UPDATE dbo.patients SET pt_fname = left(rtrim(pt_fname), 5) + ' ' + '*Sold*' WHERE PT_NUM = ?", ptNum);
                            new SqlExecutor(mergeSchema).execute(sql);
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            _log.error(e.getMessage(), e);
        }
    }

    private void syncProjectsToMerge(final Container c, final User u, final DbSchema mergeSchema)
    {
        TableInfo projects = QueryService.get().getUserSchema(u, c, "ehr").getTable("project");
        TableInfo insurance = mergeSchema.getTable("insurance");

        //first create any project missing in merge
        List<String> activeProjects = new TableSelector(projects, PageFlowUtil.set("displayName"), new SimpleFilter(FieldKey.fromString("enddateCoalesced"), new Date(), CompareType.DATE_GTE), null).getArrayList(String.class);
        int start = 0;
        int batchSize = 500;
        while (start < activeProjects.size())
        {
            List<String> sublist = activeProjects.subList(start, Math.min(activeProjects.size(), start + batchSize));
            start = start + batchSize;
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("INS_NAME"), sublist, CompareType.IN);
            filter.addCondition(FieldKey.fromString("INS_TYPE"), "I"); //this will return only those active in merge

            List<String> projectsPresent = new TableSelector(insurance, PageFlowUtil.set("INS_NAME"), filter, null).getArrayList(String.class);
            sublist.removeAll(projectsPresent);
            if (!sublist.isEmpty())
            {
                _log.info("creating " + sublist.size() + " projects in merge");
                Set<FieldKey> fks = PageFlowUtil.set(FieldKey.fromString("displayName"), FieldKey.fromString("investigatorId/lastName"), FieldKey.fromString("investigatorId/firstName"), FieldKey.fromString("enddate"));
                final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(projects, fks);

                TableSelector projectTs = new TableSelector(projects, cols.values(), new SimpleFilter(FieldKey.fromString("displayName"), sublist, CompareType.IN), null);
                projectTs.forEach(new Selector.ForEachBlock<ResultSet>()
                {
                    @Override
                    public void exec(ResultSet object) throws SQLException
                    {
                        Results rs = new ResultsImpl(object, cols);
                        String projectName = rs.getString(FieldKey.fromString("displayName"));
                        String lastName = rs.getString(FieldKey.fromString("investigatorId/lastName"));
                        Date enddate = rs.getDate(FieldKey.fromString("enddate"));

                        RequestSyncHelper.createInsurance(mergeSchema, c, u, projectName, lastName, enddate);
                    }
                });
            }
        }

        //then find expired projects and update if needed
        List<String> expiredProjectNames = new TableSelector(projects, PageFlowUtil.set("displayName"), new SimpleFilter(FieldKey.fromString("enddateCoalesced"), new Date(), CompareType.DATE_LT), null).getArrayList(String.class);
        start = 0;
        while (start < expiredProjectNames.size())
        {
            List<String> sublist = expiredProjectNames.subList(start, Math.min(expiredProjectNames.size(), start + batchSize));
            start = start + batchSize;
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("INS_NAME"), sublist, CompareType.IN);
            filter.addCondition(FieldKey.fromString("INS_TYPE"), "C", CompareType.NEQ_OR_NULL);
            List<Integer> insuranceToUpdate = new TableSelector(insurance, PageFlowUtil.set("INS_INDEX"), filter, null).getArrayList(Integer.class);
            if (!insuranceToUpdate.isEmpty())
            {
                _log.info("marking " + insuranceToUpdate.size() + " projects inactive in merge");
                for (Integer index : insuranceToUpdate)
                {
                    SQLFragment sql = new SQLFragment("UPDATE dbo.insurance SET INS_TYPE = 'C', INS_ADDR1 = (INS_ADDR1 + ' ' + '(Expired)') WHERE INS_INDEX = ?", index);
                    new SqlExecutor(mergeSchema).execute(sql);
                }
            }
        }
    }

    private Integer resolveProject(Container c, User u, String projectName)
    {
        if (_cachedProjectNames.containsKey(projectName))
        {
            return _cachedProjectNames.get(projectName);
        }

        if ("CLINIC".equals(projectName) || "PATHOLOGY".equals(projectName) || "SURGERY".equals(projectName))
        {
            projectName = EHRService.get().getEHRDefaultClinicalProjectName(c);
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
