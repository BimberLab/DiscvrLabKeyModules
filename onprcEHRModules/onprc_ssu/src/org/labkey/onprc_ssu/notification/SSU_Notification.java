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
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.ldk.notification.AbstractNotification;
import org.labkey.api.module.Module;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.onprc_ssu.ONPRC_SSUSchema;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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
    public String getEmailSubject()
    {
        return "SSU Alerts: " + _dateTimeFormat.format(new Date());
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

    public String getMessage(Container c, User u)
    {
        StringBuilder msg = new StringBuilder();

        Container ehrContainer = EHRService.get().getEHRStudyContainer(c);
        if (ehrContainer != null && ehrContainer.hasPermission(u, ReadPermission.class))
        {
            surgeriesToday(ehrContainer, u, msg);
            surgeriesTomorrow(c, u, msg);
            surgeryScheduleCheck(c, ehrContainer, u, msg);

            surgeriesWithoutCases(ehrContainer, u, msg);
            surgeriesWithoutOrders(ehrContainer, u, msg);
        }

        return msg.toString();
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

        TableInfo ti = QueryService.get().getUserSchema(u, ehrContainer, "study").getTable("encounters");
        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, PageFlowUtil.set(FieldKey.fromString("Id"), FieldKey.fromString("procedureid"), FieldKey.fromString("procedureid/name")));

        TableSelector ts = new TableSelector(ti, cols.values(), filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are " + count + " animals that have had a surgery in the past 48H, but do not have an open surgery case, excluding procedures with no followup days.</b><br>");
            msg.append("<p><a href='" + getExecuteQueryUrl(ehrContainer, "study", "encounters", "Surgeries", filter) + "'>Click here to view them</a><br>\n");
            msg.append("<hr>\n");
        }
    }

    private void surgeriesWithoutOrders(final Container ehrContainer, User u, final StringBuilder msg)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DATE, -2);

        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("date"), cal.getTime(), CompareType.DATE_GTE);
        filter.addCondition(FieldKey.fromString("type"), "Surgery", CompareType.EQUAL);
        filter.addCondition(FieldKey.fromString("Id/activeTreatments/totalSurgicalTreatments"), null, CompareType.ISBLANK);
        filter.addCondition(new SimpleFilter.OrClause(new CompareType.CompareClause(FieldKey.fromString("procedureid/analgesiaRx"), CompareType.NONBLANK, null), new CompareType.CompareClause(FieldKey.fromString("procedureid/antibioticRx"), CompareType.NONBLANK, null)));

        TableInfo ti = QueryService.get().getUserSchema(u, ehrContainer, "study").getTable("encounters");
        final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, PageFlowUtil.set(FieldKey.fromString("Id"), FieldKey.fromString("procedureid"), FieldKey.fromString("date"), FieldKey.fromString("chargetype"), FieldKey.fromString("procedureid/name")));

        TableSelector ts = new TableSelector(ti, cols.values(), filter, null);
        long count = ts.getRowCount();
        if (count > 0)
        {
            msg.append("<b>WARNING: There are " + count + " animals that have had a surgery in the past 48H, but do not have any surgical medications, excluding procedures without default post-op analgesia/antibiotics.  NOTE: this currently only looks for the presence of ANY surgical medication, and does not check whether the right medications have been ordered</b><br>");
            msg.append("<table border=1 style='border-collapse: collapse;'>");
            msg.append("<tr style='font-weight: bold;'><td>Id</td><td>Date</td><td>Procedure</td><td>Charge Type</td></tr>");

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
                    String formattedDate = rs.getObject(FieldKey.fromString("date")) == null ? "No Date" : _dateFormat.format(rs.getDate(FieldKey.fromString("date")));
                    msg.append("<td>").append(formattedDate).append("</td>");
                    msg.append("<td>").append(safeAppend(rs, "procedureid/name", "No Procedure")).append("</td>");
                    msg.append("<td>").append(safeAppend(rs, "chargetype", "None")).append("</td>");
                    msg.append("</tr>");
                }
            });
            msg.append("</table>");
            msg.append("<hr>\n");
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
            msg.append("<b>" + count + " surgeries have been performed today.</b><br>");
            msg.append("<p><a href='" + getExecuteQueryUrl(ehrContainer, "study", "encounters", "Surgeries", filter) + "'>Click here to view them</a><br>\n");
            msg.append("<hr>\n");
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
            msg.append("<b>" + count + " surgeries have been performed today.</b><br>");
            msg.append("<p><a href='" + getExecuteQueryUrl(c, ONPRC_SSUSchema.NAME, ONPRC_SSUSchema.TABLE_SCHEDULE, null, filter) + "'>Click here to view them</a><br>\n");
            msg.append("<hr>\n");
        }
    }

    private void surgeryScheduleCheck(Container c, final Container ehrContainer, User u, final StringBuilder msg)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("date"), new Date(), CompareType.DATE_GT);
        filter.addCondition(new SimpleFilter.OrClause(new CompareType.CompareClause(FieldKey.fromString("Id/surgeryChecklist/status"), CompareType.NONBLANK, null), new CompareType.CompareClause(FieldKey.fromString("isAssignedAtTime"), CompareType.EQUAL, "N"), new CompareType.CompareClause(FieldKey.fromString("project"), CompareType.ISBLANK, null)));

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
                FieldKey.fromString("isAssignedAtTime")));

        TableSelector ts = new TableSelector(ti, cols.values(), filter, new Sort("date,Id"));
        if (ts.exists())
        {
            msg.append("<b>The following surgeries are scheduled, but have problems flagged.</b><br><br>");
            msg.append("<table border=1 style='border-collapse: collapse;'>");
            msg.append("<tr style='font-weight: bold;'><td>Id</td><td>Date</td><td>Procedure</td><td>Project</td><td>Assigned To Project?</td><td>PLT</td><td>HCT</td><td>Labwork Flags</td></tr>");

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
                    String formattedDate = rs.getObject(FieldKey.fromString("date")) == null ? "No Date" : _dateFormat.format(rs.getDate(FieldKey.fromString("date")));
                    msg.append("<td>").append(formattedDate).append("</td>");
                    msg.append("<td>").append(safeAppend(rs, "procedureid/name", "No Procedure")).append("</td>");
                    msg.append("<td>").append(safeAppend(rs, "project/displayName", "No Project")).append("</td>");
                    msg.append("<td>").append(safeAppend(rs, "isAssignedAtTime", "")).append("</td>");

                    msg.append("<td>").append(safeAppend(rs, "Id/surgeryChecklist/PLT", "")).append("</td>");
                    msg.append("<td>").append(safeAppend(rs, "Id/surgeryChecklist/HCT", "")).append("</td>");
                    msg.append("<td>").append(safeAppend(rs, "Id/surgeryChecklist/status", "")).append("</td>");
                    msg.append("</tr>");
                }
            });
            msg.append("</table>");
            msg.append("<hr>\n");
        }
    }
}
