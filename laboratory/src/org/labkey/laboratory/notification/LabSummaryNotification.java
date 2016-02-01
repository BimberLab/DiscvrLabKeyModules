package org.labkey.laboratory.notification;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.SummaryNavItem;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.laboratory.NavItem;
import org.labkey.api.ldk.notification.Notification;
import org.labkey.api.module.Module;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * User: bimber
 * Date: 11/7/13
 * Time: 6:08 PM
 */
public class LabSummaryNotification implements Notification
{
    protected final static Logger log = Logger.getLogger(LabSummaryNotification.class);
    protected final static SimpleDateFormat _dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    protected final static SimpleDateFormat _dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd kk:mm");
    private static final String lastSave = "lastSave";
    private NumberFormat _pctFormat = null;
    private Module _owner;
    private static final String PROP_CATEGORY = "laboratory.LabSummaryNotification";

    public LabSummaryNotification(Module owner)
    {
        _owner = owner;
    }

    public String getName()
    {
        return "Lab Summary Notification";
    }

    public String getCategory()
    {
        return "Laboratory";
    }

    public boolean isAvailable(Container c)
    {
        return c.getActiveModules().contains(_owner);
    }

    public String getDescription()
    {
        return "This creates a report summarizing laboratory data usage";
    }

    public String getEmailSubject()
    {
        return "Lab Data Summary: " + _dateTimeFormat.format(new Date());
    }

    @Override
    public String getCronString()
    {
        return "0 0 11 1 * ?";
    }

    public String getScheduleDescription()
    {
        return "on the 1st of each month at 11AM";
    }

    private Map<String, String> getSavedValues(Container c)
    {
        return PropertyManager.getProperties(c, PROP_CATEGORY);
    }

    private void saveValues(Container c, Map<String, String> saved, Map<String, String> newValues)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(c, PROP_CATEGORY, true);

        Long lastSaveMills = map.containsKey(lastSave) ? Long.parseLong(map.get(lastSave)) : null;

        //if values have already been cached for this alert on this day, dont re-cache them.
        if (lastSaveMills != null)
        {
            if (DateUtils.isSameDay(new Date(), new Date(lastSaveMills)))
            {
                return;
            }
        }

        newValues.put(lastSave, String.valueOf(new Date().getTime()));
        map.putAll(newValues);

