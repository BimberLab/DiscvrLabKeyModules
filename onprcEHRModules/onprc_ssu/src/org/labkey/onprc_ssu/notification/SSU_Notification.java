package org.labkey.onprc_ssu.notification;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ldk.notification.AbstractNotification;
import org.labkey.api.module.Module;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.onprc_ssu.ONPRC_SSUSchema;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 2/3/14
 * Time: 10:39 PM
 */
public class SSU_Notification extends AbstractNotification
{
    public SSU_Notification(Module owner)
    {
        super(owner);
    }

    @Override
    public String getName()
    {
        return "SSU Notification";
    }

    @Override
    public String getEmailSubject(Container c)
    {
        return "SSU Alerts: " + getDateTimeFormat(c).format(new Date());
    }

    @Override
    public String getCronString()
    {
        return "0 0 16 * * ?";
    }

    @Override
    public String getCategory()
    {
        return "SSU";
    }

    @Override
    public String getScheduleDescription()
    {
        return "every day at 4:00";
    }

    @Override
    public String getDescription()
    {
        return "The report is designed provide a summary of surgeries performed today and alert for any surgeries missing cases or post-op meds.";
    }

    @Override
    public String getMessageBodyHTML(Container c, User u)
    {
        StringBuilder msg = new StringBuilder();

        Container ehrContainer = EHRService.get().getEHRStudyContainer(c);
        if (ehrContainer != null && ehrContainer.hasPermission(u, ReadPermission.class))
        {
            surgeriesToday(ehrContainer, u, msg);
            surgeriesTomorrow(c, u, msg);
            casesClosedToday(ehrContainer, u, msg);
            surgeryScheduleCheck(c, ehrContainer, u, msg);
            animalsWithoutRoundsToday(ehrContainer, u, msg);

            nonFinalizedSurgeries(ehrContainer, u, msg);
            surgeriesWithoutCases(ehrContainer, u, msg);
            surgeriesWithoutOrders(ehrContainer, u, msg);
        }

        return msg.toString();
    }

    private void nonFinalizedSurgeries(Container ehrContainer, User u, StringBuilder msg)
    {
        //note: we want a date/time comparison
        Date date = new Date();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("date"), date, CompareType.LTE);
        filter.addCondition(FieldKey.fromString("type"), "Surgery", CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("qcstate/publicdata"), true, CompareType.NEQ_OR_NULL);
        filter.addCondition(FieldKey.fromString("qcstate"), EHRService.QCSTATES.ReviewRequired.getQCState(ehrContainer).getRowId(), CompareType.NEQ_OR_NULL);

