package org.labkey.onprc_ssu.notification;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.ldk.notification.AbstractNotification;
import org.labkey.api.module.Module;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;

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
        return "0 15 12 * * ?";
    }

    @Override
    public String getCategory()
    {
        return "SSU";
    }

    @Override
    public String getScheduleDescription()
    {
        return "every day at 12:15";
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
            surgeriesWithCases(ehrContainer, u, msg);
        }

        return msg.toString();
    }

    private void surgeriesWithCases(Container ehrContainer, User u, StringBuilder msg)
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
            msg.append("<p><a href='" + getExecuteQueryUrl(ehrContainer, "study", "encounters", "Surgeries", filter) + "&query.Id/curLocation/area~eq=Hospital&query.Id/curLocation/room/housingType/value~eq=Cage Location&query.enddate~isblank&query.daysInArea~gte=30'>Click here to view them</a><br>\n");
            msg.append("<hr>\n");
        }
    }
}
