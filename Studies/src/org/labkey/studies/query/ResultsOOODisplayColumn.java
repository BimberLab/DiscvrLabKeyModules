package org.labkey.studies.query;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;

import java.text.DecimalFormat;
import java.util.Set;

public class ResultsOOODisplayColumn extends DataColumn
{
    public ResultsOOODisplayColumn(ColumnInfo col)
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
        FieldKey oor = FieldKey.fromString("resultOOOIndicator");
        if (getBoundColumn() != null)
        {
            return FieldKey.fromParts(getBoundColumn().getFieldKey().getParent(), oor);
        }
        else
        {
            return oor;
        }
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        keys.add(getOOR());
    }
}
