package org.labkey.GeneticsCore;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Set;

/**
 * Created by bimber on 9/8/2016.
 */
public class AlignmentDisplayColumn extends DataColumn
{
    public AlignmentDisplayColumn(ColumnInfo columnInfo)
    {
        super(columnInfo);
    }

    public static class Factory implements DisplayColumnFactory
    {
        public Factory()
        {

        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new AlignmentDisplayColumn(colInfo);
        }
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object key = getValue(ctx);
        if (key != null)
        {
            out.write("<a class=\"labkey-text-link\" style=\"max-width: 500px;\" onclick=\"GeneticsCore.window.EditAlignmentsWindow.editRow(" + PageFlowUtil.jsString(key.toString()) + ", " + PageFlowUtil.jsString(ctx.getCurrentRegion().getName()) + ", arguments[0], arguments[1], arguments[2]);\">");
            out.write("Edit Alignments</a>");
            SimpleFilter filter = new SimpleFilter();
            filter.addUrlFilters(ctx.getSortFilterURLHelper(), ctx.getCurrentRegion().getName());
            FieldKey pctFk = new FieldKey(getBoundColumn().getFieldKey().getParent(), "percent_from_locus");
            Object filterVal = null;
            String filterOperator = null;
            for (SimpleFilter.FilterClause fc : filter.getClauses())
            {
                if (fc.getFieldKeys().contains(pctFk))
                {
                    if (fc instanceof CompareType.CompareClause && fc.getParamVals() != null)
                    {
                        filterOperator = ((CompareType.CompareClause)fc).getCompareType().getPreferredUrlKey();
                        filterVal = StringUtils.join(Arrays.asList(fc.getParamVals()), ";");
                    }
                }
            }

            String lineages = ctx.get(new FieldKey(getBoundColumn().getFieldKey().getParent(), "lineages"), String.class);
            Integer analysisId = ctx.get(new FieldKey(getBoundColumn().getFieldKey().getParent(), "analysis_id"), Integer.class);
            if (lineages != null)
            {
                out.write("<br><a class=\"labkey-text-link\" style=\"max-width: 500px;\" onclick=\"GeneticsCore.window.EditAlignmentsWindow.editLineage(" + PageFlowUtil.jsString(analysisId.toString()) + "," + PageFlowUtil.jsString(lineages.toString()) + ", " + PageFlowUtil.jsString(ctx.getCurrentRegion().getName()) + ", " + PageFlowUtil.jsString(filterOperator) + ", " + PageFlowUtil.jsString(String.valueOf(filterVal)) + ");\">");
                out.write("Edit Lineage</a>");
            }
        }
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);

        keys.add(new FieldKey(getBoundColumn().getFieldKey().getParent(), "lineages"));
        keys.add(new FieldKey(getBoundColumn().getFieldKey().getParent(), "analysis_id"));
    }

    @Override
    public @NotNull
    Set<ClientDependency> getClientDependencies()
    {
        return PageFlowUtil.set(ClientDependency.fromPath("SequenceAnalysis/sequenceAnalysis"), ClientDependency.fromPath("geneticscore/window/EditAlignmentsWindow.js"));
    }
}
