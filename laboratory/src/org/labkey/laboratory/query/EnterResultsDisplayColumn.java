package org.labkey.laboratory.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.assay.AssayService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;

/**
 * User: bimber
 * Date: 11/6/12
 * Time: 10:26 AM
 */
public class EnterResultsDisplayColumn extends DataColumn
{
    public EnterResultsDisplayColumn(ColumnInfo col)
    {
        super(col);
        addDisplayClass("labkey-details");
    }

    @Override
    public String renderURL(RenderContext ctx)
    {
        Integer assayId = (Integer)ctx.get("assayId");
        Integer runid = (Integer)ctx.get("runid");
        Container c = ContainerManager.getForId((String)ctx.get("container"));

        ExpProtocol protocol = ExperimentService.get().getExpProtocol(assayId);
        if (runid == null && protocol != null)
        {
            ActionURL url = AssayService.get().getProvider(protocol).getImportURL(c, protocol);
            url.addParameter("templateId", ctx.get("rowid").toString());
            return url.toString();
        }
        return null;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object value = getValue(ctx);
        String url = renderURL(ctx);

        if (value != null && url != null)
        {
            Map<String, String> props;
            if (_linkTarget != null)
            {
                props = Collections.singletonMap("target", _linkTarget);
            }
            else
            {
                props = Collections.emptyMap();
            }
            out.write(PageFlowUtil.link(value.toString()).href(url).attributes(props).toString());
        }
    }

    @Override
    public void renderGridHeaderCell(RenderContext ctx, Writer out, String headerClass) throws IOException
    {
        out.write("<td class=\"labkey-column-header\"></td>");
    }
}
