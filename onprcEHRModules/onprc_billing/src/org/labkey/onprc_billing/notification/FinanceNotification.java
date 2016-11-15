/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.onprc_billing.notification;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ldk.notification.AbstractNotification;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.onprc_billing.ONPRC_BillingManager;
import org.labkey.onprc_billing.ONPRC_BillingModule;
import org.labkey.onprc_billing.ONPRC_BillingSchema;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 4/5/13
 * Time: 2:25 PM
 */
public class FinanceNotification extends AbstractNotification
{
    protected static final DecimalFormat _dollarFormat = new DecimalFormat("$###,##0.00");

    public FinanceNotification()
    {
        super(ModuleLoader.getInstance().getModule(ONPRC_BillingModule.class));
    }

    @Override
    public String getName()
    {
        return "Finance Notification";
    }

    @Override
    public String getCategory()
    {
        return "Billing";
    }

    @Override
    public String getEmailSubject(Container c)
    {
        return "Finance/Billing Alerts: " + getDateTimeFormat(c).format(new Date());
    }

    @Override
    public String getCronString()
    {
        return "0 0 8 * * ?";
    }

    @Override
    public String getScheduleDescription()
    {
        return "every day at 8:00AM";
    }

    @Override
    public String getDescription()
    {
        return "This report is designed to provide a daily summary of current or projected charges since the last invoice date.  It will summarize the total dollar amount, as well as flag suspicious or incomplete items.";
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

        Date lastInvoiceDate = getLastInvoiceDate(c, u);
        //if we have no previous value, set to an arbitrary value
        if (lastInvoiceDate == null)
            lastInvoiceDate = DateUtils.truncate(new Date(0), Calendar.DATE);

        Map<String, Map<String, Map<String, Map<String, Integer>>>> dataMap = new TreeMap<>();
        Map<String, Map<String, Double>> totalsByCategory = new TreeMap<>();

        Calendar start = Calendar.getInstance();
        start.setTime(lastInvoiceDate);
        start.add(Calendar.DATE, 1);

        Calendar endDate = Calendar.getInstance();
        endDate.setTime(new Date());
        endDate.add(Calendar.DATE, 1);

        Map<String, String> categoryToQuery = new HashMap<>();
        categoryToQuery.put("Per Diems", "perDiemRates");
        categoryToQuery.put("Lease Fees", "leaseFeeRates");
        categoryToQuery.put("Procedure Charges", "procedureFeeRates");
        categoryToQuery.put("Labwork Charges", "labworkFeeRates");
        categoryToQuery.put("Other Charges", "miscChargesFeeRates");
        categoryToQuery.put("SLA Per Diems", "slaPerDiemRates");

        Map<String, Container> containerMap = new HashMap<>();
        Container slaContainer = ONPRC_BillingManager.get().getSLADataFolder(c);
        if (slaContainer != null)
        {
            getProjectSummary(slaContainer, u, start, endDate, "SLA Per Diems", categoryToQuery, dataMap, totalsByCategory);
            containerMap.put("SLA Per Diems", slaContainer);
        }

        Container ehrContainer = EHRService.get().getEHRStudyContainer(c);
        if (ehrContainer != null)
        {
            getProjectSummary(ehrContainer, u, start, endDate, "Per Diems", categoryToQuery, dataMap, totalsByCategory);
            containerMap.put("Per Diems", ehrContainer);

            getProjectSummary(ehrContainer, u, start, endDate, "Lease Fees", categoryToQuery, dataMap, totalsByCategory);
            containerMap.put("Lease Fees", ehrContainer);

            getProjectSummary(ehrContainer, u, start, endDate, "Procedure Charges", categoryToQuery, dataMap, totalsByCategory);
            containerMap.put("Procedure Charges", ehrContainer);

            getProjectSummary(ehrContainer, u, start, endDate, "Labwork Charges", categoryToQuery, dataMap, totalsByCategory);
            containerMap.put("Labwork Charges", ehrContainer);

            getProjectSummary(ehrContainer, u, start, endDate, "Other Charges", categoryToQuery, dataMap, totalsByCategory);
            containerMap.put("Other Charges", ehrContainer);
        }

        //added first due to importance
        simpleAlert(financeContainer, u , msg, "onprc_billing", "duplicateAliases", " duplicate aliases in the OGA data.  This is a potentially serious problem that could result in improper or duplicate charges.  These should be corrected ASAP, which probably requires contacting OGA to fix the data on their side.");
        simpleAlert(ehrContainer, u , msg, "onprc_billing", "invalidProjectAccountEntries", " project/alias records with invalid or overlapping intervals.  This is a potentially serious problem that could result in improper or duplicate charges.  These should be corrected ASAP.");

        writeResultTable(msg, lastInvoiceDate, start, endDate, dataMap, totalsByCategory, categoryToQuery, containerMap, c);

        getInvalidProjectAliases(ehrContainer, u , msg);
        getExpiredAliases(ehrContainer, u , msg);
        getAliasesDisabled(ehrContainer, u, msg);
        getProjectsWithoutAliases(ehrContainer, u, msg);
        projectAliasesExpiringSoon(ehrContainer, u, msg);
        getProjectsNotActive(ehrContainer, u, msg);
        getExpiredCreditAliases(ehrContainer, u, msg);
        getCreditAliasesDisabled(ehrContainer, u, msg);
        chargesMissingRates(financeContainer, u, msg);
        surgeriesNotBilled(ehrContainer, u, start, endDate, msg);
        simpleAlert(financeContainer, u , msg, "onprc_billing", "invalidChargeRateEntries", " charge rate records with invalid or overlapping intervals.  This indicates a problem with how the records are setup in the system and may cause problems with the billing calculation.");
        simpleAlert(financeContainer, u , msg, "onprc_billing", "invalidChargeRateExemptionEntries", " charge rate exemptions with invalid or overlapping intervals.  This indicates a problem with how the records are setup in the system and may cause problems with the billing calculation.");
        simpleAlert(financeContainer, u , msg, "onprc_billing", "invalidProjectMultiplierEntries", " project multipliers with invalid or overlapping intervals.  This indicates a problem with how the records are setup in the system and may cause problems with the billing calculation.");
        simpleAlert(financeContainer, u , msg, "onprc_billing", "invalidCreditAccountEntries", " credit account records with invalid or overlapping intervals.  This indicates a problem with how the records are setup in the system and may cause problems with the billing calculation.");
        simpleAlert(financeContainer, u , msg, "onprc_billing", "invalidChargeUnitAccounts", " charge unit account records with invalid or overlapping intervals.  This indicates a problem with how the records are setup in the system and may cause problems with the billing calculation.");
        simpleAlert(financeContainer, u , msg, "onprc_billing", "duplicateChargeableItems", " active chargeable item records with duplicate names or item codes.  This indicates a problem with how the records are setup in the system and may cause problems with the billing calculation.");

        return msg.toString();
    }

