package org.labkey.mergesync;

import org.apache.log4j.Logger;
import org.labkey.api.cache.CacheManager;
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
        _log.info("Pulling results from merge entered since: " + (lastRun == null ? "never run before" : _dateTimeFormat.format(lastRun)));

        TableInfo mergeOrders = MergeSyncManager.get().getMergeSchema().getTable(MergeSyncManager.TABLE_MERGE_ORDERS);
        if (mergeOrders == null)
        {
            _log.warn("Unable to find merge orders table, aborting");
            return;
        }

        pullKnownResults();

        MergeSyncManager.get().setLastRun(new Date());
    }

    /**
     * Pulls results from merge for any request that originated from LK
     */
    private void pullKnownResults()
    {
        //iterate each accession number we expect to find.
        Map<String, Object> accessionToObjectIdMap = new HashMap<>();
        TableInfo ordersSynced = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_ORDERSSYNCED);
        TableSelector ts = new TableSelector(ordersSynced, PageFlowUtil.set("objectid", "order_accession", "test_accession"), new SimpleFilter(FieldKey.fromString("resultsreceived"), false, CompareType.NEQ), null);
        Collection<Map<String, Object>> ordersSyncedRows = ts.getMapCollection();
        for (Map<String, Object> orderRow : ordersSyncedRows)
        {
            //attempt to find results in merge
            Integer orderAccession = (Integer)orderRow.get("order_accession");
            if (orderAccession == null)
            {
                _log.error("No orderAccession for row with objectid: " + orderRow.get("objectid"));
                continue;
            }

            if (accessionToObjectIdMap.containsKey(orderAccession))
            {
                _log.error("Duplicate orderAccession numbers: " + orderRow.get("objectid"));
            }

            //try to find results, process if found
            SQLFragment resultSql = getResultSql(orderAccession);
            final DbScope scope = DbScope.getLabkeyScope();

            SqlSelector ss = new SqlSelector(MergeSyncManager.get().getMergeSchema().getScope(), resultSql);
            if (ss.exists())
            {
                ss.forEach(new Selector.ForEachBlock<ResultSet>()
                {
                    @Override
                    public void exec(ResultSet object) throws SQLException
                    {
                        _log.info("found!");
                        try (DbScope.Transaction transaction = scope.ensureTransaction())
                        {
                            //TODO

                            transaction.commit();
                        }
                    }
                });
            }
        }
    }

    /**
     * Pulls results from merge, even if the request did not originate from LK
     */
    private void pullUnknownResults()
    {

    }

    private String resolveTestName(Container c, String mergeServiceName)
    {
        String cacheKey = this.getClass().getName() + "||" + c.getId() + "||testNameMap";
        if (CacheManager.getSharedCache().get(cacheKey) == null)
        {
            TableInfo ti = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_TESTNAMEMAPPING);
            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
            Map<String, String> ret = new HashMap<>();
            for (Map<String, Object> row : ts.getMapArray())
            {
                ret.put((String)row.get("mergetestname"), (String)row.get("servicename"));
            }

            CacheManager.getSharedCache().put(cacheKey, ret);
        }

        Map<String, String> map = (Map)CacheManager.getSharedCache().get(cacheKey);

        return map.get(mergeServiceName);
    }

    private SQLFragment getResultSql(int orderAccession)
    {
        return new SQLFragment("");
    }
}
