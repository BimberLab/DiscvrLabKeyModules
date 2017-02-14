package org.labkey.sequenceanalysis.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;

/**
 * Created by bimber on 2/8/2017.
 */
public class GenbankDisplayColumnFactory implements DisplayColumnFactory
{
    public GenbankDisplayColumnFactory()
    {

    }

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        DataColumn ret = new DataColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                Object val = ctx.get(getBoundColumn().getFieldKey());
                if (val != null)
                {
                    String[] vals = String.valueOf(val).replaceAll("\\s+", "").split(";|,");
                    String delim = "";
                    for (String v : vals)
                    {
                        out.write(delim + "<a href=" + getFormattedURL(v) + ">" + PageFlowUtil.encode(v) + "</a>");
                        delim = "; ";
                    }
                }
            }
        };

        return ret;
    }

    protected String getFormattedURL(String v)
    {
        return "http://www.ncbi.nlm.nih.gov/nuccore/?term=" + v;
    }
}