        map.save();
    }

    private String getPctChange(Long oldVal, Long newVal, double threshold)
    {
        if (oldVal == null || newVal == null || oldVal == 0)
        {
            return "<td></td>";
        }

        double pct = (newVal.doubleValue() / oldVal.doubleValue()) - 1.0;
        String style = "";
        if (Math.abs(pct) > threshold)
        {
            style = " style='background-color:yellow;'";
        }

        return "<td" + style + ">" + _pctFormat.format(pct) + "</td>";

    }

    public String getMessage(Container c, User u)
    {
        _pctFormat = NumberFormat.getPercentInstance();
        _pctFormat.setMaximumFractionDigits(1);

        Map<String, String> saved = getSavedValues(c);
        Map<String, String> newValues = new HashMap<>();

        StringBuilder msg = new StringBuilder();

        //getSecurityStats(c, u, msg, saved, newValues);
        getWorkbookSummary(c, u, msg, saved, newValues);
        getDataSummary(c, u, msg, saved, newValues);
        getFileSummary(c, u, msg, saved, newValues);

        msg.insert(0, "This email contains a summary of data and file usage.  It was run on: " + _dateTimeFormat.format(new Date()) + ".<p></p>");

        saveValues(c, saved, newValues);

        return msg.toString();
    }

    public void getSecurityStats(Container c, User u, final StringBuilder msg, Map<String, String> saved, Map<String, String> toSave)
    {

    }

    public void getWorkbookSummary(Container c, User u, final StringBuilder msg, Map<String, String> saved, Map<String, String> toSave)
    {
        msg.append("<br><b>Workbook Summary:</b><br>");

        msg.append("<table border=1 style='border-collapse: collapse;'>");
        msg.append("<tr style='font-weight:bold;'><td>").append("Folder").append("</td><td>").append("# of Workbooks").append("</td><td>").append("Previous Value").append("</td><td>").append("% Change").append("</td></tr>");

        String rowCount = "workbookCount";
        Map<String, String> newValueMap = new HashMap<>();
        JSONObject oldValueMap = saved.containsKey(rowCount) ? new JSONObject(saved.get(rowCount)) : null;

        Map<String, Long> totals = new TreeMap<>();
        TableInfo containers = DbSchema.get("core").getTable("containers");

        //first add this container
        SimpleFilter filter1 = new SimpleFilter(FieldKey.fromString("Type"), "workbook");
        filter1.addCondition(FieldKey.fromString("Parent"), c.getId());
        TableSelector ts = new TableSelector(containers, filter1, null);
        totals.put(c.getPath(), ts.getRowCount());

        //then children
        for (Container child : c.getChildren())
        {
            if (child.isWorkbook())
                continue;

            SimpleFilter filter2 = new SimpleFilter(FieldKey.fromString("Type"), "workbook");
            filter2.addCondition(FieldKey.fromString("Parent"), child.getId());
            TableSelector ts2 = new TableSelector(containers, filter2, null);
            totals.put(child.getPath(), ts2.getRowCount());
        }

        for (String folderPath : totals.keySet())
        {
            String key = folderPath;
            Long total = totals.get(folderPath);
            total = total == null ? 0L : total;

            newValueMap.put(key, total.toString());
            Long previousCount = null;
            if (oldValueMap != null && oldValueMap.containsKey(key))
            {
                previousCount = oldValueMap.getLong(key);
            }

            String formattedTotal = total == null ? "" : NumberFormat.getInstance().format(total);
            String formattedPreviousCount = previousCount == null ? "" : NumberFormat.getInstance().format(previousCount);
            String pctChange = getPctChange(previousCount, total, 0.05);

            msg.append("<tr><td>").append(folderPath).append("</td><td>").append(formattedTotal).append("</td><td>").append(formattedPreviousCount).append("</td>").append(pctChange).append("</tr>");
        }

        msg.append("</table><p></p><hr>");

        if (newValueMap.size() > 0)
            toSave.put(rowCount, new JSONObject(newValueMap).toString());
    }

    public void getDataSummary(Container c, User u, final StringBuilder msg, Map<String, String> saved, Map<String, String> toSave)
    {
        msg.append("<br><b>Data Summary:</b><br>");

        msg.append("<table border=1 style='border-collapse: collapse;'>");
        msg.append("<tr style='font-weight:bold;'><td>").append("Name").append("</td><td>").append("# of Rows").append("</td><td>").append("Previous Value").append("</td><td>").append("% Change").append("</td></tr>");

        String rowCount = "rowCount";
        Map<String, String> newValueMap = new HashMap<>();
        JSONObject oldValueMap = saved.containsKey(rowCount) ? new JSONObject(saved.get(rowCount)) : null;

        Set<DataProvider> providers = LaboratoryService.get().getDataProviders();
        List<SummaryNavItem> items = new ArrayList<>();
        for (DataProvider dp : providers)
        {
            items.addAll(dp.getSummary(c, u));
        }

        LaboratoryService.get().sortNavItems(items);

        for (SummaryNavItem item : items)
        {
            String key = item.getPropertyManagerKey();
            Long total = item.getRowCount(c, u);
            total = total == null ? 0L : total;

            newValueMap.put(key, total.toString());
            Long previousCount = null;
            if (oldValueMap != null && oldValueMap.containsKey(key))
            {
                previousCount = oldValueMap.getLong(key);
            }

            String formattedTotal = total == null ? "" : NumberFormat.getInstance().format(total);
            String formattedPreviousCount = previousCount == null ? "" : NumberFormat.getInstance().format(previousCount);
            String pctChange = getPctChange(previousCount, total, 0.05);

            msg.append("<tr><td>").append(item.getLabel()).append("</td><td>").append(formattedTotal).append("</td><td>").append(formattedPreviousCount).append("</td>").append(pctChange).append("</tr>");
        }

        msg.append("</table><p></p><hr>");

        if (newValueMap.size() > 0)
            toSave.put(rowCount, new JSONObject(newValueMap).toString());
    }

    public void getFileSummary(Container c, User u, final StringBuilder msg, Map<String, String> saved, Map<String, String> toSave)
    {
        msg.append("<br><b>File Usage:</b><br>");

        String fileRootSizes = "fileRootSizes";
        String fileRootCounts = "fileRootCounts";

        Map<String, String> newValueMap = new HashMap<>();
        JSONObject oldValueMap = saved.containsKey(fileRootSizes) ? new JSONObject(saved.get(fileRootSizes)) : null;

        Map<String, String> newValueMapCounts = new HashMap<>();
        JSONObject oldValueMapCounts = saved.containsKey(fileRootCounts) ? new JSONObject(saved.get(fileRootCounts)) : null;

        JSONArray ret = new JSONArray();
        ret.put(LDKService.get().getContainerSizeJson(c, u, false, true));
        for (Container child : c.getChildren())
        {
            if (!child.isWorkbook())
                ret.put(LDKService.get().getContainerSizeJson(child, u, false, true));
        }

        msg.append("<table border=1 style='border-collapse: collapse;'><tr style='font-weight:bold;'>");
        msg.append("<td>").append("Folder Path").append("</td><td>").append("File Path").append("</td><td>").append("Size").append("</td><td>").append("Previous Value").append("</td><td>").append("% Change").append("</td><td>").append("Total Files").append("</td><td>").append("Previous Value").append("</td><td>").append("% Change").append("</td></tr>");

        for (JSONObject json : ret.toJSONObjectArray())
        {
            if (json.has("fileRoots"))
            {
                JSONArray fileRoots = json.getJSONArray("fileRoots");
                for (JSONObject fr : fileRoots.toJSONObjectArray())
                {
                    //find previous value for filesize
                    String key = json.getString("path");
                    Long size = fr.containsKey("rootSizeInt") ? fr.getLong("rootSizeInt") : null;

                    newValueMap.put(key, size.toString());
                    Long previousSize = null;
                    if (oldValueMap != null && oldValueMap.containsKey(key))
                    {
                        previousSize = oldValueMap.getLong(key);
                    }

                    String formattedPreviousSize = previousSize == null ? "" : FileUtils.byteCountToDisplaySize(previousSize);
                    String pctChange = getPctChange(previousSize, size, 0.05);

                    //then do the same for file count
                    String fileCountKey = json.getString("path");
                    Long totalFiles = fr.containsKey("totalFiles") ? fr.getLong("totalFiles") : null;

                    newValueMapCounts.put(fileCountKey, totalFiles.toString());
                    Long previousCount = null;
                    if (oldValueMapCounts != null && oldValueMapCounts.containsKey(fileCountKey))
                    {
                        previousCount = oldValueMapCounts.getLong(fileCountKey);
                    }
                    String formattedPreviousCount = previousCount == null ? "" : NumberFormat.getInstance().format(previousCount);
                    String pctChange2 = getPctChange(previousCount, totalFiles, 0.05);

                    msg.append("<tr><td>").append(json.getString("path")).append("</td><td>").append(fr.getString("rootPath")).append("</td><td>").append(fr.getString("rootSize")).append("</td><td>").append(formattedPreviousSize).append("</td>").append(pctChange).append("<td>").append(NumberFormat.getInstance().format(totalFiles)).append("</td><td>").append(formattedPreviousCount).append("</td>").append(pctChange2).append("</tr>");
                }
            }
        }

        msg.append("</table><br>");
        msg.append("<hr>");

        if (newValueMap.size() > 0)
            toSave.put(fileRootSizes, new JSONObject(newValueMap).toString());
        if (newValueMapCounts.size() > 0)
            toSave.put(fileRootCounts, new JSONObject(newValueMapCounts).toString());
    }
}
