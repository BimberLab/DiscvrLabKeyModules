package org.labkey.mergesync;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ehr.EHRDemographicsService;
import org.labkey.api.ehr.demographics.AnimalRecord;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Syncs requests made in LK (ie. records from study.clinpathRuns) to merge.
 * Primarily called through trigger scripts
 */
public class RequestSyncHelper
{
    private static final Logger _log = Logger.getLogger(RequestSyncHelper.class);
    protected final static SimpleDateFormat _dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private Container _container = null;
    private User _user = null;

    public RequestSyncHelper(int userId, String containerId)
    {
        _user = UserManager.getUser(userId);
        if (_user == null)
            throw new RuntimeException("User does not exist: " + userId);

        _container = ContainerManager.getForId(containerId);
        if (_container == null)
            throw new RuntimeException("Container does not exist: " + containerId);
    }

    public void asyncRequests(final Map[] rows)
    {
        if (!MergeSyncManager.get().isPushEnabled())
        {
            _log.info("Merge push not enabled, skipping");
            return;
        }

        JobRunner.getDefault().execute(new Runnable(){
            public void run()
            {
                doSyncRequest(Arrays.asList(rows));
            }
        });
    }

    public void doSyncRequest(Collection<Map> rows)
    {
        if (!MergeSyncManager.get().isPushEnabled())
        {
            _log.info("Merge push not enabled, skipping");
            return;
        }

        MergeSyncManager.get().validateSettings();

        DbSchema mergeSchema = MergeSyncManager.get().getMergeSchema();
        if (mergeSchema == null)
        {
            _log.error("Unable to find merge schema");
            return;
        }

        Map<String, List<Map<String, Object>>> recordsToSync = new HashMap<>();
        try
        {
            for (Map row : rows)
            {
                String animalId = (String)row.get("Id");
                Date date = (Date)row.get("date");
                String servicerequested = (String)row.get("servicerequested");
                String objectId = (String)row.get("objectid");
                String taskid = (String)row.get("taskid");
                String requestid = (String)row.get("requestid");
                String key = (taskid == null ? "" : taskid) + "<>" + (requestid == null ? "" : requestid) + "<>" + animalId + "<>" + _dateFormat.format(date);

                if (servicerequested == null || !shouldSyncService(servicerequested))
                {
                    _log.info("No mapping for service: " + servicerequested + ", skipping");
                    return;
                }

                _log.info("Syncing single request to merge for animal: " + animalId);

                if (hasBeenOrdered(_container, objectId))
                {
                    _log.error("Request has already been synced: " + objectId);
                    continue;
                }

                List<Map<String, Object>> list = recordsToSync.get(key);
                if (list == null)
                    list = new ArrayList<>();

                list.add(new CaseInsensitiveHashMap<Object>(row));

                recordsToSync.put(key, list);
            }
        }
        catch (SQLException e)
        {
            _log.error(e.getMessage(), e);
        }

        if (recordsToSync.isEmpty())
        {
            return;
        }

        DbScope scope = mergeSchema.getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            _log.info("Syncing requests to merge: " + recordsToSync.keySet().size());
            for (String key : recordsToSync.keySet())
            {
                List<Map<String, Object>> records = recordsToSync.get(key);
                String[] tokens = key.split("<>");
                String taskId = StringUtils.trimToNull(tokens[0]);
                String requestId = StringUtils.trimToNull(tokens[1]);

                //create 1 record per batch of tests
                String patientId = createPatientIfNeeded(mergeSchema, _container, _user, (String)records.get(0).get("Id"));
                Integer doctorId = getMergeUserId(mergeSchema, _user, "DOC");
                Integer techId = getMergeUserId(mergeSchema, _user, "TECH");
                if (doctorId == null)
                {
                    _log.error("Unable to resolve merge user id for: " + _user.getEmail() + " with role: DOC");
                    return;
                }

                if (techId == null)
                {
                    _log.error("Unable to resolve merge user id for: " + _user.getEmail() + " with role: TECH");
                    return;
                }

                Integer insuranceId = createInsuranceIfNeeded(mergeSchema, _container, _user, (Integer)records.get(0).get("project"));
                String visitId = createVisit(mergeSchema, _user, patientId, doctorId, techId, insuranceId, (Date)records.get(0).get("date"));
                createCopyTo(mergeSchema, _user, patientId, doctorId, techId, visitId);
                int orderId = createOrder(mergeSchema, _user, patientId, doctorId, techId, visitId, (Date)records.get(0).get("date"));

                //then 1 row per service type (clinpath runs record)
                int letterIdx = 0;
                for (Map<String, Object> row : records)
                {
                    Integer mergeTestId = resolveMergeTestId(mergeSchema, (String)row.get("servicerequested"));
                    if (mergeTestId == null)
                    {
                        _log.error("Unable to find merge test name matching: " + row.get("servicerequested"));
                        continue;
                    }

                    //TODO: figure out what scheme to use to batch tests by container
                    char containerName = ALPHABET[letterIdx];
                    letterIdx++;
                    if (letterIdx > 25)
                    {
                        _log.error("More than 26 containers were used for a merge import.  Restarting at A");
                    }
                    letterIdx = letterIdx % 26;

                    int containerId = createContainer(mergeSchema, _user, orderId, mergeTestId, doctorId, techId, (Date)records.get(0).get("date"), containerName);
                    int testId = createTest(mergeSchema, _user, patientId, visitId, orderId, containerId, mergeTestId, (Date)row.get("date"), containerName);

                    //insert record into orderssynced
                    TableInfo ordersSynced = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_ORDERSSYNCED);
                    CaseInsensitiveHashMap toInsert = new CaseInsensitiveHashMap();
                    toInsert.put("objectid", new GUID().toString());
                    toInsert.put("runid", row.get("objectid"));
                    toInsert.put("order_accession", orderId);
                    toInsert.put("test_accession", testId);
                    toInsert.put("container", _container.getId());
                    toInsert.put("createdby", _user.getUserId());
                    toInsert.put("merge_datecreated", new Date());
                    toInsert.put("created", new Date());
                    toInsert.put("taskid", taskId);
                    toInsert.put("requestid", requestId);
                    toInsert.put("resultsreceived", false);
                    Table.insert(_user, ordersSynced, toInsert);
                }

                _log.info("created merge order: " + orderId + ", with " + records.size() + " tests");
            }

