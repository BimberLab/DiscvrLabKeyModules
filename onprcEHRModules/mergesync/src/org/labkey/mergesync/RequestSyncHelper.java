package org.labkey.mergesync;

import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.JobRunner;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Syncs requests made in LK (ie. records from study.clinpathRuns) to merge.
 * Primarily called through trigger scripts
 */
public class RequestSyncHelper
{
    private static final Logger _log = Logger.getLogger(RequestSyncHelper.class);

    public RequestSyncHelper()
    {

    }

    public void asyncRequest(final String containerId, final int userId, final String taskId, final String[] objectIds, final String animalId, final Date date, final String servicename)
    {
        if (!MergeSyncManager.get().isPushEnabled())
        {
            _log.info("Merge push not enabled, skipping");
            return;
        }

        JobRunner.getDefault().execute(new Runnable(){
            public void run()
            {
                doSyncRequest(containerId, userId, taskId, Arrays.asList(objectIds), animalId, date, servicename);
            }
        });
    }

    public void doSyncRequest(String containerId, int userId, String taskId, Collection<String> objectIds, String animalId, Date date, String servicename)
    {
        if (!MergeSyncManager.get().isPushEnabled())
        {
            _log.info("Merge push not enabled, skipping");
            return;
        }

        if (!shouldSyncService(servicename))
        {
            _log.info("No mapping for service: " + servicename + ", skipping");
            return;
        }

        _log.info("Syncing single request to merge: " + animalId);
        
        MergeSyncManager.get().validateSettings();

        DbSchema mergeSchema = MergeSyncManager.get().getMergeSchema();
        if (mergeSchema == null)
        {
            _log.error("Unable to find merge schema");
            return;
        }

        Container c = ContainerManager.getForId(containerId);
        if (c == null)
        {
            _log.error("Unknown container: " + containerId);
            return;
        }
        
        User u = UserManager.getUser(userId);
        if (u == null)
        {
            _log.error("Unknown user: " + userId);
            return;
        }
        
        try
        {
            Set<String> toSync = new HashSet<>();
            for (String objectId : objectIds)
            {
                if (hasBeenOrdered(c, objectId))
                {
                    _log.error("Request has already been synced: " + objectId);
                    continue;
                }
                else
                {
                    toSync.add(objectId);
                }
            }

            if (toSync.isEmpty())
            {
                _log.error("All requests have already been synced, nothing to do");
                return;
            }

            //create 1 record per batch of tests
            int patientId = createPatientIfNeeded(mergeSchema, c, u, animalId);
            int personnelId = createUserIdNeeded(mergeSchema, u);
            int visitId = createVisit(mergeSchema, u, patientId, personnelId);
            int orderId = createOrder(mergeSchema, u, patientId, personnelId, visitId, date);

            for (String objectId : objectIds)
            {
                //TODO: container?
                //TODO: test

                //insert record into orderssynced
                TableInfo ordersSynced = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_ORDERSSYNCED);
                CaseInsensitiveHashMap toInsert = new CaseInsensitiveHashMap();
                toInsert.put("objectid", objectId);
                toInsert.put("merge_accession", orderId);
                toInsert.put("container", c.getId());
                toInsert.put("createdby", u.getUserId());
                toInsert.put("created", new Date());
                toInsert.put("resultsreceived", false);
                Table.insert(u, ordersSynced, toInsert);
            }
        }
        catch (SQLException e)
        {
            _log.error(e.getMessage(), e);
        }

        _log.info("Synced request to merge");
    }

    private boolean shouldSyncService(String servicename)
    {
        TableInfo testMapping = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_TESTNAMEMAPPING);
        TableSelector ts = new TableSelector(testMapping, new SimpleFilter(FieldKey.fromString("servicename"), servicename, CompareType.EQUAL), null);

        return ts.exists();
    }

    private boolean hasBeenOrdered(Container c, String objectid) throws SQLException
    {
        TableInfo ordersSynced = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_ORDERSSYNCED);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("objectid"), objectid, CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("container"), c.getId(), CompareType.EQUAL);
        TableSelector ts = new TableSelector(ordersSynced, filter, null);

        return ts.exists();
    }
    
    private int createPatientIfNeeded(DbSchema mergeSchema, Container c, User u, String animalId) throws SQLException
    {
        TableInfo ti = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_PATIENTS);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("PT_LNAME"), animalId);
        TableSelector ts = new TableSelector(ti, Collections.singleton("PT_NUM"), filter, null);
        List<Integer> existing = ts.getArrayList(Integer.class);
        if (!existing.isEmpty())
        {
            if (existing.size() > 1)
            {
                _log.error("More than 1 matching patient found: " + animalId);
                return existing.get(0);
            }
        }

        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
        toInsert.put("PT_LNAME", animalId);
        toInsert.put("PT_FNAME", "");  //TODO: create expression
        toInsert.put("PT_SEX", "o");
        toInsert.put("PT_DOB", new Date());
        toInsert.put("PT_RACE", "ONPRC");

        Map<String, Object> inserted = Table.insert(u, ti, toInsert);

        return (Integer)inserted.get("PT_NUM");
    }

    private int createUserIdNeeded(DbSchema mergeSchema, User u)
    {
        //TODO
        return 0;
    }

    private int createVisit(DbSchema mergeSchema, User u, int patientId, int personnelId) throws SQLException
    {
        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
        toInsert.put("V_PTNUM", patientId);
        toInsert.put("V_TYPE", "O");
        toInsert.put("V_IO", "o");
        toInsert.put("V_ADMDT", new Date());
        toInsert.put("V_WARD", "ONPRC");
        toInsert.put("V_EPRSN", personnelId);
        toInsert.put("V_WORKCOMP", "N");

        //TODO: unsure what this means
        toInsert.put("V_ADTZ", 5);

        TableInfo ti = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_VISITS);
        Map<String, Object> inserted = Table.insert(u, ti, toInsert);

        return (Integer)inserted.get("V_ID");
    }

    private int createOrder(DbSchema mergeSchema, User u, int patientId, int personnelId, int visitId, Date date) throws SQLException
    {
        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
        toInsert.put("O_PTNUM", patientId);
        toInsert.put("O_VID", visitId);
        toInsert.put("O_DATE", date);
        toInsert.put("O_COLLDATE", date);

        toInsert.put("O_PRIO", "ONPRC");
        toInsert.put("O_DOCTOR", personnelId);
        toInsert.put("O_EPRSN", personnelId);
        toInsert.put("O_FASTING", "N");

        toInsert.put("O_LOC", "ONP");
        toInsert.put("O_WARD", "ONPRC");
//        O_DTZ
//        O_CLTZ
//        O_CTZ
//        O_STZ
//        O_VTZ
//        O_RCVTZ
//        O_RPTDT
//        O_RPTTZ

        TableInfo ti = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_ORDERS);
        Map<String, Object> inserted = Table.insert(u, ti, toInsert);

        return (Integer)inserted.get("O_ACCNUM");
    }
}
