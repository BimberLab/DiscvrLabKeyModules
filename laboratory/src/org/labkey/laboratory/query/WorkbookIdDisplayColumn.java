package org.labkey.laboratory.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;

import java.io.IOException;
import java.io.Writer;

/**
 * User: bimber
 * Date: 2/10/13
 * Time: 12:53 PM
 */
public class WorkbookIdDisplayColumn extends DataColumn
{
    public WorkbookIdDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        //if the lookup is broken, dont render a value
        Object dv = getDisplayValue(ctx);
        if (dv == null || "".equals(dv))
            return;

        super.renderGridCellContents(ctx, out);
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        Object ret = super.getDisplayValue(ctx);

        //if the lookup is broken, dont render a value.  note: return empty string so client API draws the distinction between deliberately empty and a genuine NULL value
        return ret == null ? "" : ret;
    }
}