package org.labkey.sequenceanalysis.query;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryForeignKey;

/**
 * User: bimber
 * Date: 5/19/13
 * Time: 3:17 PM
 */
public class AnalysesTableCustomizer implements TableCustomizer
{
    public AnalysesTableCustomizer()
    {

    }

    @Override
    public void customize(TableInfo ti)
    {
        if (ti instanceof AbstractTableInfo)
        {
            String alignmentIndex = "alignmentfileindex";
            if (ti.getColumn(alignmentIndex) == null)
            {
                SQLFragment sql = new SQLFragment("(SELECT MAX(d2.RowId) as indexId\n" +
                    "FROM sequenceanalysis.sequence_analyses sa\n" +
                    "JOIN exp.data d1 ON (d1.RowId = sa.alignmentFile)\n" +
                    "JOIN exp.Data d2 ON (d2.Name LIKE (" + ti.getSqlDialect().concatenate("d1.Name", "'%'") + "))\n" +
                    "JOIN exp.DataInput di ON (d2.RowId = di.DataId AND di.Role = 'Aligned Reads Index')\n" +
                    "WHERE sa.rowId = " + ExprColumn.STR_TABLE_ALIAS + ".RowId\n" +
                    "GROUP BY sa.rowid)");

                ExprColumn newCol = new ExprColumn(ti, alignmentIndex, sql, JdbcType.INTEGER, ti.getColumn("rowId"), ti.getColumn("alignmentFile"));
                newCol.setLabel("Alignment File Index");
                newCol.setFk(new QueryForeignKey("exp", getContainer(ti), null, ti.getUserSchema().getUser(), "data", "rowid", "name"));
                ((AbstractTableInfo)ti).addColumn(newCol);
            }
        }

        LaboratoryService.get().getLaboratoryTableCustomizer().customize(ti);
    }

    private Container getContainer(TableInfo ti)
    {
        Container c = ti.getUserSchema().getContainer();
        return c.isWorkbook() ? c.getParent() : c;
    }
}
