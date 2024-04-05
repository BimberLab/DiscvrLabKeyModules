package org.labkey.sequenceanalysis.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
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
            private boolean _handlerRegistered = false;

            @Override
            public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
            {
                Object val = ctx.get(FieldKey.fromString(getBoundColumn().getFieldKey().getParent(), "rowId"));
                out.write(PageFlowUtil.link("Download Sequence").attributes(Map.of(
                    "data-rowid", val.toString()
                )).addClass("sdc-row").toString());

                if (!_handlerRegistered)
                {
                    HttpView.currentPageConfig().addHandlerForQuerySelector("a.sdc-row", "click", "SequenceAnalysis.window.DownloadSequencesWindow.downloadSingle(this.attributes.getNamedItem('data-rowid').value); return false;");
                    _handlerRegistered = true;
                }
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
