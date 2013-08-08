package org.labkey.laboratory.query;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.query.ExprColumn;

import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 11/9/12
 * Time: 9:21 AM
 */
public class SamplesCustomizer implements TableCustomizer
{
    public SamplesCustomizer()
    {

    }

    public void customize(TableInfo ti)
    {
        //apply defaults
        TableCustomizer tc = LDKService.get().getDefaultTableCustomizer();
        tc.customize(ti);

        if (ti instanceof AbstractTableInfo)
        {
            //appendAmountColumn((AbstractTableInfo)ti);
        }

        //this customizer also sorts columns, so we append amount first
        TableCustomizer tc2 = new LaboratoryTableCustomizer();
        tc2.customize(ti);
    }

    private void appendAmountColumn(AbstractTableInfo ti)
    {
        if (ti.getColumn("amount") == null)
        {
            ColumnInfo conc = ti.getColumn("concentration");
            ColumnInfo quantity = ti.getColumn("quantity");
            SQLFragment sql = new SQLFragment("(" + ExprColumn.STR_TABLE_ALIAS +".concentration * " + ExprColumn.STR_TABLE_ALIAS + ".quantity)");
            ExprColumn col = new ExprColumn(ti, "amount", sql, JdbcType.DOUBLE, conc, quantity);
            col.setLabel("Amount");
            col.setDescription("This field takes the concentration multiplied by the quantity fields.  It is automatically calculated and does not take units or other information into account.");

            //inject amount column after quantity.
            List<ColumnInfo> columns = new ArrayList<ColumnInfo>();
            columns.addAll(ti.getColumns());
            for (ColumnInfo c : columns)
                ti.removeColumn(c);

            int idx = columns.indexOf(quantity);
            columns.add(idx + 1, col);
            for (ColumnInfo c : columns)
                ti.addColumn(c);
        }
    }
}