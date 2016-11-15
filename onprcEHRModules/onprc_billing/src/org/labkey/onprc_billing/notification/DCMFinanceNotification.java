package org.labkey.onprc_billing.notification;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.onprc_billing.ONPRC_BillingManager;
import org.labkey.onprc_billing.ONPRC_BillingSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**

 */
public class DCMFinanceNotification extends FinanceNotification
{
    @Override
    public String getName()
    {
        return "DCM Finance Notification";
    }

    @Override
    public String getEmailSubject(Container c)
    {
        return "DCM Finance/Billing Alerts: " + getDateTimeFormat(c).format(new Date());
    }

    @Override
    public String getCronString()
    {
        return "0 30 7 * * ?";
    }

    @Override
    public String getScheduleDescription()
    {
        return "every day at 7:30AM";
    }

    @Override
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

        //NOTE: this is a slightly odd approach, but the primary notification groups these data by FA.  we take this map and group it instead by project/alias
        Map<String, Map<String, Map<String, Integer>>> newDataMap = new TreeMap<>();
        Set<FieldDescriptor> foundCols = new LinkedHashSet<>();
        for (String financialAnalyst : dataMap.keySet())
        {
            //first collect all distinct columns we will need to show
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

            //then transform the initial Map based on FA into one based on project/alias
            for (String key : dataMap.get(financialAnalyst).keySet())
            {
                List<String> tokens = new ArrayList<>(Arrays.asList(key.split("<>")));
                tokens.remove(0); //remove FA
                String newKey = StringUtils.join(tokens, "<>");
                Map<String, Map<String, Integer>> newDataByCategory = newDataMap.get(newKey);
                if (newDataByCategory == null)
                    newDataByCategory = new TreeMap<>();

                Map<String, Map<String, Integer>> dataByCategory = dataMap.get(financialAnalyst).get(key);
                for (String category : dataByCategory.keySet())
                {
                    Map<String, Integer> newTotals = newDataByCategory.get(category);
                    if (newTotals == null)
                        newTotals = new TreeMap<>();

                    Map<String, Integer> totals = dataByCategory.get(category);
                    for (String t : totals.keySet())
                    {
                        Integer newVal = newTotals.containsKey(t) ? newTotals.get(t) : 0;
                        newVal += totals.get(t);
                        newTotals.put(t, newVal);
                    }

                    newDataByCategory.put(category, newTotals);
                    newDataMap.put(newKey, newDataByCategory);
                }
            }
        }

        //reorder columns based on initial order
        LinkedHashSet<FieldDescriptor> toShow = new LinkedHashSet<>();
        toShow.addAll(Arrays.asList(_fields));
        toShow.retainAll(foundCols);

        //now build the table itself
        msg.append("<table border=1 style='border-collapse: collapse;'><tr style='font-weight: bold;'><td>Project</td><td>Alias</td><td>OGA Project</td><td>Category</td>");
        for (FieldDescriptor fd : toShow)
        {
            msg.append("<td>" + fd.getLabel() + "</td>");
        }
        msg.append("</tr>");

        //and append rows
        for (String key : newDataMap.keySet())
        {
            String[] tokens = key.split("<>");
            Map<String, Map<String, Integer>> dataByCategory = newDataMap.get(key);
            for (String category : dataByCategory.keySet())
            {
                Map<String, Integer> totals = dataByCategory.get(category);

                String baseUrl = getExecuteQueryUrl(containerMap.get(category), ONPRC_BillingSchema.NAME, categoryToQuery.get(category), null) + "&query.param.StartDate=" + getDateFormat(c).format(start.getTime()) + "&query.param.EndDate=" + getDateFormat(c).format(endDate.getTime());
                String projUrl = baseUrl + ("None".equals(tokens[0]) ? "&query.project/displayName~isblank" : "&query.project/displayName~eq=" + tokens[0]);
                msg.append("<tr><td><a href='" + projUrl + "'>" + tokens[0] + "</a></td>");

                //alias
                String accountUrl = null;
                Container financeContainer = ONPRC_BillingManager.get().getBillingContainer(containerMap.get(category));
                if (financeContainer != null && !"Unknown".equals((tokens[1])))
                {
                    accountUrl = getExecuteQueryUrl(financeContainer, ONPRC_BillingSchema.NAME, "aliases", null, null) + "&query.alias~eq=" + tokens[1];
                }

                if (accountUrl != null)
                {
                    msg.append("<td><a href='" + accountUrl + "'>" + tokens[1] + "</a></td>");
                }
                else
                {
                    msg.append("<td>" + (tokens[1]) + "</td>");
                }

                msg.append("<td>" + (tokens[2]) + "</td>");
                msg.append("<td>" + category + "</td>");

                for (FieldDescriptor fd : toShow)
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
        msg.append("<hr><p>");
    }
}
