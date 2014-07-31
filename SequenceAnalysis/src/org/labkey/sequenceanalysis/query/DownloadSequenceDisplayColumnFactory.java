package org.labkey.sequenceanalysis.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Sort;
import org.labkey.api.data.UrlColumn;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.URLHelper;

import java.io.IOException;
import java.io.Writer;

/**
 * User: bimber
 * Date: 7/22/2014
 * Time: 3:27 PM
 */
public class DownloadSequenceDisplayColumnFactory implements DisplayColumnFactory
{
    public DownloadSequenceDisplayColumnFactory()
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
                out.write(PageFlowUtil.textLink("Download Sequence", "javascript:void(0);", "SequenceAnalysis.window.DownloadSequencesWindow.downloadSingle(" + ctx.get("rowId") + ")", null));
            }

            @Override
            public boolean isSortable()
            {
                return false;
            }

            @Override
            public boolean isFilterable()
            {
                return false;
            }

            @Override
            public boolean isEditable()
            {
                return false;
            }
        };

        ret.setCaption("");

        return ret;
    }
}
