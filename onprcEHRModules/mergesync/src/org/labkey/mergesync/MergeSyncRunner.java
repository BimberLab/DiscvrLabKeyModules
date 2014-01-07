package org.labkey.mergesync;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ExecutingSelector;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Collection;
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

    private static final String TABLE_MERGE_ORDERS = "orders";
    private static final String TABLE_MERGE_RESULTS = "results";
    private static final String TABLE_MERGE_CONTAINERS = "containers";
    private static final String TABLE_MERGE_VISITS = "visits";
    private static final String TABLE_MERGE_PERSONNEL = "prsnl";

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

        pullFromMerge();
    }

    public void pullFromMerge()
    {
        Date lastRun = MergeSyncManager.get().getLastRun();
        _log.info("Pulling results from merge entered since: " + (lastRun == null ? "never run before" : _dateTimeFormat.format(lastRun)));

        TableInfo mergeResults = getMergeSchema().getTable(TABLE_MERGE_RESULTS);
        TableInfo mergeOrders = getMergeSchema().getTable(TABLE_MERGE_ORDERS);
        if (mergeOrders == null)
        {
            _log.warn("Unable to find merge orders table, aborting");
            return;
        }

        //determine the set of accession numbers we expect to find
        TableInfo ordersSynced = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_ORDERSSYNCED);
        TableSelector ts = new TableSelector(ordersSynced, new SimpleFilter(FieldKey.fromString("resultsreceived"), true, CompareType.NEQ), null);
        Collection<Map<String, Object>> ordersSyncedRows = ts.getMapCollection();

        Map<String, Object> accessionToObjectIdMap = new HashMap<>();
        for (Map<String, Object> orderRow : ordersSyncedRows)
        {
            //attempt to find results in merge
            String accession = (String)orderRow.get("merge_accession");
            if (accession == null)
            {
                _log.error("No accession for row with objectid: " + orderRow.get("objectid"));
                continue;
            }

            if (accessionToObjectIdMap.containsKey(accession))
            {
                _log.error("Duplicate accession numbers: " + orderRow.get("objectid"));
            }

            accessionToObjectIdMap.put(accession, orderRow.get("objectid"));

        }

        //now find merge records
        TableSelector tsOrders = new TableSelector(mergeOrders, new SimpleFilter(FieldKey.fromString("accession"), accessionToObjectIdMap, CompareType.IN), null);


        MergeSyncManager.get().setLastRun(new Date());
    }

    public void syncRequest(Container c, User u, String objectId, String Id, String date, String requestType)
    {
        _log.info("Syncing single to merge");
        MergeSyncManager.get().validateSettings();

        DbSchema mergeSchema = getMergeSchema();
        if (mergeSchema == null)
        {
            _log.error("Unable to find merge schema");
            return;
        }

//        try
//        {
//
//        }
//        catch (SQLException e)
//        {
//            _log.error(e.getMessage(), e);
//        }

        _log.info("Synced request to merge");
    }

    private DbSchema getMergeSchema()
    {
        DbScope scope = DbScope.getDbScope(MergeSyncManager.get().getDataSourceName());
        if (scope == null)
            return null;

        return scope.getSchema(MergeSyncManager.get().getSchemaName());
    }

    private Map<String, String> _testNameMap = null;

    private String resolveTestName(String testName)
    {
        if (_testNameMap == null)
        {

        }

        return _testNameMap.get(testName);
    }

    private void ensureAnimalPresent(Container c, User u, String id)
    {

    }

    private void ensureUserPresent(User u)
    {

    }
}
