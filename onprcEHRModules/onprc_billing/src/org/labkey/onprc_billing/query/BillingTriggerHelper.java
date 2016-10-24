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
import org.labkey.api.ehr.security.EHRDataAdminPermission;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.onprc_billing.ONPRC_BillingManager;
import org.labkey.onprc_billing.ONPRC_BillingSchema;
import org.labkey.onprc_billing.security.ONPRCBillingAdminPermission;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
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
            Map<String, List<Aggregate.Result>> aggs = ts.getAggregates(Arrays.asList(new Aggregate(FieldKey.fromString("billingPeriodEnd"), Aggregate.BaseType.MAX)));
            for (List<Aggregate.Result> ag : aggs.values())
            {
                for (Aggregate.Result r : ag)
                {
                    if (r.getValue() instanceof Date)
                    {
                        _lastInvoiceDate = (Date)r.getValue();
                        if (_lastInvoiceDate != null)
                            _lastInvoiceDate = DateUtils.truncate(_lastInvoiceDate, Calendar.DATE);
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

        Date toTest = DateUtils.truncate(new Date(d.getTime()), Calendar.DATE);
        return toTest.before(lastInvoice);
    }

    public void addAuditEntry(String tableName, String objectId, String msg)
    {
        BillingAuditProvider.addAuditEntry(getContainer(), getUser(), tableName, objectId, msg);
    }

    public boolean supportsCustomUnitCost(int chargeId)
    {
        Map<String, Object> row = getCharge(chargeId);
        if (row != null && row.containsKey("allowscustomunitcost") && row.get("allowscustomunitcost") != null)
        {
            return (boolean)row.get("allowscustomunitcost");
        }

        return false; //unknown charge, assume false
    }

    public boolean supportsBlankAnimal(int chargeId)
    {
        Map<String, Object> row = getCharge(chargeId);
        if (row != null && row.containsKey("allowblankid") && row.get("allowblankid") != null)
        {
            return (boolean)row.get("allowblankid");
        }

        return false; //unknown charge, assume false
    }

    private Map<Integer, Map<String, Object>> _cachedCharges = new HashMap<>();

    private Map<String, Object> getCharge(Integer chargeId)
    {
        if (_cachedCharges.containsKey(chargeId))
        {
            return _cachedCharges.get(chargeId);
        }

        Container target = ONPRC_BillingManager.get().getBillingContainer(getContainer());
        if (target != null)
        {
            TableInfo chargeableItems = DbSchema.get(ONPRC_BillingSchema.NAME).getTable(ONPRC_BillingSchema.TABLE_CHARGEABLE_ITEMS);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("rowid"), chargeId, CompareType.EQUAL);
            filter.addCondition(FieldKey.fromString("container"), target.getId(), CompareType.EQUAL);
            TableSelector ts = new TableSelector(chargeableItems, PageFlowUtil.set("rowid", "name", "allowscustomunitcost", "allowblankid", "active"), filter, null);
            Map<String, Object>[] ret = ts.getMapArray();
            if (ret != null && ret.length == 1)
            {
                _cachedCharges.put(chargeId, ret[0]);
            }
        }

        return _cachedCharges.get(chargeId);
    }

    public boolean isSiteAdmin()
    {
        return getUser().isSiteAdmin();
    }

    public boolean isBillingAdmin()
    {
        return getContainer().hasPermission(getUser(), ONPRCBillingAdminPermission.class);
    }

    public boolean isDataAdmin()
    {
        return getContainer().hasPermission(getUser(), EHRDataAdminPermission.class);
    }
}
