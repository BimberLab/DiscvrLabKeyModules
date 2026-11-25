package org.labkey.studies.query;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.writer.HtmlWriter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AccessionsDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new DataColumn(colInfo)
        {
            @Override
            public void addQueryFieldKeys(Set<FieldKey> keys)
            {
                super.addQueryFieldKeys(keys);
            }

            private FieldKey getBoundKey(String colName)
            {
                return new FieldKey(getBoundColumn().getFieldKey().getParent(), colName);
            }

            @Override
            public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
            {
                String rawValue = StringUtils.trimToNull(ctx.get(getBoundKey("accessions"), String.class));
                if (rawValue == null)
                {
                    return;
                }

                List<String> tokens = Arrays.asList(rawValue.split(","));
                Collections.sort(tokens);

                HtmlString delim = HtmlString.EMPTY_STRING;
                for (String accession : tokens)
                {
                    out.write(delim);
                    if (accession.startsWith("SRA"))
                    {
                        out.write(LinkBuilder.simpleLink(accession, "https://trace.ncbi.nlm.nih.gov/Traces/sra/?run=" + accession).target("_blank"));
                    }
                    else if (accession.startsWith("GS"))
                    {
                        out.write(LinkBuilder.simpleLink(accession, "https://www.ncbi.nlm.nih.gov/geo/query/acc.cgi?acc=" + accession).target("_blank"));
                    }
                    else
                    {
                        out.write(accession);
                    }

                    delim = HtmlString.BR;
                }
            }
        };
    }
}