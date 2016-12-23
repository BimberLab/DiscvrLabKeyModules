/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.sla.etl;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewContext.StackResetter;
import org.labkey.sla.SLAModule;
import org.labkey.sla.SLASchema;

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ETLRunnable implements Runnable
{
    public final static String TIMESTAMP_PROPERTY_DOMAIN = "org.labkey.sla.etl.timestamp";
    public final static String ROWVERSION_PROPERTY_DOMAIN = "org.labkey.sla.etl.rowversion";
    public final static String CONFIG_PROPERTY_DOMAIN = "org.labkey.sla.etl.config";
    public final static byte[] DEFAULT_VERSION = new byte[0];

    private final static Logger log = Logger.getLogger(ETLRunnable.class);
    private static final int UPSERT_BATCH_SIZE = 500;
    private boolean isRunning = false;
    private boolean shutdown;

    private Map<String, String> slaQueries;

    public ETLRunnable() throws IOException
    {
        refreshQueries();
    }

    private void refreshQueries() throws IOException
    {
        this.slaQueries = loadQueries(getResource("sla").list());
    }

    @Override
    public void run()
    {
        User user;
        Container container;
        shutdown = false;

        if (isRunning)
        {
            log.error("SLA ETL is already running, aborting");
            return;
        }
        isRunning = true;

        try
        {
            try
            {
                //always reload the queries if we're in devmode
                if (AppProps.getInstance().isDevMode())
                    refreshQueries();

                user = UserManager.getUser(new ValidEmail(getConfigProperty("labkeyUser")));
                container = getContainer();
                if (null == user)
                {
                    throw new BadConfigException("bad configuration: invalid labkey user");
                }
            }
            catch (ValidEmail.InvalidEmailException | BadConfigException | IOException e)
            {
                log.error(e.getMessage(), e);
                return;
            }

            try (StackResetter ignored = ViewContext.pushMockViewContext(user, container, new ActionURL("sla", "fake.view", container)))
            {
                log.info("Begin incremental sync from external datasource.");

                QueryService.get().setEnvironment(QueryService.Environment.USER, user);
                ETLAuditProvider.addAuditEntry(container, user, "START", "Starting SLA synchronization", 0);

                for (String tableName : slaQueries.keySet())
                {
                    long lastTs = getLastTimestamp(tableName);
                    byte[] lastRow = getLastVersion(tableName);
                    String version = (lastRow == DEFAULT_VERSION ? "never" : new String(Base64.encodeBase64(lastRow), "US-ASCII"));
                    log.info(String.format("table sla.%s last synced %s", tableName, lastTs == 0 ? "never" : new Date(lastTs).toString()));
                    log.info(String.format("table sla.%s rowversion was %s", tableName, version));
                }

                UserSchema slaSchema = QueryService.get().getUserSchema(user, container, "sla");

                try
                {
                    int slaErrors = merge(user, container, slaQueries, slaSchema);

                    truncateEtlRuns();

                    log.info("End incremental sync run.");

                    ETLAuditProvider.addAuditEntry(container, user, "FINISH", "Finishing SLA synchronization", slaErrors);
                }
                catch (BatchValidationException e)
                {
                    log.error(e.getMessage());
                    throw e;
                }
            }
            catch (Throwable x)
            {
                // Depending on the configuration of the executor,
                // if run() throws anything future executions may all canceled.
                // But we'd rather catch unexpected exceptions and continue trying,
                // to smooth over any transient issues like the remote datasource
                // being temporarily unavailable.
                log.error("Fatal incremental sync error", x);
                ETLAuditProvider.addAuditEntry(container, user, "FATAL ERROR", "Fatal error during SLA synchronization", 0);

            }
            finally
            {
                QueryService.get().clearEnvironment();
            }
        }
        finally
        {
            isRunning = false;
        }

    }

    public Container getContainer() throws BadConfigException
    {
        String path = getConfigProperty("labkeyContainer");
        Container container = ContainerManager.getForPath(path);
        if (null == container)
        {
            throw new BadConfigException("bad configuration: invalid labkey container: [" + path + "]");
        }

        return container;
    }

    private void truncateEtlRuns() throws SQLException
    {
        TableInfo ti = SLASchema.getInstance().getSchema().getTable(SLASchema.TABLE_ETL_RUNS);
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DATE, -20);

        SQLFragment sql = new SQLFragment("DELETE FROM " + ti.getSelectName() + " WHERE date < ?", cal.getTime());
        SqlExecutor ex = new SqlExecutor(ti.getSchema());
        ex.execute(sql);
    }

    private void runQueries(User user, Container container, Map<String, String> queries) throws BadConfigException, BatchValidationException
    {
        for (Map.Entry<String, String> kv : queries.entrySet())
        {
            if (isShutdown())
            {
                return;
            }

            String fileName = null;
            String sql;
            PreparedStatement s = null;

            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
            {
                fileName = kv.getKey();
                sql = kv.getValue();


                log.info("Running script " + fileName);
                String[] sqls = sql.split("GO");

                for (String script : sqls)
                {
                    s = DbScope.getLabKeyScope().getConnection().prepareStatement(script);
                    s.execute();
                }
                transaction.commit();
            }
            catch (SQLException e)
            {
                log.error("Unable to run script " + fileName);
                log.error(e.getMessage());
                continue;
            }
            finally
            {
                close(s);
            }
        }
    }

    private TableInfo getRealTable(TableInfo targetTable)
    {
        TableInfo realTable = null;
        if (targetTable instanceof FilteredTable)
        {
            DbSchema dbSchema;
            if (targetTable instanceof DatasetTable)
            {
                Domain domain = targetTable.getDomain();
                if (domain != null)
                {
                    StorageProvisioner.createTableInfo(domain);
                }
            }
            else if (targetTable.getSchema() != null)
            {
                realTable = targetTable.getSchema().getTable(targetTable.getName());
            }
        }
        return realTable;
    }

    private PreparedStatement prepareQuery(String targetTableName, String sql, Connection originConnection) throws SQLException
    {
        PreparedStatement ps = originConnection.prepareStatement(sql);

        // Each statement will have zero or more bind variables in it. Set them all to
        // the baseline timestamp date.
        int paramCount = ps.getParameterMetaData().getParameterCount();
        if (paramCount == 0)
            log.warn("Table lacks any parameters: " + targetTableName);

        byte[] fromVersion = getLastVersion(targetTableName);
        for (int i = 1; i <= paramCount; i++)
        {
            ps.setBytes(i, fromVersion);
        }

        return ps;
    }

    /** @return count of collections that encountered errors */
    private int merge(User user, Container container, Map<String, String> queries, UserSchema schema) throws BadConfigException, BatchValidationException
    {
        DbScope scope = schema.getDbSchema().getScope();
        int errorCount = 0;

        for (Map.Entry<String, String> kv : queries.entrySet())
        {
            if (isShutdown())
            {
                return errorCount;
            }

            Connection originConnection = null;
            String targetTableName = null;
            String sql;
            PreparedStatement ps = null;
            ResultSet rs = null;

            try
            {
                targetTableName = kv.getKey();
                //NOTE: in order to make scripts run in the correct order, we allow underscore prefixes on the file names
                if (targetTableName.startsWith("_"))
                    targetTableName = targetTableName.replaceFirst("_", "");

                sql = kv.getValue();

                //debug purposes only
                //sql = "SELECT TOP 200 * FROM (\n" + sql + "\n) t order by t.date desc";

                TableInfo targetTable = schema.getTable(targetTableName);
                if (targetTable == null)
                {
                    log.error(targetTableName + " is not a known labkey table name, skipping the so-named sql query");
                    errorCount++;
                    continue;
                }

                //find the physical table for deletes
                TableInfo realTable = getRealTable(targetTable);

                if (realTable == null)
                {
                    log.error("Unable to find real table for: " + targetTable.getSelectName());
                    continue;
                }

                // Are we starting with an empty collection?
                // Optimizations below if true.
                boolean isTargetEmpty = isEmpty(targetTable);

                originConnection = getOriginConnection();

                log.info("Preparing query " + targetTableName);
                ps = prepareQuery(targetTableName, sql, originConnection);

                boolean hadResultsOnStart = true;
                if (getLastVersion(targetTableName) == DEFAULT_VERSION)
                {
                    if (realTable != null)
                    {
                        if (CANNOT_TRUNCATE.contains(targetTableName))
                        {
                            log.error("Attempting to truncate " + targetTableName + ", which is not allowed.  Upstream code should be checked.");
                        }
                        else
                        {
                            log.info("Truncating target table, since last rowversion is null: " + targetTableName);
                            SQLFragment truncateSql;
                            if (realTable.getColumn("container") == null)
                            {
                                log.info("Table does not have a container col, deleting rows using truncate");
                                truncateSql = new SQLFragment("TRUNCATE TABLE " + realTable.getSelectName());
                            }
                            else
                            {
                                log.info("Table has a container col, deleting rows using delete");
                                truncateSql = new SQLFragment("DELETE FROM " + realTable.getSelectName() + " WHERE container = ?", targetTable.getUserSchema().getContainer().getId());
                            }

                            new SqlExecutor(realTable.getSchema()).execute(truncateSql);
                            hadResultsOnStart = false;
                            isTargetEmpty = true;
                        }
                    }
                    else
                    {
                        log.error("unable to truncate targetTable, since realTable is null: " + targetTableName);
                    }
                }

                Date fromDate = new Date(getLastTimestamp(targetTableName));
                log.info("querying for " + targetTableName + " since " + new Date(fromDate.getTime()));

                // get deletes from the origin.
                Set<String> deletedIds = getDeletes(originConnection, targetTableName, getLastVersion(targetTableName));

                int updates = 0;
                int currentBatch = 0;
                byte[] newBaselineVersion = getOriginDataSourceCurrentVersion();
                Long newBaselineTimestamp = getOriginDataSourceCurrentTime();
                boolean rollback = false;

                rs = ps.executeQuery();
                log.info("query " + targetTableName + " returned");

                QueryUpdateService updater = targetTable.getUpdateService();
                updater.setBulkLoad(true);
                Map<String, Object> extraContext = new HashMap<>();
                extraContext.put("dataSource", "etl");

                //NOTE: the purpose of this switch is to allow alternate keyfields, such as
                ColumnInfo filterColumn = targetTable.getColumn("objectid");
                ColumnInfo pkColumn = targetTable.getPkColumns().get(0);
                if(filterColumn == null)
                {
                    log.info("objectid column not found for table: " + targetTable.getName() + ", using " + pkColumn.getName() + " instead");
                    filterColumn = pkColumn;
                }

                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    // perform any deletes
                    if (!deletedIds.isEmpty())
                    {
                        Map<String, SQLFragment> joins = new HashMap<>();
                        filterColumn.declareJoins("t", joins);

                        // Some ehr records are transformed into multiple records en route to labkey. the multiple records have objectids that
                        // are constructed from the original objectid plus a suffix. If one of those original records gets deleted we only know the
                        // original objectid. So we need to find the child ones with a LIKE objectid% query. Which is really complicated.
                        SQLFragment like = new SQLFragment("SELECT ");
                        like.append(filterColumn.getSelectName());
                        like.append(" FROM ");
                        like.append(targetTable.getFromSQL("t"));
                        if (!joins.isEmpty())
                            like.append(joins.values().iterator().next());
                        like.append(" WHERE ");

                        SQLFragment likeWithIds = new SQLFragment(like.getSQL(), new ArrayList<>(like.getParams()));
                        int count = 0;
                        int deleted = 0;
                        for (final String deletedId : deletedIds)
                        {
                            // Do this query in batches no larger than 100 to prevent from building up too many JDBC
                            // parameters
                            likeWithIds.add(deletedId);
                            if (count > 0)
                            {
                                likeWithIds.append(" OR ");
                            }

                            if (targetTable.getSqlDialect().isPostgreSQL())
                            {
                                String delim = "||";
                                likeWithIds.append(filterColumn.getValueSql("t")).append(" LIKE ? " + delim + " '%' ");
                            }
                            else
                            {
                                String delim = "+";
                                likeWithIds.append(filterColumn.getValueSql("t")).append(" LIKE CAST((? " + delim + " '%') as nvarchar(4000)) ");
                            }

                            count++;
                            if (count > 100)
                            {
                                //if we have the DB table, just do the delete directly.
                                log.info("attempting to delete " + count + " rows from table: " + targetTableName + " based on deleted_records, using: " + filterColumn.getFieldKey().toString());
                                //log.info(StringUtils.join(likeWithIds.getParams(), ", "));

                                SimpleFilter filter = new SimpleFilter();
                                filter.addWhereClause("" + filterColumn.getSelectName() + " IN (" + likeWithIds.getSQL() + ")", likeWithIds.getParamsArray(), filterColumn.getFieldKey());
                                deleted += Table.delete(realTable, filter);

                                // Reset the count and SQL
                                likeWithIds = new SQLFragment(like.getSQL(), new ArrayList<>(like.getParams()));
                                count = 0;
                            }
                        }
                        if (hadResultsOnStart && count > 0)
                        {
                            //if we have the DB table, just do the delete directly.
                            log.info("attempting to delete " + count + " rows from table: " + targetTableName + " based on deleted_records, using: " + filterColumn.getFieldKey().toString());
                            log.info(StringUtils.join(likeWithIds.getParams(), ", "));

                            SimpleFilter filter = new SimpleFilter();
                            filter.addWhereClause("" + filterColumn.getSelectName() + " IN (" + likeWithIds.getSQL() + ")", likeWithIds.getParamsArray(), filterColumn.getFieldKey());
                            deleted += Table.delete(realTable, filter);
                        }
                        else
                        {
                            log.info("table had no previous rows, skipping pre-delete");
                        }

                        if (deleted > 0)
                            log.info("total rows deleted: " + deleted);
                        else
                            log.info("no rows were deleted");
                    }

                    List<Map<String, Object>> sourceRows = new ArrayList<>();
                    // accumulating batches of rows. would employ ResultSet.isDone to manage the last remainder
                    // batch but the MySQL jdbc driver doesn't support that method if it is a streaming result set.
                    boolean isDone = false;
                    boolean isDemographics = false;
                    List<Object> searchParams = new ArrayList<>();
                    if (targetTable instanceof DatasetTable)
                    {
                        if(((DatasetTable)targetTable).getDataset().isDemographicData())
                        {
                            log.info("table is demographics, filtering on Id");
                            isDemographics = true;
                            filterColumn = targetTable.getColumn("Id");
                        }
                    }

                    while (!isDone)
                    {
                        if (isShutdown())
                        {
                            rollback = true;
                            return errorCount;
                        }

                        isDone = !rs.next();
                        if (!isDone)
                        {
                            sourceRows.add(mapResultSetRow(rs));
                            try
                            {

                                assert filterColumn != null;
                                searchParams.add(rs.getObject(filterColumn.getColumnName()));
                            }
                            catch (SQLException e)
                            {
                                log.error("Unable to find column " + filterColumn.getColumnName() + " in ETL script for " + targetTableName);
                                throw e;
                            }
                        }
                        else
                        {
                            // avoid leaving the statement open while we process the results.
                            close(rs);
                            rs = null;
                            close(ps);
                            close(originConnection);
                        }

                        //this is performed in batches.  first we select any existing rows, then delete these.
                        //once complete, then we do an insert
                        if (sourceRows.size() == UPSERT_BATCH_SIZE || isDone)
                        {
                            if (!isTargetEmpty && searchParams.size() > 0) {
                                long start = new Date().getTime();

                                //use objectId to obtain LSIDs
                                SimpleFilter filter = new SimpleFilter(FieldKey.fromString(filterColumn.getColumnName()), searchParams, CompareType.IN);
                                Set<String> cols = new HashSet(targetTable.getPkColumnNames());
                                TableSelector ts = new TableSelector(targetTable, cols, filter, null);
                                Map<String, Object>[] rows = ts.getMapArray();

                                long duration = ((new Date()).getTime() - start) / 1000;
                                log.info("Pre-selected " + searchParams.size() + " rows for table: " + targetTable.getName() + " using column: " + filterColumn.getColumnName() + ", which took: " + duration + "s");

                                if (rows.length > 0)
                                {
                                    log.info("Preparing for insert by pre-deleting " + rows.length + " rows for table: " + targetTable.getName());
                                    start = new Date().getTime();
                                    int totalDeleted;
                                    if (realTable != null)
                                    {
                                        List<Object> pks = new ArrayList<>();
                                        for (Map<String, Object> r : rows)
                                        {
                                            pks.add(r.get(pkColumn.getName()));
                                        }

                                        ColumnInfo deleteCol = pkColumn;
                                        if (isDemographics && realTable.getColumn(deleteCol.getName()) == null)
                                        {
                                            deleteCol = realTable.getColumn("participantId");
                                        }
                                        totalDeleted = Table.delete(realTable, new SimpleFilter(deleteCol.getFieldKey(), pks, CompareType.IN));

                                        if (totalDeleted > 0 && pks.size() < 100)
                                            log.info(StringUtils.join(pks, ","));
                                    }
                                    else
                                    {
                                        log.error("Real table not found: " + targetTableName);
                                        List<Map<String, Object>> deleted = updater.deleteRows(user, container, Arrays.asList(rows), null, extraContext);
                                        totalDeleted = deleted.size();
                                    }

                                    if (totalDeleted != rows.length)
                                    {
                                        log.warn("Table: " + targetTable.getName() + " delete abnormality.  searchParams: " + searchParams.size() + ", rows: " + rows.length + ", deleted: " + totalDeleted);
                                        TableSelector ts1 = new TableSelector(targetTable, cols, filter, null);
                                        Map<String, Object>[] rows2 = ts1.getMapArray();
                                        log.info("rows: " + rows2.length);
                                    }
                                    duration = ((new Date()).getTime() - start) / 1000;
                                    log.info("Finished pre-deleting " + totalDeleted + " rows for table: " + targetTable.getName() + ", which took: " + duration + "s");
                                }
                                else
                                    log.info("No existing rows found for table: " + targetTable.getName() + ", delete not necessary");
                            }

                            long start = new Date().getTime();
                            BatchValidationException errors = new BatchValidationException();
                            updater.insertRows(user, container, sourceRows, errors, null, extraContext);
                            if (errors.hasErrors())
                            {
                                log.error("There were errors during the sync for: " + targetTableName);
                                for (ValidationException e : errors.getRowErrors())
                                {
                                    log.error(e.getMessage());
                                }
                                throw errors;
                            }
                            updates += sourceRows.size();
                            currentBatch += sourceRows.size();
                            long duration = ((new Date()).getTime() - start) / 1000;
                            log.info("Insert took: " + duration + "s");
                            log.info("Updated " + updates + " records for " + targetTableName);
                            sourceRows.clear();
                            searchParams.clear();

                            if (currentBatch >= 40000)
                            {
                                log.info("committing transaction: " + targetTableName);
                                transaction.commitAndKeepConnection();

//                                if (realTable != null)
//                                {
//                                    //NOTE: this may be less important on SQLServer
//                                    log.info("Performing Analyze");
//                                    String analyze = realTable.getSchema().getSqlDialect().getAnalyzeCommandForTable(realTable.getSchema().getName() + "." + realTable.getName());
//                                    new SqlExecutor(realTable.getSchema()).execute(new SQLFragment(analyze));
//                                }
//                                else
//                                {
//                                    log.warn("realTable is null, wont analyze");
//                                }
                                currentBatch = 0;
                            }
                        }

                    } // each record
                    transaction.commit();
                } // all records in a target
                catch (Throwable e) // comm errors and timeouts with remote, labkey exceptions
                {
                    log.error(e, e);
                    rollback = true;
                    errorCount++;
                }
                finally
                {
                    if (rollback)
                    {
                        if (originConnection != null && !originConnection.isClosed())
                            originConnection.close();

                        log.warn("closed connection and rolled back update of " + targetTableName);
                    }
                    else
                    {
                        setLastVersion(targetTableName, newBaselineVersion);
                        setLastTimestamp(targetTableName, newBaselineTimestamp);

                       log.info(MessageFormat.format("Committed updates for {0} records in {1}", updates, targetTableName));

                        try
                        {
                            TableInfo ti = SLASchema.getInstance().getSchema().getTable(SLASchema.TABLE_ETL_RUNS);
                            Map<String, Object> row = new HashMap<>();
                            row.put("date", new Date(newBaselineTimestamp));
                            row.put("queryname", targetTableName);
                            byte[] encoded = Base64.encodeBase64(newBaselineVersion);
                            row.put("rowversion", new String(encoded, "US-ASCII"));
                            row.put("container", container.getEntityId());
                            Table.insert(user, ti, row);
                        }
                        catch (UnsupportedEncodingException e)
                        {
                            log.error(e.getMessage());
                        }
                    }
                }
            }
            catch (SQLException e)
            {
                // exception connecting, preparing or executing the query to the origin db
                log.error(String.format("Error syncing '%s' - caught SQLException: %s", targetTableName, e.getMessage()), e);
                errorCount++;
            }
            finally
            {
                close(rs);
                close(ps);
                close(originConnection);
            }

        } // each target collection
        return errorCount;
    }

    /**
     *
     * @param tableName for which to get deletes
     * @param fromVersion return deletes performed after this version
     * @return
     * @throws java.sql.SQLException
     * @throws BadConfigException
     */
    Set<String> getDeletes(Connection originConnection, String tableName, byte[] fromVersion) throws SQLException, BadConfigException
    {
        PreparedStatement ps = null;
        ResultSet rs = null;
        Set<String> deletes = new HashSet<>();

        try
        {

            StringBuilder sql = new StringBuilder("SELECT objectid FROM dbo.deleted_records WHERE ts2 > ?");
            String[] sources = LK_TO_IRIS.get(tableName);
            if (sources == null || sources.length == 0)
            {
                log.error("No table mapping for: " + tableName);
                return Collections.emptySet();
            }
            else
            {
                sql.append(" AND tableName IN ('");
                String delim = "";
                for (String source : sources)
                {
                    sql.append(delim).append(source);
                    delim = "', '";
                }

                sql.append(delim).append(tableName);
                sql.append("')");
            }

            ps = originConnection.prepareStatement(sql.toString());
            ps.setBytes(1, fromVersion);

            rs = ps.executeQuery();
            while (rs.next())
            {
                deletes.add(rs.getString(1));
            }

            if (!deletes.isEmpty())
                log.info(deletes.size() + " potential deletes pending for " + tableName);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            ResultSetUtil.close(rs);
            close(ps);
        }

        return deletes;
    }

    /**
     * @return the timestamp last stored by the ETL, or 0 time if not present.  this
     * is for display only.  rowVersion is used by the DB
     */
    private long getLastTimestamp(String tableName)
    {
        Map<String, String> m = PropertyManager.getProperties(TIMESTAMP_PROPERTY_DOMAIN);
        String value = m.get(tableName);
        if (value != null)
        {
            return Long.parseLong(value);
        }

        return 0;
    }

    /**
     * @return the rowverion last stored by @setLastVersion
     */
    private byte[] getLastVersion(String tableName)
    {
        Map<String, String> m = PropertyManager.getProperties(ROWVERSION_PROPERTY_DOMAIN);
        String value = m.get(tableName);
        if (value != null)
        {
            return Base64.decodeBase64(value);
        }
        else
        {
            return DEFAULT_VERSION;
        }
    }

    boolean isEmpty(TableInfo tinfo) throws SQLException
    {
        SQLFragment sql = new SQLFragment("SELECT * FROM ").append(tinfo.getFromSQL("x"));
        return !new SqlSelector(tinfo.getSchema(), sql).exists();
    }

    /**
     * Persist the baseline timestamp to use next time we sync this table. It should be the origin db's idea of what time it is.
     *
     * @param ts the timestamp we want returned by the next call to @getLastTimestamp
     */
    private void setLastTimestamp(String tableName, Long ts)
    {
        log.info(String.format("setting new baseline timestamp of %s on collection %s", new Date(ts.longValue()).toString(), tableName));
        PropertyManager.PropertyMap pm = PropertyManager.getWritableProperties(TIMESTAMP_PROPERTY_DOMAIN, true);
        pm.put(tableName, ts.toString());
        pm.save();
    }

    /**
     * @return the current time according to the origin db.
     */
    private Long getOriginDataSourceCurrentTime() throws SQLException, BadConfigException
    {
        Connection con = null;
        ResultSet rs = null;
        Long ts = null;
        try
        {
            con = getOriginConnection();
            PreparedStatement ps = con.prepareStatement("select GETDATE()");
            rs = ps.executeQuery();
            if (rs.next())
            {
                ts = new Long(rs.getTimestamp(1).getTime());
            }
        }
        finally
        {
            ResultSetUtil.close(rs);
            close(con);
        }
        return ts;
    }

    /**
     * Persist the baseline timestamp to use next time we sync this table. It should be the origin db's idea of what time it is.
     *
     * @param version the timestamp we want returned by the next call to @getLastTimestamp
     */
    private void setLastVersion(String tableName, byte[] version)
    {
        PropertyManager.PropertyMap pm = PropertyManager.getWritableProperties(ROWVERSION_PROPERTY_DOMAIN, true);
        byte[] encoded = Base64.encodeBase64(version);
        try
        {
            String toStore = new String(encoded, "US-ASCII");
            pm.put(tableName, toStore);
            pm.save();
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the current version according to the origin db.
     */
    private byte[] getOriginDataSourceCurrentVersion() throws SQLException, BadConfigException
    {
        Connection con = null;
        byte[] version = null;
        ResultSet rs = null;
        try
        {
            con = getOriginConnection();
            PreparedStatement ps = con.prepareStatement("SELECT @@DBTS;");
            rs = ps.executeQuery();
            if (rs.next())
            {
                version = rs.getBytes(1);
            }
        }
        finally
        {
            close(con);
            ResultSetUtil.close(rs);
        }
        return version;
    }

    private Connection getOriginConnection() throws SQLException, BadConfigException
    {
        try
        {
            Class.forName(getConfigProperty("jdbcDriver")).newInstance();
        }
        catch (Throwable t)
        {
            throw new RuntimeException(t);
        }

        return DriverManager.getConnection(getConfigProperty("jdbcUrl"));
    }

    private Map<String, String> loadQueries(Collection<Resource> sqlFiles) throws IOException
    {
        Map<String, String> qMap = new TreeMap<>();

        for (Resource sqlFile : sqlFiles)
        {
            FileResource f = (FileResource)sqlFile;

            if (!f.getName().endsWith("sql")) continue;
            String sql = Files.toString(f.getFile(), Charsets.UTF_8);
            String key = f.getName().substring(0, f.getName().indexOf('.'));
            qMap.put(key, sql);
        }

        return qMap;
    }

    private Map<String, Object> mapResultSetRow(ResultSet rs) throws SQLException
    {
        Map<String, Object> map = new CaseInsensitiveHashMap<>();
        ResultSetMetaData md = rs.getMetaData();
        int columnCount = md.getColumnCount();
        for (int i = 1; i <= columnCount; i++)
        {
            // Pull out of ResultSet based on label instead of name, as the column
            // may have been aliased to match with its target in the dataset or list
            map.put(md.getColumnLabel(i), rs.getObject(i));
        }
        return map;
    }

    private MergedDirectoryResource getResource(String path)
    {
        return ((MergedDirectoryResource)ModuleLoader.getInstance().getModule(SLAModule.class).getModuleResource("/etl/" + path));
    }

    private static void close(Closeable o)
    {
        if (o != null) try
        {
            o.close();
        }
        catch (Exception ignored)
        {
        }
    }

    private static void close(Connection o)
    {
        if (o != null) try
        {
            o.close();
        }
        catch (Exception ignored)
        {
        }
    }

    private static void close(ResultSet o)
    {
        if (o != null) try
        {
            o.close();
        }
        catch (Exception ignored)
        {
            log.error("There was an error closing a result set in the ETL: " + ignored.getMessage());
        }
    }

    private static void close(PreparedStatement o)
    {
        if (o != null) try
        {
            o.close();
        }
        catch (Exception ignored)
        {
            log.error("There was an error closing a result set in the ETL: " + ignored.getMessage());
        }
    }

    public int getRunIntervalInMinutes()
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get("runIntervalInMinutes");
        return null == prop ? 0 : Integer.parseInt(prop);
    }

    private String getConfigProperty(String key) throws BadConfigException
    {
        String prop = PropertyManager.getProperties(CONFIG_PROPERTY_DOMAIN).get(key);
        if (null == prop)
            throw new BadConfigException("No " + key + " is configured");
        else
            return prop;

    }

    public boolean isShutdown()
    {
        return shutdown;
    }

    public void shutdown()
    {
        this.shutdown = true;
    }

    public boolean isRunning()
    {
        return isRunning;
    }

    public String validateEtlSync(boolean attemptRepair) throws Exception
    {
        try
        {
            User user = UserManager.getUser(new ValidEmail(getConfigProperty("labkeyUser")));
            Container container = ContainerManager.getForPath(getConfigProperty("labkeyContainer"));
            if (null == user)
            {
                throw new BadConfigException("bad configuration: invalid labkey user");
            }
            if (null == container)
            {
                throw new BadConfigException("bad configuration: invalid labkey container");
            }

            UserSchema slaSchema = QueryService.get().getUserSchema(user, container, "sla");

            StringBuilder sb = new StringBuilder();
            String result = validateEtlScript(slaQueries, slaSchema, attemptRepair);
            if (result != null)
            {
                sb.append("Validating SLA Schema:<br>");
                sb.append(result);
                sb.append("<hr>");
            }

            return sb.toString();
        }
        catch (Exception e)
        {
            log.error(e.getMessage());
        }
        catch (BadConfigException e)
        {
            log.error(e.getMessage());
        }

        return null;
    }

    public final static Set<String> CANNOT_TRUNCATE = new HashSet<String>()
    {
        {

        }
    };

    private final Map<String, String[]> LK_TO_IRIS = new HashMap<String, String[]>()
    {
        {
            put("allowableAnimals", new String[]{"IACUC_SLAYearly", "IACUC_SLAAnimals", "Ref_ProjectsIACUC"});
            put("census", new String[]{"Af_SmallLabAnimals"});
            put("purchase", new String[]{"SLA_Purchase"});
            put("purchasedetails", new String[]{"SLA_Purchasedetails"});
            put("requestors", new String[]{"SLA_Purchase"});
            put("vendors", new String[]{"Ref_SLAVendors"});

        }
    };

    private String[] TABLES_WITH_LK_ADDITIONS = new String[]{};

    private String validateEtlScript(Map<String, String> queries, UserSchema schema, boolean attemptRepair) throws BadConfigException, BatchValidationException
    {
        StringBuilder sb = new StringBuilder();
        DbScope scope = schema.getDbSchema().getScope();
        String sql = null;
        boolean hasErrors = false;

        Connection originConnection = null;
        String targetTableName = null;
        PreparedStatement ps = null;
        PreparedStatement ps2 = null;
        PreparedStatement ps3 = null;
        ResultSet rs = null;

        try
        {
            for (Map.Entry<String, String> kv : queries.entrySet())
            {
                try
                {
                    if (originConnection == null)
                        originConnection = getOriginConnection();

                    targetTableName = kv.getKey();
                    //NOTE: in order to make scripts run in the correct order, we allow underscore prefixes on the file names
                    if (targetTableName.startsWith("_"))
                        targetTableName = targetTableName.replaceFirst("_", "");

                    if (targetTableName.equals("lookups"))
                    {
                        continue;
                    }

                    TableInfo targetTable = schema.getTable(targetTableName);
                    if (targetTable == null)
                    {
                        log.error(targetTableName + " is not a known labkey table name, skipping the so-named sql query");
                        continue;
                    }

                    //find the physical table for deletes
                    TableInfo realTable = getRealTable(targetTable);
                    if (realTable == null)
                    {
                        log.error("Unable to find real table for: " + targetTable.getSelectName());
                        continue;
                    }

                    ColumnInfo filterCol = realTable.getColumn("objectid");
                    ColumnInfo pkColumn = targetTable.getPkColumns().get(0);
                    if(filterCol == null)
                    {
                        log.info("objectid column not found for table: " + targetTable.getName() + ", using " + pkColumn.getName() + " instead");
                        filterCol = pkColumn;
                    }

                    sql = kv.getValue();
                    sql = "SELECT t." + filterCol.getSelectName() + " AS col1, t.objectid as objectid, t2." + filterCol.getSelectName() + " AS col2  " +
                            "FROM (" + sql + "\n) t \n" +
                            "FULL JOIN " + scope.getDatabaseName() + "." + realTable.getSelectName() + " t2 \n" +
                            "ON (t." + filterCol.getSelectName() + " = t2." + filterCol.getSelectName() + ") \n" +
                            "WHERE (t." + filterCol.getSelectName() + " IS NULL OR t2." + filterCol.getSelectName() + " IS NULL)" +
                                (realTable.getColumn("taskid") == null ? "" : " AND t2.taskid IS NULL") +
                                (realTable.getColumn("container") == null ? "" : " AND (t2.container = '" + targetTable.getUserSchema().getContainer().getId() + "' or t2.container is null)");

                    ps = originConnection.prepareStatement(sql);
                    int paramCount = ps.getParameterMetaData().getParameterCount();
                    for (int i = 1; i <= paramCount; i++)
                    {
                        ps.setBytes(i, DEFAULT_VERSION);
                    }
                    sb.append("*************************<br>");
                    sb.append("validating ETL for table: " + targetTableName + "<br><br>");
                    rs = ps.executeQuery();
                    Set<String> missingFromLK = new HashSet<>();
                    Set<String> toDeleteFromLK = new HashSet<>();
                    while (rs.next())
                    {
                        String col1 = rs.getString("objectid");
                        if (col1 != null)
                            missingFromLK.add(col1);

                        String col2 = rs.getString("col2");
                        if (col2 != null)
                            toDeleteFromLK.add(col2);
                    }

                    ps.close();
                    rs.close();

                    boolean hasLKInserts = Arrays.asList(TABLES_WITH_LK_ADDITIONS).contains(targetTableName);
                    if ((missingFromLK.size()) > 0 || (toDeleteFromLK.size() > 0 && !hasLKInserts))
                    {
                        sb.append("table: " + targetTableName + (realTable == null ? "" : " (" + realTable.getSelectName() + ") ") + " has " + missingFromLK.size() + " records missing and " + toDeleteFromLK.size() + " to delete<br>");
                        if (missingFromLK.size() > 0)
                        {
                            sb.append("records missing:<br>");
                            List<String> toShow = new ArrayList<>();
                            toShow.addAll(missingFromLK);
                            sb.append("'" + StringUtils.join(toShow, "','") + "'").append("<br><br>");

                            if (attemptRepair)
                            {
                                String[] tables = LK_TO_IRIS.get(targetTableName);
                                if (tables != null)
                                {
                                    for (String table : tables)
                                    {
                                        //although in LK objectIds can have other info appended, we need to truncate to the original objectid for IRIS
                                        Set<String> objectIdsToAdd = new HashSet<>();
                                        for (String objectid : missingFromLK)
                                        {
                                            if (objectid.length() >= 36)
                                                objectIdsToAdd.add(objectid.substring(0, 36));
                                            else
                                                objectIdsToAdd.add(objectid);
                                        }
                                        //SQLServer will automatically bump the rowversion for us
                                        ps2 = originConnection.prepareStatement("UPDATE dbo." + table + " SET objectid = objectid WHERE objectid IN ('" + StringUtils.join(new ArrayList<>(objectIdsToAdd), "','") + "')");
                                        ps2.execute();
                                        ps2.close();
                                    }
                                }
                                else
                                {
                                    log.error("Unable to find entry in LK_TO_IRIS for table: " + targetTableName);
                                }
                            }
                        }

                        if (toDeleteFromLK.size() > 0 && !hasLKInserts)
                        {
                            sb.append("to delete from LabKey:<br>");
                            List<String> toShow = new ArrayList<>();
                            toShow.addAll(toDeleteFromLK);
                            sb.append("'" + StringUtils.join(toShow, "','") + "'").append("<br><br>");
                            if (attemptRepair)
                            {
                                //rather than delete directly, append record into deleted_records and let the next ETL handle it.
                                //this provides additional validation that the ETL is operating correctly
                                for (String key : toDeleteFromLK)
                                {
                                    key = key.substring(0, 36);
                                    ps3 = originConnection.prepareStatement("INSERT INTO dbo.deleted_records (objectid, ts, tableName) VALUES (?, ?, ?)");
                                    byte[] version = getOriginDataSourceCurrentVersion();
                                    ps3.setString(1, key);
                                    ps3.setBytes(2, version);
                                    ps3.setString(3, targetTableName);
                                    ps3.execute();
                                    ps3.close();
                                }
                            }
                        }

                        hasErrors = true;

                        close(rs);
                        close(ps);
                        close(ps2);
                        close(ps3);
                    }
                }
                catch (Exception e)
                {
                    log.error(e.getMessage(), e);
                    log.info(sql);
                }
                finally
                {
                    close(rs);
                    close(ps);
                    close(ps2);
                    close(ps3);
                }
            }
        }
        finally
        {
            close(originConnection);
            close(rs);
            close(ps);
            close(ps2);
            close(ps3);
        }

        if (hasErrors)
        {
            return sb.toString();
        }

        return null;
    }

    public class BadConfigException extends Throwable
    {
        public BadConfigException(String s)
        {
            super(s);
        }
    }
}
