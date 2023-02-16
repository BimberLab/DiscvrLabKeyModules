package org.labkey.sequenceanalysis.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Sort;
import org.labkey.api.data.UrlColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

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
                Object val = ctx.get(FieldKey.fromString(getBoundColumn().getFieldKey().getParent(), "rowId"));
                out.write(PageFlowUtil.link("Download Sequence").onClick("SequenceAnalysis.window.DownloadSequencesWindow.downloadSingle(" + val + ")").toString());
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

            @Override
            public @NotNull Set<ClientDependency> getClientDependencies()
            {
                return new LinkedHashSet<>(Collections.singletonList(ClientDependency.fromPath("sequenceanalysis/window/DownloadSequencesWindow.js")));
            }

            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);

                keys.add(FieldKey.fromString(getBoundColumn().getFieldKey().getParent(), "rowId"));
            }
        };

        ret.setCaption("");

        return ret;
    }
}
