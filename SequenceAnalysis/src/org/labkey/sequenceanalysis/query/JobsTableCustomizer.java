package org.labkey.sequenceanalysis.query;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.query.ExprColumn;

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
                //NOTE: the readset can technically include data from multiple pipeline jobs
                ExprColumn newCol = new ExprColumn(ti, totalReadsets, getReadsetSql(), JdbcType.INTEGER, ti.getColumn("RowId"));
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

            String totalRuns = "totalRuns";
            if (ti.getColumn(totalRuns) == null)
            {
                ExprColumn newCol = new ExprColumn(ti, totalRuns, new SQLFragment("(SELECT count(*) as _expr FROM exp.experimentrun r WHERE r.jobid = " + ExprColumn.STR_TABLE_ALIAS + ".RowId OR r.jobid = (SELECT p.RowId FROM pipeline.statusfiles p WHERE " + ExprColumn.STR_TABLE_ALIAS + ".jobparent = p.job))"), JdbcType.INTEGER, ti.getColumn("RowId"));
                newCol.setLabel("ExpRuns Using This Job");
                ((AbstractTableInfo)ti).addColumn(newCol);
            }

            String hasSequenceData = "hasSequenceData";
            if (ti.getColumn(hasSequenceData) == null)
            {
                SQLFragment sql = new SQLFragment("(SELECT CASE " +
                        "WHEN sum(CASE WHEN (rs.RowId IS NULL AND rd.RowId IS NULL AND sa.RowId IS NULL AND o.RowId IS NULL) THEN 0 ELSE 1 END) = 0 THEN " + ti.getSqlDialect().getBooleanFALSE() + "\n" +
                        "WHEN sum(CASE WHEN (rs.RowId IS NULL AND rd.RowId IS NULL AND sa.RowId IS NULL AND o.RowId IS NULL) THEN 0 ELSE 1 END) IS NULL THEN " + ti.getSqlDialect().getBooleanFALSE() + "\n" +
                        "ELSE " + ti.getSqlDialect().getBooleanTRUE() + " END as expr\n" +
                        "FROM exp.experimentrun r\n" +
                        "LEFT JOIN sequenceanalysis.sequence_readsets rs ON (r.RowId = rs.runid)\n" +
                        "LEFT JOIN sequenceanalysis.readdata rd on (r.RowId = rd.runid)\n" +
                        "LEFT JOIN sequenceanalysis.sequence_analyses sa ON (r.RowId = sa.runid)\n" +
                        "LEFT JOIN sequenceanalysis.outputfiles o ON (r.RowId = o.runid)\n" +
                        "WHERE (r.jobid = " + ExprColumn.STR_TABLE_ALIAS + ".RowId OR r.jobid = (SELECT p.RowId FROM pipeline.statusfiles p WHERE " + ExprColumn.STR_TABLE_ALIAS + ".jobparent = p.job))\n" +
                        ")");
                ExprColumn newCol = new ExprColumn(ti, hasSequenceData, sql, JdbcType.BOOLEAN, ti.getColumn("RowId"));
                newCol.setLabel("Has Sequence Data?");
                ((AbstractTableInfo)ti).addColumn(newCol);
            }

            LDKService.get().getDefaultTableCustomizer().customize(ti);
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

    private SQLFragment getReadsetSql()
    {
        return new SQLFragment("(SELECT COUNT(distinct t.readset) as expr\n" +
                "FROM (\n" +
                "SELECT rs.rowid as readset\n" +
                "FROM exp.experimentrun r\n" +
                "JOIN sequenceanalysis.sequence_readsets rs ON (r.RowId = rs.runid)\n" +
                "WHERE (r.jobid = " + ExprColumn.STR_TABLE_ALIAS + ".RowId OR r.jobid = (SELECT p.RowId FROM pipeline.statusfiles p WHERE " + ExprColumn.STR_TABLE_ALIAS + ".jobparent = p.job))\n" +
                "UNION ALL\n" +
                "SELECT rd.readset " +
                "FROM exp.experimentrun r\n" +
                "JOIN sequenceanalysis.readdata rd ON (r.RowId = rd.runid)\n" +
                "WHERE (r.jobid = " + ExprColumn.STR_TABLE_ALIAS + ".RowId OR r.jobid = (SELECT p.RowId FROM pipeline.statusfiles p WHERE " + ExprColumn.STR_TABLE_ALIAS + ".jobparent = p.job))\n" +
                ") t\n" +
                ")");
    }
}
