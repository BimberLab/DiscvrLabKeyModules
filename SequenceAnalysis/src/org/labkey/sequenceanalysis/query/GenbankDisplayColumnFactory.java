package org.labkey.sequenceanalysis.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.writer.HtmlWriter;

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
        return new DataColumn(colInfo)
        {
            @Override
            public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
            {
                String val = ctx.get(getBoundColumn().getFieldKey(), String.class);
                if (val != null)
                {
                    String[] vals = val.replaceAll("\\s+", "").split("[;,]");
                    String delim = "";
                    for (String v : vals)
                    {
                        out.write(delim);
                        out.write(LinkBuilder.simpleLink(v, getFormattedURL(v)));
                        delim = "; ";
                    }
                }
            }
        };
    }

    protected String getFormattedURL(String v)
    {
        return "http://www.ncbi.nlm.nih.gov/nuccore/?term=" + v;
    }
}
