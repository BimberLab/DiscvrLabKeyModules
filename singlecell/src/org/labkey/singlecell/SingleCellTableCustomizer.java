package org.labkey.singlecell;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;

public class SingleCellTableCustomizer extends AbstractTableCustomizer
{
    @Override
    public void customize(TableInfo table)
    {
        if (table instanceof AbstractTableInfo)
        {
            AbstractTableInfo ti = (AbstractTableInfo) table;
            if (matches(ti, SingleCellSchema.SEQUENCE_SCHEMA_NAME, SingleCellSchema.TABLE_READSETS))
            {
                customizeReadsets(ti);
            }
            else if (matches(ti, SingleCellSchema.NAME, SingleCellSchema.TABLE_SAMPLES))
            {
                customizeSamples(ti);
            }
            else if (matches(ti, SingleCellSchema.NAME, SingleCellSchema.TABLE_SORTS))
            {
                customizeSorts(ti);
            }
            else if (matches(ti, SingleCellSchema.NAME, SingleCellSchema.TABLE_CDNAS))
            {
                customizeCdnas(ti);
            }
            else if (matches(ti, SingleCellSchema.NAME, SingleCellSchema.TABLE_HASHING_LABELS))
            {
                LDKService.get().applyNaturalSort(ti, "name");
            }
            else if (matches(ti, SingleCellSchema.NAME, SingleCellSchema.TABLE_CITE_SEQ_ANTIBODIES))
            {
                LDKService.get().applyNaturalSort(ti, "antibodyName");
            }
        }
    }

    private void customizeCdnas(AbstractTableInfo ti)
    {
        String name = "hasReadsetWithData";
        if (ti.getColumn(name) == null)
        {
            SQLFragment sql = new SQLFragment("CASE " +
                    " WHEN (select count(*) as expr FROM sequenceanalysis.sequence_readsets r JOIN sequenceanalysis.readdata d ON (r.rowid = d.readset) WHERE r.rowid = " + ExprColumn.STR_TABLE_ALIAS + ".readsetId) > 0 THEN " + ti.getSqlDialect().getBooleanTRUE() +
                    " WHEN (select count(*) as expr FROM sequenceanalysis.sequence_readsets r JOIN sequenceanalysis.readdata d ON (r.rowid = d.readset) WHERE r.rowid = " + ExprColumn.STR_TABLE_ALIAS + ".tcrReadsetId) > 0 THEN " + ti.getSqlDialect().getBooleanTRUE() +
                    " WHEN (select count(*) as expr FROM sequenceanalysis.sequence_readsets r JOIN sequenceanalysis.readdata d ON (r.rowid = d.readset) WHERE r.rowid = " + ExprColumn.STR_TABLE_ALIAS + ".hashingReadsetId) > 0 THEN " + ti.getSqlDialect().getBooleanTRUE() +
                    " WHEN (select count(*) as expr FROM sequenceanalysis.sequence_readsets r JOIN sequenceanalysis.readdata d ON (r.rowid = d.readset) WHERE r.rowid = " + ExprColumn.STR_TABLE_ALIAS + ".citeseqReadsetId) > 0 THEN " + ti.getSqlDialect().getBooleanTRUE() +
                    " ELSE " + ti.getSqlDialect().getBooleanFALSE() + " END");

            ExprColumn newCol = new ExprColumn(ti, name, sql, JdbcType.BOOLEAN, ti.getColumn("readsetId"), ti.getColumn("tcrReadsetId"), ti.getColumn("hashingReadsetId"), ti.getColumn("citeseqReadsetId"));
            newCol.setLabel("Has Any Readset With Data?");
            ti.addColumn(newCol);
        }

        String name2 = "allReadsetsHaveData";
        if (ti.getColumn(name2) == null)
        {
            SQLFragment sql = new SQLFragment("CASE " +
                    " WHEN (" + ExprColumn.STR_TABLE_ALIAS + ".readsetId IS NOT NULL AND (select count(*) as expr FROM sequenceanalysis.sequence_readsets r JOIN sequenceanalysis.readdata d ON (r.rowid = d.readset) WHERE r.rowid = " + ExprColumn.STR_TABLE_ALIAS + ".readsetId) = 0) THEN " + ti.getSqlDialect().getBooleanFALSE() +
                    " WHEN (" + ExprColumn.STR_TABLE_ALIAS + ".tcrReadsetId IS NOT NULL AND (select count(*) as expr FROM sequenceanalysis.sequence_readsets r JOIN sequenceanalysis.readdata d ON (r.rowid = d.readset) WHERE r.rowid = " + ExprColumn.STR_TABLE_ALIAS + ".tcrReadsetId) = 0) THEN " + ti.getSqlDialect().getBooleanFALSE() +
                    " WHEN (" + ExprColumn.STR_TABLE_ALIAS + ".hashingReadsetId IS NOT NULL AND (select count(*) as expr FROM sequenceanalysis.sequence_readsets r JOIN sequenceanalysis.readdata d ON (r.rowid = d.readset) WHERE r.rowid = " + ExprColumn.STR_TABLE_ALIAS + ".hashingReadsetId) = 0) THEN " + ti.getSqlDialect().getBooleanFALSE() +
                    " WHEN (" + ExprColumn.STR_TABLE_ALIAS + ".citeseqReadsetId IS NOT NULL AND (select count(*) as expr FROM sequenceanalysis.sequence_readsets r JOIN sequenceanalysis.readdata d ON (r.rowid = d.readset) WHERE r.rowid = " + ExprColumn.STR_TABLE_ALIAS + ".citeseqReadsetId) = 0) THEN " + ti.getSqlDialect().getBooleanFALSE() +
                    " ELSE " + ti.getSqlDialect().getBooleanTRUE() + " END");

            ExprColumn newCol = new ExprColumn(ti, name2, sql, JdbcType.BOOLEAN, ti.getColumn("readsetId"), ti.getColumn("tcrReadsetId"), ti.getColumn("hashingReadsetId"), ti.getColumn("citeseqReadsetId"));
            newCol.setLabel("All Readsets Have Data?");
            ti.addColumn(newCol);
        }

        LDKService.get().applyNaturalSort(ti, "plateId");
    }

