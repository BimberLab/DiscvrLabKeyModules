package org.labkey.laboratory;

import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.QueryCountNavItem;
import org.labkey.api.query.FieldKey;

/**
 * User: bimber
 * Date: 9/27/13
 * Time: 2:58 PM
 */
public class SamplesCountNavItem extends QueryCountNavItem
{
    public SamplesCountNavItem(DataProvider provider, String schema, String query, String category, String label)
    {
        super(provider, schema, query, category, label);
    }

    @Override
    protected SimpleFilter getFilter(Container c, TableInfo ti)
    {
        SimpleFilter filter = super.getFilter(c, ti);
        filter.addCondition(FieldKey.fromString("dateremoved"), null, CompareType.ISBLANK);
        return filter;
    }
}
