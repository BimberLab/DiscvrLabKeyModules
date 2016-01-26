package org.labkey.onprc_reports;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 5/16/13
 * Time: 3:41 PM
 */
public class ONPRC_ReportsCustomizer implements TableCustomizer
{
    @Override
    public void customize(TableInfo table)
    {
        if (table instanceof AbstractTableInfo)
        {
            if (matches(table, "study", "Animal"))
            {
                appendMHCColumn((AbstractTableInfo)table);
            }
        }
    }

    private void appendMHCColumn(AbstractTableInfo ti)
    {
        String name = "mhcSummary";
        if (ti.getColumn(name) == null)
        {
            ColumnInfo col = getWrappedIdCol(ti.getUserSchema(), ti, name, "demographicsMHCTests");
            col.setLabel("MHC Test Summary");
            ti.addColumn(col);
        }

        String name2 = "dnaBank";
        if (ti.getColumn(name2) == null)
        {
            ColumnInfo col = getWrappedIdCol(ti.getUserSchema(), ti, name2, "demographicsDNABank");
            col.setLabel("DNA Bank Summary");
            ti.addColumn(col);
        }
    }

    private ColumnInfo getWrappedIdCol(UserSchema us, AbstractTableInfo ds, String name, String queryName)
    {
        String ID_COL = "Id";
        WrappedColumn col = new WrappedColumn(ds.getColumn(ID_COL), name);
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        col.setUserEditable(false);
        col.setFk(new QueryForeignKey(us, null, queryName, ID_COL, ID_COL));

        return col;
    }

    private boolean matches(TableInfo ti, String schema, String query)
    {
        return ti.getSchema().getName().equalsIgnoreCase(schema) && ti.getName().equalsIgnoreCase(query);
    }
}
