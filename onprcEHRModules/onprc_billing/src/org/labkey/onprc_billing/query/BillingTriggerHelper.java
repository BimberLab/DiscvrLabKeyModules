package org.labkey.onprc_billing.query;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.onprc_billing.ONPRC_BillingSchema;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 11/26/13
 * Time: 4:07 PM
 */
public class BillingTriggerHelper
{
    private Container _container = null;
    private User _user = null;
    private static final Logger _log = Logger.getLogger(BillingTriggerHelper.class);

    public BillingTriggerHelper(int userId, String containerId)
    {
        _user = UserManager.getUser(userId);
        if (_user == null)
            throw new RuntimeException("User does not exist: " + userId);

        _container = ContainerManager.getForId(containerId);
        if (_container == null)
            throw new RuntimeException("Container does not exist: " + containerId);

    }

    private User getUser()
    {
        return _user;
    }

    private Container getContainer()
    {
        return _container;
    }

    private Date _lastInvoiceDate = null;
    private Date getLastInvoiceDate()
    {
        if (_lastInvoiceDate == null)
        {
            UserSchema us = QueryService.get().getUserSchema(getUser(), getContainer(), "onprc_billing_public");
            if (us == null)
            {
                _log.error("Unable to find onprc_billing_public schema in container: " + getContainer().getPath());
                return null;
            }

            TableInfo ti = us.getTable("publicInvoiceRuns");
            if (ti == null)
            {
                _log.error("Unable to find onprc_billing_public.publicInvoiceRuns in container: " + getContainer().getPath());
                return null;
            }

            TableSelector ts = new TableSelector(ti);
            Map<String, List<Aggregate.Result>> aggs = ts.getAggregates(Arrays.asList(new Aggregate(FieldKey.fromString("billingPeriodEnd"), Aggregate.Type.MAX)));
            for (List<Aggregate.Result> ag : aggs.values())
            {
                for (Aggregate.Result r : ag)
                {
                    if (r.getValue() instanceof Date)
                    {
                        _lastInvoiceDate = (Date)r.getValue();
                        if (_lastInvoiceDate != null)
                            _lastInvoiceDate = DateUtils.round(_lastInvoiceDate, Calendar.DATE);
                    }
                }
            }

        }

        return _lastInvoiceDate;
    }

    public boolean isBeforeLastInvoice(Date d)
    {
        Date lastInvoice = getLastInvoiceDate();
        if (lastInvoice == null)
            return false;

        Date toTest = DateUtils.round(new Date(d.getTime()), Calendar.DATE);
        return toTest.before(lastInvoice);
    }

    public void addAuditEntry(String tableName, String objectId, String msg)
    {
        BillingAuditViewFactory.addAuditEntry(getContainer(), getUser(), tableName, objectId, msg);
    }

    public void processProjectAccountChange(int project, String newAccount, String oldAccount) throws Exception
    {
        final Date curDate = DateUtils.round(new Date(), Calendar.DATE);
        //final Date prevDate = DateUtils.addDays(curDate, -1);

        //first find any records matching this project/oldAccount
        final TableInfo projAccount = DbSchema.get(ONPRC_BillingSchema.NAME).getTable(ONPRC_BillingSchema.TABLE_PROJECT_ACCOUNT_HISTORY);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("project"), project);
        filter.addCondition(FieldKey.fromString("account"), oldAccount);
        filter.addCondition(new SimpleFilter.OrClause(new CompareType.CompareClause(FieldKey.fromString("enddate"), CompareType.DATE_GTE, curDate), new CompareType.CompareClause(FieldKey.fromString("enddate"), CompareType.ISBLANK, null)));
        TableSelector ts1 = new TableSelector(projAccount, filter, null);
        long found = ts1.getRowCount();
        if (found > 0)
        {
            ts1.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    Map<String, Object> toUpdate = new CaseInsensitiveHashMap<>();
                    toUpdate.put("enddate", curDate);
                    Table.update(getUser(), projAccount, toUpdate, rs.getString("rowid"));
                }
            });
        }

        //now add the new record
        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
        toInsert.put("project", project);
        toInsert.put("account", newAccount);
        toInsert.put("startdate", curDate);
        Table.insert(getUser(), projAccount, toInsert);
    }
}
