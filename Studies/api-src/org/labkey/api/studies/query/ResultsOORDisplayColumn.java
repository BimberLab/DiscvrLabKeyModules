package org.labkey.api.studies.query;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;

import java.text.DecimalFormat;
import java.util.Set;

public class ResultsOORDisplayColumn extends DataColumn
{
    public ResultsOORDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    @Override
    public Class getDisplayValueClass()
    {
        return String.class;
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        Object result = ctx.get(getBoundColumn().getFieldKey(), Double.class);
        if (result == null)
        {
            return null;
        }

        if (getBoundColumn().getFormat() != null)
        {
            DecimalFormat fmt = new DecimalFormat(getBoundColumn().getFormat());
            result = fmt.format(result);
        }

        String oor = ctx.get(getOOR(), String.class);
        if (StringUtils.isEmpty(oor))
        {
            return result;
        }

        return oor + result;
    }

    private FieldKey getOOR()
    {
        ColumnInfo col = getBoundColumn();
        if (col == null)
        {
            return null;
        }

        FieldKey oor = FieldKey.fromString(col.getFieldKey().getName() + "OORIndicator");

        return getBoundColumn().getFieldKey().getParent() == null ? oor : FieldKey.fromParts(getBoundColumn().getFieldKey().getParent(), oor);
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        keys.add(getOOR());
    }
}
