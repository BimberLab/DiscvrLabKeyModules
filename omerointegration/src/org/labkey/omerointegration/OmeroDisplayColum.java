package org.labkey.omerointegration;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 10/21/2015.
 */
public class OmeroDisplayColum extends DataColumn
{
    private Map<String, OmeroServer> _serverMap = new HashMap<>();

    public OmeroDisplayColum(ColumnInfo col)
    {
        super(col);
        _textAlign = "left";
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object o = getValue(ctx);
        if (o != null)
        {
            String omeroId = o.toString();

            StringBuilder html = new StringBuilder();
            Container c = ctx.getContainer();
            if (c == null)
            {
                out.write("<" + omeroId + ">");
                return;
            }

            if (_serverMap.get(c.getId()) == null)
            {
                _serverMap.put(c.getId(), new OmeroServer(c));
            }

            OmeroServer server = _serverMap.get(c.getId());
            if (!server.isValid())
            {
                out.write("<" + omeroId + ">");
                return;
            }

            DetailsURL thumbnailURL = DetailsURL.fromString("omerointegration/downloadThumbnail.view?omeroId=" + omeroId, c);

            html.append("<span onclick=\"OMERO.Utils.renderViewer('" + PageFlowUtil.filter(server.getViewerUrl(omeroId)) + "')\">");

            html.append("<img src=\"");
            html.append(thumbnailURL.getActionURL().toString());
            html.append("\" style=\"height:96px; width: 96px;\" />");

            html.append("</span>");

            out.write(html.toString());
        }
    }

    @Override
    public @NotNull Set<ClientDependency> getClientDependencies()
    {
        return PageFlowUtil.set(ClientDependency.fromPath("omerointegration/utils.js"), ClientDependency.fromPath("ldk/LDKApi.lib.xml"));
    }
}