    protected class FieldDescriptor {
        private String _fieldName;
        private boolean _flagIfNonNull;
        private String _label;
        private boolean _shouldHighlight;

        public FieldDescriptor(String fieldName, boolean flagIfNonNull, String label, boolean shouldHighlight)
        {            
            _fieldName = fieldName;
            _flagIfNonNull = flagIfNonNull;
            _label = label;
            _shouldHighlight = shouldHighlight;
        }

        public String getFieldName()
        {
            return _fieldName;
        }

        public boolean isShouldHighlight()
        {
            return _shouldHighlight;
        }

        public FieldKey getFieldKey()
        {
            return FieldKey.fromString(_fieldName);
        }

        public String getLabel()
        {
            return _label;
        }

        public boolean shouldFlag(Results rs) throws SQLException
        {
            Object val = rs.getObject(getFieldKey());

            return _flagIfNonNull ? val != null : val == null;
        }

        public String getFilter()
        {
            return "&query." + getFieldName() + "~" + (_flagIfNonNull ? "isnonblank" : "isblank");
        }
    }

    private class MissingProjectFieldDescriptor extends FieldDescriptor
    {
        public MissingProjectFieldDescriptor()
        {
            super("project", false, "Missing Project", true);
        }

        @Override
        public boolean shouldFlag(Results rs) throws SQLException
        {
            Object project = rs.getObject(FieldKey.fromString("project"));
            Object account = rs.getObject(FieldKey.fromString("account"));

            return account == null && project == null;
        }
    }