    private void customizeSorts(AbstractTableInfo ti)
    {
        LDKService.get().applyNaturalSort(ti, "plateId");
        LDKService.get().applyNaturalSort(ti, "hto");

        String name = "numLibraries";
        if (ti.getColumn(name) == null)
        {
            DetailsURL details = DetailsURL.fromString("/query/executeQuery.view?schemaName=singlecell&query.queryName=cdna_libraries&query.sortId~eq=${rowid}", (ti.getUserSchema().getContainer().isWorkbook() ? ti.getUserSchema().getContainer().getParent() : ti.getUserSchema().getContainer()));

            SQLFragment sql = new SQLFragment("(select count(*) as expr FROM " + SingleCellSchema.NAME + "." + SingleCellSchema.TABLE_CDNAS + " s WHERE s.sortId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ti, name, sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol.setLabel("# cDNA Libraries");
            newCol.setURL(details);
            ti.addColumn(newCol);
        }

        name = "maxCellsForPlate";
        if (ti.getColumn(name) == null)
        {
            SQLFragment sql = new SQLFragment("(select count(*) as expr FROM " + SingleCellSchema.NAME + "." + SingleCellSchema.TABLE_SORTS + " s WHERE s.plateId = " + ExprColumn.STR_TABLE_ALIAS + ".plateId AND s.container = " + ExprColumn.STR_TABLE_ALIAS + ".container)");
            ExprColumn newCol = new ExprColumn(ti, name, sql, JdbcType.INTEGER, ti.getColumn("plateId"), ti.getColumn("container"));
            newCol.setLabel("Max Cells/Well In Plate");
            ti.addColumn(newCol);
        }
    }

    private void customizeSamples(AbstractTableInfo ti)
    {
        String name = "numSorts";
        if (ti.getColumn(name) == null)
        {
            DetailsURL details = DetailsURL.fromString("/query/executeQuery.view?schemaName=singlecell&query.queryName=sorts&query.sampleId~eq=${rowid}", (ti.getUserSchema().getContainer().isWorkbook() ? ti.getUserSchema().getContainer().getParent() : ti.getUserSchema().getContainer()));

            SQLFragment sql = new SQLFragment("(select count(*) as expr FROM " + SingleCellSchema.NAME + "." + SingleCellSchema.TABLE_SORTS + " s WHERE s.sampleId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ti, "numSorts", sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol.setLabel("# Sorts");
            newCol.setURL(details);
            ti.addColumn(newCol);
        }

        name = "numlibraries";
        if (ti.getColumn(name) == null)
        {
            DetailsURL details = DetailsURL.fromString("/query/executeQuery.view?schemaName=singlecell&query.queryName=cdna_libraries&query.sortId/sampleId~eq=${rowid}", (ti.getUserSchema().getContainer().isWorkbook() ? ti.getUserSchema().getContainer().getParent() : ti.getUserSchema().getContainer()));

            SQLFragment sql = new SQLFragment("(select count(c.rowid) as expr FROM " + SingleCellSchema.NAME + "." + SingleCellSchema.TABLE_SORTS + " so JOIN " + SingleCellSchema.NAME + "." + SingleCellSchema.TABLE_CDNAS + " c ON (so.rowid = c.sortId) WHERE so.sampleId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ti, "numLibraries", sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol.setLabel("# cDNA Libraries");
            newCol.setURL(details);
            ti.addColumn(newCol);
        }
    }

    private void customizeReadsets(AbstractTableInfo ti)
    {
        String name = "numTCRLibraries";
        if (ti.getColumn(name) == null)
        {
            SQLFragment sql = new SQLFragment("(select count(*) as expr FROM " + SingleCellSchema.NAME + "." + SingleCellSchema.TABLE_CDNAS + " c WHERE c.readsetId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid OR c.tcrReadsetId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ti, name, sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol.setLabel("# TCR Libraries");
            ti.addColumn(newCol);
        }

        String cDNA = "cDNA";
        if (ti.getColumn(cDNA) == null)
        {
            SQLFragment sql = new SQLFragment("(CASE" +
                    " WHEN ((select count(*) as expr FROM " + SingleCellSchema.NAME + "." + SingleCellSchema.TABLE_CDNAS + " c WHERE c.readsetId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid OR c.tcrReadsetId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid) > 0) " +
                    " THEN (select max(c.rowid) FROM " + SingleCellSchema.NAME + "." + SingleCellSchema.TABLE_CDNAS + " c WHERE c.readsetId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid OR c.tcrReadsetId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid) " +
                    " ELSE null " +
                    "END)");
            ExprColumn newCol = new ExprColumn(ti, cDNA, sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol.setLabel("cDNA Library");
            UserSchema us = QueryService.get().getUserSchema(ti.getUserSchema().getUser(), (ti.getUserSchema().getContainer().isWorkbook() ? ti.getUserSchema().getContainer().getParent() : ti.getUserSchema().getContainer()), SingleCellSchema.NAME);
            newCol.setFk(QueryForeignKey.from(us, ti.getContainerFilter())
                    .table(SingleCellSchema.TABLE_CDNAS)
                    .key("rowid")
                    .display("rowid"));
            ti.addColumn(newCol);
        }

        String totalCells = "totalCells";
        if (ti.getColumn(totalCells) == null)
        {
            SQLFragment sql = new SQLFragment("(CASE" +
                    " WHEN ((select count(*) as expr FROM " + SingleCellSchema.NAME + "." + SingleCellSchema.TABLE_CDNAS + " c WHERE c.readsetId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid OR c.tcrReadsetId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid) > 0) " +
                    " THEN (select sum(s.cells) FROM " + SingleCellSchema.NAME + "." + SingleCellSchema.TABLE_CDNAS + " c JOIN " + SingleCellSchema.NAME + "." + SingleCellSchema.TABLE_SORTS + " s ON (c.sortid = s.rowid) WHERE c.readsetId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid OR c.tcrReadsetId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid) " +
                    " ELSE null " +
                    "END)");
            ExprColumn newCol = new ExprColumn(ti, totalCells, sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol.setLabel("cDNA/Total Cells");
            UserSchema us = QueryService.get().getUserSchema(ti.getUserSchema().getUser(), (ti.getUserSchema().getContainer().isWorkbook() ? ti.getUserSchema().getContainer().getParent() : ti.getUserSchema().getContainer()), SingleCellSchema.NAME);
            newCol.setFk(QueryForeignKey.from(us, ti.getContainerFilter())
                    .table(SingleCellSchema.TABLE_CDNAS)
                    .key("rowid")
                    .display("rowid"));
            ti.addColumn(newCol);
        }
    }
}