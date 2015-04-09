package org.labkey.sequenceanalysis.query;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryForeignKey;

/**
 * User: bimber
 * Date: 5/19/13
 * Time: 3:17 PM
 */
public class JobsTableCustomizer implements TableCustomizer
{
    public JobsTableCustomizer()
    {

    }

    @Override
    public void customize(TableInfo ti)
    {
        if (ti instanceof AbstractTableInfo)
        {
            String totalReadsets = "totalReadsets";
            if (ti.getColumn(totalReadsets) == null)
            {
                ExprColumn newCol = new ExprColumn(ti, totalReadsets, getSql("sequence_readsets"), JdbcType.INTEGER, ti.getColumn("RowId"));
                newCol.setLabel("Readsets From This Job");
                ((AbstractTableInfo)ti).addColumn(newCol);
            }

            String totalAnalyses = "totalAnalyses";
            if (ti.getColumn(totalAnalyses) == null)
            {
                ExprColumn newCol = new ExprColumn(ti, totalAnalyses, getSql("sequence_analyses"), JdbcType.INTEGER, ti.getColumn("RowId"));
                newCol.setLabel("Analyses From This Job");
                ((AbstractTableInfo)ti).addColumn(newCol);
            }

            String totalOutputs = "totalOutputs";
            if (ti.getColumn(totalOutputs) == null)
            {
                ExprColumn newCol = new ExprColumn(ti, totalOutputs, getSql("outputfiles"), JdbcType.INTEGER, ti.getColumn("RowId"));
                newCol.setLabel("Output Files From This Job");
                ((AbstractTableInfo)ti).addColumn(newCol);
            }

            String hasSequenceData = "hasSequenceData";
            if (ti.getColumn(hasSequenceData) == null)
            {
                SQLFragment sql = new SQLFragment("(SELECT CASE WHEN (COUNT(distinct rs.rowid) > 0 OR COUNT(distinct sa.rowid) > 0 OR COUNT(distinct o.rowid) > 0) THEN " + ti.getSqlDialect().getBooleanTRUE() + " ELSE " + ti.getSqlDialect().getBooleanFALSE() + " END as expr\n" +
                        "FROM exp.experimentrun r\n" +
                        "LEFT JOIN sequenceanalysis.sequence_readsets rs ON (r.RowId = rs.runid)\n" +
                        "LEFT JOIN sequenceanalysis.sequence_analyses sa ON (r.RowId = sa.runid)\n" +
                        "LEFT JOIN sequenceanalysis.outputfiles o ON (r.RowId = o.runid)\n" +
                        "WHERE (r.jobid = " + ExprColumn.STR_TABLE_ALIAS + ".RowId OR r.rowid = (SELECT p.RowId FROM pipeline.statusfiles p WHERE " + ExprColumn.STR_TABLE_ALIAS + ".jobparent = p.job))\n" +
                        ")");
                ExprColumn newCol = new ExprColumn(ti, hasSequenceData, sql, JdbcType.BOOLEAN, ti.getColumn("RowId"));
                newCol.setLabel("Has Sequence Data?");
                ((AbstractTableInfo)ti).addColumn(newCol);
            }
        }
    }

    private SQLFragment getSql(String tableName)
    {
        return new SQLFragment("(SELECT COUNT(distinct rs.rowid) as expr\n" +
                "FROM exp.experimentrun r\n" +
                "JOIN sequenceanalysis." + tableName + " rs ON (r.RowId = rs.runid)\n" +
                "WHERE (r.jobid = " + ExprColumn.STR_TABLE_ALIAS + ".RowId OR r.jobid = (SELECT p.RowId FROM pipeline.statusfiles p WHERE " + ExprColumn.STR_TABLE_ALIAS + ".jobparent = p.job))\n" +
                ")");
    }
}