    protected FieldDescriptor[] _fields = new FieldDescriptor[]
    {
        new MissingProjectFieldDescriptor(),
        new FieldDescriptor("isMissingAccount", true, "Missing Alias", true),
        new FieldDescriptor("isExpiredAccount", true, "Expired/Invalid Alias", true),
        new FieldDescriptor("isAcceptingCharges", true, "Alias Not Accepting Charges", true),
        new FieldDescriptor("lacksRate", true, "Lacks Rate", true),
        new FieldDescriptor("creditAccount", false, "Missing Credit Alias", true),
        new FieldDescriptor("isMissingFaid", true, "Missing FAID", true),
        new FieldDescriptor("investigatorId/lastName", false, "Missing Investigator", true),
        new FieldDescriptor("isUnknownAliasType", true, "Unknown Alias Type", true),
        new FieldDescriptor("matchesProject", true, "Project Does Not Match Assignment", false),
        //new FieldDescriptor("isMiscCharge", true, "Manually Entered", false),
        new FieldDescriptor("isAdjustment", true, "Adjustment/Reversal", false),
        new FieldDescriptor("isExemption", true, "Rate Exemption", false),
        new FieldDescriptor("isNonStandardRate", true, "Industry/Reduced F&A", false),
        new FieldDescriptor("isOldCharge", true, "Over 45 Days Old", false),
        new FieldDescriptor("isMultipleProjects", true, "Per Diems Split Between Projects", false)
    };

