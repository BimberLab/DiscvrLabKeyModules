package org.labkey.onprc_billing.notification;

import org.apache.commons.lang3.time.DateUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.onprc_billing.ONPRC_BillingManager;
import org.labkey.onprc_billing.ONPRC_BillingSchema;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**

 */
public class BillingValidationNotification extends FinanceNotification
{
    public BillingValidationNotification()
    {

    }

    @Override
    public String getName()
    {
        return "Billing Validation Notification";
    }

    @Override
    public String getEmailSubject(Container c)
    {
        return "Billing Validation: " + getDateTimeFormat(c).format(new Date());
    }

    @Override
    public String getCronString()
    {
        return null;
    }

    @Override
    public String getScheduleDescription()
    {
        return "on demand only";
    }

    @Override
    public String getDescription()
    {
        return "This report is designed to provide validation of previous billing runs, verifying charges entered against those expected based on the current state of the data.";
    }

    @Override
    public String getMessageBodyHTML(Container c, User u)
    {
        StringBuilder msg = new StringBuilder();

        Date now = new Date();
        msg.append(getDescription() + "  It was run on: " + getDateFormat(c).format(now) + " at " + _timeFormat.format(now) + ".<p>");

        Container financeContainer = ONPRC_BillingManager.get().getBillingContainer(c);
        if (financeContainer == null)
        {
            log.error("Finance container is not defined, so the FinanceNotification cannot run");
            return null;
        }

        Date lastInvoiceDate = getLastInvoiceDate(financeContainer, u);
        //if we have no previous value, set to an arbitrary value
        if (lastInvoiceDate == null)
            lastInvoiceDate = DateUtils.truncate(new Date(0), Calendar.DATE);

        Calendar start = Calendar.getInstance();
        start.setTime(lastInvoiceDate);
        start.add(Calendar.DATE, -90);

        Calendar endDate = Calendar.getInstance();
        endDate.setTime(lastInvoiceDate);

        msg.append(runValidation(c, u, start.getTime(), endDate.getTime()));

        return msg.toString();
    }

    public String runValidation(Container c, User u, Date start, Date endDate)
    {
        StringBuilder msg = new StringBuilder();
        Container ehrContainer = EHRService.get().getEHRStudyContainer(c);
        if (ehrContainer != null)
        {
            performCheck(ehrContainer, u, msg, start, endDate, "Lease Fee", "leaseFeeValidation", "leaseFeeValidation2");
            performCheck(ehrContainer, u, msg, start, endDate, "Labwork Fee", "labworkFeeValidation", "labworkFeeValidation2");
            performCheck(ehrContainer, u, msg, start, endDate, "Procedure Fee", "procedureFeeValidation", "procedureFeeValidation2");
            performCheck(ehrContainer, u, msg, start, endDate, "Per Diem", "perDiemFeeValidation", "perDiemFeeValidation2");
            performCheck(ehrContainer, u, msg, start, endDate, "Misc Charges", "miscChargesFeeValidation", "miscChargesFeeValidation2");
        }

        Container slaContainer = ONPRC_BillingManager.get().getSLADataFolder(c);
        if (slaContainer != null)
        {
            performCheck(slaContainer, u, msg, start, endDate, "SLA Per Diem", "slaPerDiemFeeValidation", "slaPerDiemFeeValidation2");
        }

        return msg.toString();
    }

    private void performCheck(Container financeContainer, User u, StringBuilder msg, Date startDate, Date enddate, String label, String queryName1, String queryName2)
    {
        Map<String, Object> params = new HashMap<>();
        Long numDays = ((DateUtils.truncate(enddate, Calendar.DATE).getTime() - startDate.getTime()) / DateUtils.MILLIS_PER_DAY) + 1;
        params.put("StartDate", startDate);
        params.put("EndDate", enddate);
        params.put("NumDays", numDays.intValue());

        UserSchema us = QueryService.get().getUserSchema(u, financeContainer, ONPRC_BillingSchema.NAME);
        TableInfo table1 = us.getTable(queryName1);

        SQLFragment sql = table1.getFromSQL("t");
        QueryService.get().bindNamedParameters(sql, params);
        sql = new SQLFragment("SELECT * FROM ").append(sql);
        QueryService.get().bindNamedParameters(sql, params);

        SqlSelector ss = new SqlSelector(table1.getSchema(), sql);
        long count = ss.getRowCount();
        if (count > 0)
        {
            msg.append("<b>Warning: there are " + count + " " + label + " items expected, but not present in invoiced items.</b><p>");
            String url = getExecuteQueryUrl(financeContainer, "onprc_billing", queryName1, null);
            url += "&query.param.StartDate=" + getDateFormat(financeContainer).format(startDate);
            url += "&query.param.EndDate=" + getDateFormat(financeContainer).format(enddate);
            url += "&query.param.NumDays=" + numDays.intValue();
            url += "&query.sort=-date,Id";

            msg.append("<a href='" + url + "'>Click here to view them</a>");
            msg.append("<hr>");
        }

        //then find invoiced items not expected
        TableInfo table2 = us.getTable(queryName2);
        SQLFragment sql2 = table2.getFromSQL("t");
        QueryService.get().bindNamedParameters(sql2, params);
        sql2 = new SQLFragment("SELECT * FROM ").append(sql2);
        QueryService.get().bindNamedParameters(sql2, params);

        SqlSelector ss2 = new SqlSelector(table2.getSchema(), sql2);
        long count2 = ss2.getRowCount();
        if (count2 > 0)
        {
            msg.append("<b>Warning: there are " + count2 + " " + label + " items present in invoiced items, but not expected.</b><p>");
            String url = getExecuteQueryUrl(financeContainer, "onprc_billing", queryName2, null);
            url += "&query.param.StartDate=" + getDateFormat(financeContainer).format(startDate);
            url += "&query.param.EndDate=" + getDateFormat(financeContainer).format(enddate);
            url += "&query.param.NumDays=" + numDays.intValue();
            url += "&query.sort=-date,Id";

            msg.append("<a href='" + url + "'>Click here to view them</a>");
            msg.append("<hr>");
        }
    }
}
