package org.labkey.laboratory;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.FieldKey;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: bimber
 * Date: 1/31/13
 * Time: 8:29 PM
 */
public class LaboratoryUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(LaboratoryUpgradeCode.class);

    /** called at 12.277-12.278 */
    @SuppressWarnings({"UnusedDeclaration"})
    public void initWorkbookTable(final ModuleContext moduleContext)
    {
        try
        {
            LaboratoryManager.get().initWorkbooksForContainer(moduleContext.getUpgradeUser(), ContainerManager.getRoot());
        }
        catch (Exception e)
        {
            _log.error("Error upgrading laboratory module", e);
        }
    }

    /** called at 12.292-12.293 and 12.293-12.294 */
    @SuppressWarnings({"UnusedDeclaration"})
    public void migrateQuantityField(final ModuleContext moduleContext)
    {
        try
        {
            final TableInfo ti = LaboratorySchema.getInstance().getTable(LaboratorySchema.TABLE_SAMPLES);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("quantity_string"), null, CompareType.NONBLANK);
            filter.addCondition(FieldKey.fromString("quantity"), null, CompareType.ISBLANK);

            TableSelector ts = new TableSelector(ti, Table.ALL_COLUMNS, filter, null);
            ts.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    int rowId = rs.getInt("rowid");

                    String qs = rs.getString("quantity_string");
                    qs = StringUtils.trimToNull(qs);
                    if (qs == null)
                        return;

                    Double d = null;
                    try
                    {
                        d = Double.parseDouble(qs);
                    }
                    catch (NumberFormatException e)
                    {
                        //ignore
                    }

                    if (d != null)
                    {
                        Map<String, Object> map = new CaseInsensitiveHashMap<>();
                        map.put("quantity", d);

                        Table.update(moduleContext.getUpgradeUser(), ti, map, new Object[]{rowId});
                    }
                }
            });
        }
        catch (Exception e)
        {
            _log.error("Error upgrading laboratory module", e);
        }
    }
}