    private void getProjectSummary(Container c, User u, final Calendar start, Calendar endDate, final String categoryName, Map<String, String> categoryToQuery, final Map<String, Map<String, Map<String, Map<String, Integer>>>> dataMap, final Map<String, Map<String, Double>> totalsByCategory)
    {
        UserSchema us = QueryService.get().getUserSchema(u, c, ONPRC_BillingSchema.NAME);
        QueryDefinition qd = us.getQueryDefForTable(categoryToQuery.get(categoryName));
        List<QueryException> errors = new ArrayList<>();
        TableInfo ti = qd.getTable(us, errors, true);

        Map<String, Object> params = new HashMap<>();
        Long numDays = ((DateUtils.truncate(new Date(), Calendar.DATE).getTime() - start.getTimeInMillis()) / DateUtils.MILLIS_PER_DAY) + 1;
        params.put("StartDate", start.getTime());
        params.put("EndDate", endDate.getTime());
        params.put("NumDays", numDays.intValue());

        Set<FieldKey> fieldKeys = new HashSet<>();
        for (ColumnInfo col : ti.getColumns())
        {
            fieldKeys.add(col.getFieldKey());
        }

        for (FieldDescriptor fd : _fields)
        {
            fieldKeys.add(fd.getFieldKey());
        }

        fieldKeys.add(FieldKey.fromString("project/displayName"));
        fieldKeys.add(FieldKey.fromString("account"));
        fieldKeys.add(FieldKey.fromString("account/fiscalAuthority/lastName"));
        fieldKeys.add(FieldKey.fromString("account/projectNumber"));

        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, fieldKeys);
        TableSelector ts = new TableSelector(ti, cols.values(), null, null);
        ts.setNamedParameters(params);

        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet object) throws SQLException
            {
                Results rs = new ResultsImpl(object, cols);
                Map<String, Double> totalsMap = totalsByCategory.get(categoryName);
                if (totalsMap == null)
                    totalsMap = new HashMap<>();

                Double unitCost = rs.getDouble(FieldKey.fromString("unitCost"));
                Double quantity = rs.getDouble(FieldKey.fromString("quantity"));
                if (unitCost != null && quantity != null)
                {
                    Double t = totalsMap.containsKey("totalCost") ? totalsMap.get("totalCost") : 0.0;
                    t += (quantity * unitCost);
                    totalsMap.put("totalCost", t);
                }

                if (quantity != null)
                {
                    Double t = totalsMap.containsKey("total") ? totalsMap.get("total") : 0.0;
                    t += quantity;
                    totalsMap.put("total", t);
                }

                totalsByCategory.put(categoryName, totalsMap);
                
                String projectDisplay = rs.getString(FieldKey.fromString("project/displayName"));
                if (projectDisplay == null)
                {
                    projectDisplay = "None";
                }

                String financialAnalyst = rs.getString(FieldKey.fromString("account/fiscalAuthority/lastName"));
                if (financialAnalyst == null)
                {
                    financialAnalyst = "Not Assigned";
                }

                String account = rs.getString(FieldKey.fromString("account"));
                account = StringUtils.trimToNull(account);
                if (account == null)
                {
                    account = "Unknown";
                }

                String projectNumber = rs.getString(FieldKey.fromString("account/projectNumber"));
                if (projectNumber == null)
                {
                    projectNumber = "None";
                }

                String key = StringUtils.join(new String[]{financialAnalyst, projectDisplay, account, projectNumber}, "<>");

                for (FieldDescriptor fd : _fields)
                {
                    if (!rs.hasColumn(fd.getFieldKey()))
                    {
                        continue;
                    }

                    if (fd.shouldFlag(rs))
                    {
                        Map<String, Map<String, Map<String, Integer>>> valuesForFA = dataMap.get(financialAnalyst);
                        if (valuesForFA == null)
                            valuesForFA = new TreeMap<>();

                        Map<String, Map<String, Integer>> valuesForKey = valuesForFA.get(key);
                        if (valuesForKey == null)
                            valuesForKey = new TreeMap<>();

                        Map<String, Integer> values = valuesForKey.get(categoryName);
                        if (values == null)
                            values = new HashMap<>();


                        Integer count = values.containsKey(fd.getFieldName()) ? values.get(fd.getFieldName()) : 0;
                        count++;
                        values.put(fd.getFieldName(), count);

                        valuesForKey.put(categoryName, values);
                        valuesForFA.put(key, valuesForKey);
                        dataMap.put(financialAnalyst, valuesForFA);
                    }
                }
            }
        });
    }

    protected void writeResultTable(final StringBuilder msg, Date lastInvoiceEnd, Calendar start, Calendar endDate, final Map<String, Map<String, Map<String, Map<String, Integer>>>> dataMap, final Map<String, Map<String, Double>> totalsByCategory, Map<String, String> categoryToQuery, Map<String, Container> containerMap, Container c)
    {
        msg.append("<b>Charge Summary:</b><p>");
        msg.append("The table below summarizes projected charges since the since the last invoice date of " + getDateFormat(c).format(lastInvoiceEnd));

        msg.append("<table border=1 style='border-collapse: collapse;'><tr style='font-weight: bold;'><td>Category</td><td># Items</td><td>Amount</td>");
        for (String category : totalsByCategory.keySet())
        {
            Map<String, Double> totalsMap = totalsByCategory.get(category);
            Container container = containerMap.get(category);

            String url = getExecuteQueryUrl(container, ONPRC_BillingSchema.NAME, categoryToQuery.get(category), null) + "&query.param.StartDate=" + getDateFormat(c).format(start.getTime()) + "&query.param.EndDate=" + getDateFormat(c).format(endDate.getTime());
            msg.append("<tr><td><a href='" + url + "'>" + category + "</a></td><td>" + totalsMap.get("total") + "</td><td>" + _dollarFormat.format(totalsMap.get("totalCost")) + "</td></tr>");
        }
        msg.append("</table><br><br>");

        msg.append("The tables below highlight any suspicious or abnormal items, grouped by project.  These will not necessarily be problems, but may warrant investigation.<br><br>");

        for (String financialAnalyst : dataMap.keySet())
        {
            //first build header row.  we want to keep fields in the same order as _fields for consistency between tables
            Set<FieldDescriptor> foundCols = new LinkedHashSet<>();
            outerloop:
            for (FieldDescriptor fd : _fields)
            {
                for (String key : dataMap.get(financialAnalyst).keySet())
                {
                    Map<String, Map<String, Integer>> projectDataByCategory = dataMap.get(financialAnalyst).get(key);
                    for (String category : projectDataByCategory.keySet())
                    {
                        if (projectDataByCategory.get(category).containsKey(fd.getFieldName()))
                        {
                            foundCols.add(fd);
                            continue outerloop;
                        }
                    }
                }
            }

            msg.append("<table border=1 style='border-collapse: collapse;'><tr style='font-weight: bold;'><td>Financial Analyst</td><td>Project</td><td>Alias</td><td>OGA Project</td><td>Category</td>");
            for (FieldDescriptor fd : foundCols)
            {
                msg.append("<td>" + fd.getLabel() + "</td>");
            }
            msg.append("</tr>");

            //then append the rows
            for (String key : dataMap.get(financialAnalyst).keySet())
            {
                String[] tokens = key.split("<>");
                Map<String, Map<String, Integer>> dataByCategory = dataMap.get(financialAnalyst).get(key);
                for (String category : dataByCategory.keySet())
                {
                    Map<String, Integer> totals = dataByCategory.get(category);

                    String baseUrl = getExecuteQueryUrl(containerMap.get(category), ONPRC_BillingSchema.NAME, categoryToQuery.get(category), null) + "&query.param.StartDate=" + getDateFormat(c).format(start.getTime()) + "&query.param.EndDate=" + getDateFormat(c).format(endDate.getTime());
                    String projUrl = baseUrl + ("None".equals(tokens[1]) ? "&query.project/displayName~isblank" : "&query.project/displayName~eq=" + tokens[1]);
                    msg.append("<tr><td>" + financialAnalyst + "</td>");    //the FA
                    msg.append("<td><a href='" + projUrl + "'>" + tokens[1] + "</a></td>");

                    //alias
                    String accountUrl = null;
                    Container financeContainer = ONPRC_BillingManager.get().getBillingContainer(containerMap.get(category));
                    if (financeContainer != null && !"Unknown".equals((tokens[2])))
                    {
                        accountUrl = getExecuteQueryUrl(financeContainer, ONPRC_BillingSchema.NAME, "aliases", null, null) + "&query.alias~eq=" + tokens[2];
                    }

                    if (accountUrl != null)
                    {
                        msg.append("<td><a href='" + accountUrl + "'>" + tokens[2] + "</a></td>");
                    }
                    else
                    {
                        msg.append("<td>" + (tokens[2]) + "</td>");
                    }

                    msg.append("<td>" + (tokens[3]) + "</td>");
                    msg.append("<td>" + category + "</td>");

                    for (FieldDescriptor fd : foundCols)
                    {
                        if (totals.containsKey(fd.getFieldName()))
                        {
                            String url = projUrl + fd.getFilter();
                            msg.append("<td" + (fd.isShouldHighlight() ? " style='background-color: yellow;'" : "") + "><a href='" + url + "'>" + totals.get(fd.getFieldName()) + "</a></td>");
                        }
                        else
                        {
                            msg.append("<td></td>");
                        }
                    }

                    msg.append("</tr>");
                }
            }

            msg.append("</table><br><br>");
        }

        msg.append("<hr><p>");
    }

    private void surgeriesNotBilled(Container c, User u, final Calendar start, Calendar endDate, StringBuilder msg)
    {
        Map<String, Object> params = new HashMap<>();
        params.put("StartDate", start.getTime());
        params.put("EndDate", endDate.getTime());

        TableInfo ti = QueryService.get().getUserSchema(u, c, "onprc_billing").getTable("proceduresNotBilled");
        if (params != null)
        {
            SQLFragment sql = ti.getFromSQL("t");
            QueryService.get().bindNamedParameters(sql, params);
            sql = new SQLFragment("SELECT * FROM ").append(sql);
            QueryService.get().bindNamedParameters(sql, params);

            SqlSelector ss = new SqlSelector(ti.getSchema(), sql);
            long count = ss.getRowCount();

            if (count > 0)
            {
                msg.append("Note: there are " + count + " surgeries that have been performed, but will not be billed.  This is not necessarily a problem; however, if there is a procedure listed that one would expect to be charge then the procedure fee structure should be inspected.<p>");
                String url = getExecuteQueryUrl(c, "onprc_billing", "proceduresNotBilled", null);
                url += "&query.param.StartDate=" + getDateFormat(c).format(start.getTime());
                url += "&query.param.EndDate=" + getDateFormat(c).format(endDate.getTime());

                msg.append("<a href='" + url + "'>Click here to view them</a>");
                msg.append("<hr>");
            }
        }
    }

    private void chargesMissingRates(Container c, User u, StringBuilder msg)
    {
        Map<String, Object> params = Collections.singletonMap("date", new Date());
        TableInfo ti = QueryService.get().getUserSchema(u, c, "onprc_billing").getTable("chargesMissingRate");
        if (params != null)
        {
            SQLFragment sql = ti.getFromSQL("t");
            QueryService.get().bindNamedParameters(sql, params);
            sql = new SQLFragment("SELECT * FROM ").append(sql);
            QueryService.get().bindNamedParameters(sql, params);

            SqlSelector ss = new SqlSelector(ti.getSchema(), sql);
            long count = ss.getRowCount();

            if (count > 0)
            {
                msg.append("<b>Warning: there are " + count + " active charge items missing either a default rate or a default credit alias.  This may cause problems with the billing calculation.</b><p>");
                String url = getExecuteQueryUrl(c, "onprc_billing", "chargesMissingRate", null);
                url += "&query.param.Date=" + getDateFormat(c).format(new Date());

                msg.append("<a href='" + url + "'>Click here to view them</a>");
                msg.append("<hr>");
            }
        }
    }

    private void projectAliasesExpiringSoon(Container ehrContainer, User u, StringBuilder msg)
    {
        TableInfo ti = QueryService.get().getUserSchema(u, ehrContainer, "ehr").getTable("project");
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("enddateCoalesced"), "-0d", CompareType.DATE_GTE);
        filter.addCondition(FieldKey.fromString("account/budgetEndDate"), "+30d", CompareType.DATE_LTE);

        TableSelector ts = new TableSelector(ti, filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("Note: there are " + count + " active ONPRC projects with aliases where the budget period will expire in the next 30 days.<p>");
            msg.append("<a href='" + getExecuteQueryUrl(ehrContainer, "ehr", "project", "Alias Info") + "&" + filter.toQueryString("query") + "'>Click here to view them</a>");
            msg.append("<hr>");
        }

        SimpleFilter filter2 = new SimpleFilter(FieldKey.fromString("enddateCoalesced"), "-0d", CompareType.DATE_GTE);
        filter2.addCondition(FieldKey.fromString("maxAliasEnd"), "+30d", CompareType.DATE_LTE);

        TableSelector ts2 = new TableSelector(ti, filter2, null);
        long count2 = ts2.getRowCount();
        if (count2 > 0)
        {
            msg.append("Note: there are " + count2 + " active ONPRC projects where the associated alias will end in the next 30 days.<p>");
            msg.append("<a href='" + getExecuteQueryUrl(ehrContainer, "ehr", "project", "Alias Info") + "&" + filter2.toQueryString("query") + "'>Click here to view them</a>");
            msg.append("<hr>");
        }
    }

    private void simpleAlert(Container c, User u, StringBuilder msg, String schemaName, String queryName, String message)
    {
        TableInfo ti = QueryService.get().getUserSchema(u, c, schemaName).getTable(queryName);
        TableSelector ts = new TableSelector(ti);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>Warning: there are " + count + " " + message + "</b><p>");
            msg.append("<a href='" + getExecuteQueryUrl(c, schemaName, queryName, null) + "'>Click here to view them</a>");
            msg.append("<hr>");
        }
    }

    protected Date getLastInvoiceDate(Container c, User u)
    {
        Container financeContainer = ONPRC_BillingManager.get().getBillingContainer(c);
        if (financeContainer == null)
        {
            return null;
        }

        TableInfo ti = QueryService.get().getUserSchema(u, financeContainer, ONPRC_BillingSchema.NAME).getTable(ONPRC_BillingSchema.TABLE_INVOICE_RUNS);
        TableSelector ts = new TableSelector(ti);
        Map<String, List<Aggregate.Result>> aggs = ts.getAggregates(Collections.singletonList(new Aggregate(FieldKey.fromString("billingPeriodEnd"), Aggregate.BaseType.MAX)));
        for (List<Aggregate.Result> ag : aggs.values())
        {
            for (Aggregate.Result r : ag)
            {
                if (r.getValue() instanceof Date)
                {
                    return r.getValue() == null ? null : DateUtils.truncate((Date)r.getValue(), Calendar.DATE);
                }
            }
        }

        return null;
    }

    private void getAliasesDisabled(Container c, User u, StringBuilder msg)
    {
        if (QueryService.get().getUserSchema(u, c, "onprc_billing_public") == null)
        {
            msg.append("<b>Warning: the ONPRC billing schema has not been enabled in this folder, so the expired alias alert cannot run<p><hr>");
            return;
        }

        TableInfo ti = QueryService.get().getUserSchema(u, c, "ehr").getTable("project");
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("enddateCoalesced"), "-0d", CompareType.DATE_GTE);
        filter.addCondition(FieldKey.fromString("account/aliasEnabled"), "Y", CompareType.NEQ_OR_NULL);
        filter.addCondition(FieldKey.fromString("account"), null, CompareType.NONBLANK);
        TableSelector ts = new TableSelector(ti, filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>Warning: there are " + count + " active ONPRC projects with aliases that are not accepting charges.</b><p>");
            msg.append("<a href='" + getExecuteQueryUrl(c, "ehr", "project", "Alias Info") + "&" + filter.toQueryString("query") + "'>Click here to view them</a>");
            msg.append("<hr>");
        }
    }

    private void getInvalidProjectAliases(Container c, User u, StringBuilder msg)
    {
        TableInfo ti = QueryService.get().getUserSchema(u, c, "ehr").getTable("project");
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("account"), ",", CompareType.CONTAINS);
        TableSelector ts = new TableSelector(ti, filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>Warning: there are " + count + " ONPRC projects with duplicate active aliases.</b><p>");
            msg.append("<a href='" + getExecuteQueryUrl(c, "ehr", "project", "Alias Info") + "&" + filter.toQueryString("query") + "'>Click here to view them</a>");
            msg.append("<hr>");
        }

    }

    private void getExpiredAliases(Container c, User u, StringBuilder msg)
    {
        if (QueryService.get().getUserSchema(u, c, "onprc_billing_public") == null)
        {
            msg.append("<b>Warning: the ONPRC billing schema has not been enabled in this folder, so the expired alias alert cannot run<p><hr>");
            return;
        }

        TableInfo ti = QueryService.get().getUserSchema(u, c, "ehr").getTable("project");
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("enddateCoalesced"), "-0d", CompareType.DATE_GTE);
        filter.addCondition(FieldKey.fromString("account/budgetEndDateCoalesced"), "-0d", CompareType.DATE_LT);
        filter.addCondition(FieldKey.fromString("account"), null, CompareType.NONBLANK);
        TableSelector ts = new TableSelector(ti, filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>Warning: there are " + count + " active ONPRC projects with aliases that have an expired budget period.</b><p>");
            msg.append("<a href='" + getExecuteQueryUrl(c, "ehr", "project", "Alias Info") + "&" + filter.toQueryString("query") + "'>Click here to view them</a>");
            msg.append("<hr>");
        }
    }

    private void getCreditAliasesDisabled(Container c, User u, StringBuilder msg)
    {
        if (QueryService.get().getUserSchema(u, c, "onprc_billing_public") == null)
        {
            msg.append("<b>Warning: the ONPRC billing schema has not been enabled in this folder, so the expired alias alert cannot run<p><hr>");
            return;
        }

        TableInfo ti = QueryService.get().getUserSchema(u, c, "onprc_billing_public").getTable("creditAccount");
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("chargeId/active"), true, CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("account/aliasEnabled"), "Y", CompareType.NEQ_OR_NULL);
        filter.addCondition(FieldKey.fromString("account"), null, CompareType.NONBLANK);
        filter.addCondition(FieldKey.fromString("account"), "-1", CompareType.NEQ_OR_NULL);
        filter.addCondition(FieldKey.fromString("enddateCoalesced"), new Date(), CompareType.DATE_GTE);
        TableSelector ts = new TableSelector(ti, filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>Warning: there are " + count + " active chargeable items with credit aliases that are not accepting charges.</b><p>");
            msg.append("<a href='" + getExecuteQueryUrl(c, "onprc_billing_public", "creditAccount", null) + "&" + filter.toQueryString("query") + "'>Click here to view them</a>");
            msg.append("<hr>");
        }
    }

    private void getExpiredCreditAliases(Container c, User u, StringBuilder msg)
    {
        if (QueryService.get().getUserSchema(u, c, "onprc_billing_public") == null)
        {
            msg.append("<b>Warning: the ONPRC billing schema has not been enabled in this folder, so the expired alias alert cannot run<p><hr>");
            return;
        }

        TableInfo ti = QueryService.get().getUserSchema(u, c, "onprc_billing_public").getTable("creditAccount");
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("chargeId/active"), true, CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("account/budgetEndDateCoalesced"), "-0d", CompareType.DATE_LT);
        filter.addCondition(FieldKey.fromString("account"), null, CompareType.NONBLANK);
        filter.addCondition(FieldKey.fromString("account"), "-1", CompareType.NEQ_OR_NULL);
        filter.addCondition(FieldKey.fromString("enddateCoalesced"), new Date(), CompareType.DATE_GTE);
        TableSelector ts = new TableSelector(ti, filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>Warning: there are " + count + " active chargeable items using a credit alias with an expired budget period.</b><p>");
            msg.append("<a href='" + getExecuteQueryUrl(c, "onprc_billing_public", "creditAccount", null) + "&" + filter.toQueryString("query") + "'>Click here to view them</a>");
            msg.append("<hr>");
        }
    }

    private void getProjectsWithoutAliases(Container c, User u, StringBuilder msg)
    {
        if (QueryService.get().getUserSchema(u, c, "onprc_billing_public") == null)
        {
            msg.append("<b>Warning: the ONPRC billing schema has not been enabled in this folder, so the expired alias alert cannot run<p><hr>");
            return;
        }

        TableInfo ti = QueryService.get().getUserSchema(u, c, "ehr").getTable("project");
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("enddateCoalesced"), "-0d", CompareType.DATE_GTE);
        filter.addCondition(FieldKey.fromString("account"), null, CompareType.ISBLANK);
        TableSelector ts = new TableSelector(ti, filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>Warning: there are " + count + " active ONPRC projects without an alias.</b><p>");
            msg.append("<a href='" + getExecuteQueryUrl(c, "ehr", "project", "Alias Info") + "&" + filter.toQueryString("query") + "'>Click here to view them</a>");
            msg.append("<hr>");
        }

    }

    private void getProjectsNotActive(Container c, User u, StringBuilder msg)
    {
        if (QueryService.get().getUserSchema(u, c, "onprc_billing_public") == null)
        {
            msg.append("<b>Warning: the ONPRC billing schema has not been enabled in this folder, so the expired alias alert cannot run<p><hr>");
            return;
        }

        TableInfo ti = QueryService.get().getUserSchema(u, c, "ehr").getTable("project");
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("enddateCoalesced"), "-0d", CompareType.DATE_GTE);
        filter.addCondition(FieldKey.fromString("account/projectStatus"), "ACTIVE", CompareType.NEQ_OR_NULL);
        filter.addCondition(FieldKey.fromString("account/projectStatus"), "No Cost Ext", CompareType.NEQ_OR_NULL);
        filter.addCondition(FieldKey.fromString("account/projectStatus"), "Partial Setup", CompareType.NEQ_OR_NULL);
        filter.addCondition(FieldKey.fromString("account"), null, CompareType.NONBLANK);
        TableSelector ts = new TableSelector(ti, filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>Warning: there are " + count + " active ONPRC projects using an alias not marked as ACTIVE.</b><p>");
            msg.append("<a href='" + getExecuteQueryUrl(c, "ehr", "project", "Alias Info") + "&" + filter.toQueryString("query") + "'>Click here to view them</a>");
            msg.append("<hr>");
        }
    }
}