            transaction.commit();
            _log.info("Finished syncing requests to merge");
        }
        catch (SQLException e)
        {
            _log.error(e.getMessage(), e);
        }
    }

    private boolean shouldSyncService(String servicename)
    {
        TableInfo testMapping = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_TESTNAMEMAPPING);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("servicename"), servicename, CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("mergetestname"), null, CompareType.NONBLANK);
        TableSelector ts = new TableSelector(testMapping, filter, null);

        return ts.exists();
    }

    private Integer resolveMergeTestId(DbSchema mergeSchema, String servicename)
    {
        if (servicename == null)
            return null;

        TableInfo testMapping = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_TESTNAMEMAPPING);
        TableSelector ts = new TableSelector(testMapping, PageFlowUtil.set("mergetestname"), new SimpleFilter(FieldKey.fromString("servicename"), servicename, CompareType.EQUAL), null);
        List<String> ret = ts.getArrayList(String.class);
        if (ret.isEmpty())
            return null;

        TableInfo mergeTestNames = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_TESTINFO);
        TableSelector ts2 = new TableSelector(mergeTestNames, PageFlowUtil.set("T_TSTNUM"), new SimpleFilter(FieldKey.fromString("T_ABBR"), ret.get(0), CompareType.EQUAL), null);
        List<Integer> ret2 = ts2.getArrayList(Integer.class);

        return ret2.isEmpty() ? null : ret2.get(0);
    }

    private Map<String, Object> getMergeTestInfo(DbSchema mergeSchema, int mergeTestId)
    {
        TableInfo mergeTestNames = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_TESTINFO);
        TableSelector ts = new TableSelector(mergeTestNames, PageFlowUtil.set("T_PARTOF", "T_TYPE", "T_COLLTB"), new SimpleFilter(FieldKey.fromString("T_TSTNUM"), mergeTestId, CompareType.EQUAL), null);
        Map<String, Object> ret = ts.getMap();

        return ret;
    }

    private boolean hasBeenOrdered(Container c, String objectid) throws SQLException
    {
        TableInfo ordersSynced = MergeSyncSchema.getInstance().getSchema().getTable(MergeSyncManager.TABLE_ORDERSSYNCED);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("runid"), objectid, CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("container"), c.getId(), CompareType.EQUAL);
        TableSelector ts = new TableSelector(ordersSynced, filter, null);

        return ts.exists();
    }
    
    private String createPatientIfNeeded(DbSchema mergeSchema, Container c, User u, String animalId) throws SQLException
    {
        TableInfo ti = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_PATIENTS);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("PT_LNAME"), animalId);
        TableSelector ts = new TableSelector(ti, Collections.singleton("PT_NUM"), filter, null);
        List<String> existing = ts.getArrayList(String.class);
        if (!existing.isEmpty())
        {
            if (existing.size() > 1)
            {
                _log.error("More than 1 matching patient found: " + animalId);
            }

            return existing.get(0);
        }

        return createPatient(mergeSchema, c, u, animalId);
    }

    public static String createPatient(DbSchema mergeSchema, Container c, User u, String animalId) throws SQLException
    {
        TableInfo ti = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_PATIENTS);

        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
        toInsert.put("PT_LNAME", animalId);
        toInsert.put("PT_NUM", animalId);

        //increment index?

        AnimalRecord ar = EHRDemographicsService.get().getAnimal(c, animalId);
        if (ar != null)
        {
            toInsert.put("PT_SEX", ar.getGender() == null ? null : ar.getGender().toUpperCase());
            toInsert.put("PT_DOB", ar.getBirth());
            String species = ar.getSpecies();
            String speciesCode = null;
            if (species != null)
            {
                speciesCode = getSpeciesCode(species);
                toInsert.put("PT_RACE", speciesCode);
            }

            String firstName = speciesCode == null ? "" : "(" + speciesCode + ")";
            if (!"Alive".equals(ar.getCalculatedStatus()))
            {
                firstName += " *" + (ar.getCalculatedStatus() == null ? "Unknown" : ar.getCalculatedStatus()) + "*";
            }
            else
            {
                firstName += " -";
            }

            toInsert.put("PT_FNAME", firstName);
        }

        Map<String, Object> inserted = Table.insert(u, ti, toInsert);

        return (String)inserted.get("PT_NUM");
    }

    public static String getSpeciesCode(String species)
    {
        if (species == null)
            return null;

        //first translate project Id into the name
        TableInfo speciesTable = DbSchema.get("ehr_lookups").getTable("species");
        TableSelector ts = new TableSelector(speciesTable, PageFlowUtil.set("cites_code"), new SimpleFilter(FieldKey.fromString("common"), species), null);
        List<String> ret = ts.getArrayList(String.class);

        return ret != null && !ret.isEmpty() ? ret.get(0) : null;
    }

    private Integer createInsuranceIfNeeded(DbSchema mergeSchema, Container c, User u, Integer project) throws SQLException
    {
        if (project == null)
            return null;

        //first translate project Id into the name
        TableInfo projectTable = QueryService.get().getUserSchema(u, c, "ehr").getTable("project");
        if (projectTable == null)
        {
            _log.error("Unable to find ehr.project in container: " + _container.getPath());
            return null;
        }

        Set<FieldKey> fks = PageFlowUtil.set(FieldKey.fromString("displayName"), FieldKey.fromString("investigatorId/lastName"), FieldKey.fromString("investigatorId/firstName"), FieldKey.fromString("enddate"));
        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(projectTable, fks);

        TableSelector projectTs = new TableSelector(projectTable, cols.values(), new SimpleFilter(FieldKey.fromString("project"), project), null);
        if (!projectTs.exists())
        {
            _log.error("Unable to find project with Id: " + project + " in container: " + _container.getPath());
            return null;
        }

        try (Results rs = projectTs.getResults())
        {
            rs.next();
            String projectName = rs.getString(FieldKey.fromString("displayName"));
            String lastName = rs.getString(FieldKey.fromString("investigatorId/lastName"));
            Date enddate = rs.getDate(FieldKey.fromString("enddate"));

            TableInfo ti = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_INSURANCE);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("INS_NAME"), projectName);
            TableSelector ts = new TableSelector(ti, Collections.singleton("INS_INDEX"), filter, null);
            List<Integer> existing = ts.getArrayList(Integer.class);
            if (!existing.isEmpty())
            {
                if (existing.size() > 1)
                {
                    _log.error("More than insurance found matching project: " + project);
                }

                return existing.get(0);
            }

            return createInsurance(mergeSchema, c, u, projectName, lastName, enddate);
        }
    }

    public static Integer createInsurance(DbSchema mergeSchema, Container c, User u, String projectName, String lastName, Date enddate) throws SQLException
    {
        TableInfo ti = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_INSURANCE);
        boolean isExpired = enddate == null ? false : DateUtils.truncate(enddate, Calendar.DATE).getTime() < DateUtils.truncate(new Date(), Calendar.DATE).getTime();

        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
        toInsert.put("INS_NAME", projectName + (isExpired ? " (Expired)" : ""));
        toInsert.put("INS_ADDR1", lastName);
        toInsert.put("INS_INDEX", getAndIncrementIndex("INS_INDEX", ti));
        toInsert.put("INS_PRINT_IP", "N");
        toInsert.put("INS_PRINT_OP", "N");
        toInsert.put("INS_TYPE", (isExpired ? "C" : "I"));

        Map<String, Object> inserted = Table.insert(u, ti, toInsert);

        return (Integer)inserted.get("INS_INDEX");
    }

    private static int getAndIncrementIndex(String colName, TableInfo ti)
    {
        SqlSelector ss = new SqlSelector(ti.getSchema(), new SQLFragment("SELECT COALESCE(NX_NUM, NX_MIN) as expr FROM Nextnum WHERE nx_Fldnm = ?", colName));
        List<Integer> ret = ss.getArrayList(Integer.class);
        if (ret.isEmpty())
        {
            throw new RuntimeException("Unknown index: " + colName);
        }

        Integer i = ret.get(0);

        //increment this index
        SqlExecutor se = new SqlExecutor(ti.getSchema());
        se.execute(new SQLFragment("UPDATE Nextnum SET NX_NUM = (? + 1) WHERE nx_Fldnm = ?", i, colName));

        return i;
    }

    private Integer getMergeUserId(DbSchema mergeSchema, User u, String role)
    {
        TableInfo ti = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_PERSONNEL);
        Integer mergeUserId = null;

        //make 3 attempts to find the user.  first use login
        String displayName = u.getEmail();
        if (displayName != null && displayName.contains("@"))
        {
            displayName = displayName.split("@")[0];
        }

        if (mergeUserId == null && displayName != null)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("PR_LOGIN"), displayName, CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("PR_CLASS"), role);
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("PR_NUM"), filter, null);
            List<Integer> ret = ts.getArrayList(Integer.class);
            if (ret != null && ret.size() == 1)
            {
                mergeUserId = ret.get(0);
            }
        }

        //by convention, doctor logins append an 'x' to the end of the login
        if (mergeUserId == null && displayName != null && "DOC".equals(role))
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("PR_LOGIN"), displayName + "x", CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("PR_CLASS"), role);
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("PR_NUM"), filter, null);
            List<Integer> ret = ts.getArrayList(Integer.class);
            if (ret != null && ret.size() == 1)
            {
                mergeUserId = ret.get(0);
            }
        }

        //then first/lastname
        if (mergeUserId == null && u.getLastName() != null && u.getFirstName() != null)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("PR_FNAME"), u.getFirstName(), CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("PR_FNAME"), u.getLastName());
            filter.addCondition(FieldKey.fromString("PR_CLASS"), role);
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("PR_NUM"), filter, null);
            List<Integer> ret = ts.getArrayList(Integer.class);
            if (ret != null && ret.size() == 1)
            {
                mergeUserId = ret.get(0);
            }
        }

        if (mergeUserId != null)
        {
            return mergeUserId;
        }

        //finally default to a static username, defined in module properties
        String mergeUserName = MergeSyncManager.get().getMergeUserName();
        if (mergeUserName == null)
        {
            _log.error("Merge user not configured, cannot push requests to merge");
            return null;
        }

        //NOTE: if defaulting back to the default login, always use DOC role
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("PR_LOGIN"), mergeUserName, CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("PR_CLASS"), "DOC");
        TableSelector ts = new TableSelector(ti, PageFlowUtil.set("PR_NUM"), filter, null);
        List<Integer> ret = ts.getArrayList(Integer.class);
        
        Integer i = ret == null || ret.isEmpty() ? null : ret.get(0);
        if (i == null)
        {
            _log.error("Unable to find merge userId matching the login: " + mergeUserName + " with the role: DOC");
        }

        return i;
    }

    private void createCopyTo(DbSchema mergeSchema, User u, String patientId, int doctorId, int techId, String visitId) throws SQLException
    {
        TableInfo ti = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_COPY_TO);

        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
        toInsert.put("CO_PTNUM", patientId);
        toInsert.put("CO_VID", visitId);
        toInsert.put("CO_TYPE", "D");
        toInsert.put("CO_DOC", doctorId);

        Table.insert(u, ti, toInsert);
    }

    private String createVisit(DbSchema mergeSchema, User u, String patientId, int doctorId, int techId, Integer insuranceId, Date date) throws SQLException
    {
        TableInfo ti = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_VISITS);

        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
        toInsert.put("V_PTNUM", patientId);
        toInsert.put("V_TYPE", "O");
        toInsert.put("V_IO", "o");
        toInsert.put("V_ADMDT", convertDate(date));
        toInsert.put("V_ADTZ", getTZ(date, mergeSchema));
        toInsert.put("V_WARD", "ONPRC");
        toInsert.put("V_EPRSN", techId);
        toInsert.put("V_WORKCOMP", "N");

        //find next value in the series
        toInsert.put("V_ID", "V" + getAndIncrementIndex("V_ID", ti));

        //v_disdt, v_ht, v_wt, v_icd1, v_icd2, v_icd3, v_icd4, v_billtype, v_client  all null?


        Map<String, Object> inserted = Table.insert(u, ti, toInsert);
        String V_ID = (String)inserted.get("V_ID");

        //also create V_INS record
        TableInfo visInsTable = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_VISIT_INS);
        Map<String, Object> insToInsert = new CaseInsensitiveHashMap<>();
        insToInsert.put("vins_vid", V_ID);
        insToInsert.put("vins_ins1", insuranceId);
        Table.insert(u, visInsTable, insToInsert);

        return V_ID;
    }

    private Integer _timezone = null;

    private int getTZ(Date date, DbSchema mergeSchema)
    {
        if (_timezone == null)
        {
            TimeZone tz = Calendar.getInstance().getTimeZone();
            String abbr = tz.getDisplayName(tz.inDaylightTime(date), TimeZone.SHORT);
            TableInfo ti = mergeSchema.getTable("TIMEZONES");
            TableSelector ts = new TableSelector(ti, Collections.singleton("rowid"), new SimpleFilter(FieldKey.fromString("TZ_ABBR"), abbr), null);
            List<Integer> ret = ts.getArrayList(Integer.class);

            if (ret.isEmpty())
            {
                _log.error("Unable to find timezone matching: " + abbr);
                throw new RuntimeException("Unable to find timezone matching: " + abbr);
            }

            _timezone = ret.get(0);
        }

        return _timezone;
    }

    private Date convertDate(Date d)
    {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        TimeZone zone = Calendar.getInstance().getTimeZone();
        cal.setTimeInMillis(d.getTime() - zone.getOffset(d.getTime()));

        return cal.getTime();
    }

    private char[] ALPHABET = new char[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};

    private int createContainer(DbSchema mergeSchema, User u, int orderId, int mergeTestId, int doctorId, int techId, Date date, Character containerName) throws SQLException
    {
        TableInfo containerTable = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_CONTAINERS);
        Map<String, Object> testInfo = getMergeTestInfo(mergeSchema, mergeTestId);

        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
        toInsert.put("CNT_INDEX", getAndIncrementIndex("CNT_INDEX", containerTable));
        toInsert.put("CNT_ACCNR", orderId);

        toInsert.put("CNT_MAP", containerName.toString());
        toInsert.put("CNT_STATUS", "C");
        toInsert.put("CNT_DATE", convertDate(date));
        toInsert.put("CNT_DTZ", getTZ(date, mergeSchema));
        toInsert.put("CNT_TECH", techId);
        toInsert.put("CNT_LOC", null);
        toInsert.put("CNT_CURRLOC", "ONP");
        toInsert.put("CNT_DESTLOC", "ONP");
        toInsert.put("CNT_TUID", testInfo.get("T_COLLTB"));
        toInsert.put("CNT_LABMIC", "L");
        toInsert.put("CNT_DRAWLOC", "ONPRC");
        toInsert.put("CNT_DRAWDATE", convertDate(date));
        toInsert.put("CNT_DRTZ", getTZ(date, mergeSchema));
        toInsert.put("CNT_TYPE", "P");

        Map<String, Object> inserted = Table.insert(u, containerTable, toInsert);
        return (Integer)inserted.get("CNT_INDEX");
    }

    private int createOrder(DbSchema mergeSchema, User u, String patientId, int doctorId, int techId, String visitId, Date date) throws SQLException
    {
        TableInfo ti = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_ORDERS);

        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
        toInsert.put("O_PTNUM", patientId);
        toInsert.put("O_VID", visitId);
        toInsert.put("O_STATUS", "C");
        toInsert.put("O_DATE", convertDate(date));
        toInsert.put("O_DTZ", getTZ(date, mergeSchema));
        toInsert.put("O_COLLDT", convertDate(date));
        toInsert.put("O_CLTZ", getTZ(date, mergeSchema));
        toInsert.put("O_CDT", convertDate(date));
        toInsert.put("O_CTZ", getTZ(date, mergeSchema));
        toInsert.put("O_RCVDT", convertDate(date));
        toInsert.put("O_RCVTZ", getTZ(date, mergeSchema));
        toInsert.put("O_PRIO", 50);
        toInsert.put("O_RPTD", "N");
        toInsert.put("O_DOCTOR", doctorId);
        toInsert.put("O_EPRSN", techId);
        toInsert.put("O_FASTING", "N");
        toInsert.put("O_LOC", "ONP");
        toInsert.put("O_WARD", "ONPRC");
        toInsert.put("O_ACCNUM", getAndIncrementIndex("O_ACCNUM", ti));

        Map<String, Object> inserted = Table.insert(u, ti, toInsert);

        return (Integer)inserted.get("O_ACCNUM");
    }

    private Pair<Integer, String> getWorklistInfo(DbSchema mergeSchema, Integer mergeTestId)
    {
        SQLFragment sql = new SQLFragment("SELECT w.WK_WIDX, ws.W_DEPT FROM TESTINFO t " +
            "left join dbo.WKSTTSTS w on (w.WK_TESTN = t.T_TSTNUM) " +
            "left join WORKSTAT ws ON (w.WK_WIDX = ws.W_INDEX)" +
            "where t.T_TSTNUM = ?", mergeTestId);

        SqlSelector ss = new SqlSelector(mergeSchema, sql);
        final Pair<Integer, String> pair = Pair.of(null, null);
        ss.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                pair.first = rs.getInt("WK_WIDX");
                pair.second = rs.getString("W_DEPT");
            }
        });

        return pair;
    }

    private int createTest(DbSchema mergeSchema, User u, String patientId, String visitId, int accession, int containerId, int mergeTestId, Date date, Character containerName) throws SQLException
    {
        TableInfo ti = mergeSchema.getTable(MergeSyncManager.TABLE_MERGE_TEST);
        Map<String, Object> testInfo = getMergeTestInfo(mergeSchema, mergeTestId);
        Pair<Integer, String> worklistInfo = getWorklistInfo(mergeSchema, mergeTestId);

        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
        toInsert.put("TS_TNUM", mergeTestId);
        toInsert.put("TS_PLTNR", mergeTestId);
        toInsert.put("TS_MASTR", testInfo.get("T_PARTOF"));
        toInsert.put("TS_PTNUM", patientId);
        toInsert.put("TS_VID", visitId);
        toInsert.put("TS_ACCNR", accession);
        toInsert.put("TS_DEPT", worklistInfo.second);
        toInsert.put("TS_WKIDX", worklistInfo.first);
        toInsert.put("TS_MAP", containerName.toString());
        toInsert.put("TS_STAT", "C");
        toInsert.put("TS_PRIO", 50);
        toInsert.put("TS_EDT", convertDate(date));
        toInsert.put("TS_ETZ", getTZ(date, mergeSchema));
        toInsert.put("TS_COLDT", convertDate(date));
        toInsert.put("TS_CLTZ", getTZ(date, mergeSchema));
        toInsert.put("TS_FILLR", null);
        toInsert.put("TS_STECH", null);
        toInsert.put("TS_VTECH", null);
        toInsert.put("TS_RMTRPT", null);
        toInsert.put("TS_REFLEX", null);
        toInsert.put("TS_DBID", null);
        toInsert.put("TS_SYSTEM", null);
        toInsert.put("TS_DNLOAD", null);
        toInsert.put("TS_ICD", null);
        toInsert.put("TS_MODS", null);
        toInsert.put("TS_BILLTYPE", null);
        toInsert.put("TS_REFFLABNO", null);
        toInsert.put("TS_CHGXMT", null);
        toInsert.put("TS_INDEX", getAndIncrementIndex("TS_INDEX", ti));

        Map<String, Object> inserted = Table.insert(u, ti, toInsert);

        return (Integer)inserted.get("TS_INDEX");
    }

    public void deleteSyncRecords(String objectid)
    {
        if (objectid == null)
            return;

        SQLFragment sql = new SQLFragment("UPDATE " + MergeSyncSchema.NAME + "." + MergeSyncManager.TABLE_ORDERSSYNCED + " SET deletedate = ? WHERE runid = ?", new Date(), objectid);
        SqlExecutor se = new SqlExecutor(MergeSyncSchema.getInstance().getSchema());
        long deleted = se.execute(sql);
        _log.info("deleted " + deleted + " merge sync records following clinpath record delete");
    }
}