        TableInfo ti = QueryService.get().getUserSchema(u, ehrContainer, "study").getTable("encounters");
        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, PageFlowUtil.set(FieldKey.fromString("Id"), FieldKey.fromString("procedureid"), FieldKey.fromString("procedureid/name")));

        TableSelector ts = new TableSelector(ti, cols.values(), filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are " + count + " procedures with a date prior to: " + getDateTimeFormat(ehrContainer).format(date) + " that have not been finalized, excluding those under review.  This may indicate inproper dates in those surgeries, or cancelled surgeries that should be removed.</b><br>");
            msg.append("<p><a href='" + getExecuteQueryUrl(ehrContainer, "study", "encounters", "Surgeries", filter) + "'>Click here to view them</a><br>\n");
            msg.append("<hr>\n");
        }
    }

    private void surgeriesWithoutCases(Container ehrContainer, User u, StringBuilder msg)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DATE, -2);

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("date"), cal.getTime(), CompareType.DATE_GTE);
        filter.addCondition(FieldKey.fromString("type"), "Surgery", CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("Id/activeCases/categories"), "Surgery", CompareType.DOES_NOT_CONTAIN);
        filter.addCondition(FieldKey.fromString("procedureid/followupdays"), 0, CompareType.GT);
        filter.addCondition(FieldKey.fromString("qcstate/publicdata"), true, CompareType.EQUAL);

        TableInfo ti = QueryService.get().getUserSchema(u, ehrContainer, "study").getTable("encounters");
        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, PageFlowUtil.set(FieldKey.fromString("Id"), FieldKey.fromString("date"), FieldKey.fromString("procedureid"), FieldKey.fromString("procedureid/name")));

        TableSelector ts = new TableSelector(ti, cols.values(), filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            final TableInfo casesTable = QueryService.get().getUserSchema(u, ehrContainer, "study").getTable("cases");
            final List<Long> countList = new ArrayList<>();
            countList.add(count);

            ts.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    //determine if there are cases that spanned the surgery, but closed prior to this alert
                    SimpleFilter filter2 = new SimpleFilter(FieldKey.fromString("enddate"), rs.getDate("date"), CompareType.DATE_GT);
                    filter2.addCondition(FieldKey.fromString("category"), "Surgery", CompareType.EQUAL);
                    filter2.addCondition(FieldKey.fromString("Id"), rs.getString("Id"), CompareType.EQUAL);

                    TableSelector ts2 = new TableSelector(casesTable, PageFlowUtil.set("Id"), filter2, null);
                    if (ts2.exists())
                    {
                        long c = countList.get(0);
                        c--;
                        countList.clear();
                        countList.add(c);
                    }
                }
            });

            if (countList.get(0) > 0)
            {
                msg.append("<b>WARNING: There are " + countList.get(0) + " surgeries in the past 48H that lack an open surgery case, excluding procedures with no followup days.</b><br>");
                msg.append("<p><a href='" + getExecuteQueryUrl(ehrContainer, "study", "encounters", "Surgeries", filter) + "'>Click here to view them</a><br>\n");
                msg.append("<hr>\n");
            }
        }
        else
        {
            msg.append("All surgeries in the past 48H have an open case, excluding those with 0 followup days<hr>");
        }
    }

    private void surgeriesWithoutOrders(final Container ehrContainer, User u, final StringBuilder msg)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DATE, -2);

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("date"), cal.getTime(), CompareType.DATE_GTE);
        filter.addCondition(FieldKey.fromString("date"), new Date(), CompareType.DATE_LTE);
        filter.addCondition(FieldKey.fromString("type"), "Surgery", CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("Id/activeTreatments/totalSurgicalTreatments"), null, CompareType.ISBLANK);
        filter.addCondition(new SimpleFilter.OrClause(
            new SimpleFilter.AndClause(new CompareType.CompareClause(FieldKey.fromString("procedureid/analgesiaRx"), CompareType.NONBLANK, null), new CompareType.CompareClause(FieldKey.fromString("procedureid/analgesiaRx"), CompareType.NEQ, "None")),
            new SimpleFilter.AndClause(new CompareType.CompareClause(FieldKey.fromString("procedureid/antibioticRx"), CompareType.NONBLANK, null), new CompareType.CompareClause(FieldKey.fromString("procedureid/antibioticRx"), CompareType.NEQ, "None"))
        ));

        TableInfo ti = QueryService.get().getUserSchema(u, ehrContainer, "study").getTable("encounters");
        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, PageFlowUtil.set(FieldKey.fromString("Id"), FieldKey.fromString("procedureid"), FieldKey.fromString("date"), FieldKey.fromString("chargetype"), FieldKey.fromString("procedureid/name"), FieldKey.fromString("Id/activeTreatments/surgicalTreatments")));

        TableSelector ts = new TableSelector(ti, cols.values(), filter, null);
        long count = ts.getRowCount();
        final List<Long> countList = new ArrayList<>();
        countList.add(count);
        if (count > 0)
        {
            final TableInfo treatmentsTable = QueryService.get().getUserSchema(u, ehrContainer, "study").getTable("treatment_order");
            final StringBuilder rows = new StringBuilder();
            ts.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet object) throws SQLException
                {
                    Results rs = new ResultsImpl(object, cols);

                    //determine if there are cases that spanned the surgery, but were later closed
                    SimpleFilter filter2 = new SimpleFilter(FieldKey.fromString("enddate"), rs.getDate("date"), CompareType.DATE_GT);
                    filter2.addCondition(FieldKey.fromString("category"), "Surgical", CompareType.EQUAL);
                    filter2.addCondition(FieldKey.fromString("Id"), rs.getString("Id"), CompareType.EQUAL);

                    TableSelector ts2 = new TableSelector(treatmentsTable, PageFlowUtil.set("Id"), filter2, null);
                    if (ts2.exists())
                    {
                        Long c = countList.get(0);
                        c--;
                        countList.clear();
                        countList.add(c);
                        return;
                    }

                    DetailsURL url = DetailsURL.fromString("/ehr/participantView.view", ehrContainer);
                    String ret = AppProps.getInstance().getBaseServerUrl() + url.getActionURL().toString();
                    ret += "participantId=" + rs.getString(FieldKey.fromString("Id"));

                    rows.append("<tr>");
                    rows.append("<td><a href='" + ret + "'>").append(safeAppend(rs, "Id", "No Id")).append("</a></td>");
                    String formattedDate = rs.getObject(FieldKey.fromString("date")) == null ? "No Date" : getDateFormat(ehrContainer).format(rs.getDate(FieldKey.fromString("date")));
                    rows.append("<td>").append(formattedDate).append("</td>");
                    rows.append("<td>").append(safeAppend(rs, "procedureid/name", "No Procedure")).append("</td>");
                    rows.append("<td>").append(safeAppend(rs, "chargetype", "None")).append("</td>");
                    rows.append("<td>").append(safeAppend(rs, "Id/activeTreatments/surgicalTreatments", "None")).append("</td>");
                    rows.append("</tr>");
                }
            });

            if (countList.get(0) > 0)
            {
                msg.append("<b>WARNING: There are " + countList.get(0) + " procedures performed in that past 48H, but do not have any surgical medications ordered, excluding procedures without default post-op analgesia/antibiotics.  NOTE: this currently only looks for the presence of any surgical medication, and does not check whether the right medications have been ordered</b><br>");
                msg.append("<table border=1 style='border-collapse: collapse;'>");
                msg.append("<tr style='font-weight: bold;'><td>Id</td><td>Date</td><td>Procedure</td><td>Charge Type</td><td>Surgical Treatments</td></tr>");
                msg.append(rows);
                msg.append("</table>");
                msg.append("<hr>\n");
            }
        }
    }

    private String safeAppend(Results rs, String fieldKey, String emptyText) throws SQLException
    {
        String ret = rs.getString(FieldKey.fromString(fieldKey));
        return ret == null ? emptyText : ret;
    }

    private void surgeriesToday(Container ehrContainer, User u, StringBuilder msg)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("date"), new Date(), CompareType.DATE_EQUAL);
        filter.addCondition(FieldKey.fromString("type"), "Surgery", CompareType.EQUAL);

        TableInfo ti = QueryService.get().getUserSchema(u, ehrContainer, "study").getTable("encounters");
        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, PageFlowUtil.set(FieldKey.fromString("Id"), FieldKey.fromString("procedureid"), FieldKey.fromString("procedureid/name")));

        TableSelector ts = new TableSelector(ti, cols.values(), filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>" + count + " surgeries have been performed today, as of " + _timeFormat.format(new Date()) + ".</b><br>");
            msg.append("<p><a href='" + getExecuteQueryUrl(ehrContainer, "study", "encounters", "Surgeries", filter) + "'>Click here to view them</a><br>\n");
            msg.append("<hr>\n");
        }
        else
        {
            msg.append("No surgeries were performed today, prior to this email.<hr>");
        }
    }

    private void casesClosedToday(Container ehrContainer, User u, final StringBuilder msg)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("enddate"), new Date(), CompareType.DATE_EQUAL);
        filter.addCondition(FieldKey.fromString("category"), "Surgery", CompareType.EQUAL);

        TableInfo ti = QueryService.get().getUserSchema(u, ehrContainer, "study").getTable("cases");
        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, PageFlowUtil.set(FieldKey.fromString("Id"), FieldKey.fromString("date"), FieldKey.fromString("enddate"), FieldKey.fromString("remark")));

        TableSelector ts = new TableSelector(ti, cols.values(), filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>NOTE: " + count + " surgical cases were closed today, as of " + _timeFormat.format(new Date()) + ".</b><br>");
            msg.append("<table border=1 style='border-collapse: collapse;'>");
            msg.append("<tr style='font-weight: bold;'><td>Id</td><td>Date Opened</td><td>Date Closed</td><td>Description</td></tr>");

            ts.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {

                    msg.append("<tr><td>" + rs.getString("Id") + "</td><td>" + (rs.getDate("date") == null ? "" : getDateFormat(ehrContainer).format(rs.getDate("date"))) + "</td><td>" + (rs.getDate("enddate") == null ? "" : getDateFormat(ehrContainer).format(rs.getDate("enddate"))) + "</td><td>" + (rs.getString("remark") == null ? "" : rs.getString("remark")) + "</td></tr>");
                }
            });

            msg.append("</table><hr>\n");
        }
    }

    private void surgeriesTomorrow(Container c, User u, StringBuilder msg)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DATE, 1);

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("date"), cal.getTime(), CompareType.DATE_EQUAL);

        TableInfo ti = QueryService.get().getUserSchema(u, c, ONPRC_SSUSchema.NAME).getTable(ONPRC_SSUSchema.TABLE_SCHEDULE);
        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, PageFlowUtil.set(FieldKey.fromString("Id"), FieldKey.fromString("procedureid"), FieldKey.fromString("procedureid/name")));

        TableSelector ts = new TableSelector(ti, cols.values(), filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>" + count + " surgeries are scheduled for tomorrow.</b><br>");
            msg.append("<p><a href='" + getExecuteQueryUrl(c, ONPRC_SSUSchema.NAME, ONPRC_SSUSchema.TABLE_SCHEDULE, null, filter) + "'>Click here to view them</a><br>\n");
            msg.append("<hr>\n");
        }
        else
        {
            msg.append("There are no surgeries scheduled tomorrow.<hr>");
        }
    }

    private void surgeryScheduleCheck(Container c, final Container ehrContainer, User u, final StringBuilder msg)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DATE, 1);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("date"), cal.getTime(), CompareType.DATE_EQUAL);
        filter.addCondition(new SimpleFilter.OrClause(new CompareType.CompareClause(FieldKey.fromString("Id/surgeryChecklist/status"), CompareType.NONBLANK, null), new CompareType.CompareClause(FieldKey.fromString("isAssignedToProtocolAtTime"), CompareType.EQUAL, "N"), new CompareType.CompareClause(FieldKey.fromString("project"), CompareType.ISBLANK, null)));

        TableInfo ti = QueryService.get().getUserSchema(u, c, ONPRC_SSUSchema.NAME).getTable(ONPRC_SSUSchema.TABLE_SCHEDULE);
        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, PageFlowUtil.set(
                FieldKey.fromString("Id"),
                FieldKey.fromString("project/displayName"),
                FieldKey.fromString("procedureid"),
                FieldKey.fromString("procedureid/name"),
                FieldKey.fromString("date"),
                FieldKey.fromString("Id/surgeryChecklist/labworkDate"),
                FieldKey.fromString("Id/surgeryChecklist/PLT"),
                FieldKey.fromString("Id/surgeryChecklist/HCT"),
                FieldKey.fromString("Id/surgeryChecklist/status"),
                FieldKey.fromString("isAssignedToProtocolAtTime")));

        TableSelector ts = new TableSelector(ti, cols.values(), filter, new Sort("date,Id"));
        if (ts.exists())
        {
            msg.append("<b>The following surgeries are scheduled tomorrow, but have problems flagged.</b><br><br>");
            msg.append("<table border=1 style='border-collapse: collapse;'>");
            msg.append("<tr style='font-weight: bold;'><td>Id</td><td>Date</td><td>Procedure</td><td>Project</td><td>Assigned To Protocol?</td><td>PLT</td><td>HCT</td><td>Labwork Flags</td></tr>");

            ts.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet object) throws SQLException
                {
                    Results rs = new ResultsImpl(object, cols);

                    DetailsURL url = DetailsURL.fromString("/ehr/participantView.view", ehrContainer);
                    String ret = AppProps.getInstance().getBaseServerUrl() + url.getActionURL().toString();
                    ret += "participantId=" + rs.getString(FieldKey.fromString("Id"));

                    msg.append("<tr>");
                    msg.append("<td><a href='" + ret + "'>").append(safeAppend(rs, "Id", "No Id")).append("</a></td>");
                    String formattedDate = rs.getObject(FieldKey.fromString("date")) == null ? "No Date" : getDateFormat(c).format(rs.getDate(FieldKey.fromString("date")));
                    msg.append("<td>").append(formattedDate).append("</td>");
                    msg.append("<td>").append(safeAppend(rs, "procedureid/name", "No Procedure")).append("</td>");
                    msg.append("<td>").append(safeAppend(rs, "project/displayName", "No Project")).append("</td>");
                    msg.append("<td>").append(safeAppend(rs, "isAssignedToProtocolAtTime", "")).append("</td>");

                    msg.append("<td>").append(safeAppend(rs, "Id/surgeryChecklist/PLT", "")).append("</td>");
                    msg.append("<td>").append(safeAppend(rs, "Id/surgeryChecklist/HCT", "")).append("</td>");
                    msg.append("<td>").append(safeAppend(rs, "Id/surgeryChecklist/status", "")).append("</td>");
                    msg.append("</tr>");
                }
            });
            msg.append("</table>");
            msg.append("<hr>\n");
        }
        else
        {
            msg.append("No problems were identified for animals with scheduled surgeries.<hr>");
        }
    }

    protected void animalsWithoutRoundsToday(final Container ehrContainer, User u, final StringBuilder msg)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("daysSinceLastRounds"), 0, CompareType.GT);
        filter.addCondition(FieldKey.fromString("isActive"), true, CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("category"), "Surgery", CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("Id/demographics/calculated_status"), "Alive", CompareType.EQUAL);

        TableInfo ti = QueryService.get().getUserSchema(u, ehrContainer, "study").getTable("cases");
        Set<FieldKey> keys = new HashSet<>();
        keys.add(FieldKey.fromString("Id"));
        keys.add(FieldKey.fromString("Id/curLocation/room"));
        keys.add(FieldKey.fromString("Id/curLocation/cage"));
        keys.add(FieldKey.fromString("daysSinceLastRounds"));
        keys.add(FieldKey.fromString("remark"));

        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, keys);

        TableSelector ts = new TableSelector(ti, cols.values(), filter, new Sort("Id/curLocation/room_sortValue,Id/curLocation/cage_sortValue"));
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are " + count + " active surgical cases that do not have obs entered today.</b><br>");
            msg.append("<table border=1 style='border-collapse: collapse;'>");
            msg.append("<tr style='font-weight: bold;'><td>Room</td><td>Cage</td><td>Id</td><td>Description</td><td>Days Since Last Rounds</td></tr>");

            ts.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet object) throws SQLException
                {
                    Results rs = new ResultsImpl(object, cols);
                    msg.append("<tr>");
                    msg.append("<td>" + safeAppend(rs, "Id/curLocation/room", "None") + "</td>");
                    msg.append("<td>" + safeAppend(rs, "Id/curLocation/cage", "") + "</td>");
                    msg.append("<td>" + rs.getString(FieldKey.fromString("Id")) + "</td>");
                    msg.append("<td>" + safeAppend(rs, "remark", "") + "</td>");
                    msg.append("<td>" + safeAppend(rs, "daysSinceLastRounds", "") + "</td>");
                    msg.append("</tr>");
                }
            });

            msg.append("</table>");
            msg.append("<hr>\n");
        }
        else
        {
            msg.append("All animals with open surgery cases have rounds entered today<hr>");
        }
    }
}