package org.labkey.GeneticsCore;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;

import java.util.Collections;
import java.util.List;

/**
 * Created by bimber on 12/1/2014.
 */
public class GeneticsTableCustomizer extends AbstractTableCustomizer implements TableCustomizer
{
    @Override
    public void customize(TableInfo table)
    {
        if (table instanceof AbstractTableInfo)
        {
            AbstractTableInfo ti = (AbstractTableInfo) table;
            if (matches(ti, "sequenceanalysis", "sequence_analyses"))
            {
                customizeAnalyses(ti);
            }
            else if (matches(ti, "sequenceanalysis", "alignment_summary_by_lineage"))
            {
                customizeSummaryByLineage(ti);
            }
            else if (matches(ti, "sequenceanalysis", "alignment_summary_grouped"))
            {
                customizeSummaryGrouped(ti);
            }
        }
    }

    private void customizeAnalyses(AbstractTableInfo ti)
    {
        if (ti.getColumn("numUnmappedSbtReads") == null)
        {
            //# unmapped
            SQLFragment sql = new SQLFragment("(select SUM(a.total) from sequenceanalysis.alignment_summary a left join sequenceanalysis.alignment_summary_junction j ON (a.rowid = j.alignment_id AND a.analysis_id = j.analysis_id) WHERE j.alignment_id is null AND a.analysis_id = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ti, "numUnmappedSbtReads", sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol.setLabel("# Unmapped SBT Reads");
            ti.addColumn(newCol);

            //total reads
            SQLFragment sql2 = new SQLFragment("(select SUM(a.total) from sequenceanalysis.alignment_summary a WHERE a.analysis_id = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol2 = new ExprColumn(ti, "totalSbtReads", sql2, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol2.setLabel("Total SBT Reads");
            ti.addColumn(newCol2);

            //pct unmapped
            SQLFragment sql3 = new SQLFragment("(select CASE WHEN SUM(a.total) > 0 THEN CAST(SUM(CASE WHEN j.alignment_id is null THEN a.total ELSE 0 END) as DOUBLE PRECISION) / CAST(SUM(a.total) as DOUBLE PRECISION) ELSE NULL END from sequenceanalysis.alignment_summary a left join sequenceanalysis.alignment_summary_junction j ON (a.rowid = j.alignment_id AND a.analysis_id = j.analysis_id) WHERE a.analysis_id = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol3 = new ExprColumn(ti, "pctUnmappedSbtReads", sql3, JdbcType.DOUBLE, ti.getColumn("rowid"));
            newCol3.setLabel("% Unmapped SBT Reads");
            newCol3.setFormat("#.##%");
            ti.addColumn(newCol3);

            //# disabled
            SQLFragment sql4 = new SQLFragment("(select count(*) as expr from sequenceanalysis.alignment_summary_junction j WHERE j.status = 0 AND j.analysis_id = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol4 = new ExprColumn(ti, "numReadsDisabled", sql4, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol.setLabel("# Allele Calls Disabled");
            ti.addColumn(newCol4);

            //lastModified
            SQLFragment lastModifiedSql = new SQLFragment("(select max(modified) as expr from sequenceanalysis.alignment_summary_junction j WHERE j.analysis_id = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn lastModifiedCol = new ExprColumn(ti, "allelesLastModified", lastModifiedSql, JdbcType.TIMESTAMP, ti.getColumn("rowid"));
            lastModifiedCol.setLabel("Date Alleles Last Modified");
            ti.addColumn(lastModifiedCol);
        }

        addAssayFieldsToAnalyses(ti);
    }

    private void addAssayFieldsToAnalyses(AbstractTableInfo ti)
    {
        if (ti.getColumn("numCachedResults") == null)
        {
            AssayProvider ap = AssayService.get().getProvider("Genotype Assay");
            if (ap == null)
            {
                return;
            }

            List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(ti.getUserSchema().getContainer(), ap);
            if (protocols.size() != 1)
            {
                return;
            }

            AssayProtocolSchema schema = ap.createProtocolSchema(ti.getUserSchema().getUser(), ti.getUserSchema().getContainer(), protocols.get(0), null);
            TableInfo data = schema.getTable("data");

            SQLFragment selectSql = QueryService.get().getSelectSQL(data, Collections.singleton(data.getColumn("analysisId")), new SimpleFilter(FieldKey.fromString("run/assayType"), GeneticsCoreManager.SBT_LINEAGE_ASSAY_TYPE), null, 999999, 0, false);
            SQLFragment sql = new SQLFragment("(select count(*) FROM (").append(selectSql).append(") a WHERE a.analysisId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ti, "numCachedResults", sql, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol.setLabel("# Cached Lineages");
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=assay." + ap.getName().replaceAll(" ", "") + "." + protocols.get(0).getName() + "&query.queryName=data&query.analysisId~eq=${rowid}&query.run/assayType~eq=" + GeneticsCoreManager.SBT_LINEAGE_ASSAY_TYPE, (ti.getUserSchema().getContainer().isWorkbook() ? ti.getUserSchema().getContainer().getParent() : ti.getUserSchema().getContainer())));
            ti.addColumn(newCol);

            SQLFragment selectSql2 = QueryService.get().getSelectSQL(data, Collections.singleton(data.getColumn("analysisId")), new SimpleFilter(FieldKey.fromString("run/assayType"), GeneticsCoreManager.HAPLOTYPE_ASSAY_TYPE), null, 999999, 0, false);
            SQLFragment sql2 = new SQLFragment("(select count(*) FROM (").append(selectSql2).append(") a WHERE a.analysisId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol2 = new ExprColumn(ti, "numCachedHaplotypes", sql2, JdbcType.INTEGER, ti.getColumn("rowid"));
            newCol2.setLabel("# Cached Haplotypes");
            newCol2.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=assay." + ap.getName().replaceAll(" ", "") + "." + protocols.get(0).getName() + "&query.queryName=data&query.analysisId~eq=${rowid}&query.run/assayType~eq=" + GeneticsCoreManager.HAPLOTYPE_ASSAY_TYPE, (ti.getUserSchema().getContainer().isWorkbook() ? ti.getUserSchema().getContainer().getParent() : ti.getUserSchema().getContainer())));
            ti.addColumn(newCol2);
        }
    }

    private void customizeSummaryByLineage(AbstractTableInfo ti)
    {
        if (ti.getColumn("numCachedResults") == null)
        {
            AssayProvider ap = AssayService.get().getProvider("Genotype Assay");
            if (ap == null)
            {
                return;
            }

            List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(ti.getUserSchema().getContainer(), ap);
            if (protocols.size() != 1)
            {
                return;
            }

            Domain d = ap.getResultsDomain(protocols.get(0));
            SQLFragment sql = new SQLFragment("CASE WHEN (select count(*) FROM assayresult." + d.getStorageTableName() + " a WHERE a.analysisId = " + ExprColumn.STR_TABLE_ALIAS + ".analysis_id AND a.marker = " + ExprColumn.STR_TABLE_ALIAS + ".lineages) > 0 THEN 'Y' ELSE null END");
            ExprColumn newCol = new ExprColumn(ti, "numCachedResults", sql, JdbcType.VARCHAR, ti.getColumn("analysis_id"));
            newCol.setLabel("Is Cached?");
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=assay." + ap.getName().replaceAll(" ", "") + "." + protocols.get(0).getName() + "&query.queryName=data&query.analysisId~eq=${analysis_id}", (ti.getUserSchema().getContainer().isWorkbook() ? ti.getUserSchema().getContainer().getParent() : ti.getUserSchema().getContainer())));
            ti.addColumn(newCol);

            String chr = ti.getSqlDialect().isPostgreSQL() ? "chr" : "char";
            SQLFragment sql3 = new SQLFragment("(select ").append(ti.getSqlDialect().getGroupConcat(new SQLFragment("hs.haplotype"), true, true, chr + "(10)")).append(" as expr FROM assayresult." + d.getStorageTableName() + " a JOIN sequenceanalysis.haplotype_sequences hs ON (a.marker = hs.haplotype AND hs.type = 'Lineage' AND hs.present = " + ti.getSqlDialect().getBooleanTRUE() + ") WHERE a.analysisId = " + ExprColumn.STR_TABLE_ALIAS + ".analysis_id AND a.result IS NULL AND hs.name = " + ExprColumn.STR_TABLE_ALIAS + ".lineages)");
            ExprColumn newCol3 = new ExprColumn(ti, "cachedHaplotypes", sql3, JdbcType.VARCHAR, ti.getColumn("analysis_id"), ti.getColumn("lineages"));
            newCol3.setLabel("Matching Cached Haplotypes");
            ti.addColumn(newCol3);
        }
    }

    private void customizeSummaryGrouped(AbstractTableInfo ti)
    {
        if (ti.getColumn("cachedHaplotypes") == null)
        {
            AssayProvider ap = AssayService.get().getProvider("Genotype Assay");
            if (ap == null)
            {
                return;
            }

            List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(ti.getUserSchema().getContainer(), ap);
            if (protocols.size() != 1)
            {
                return;
            }

            Domain d = ap.getResultsDomain(protocols.get(0));
            String chr = ti.getSqlDialect().isPostgreSQL() ? "chr" : "char";
            SQLFragment sql3 = new SQLFragment("(select ").append(ti.getSqlDialect().getGroupConcat(new SQLFragment("hs.haplotype"), true, true, chr + "(10)")).append(" as expr FROM assayresult." + d.getStorageTableName() + " a JOIN sequenceanalysis.haplotype_sequences hs ON (a.marker = hs.haplotype AND hs.type = 'Lineage' AND hs.present = " + ti.getSqlDialect().getBooleanTRUE() + ") WHERE a.analysisId = " + ExprColumn.STR_TABLE_ALIAS + ".analysis_id AND a.result IS NULL AND hs.name = " + ExprColumn.STR_TABLE_ALIAS + ".lineages)");
            ExprColumn newCol3 = new ExprColumn(ti, "cachedHaplotypes", sql3, JdbcType.VARCHAR, ti.getColumn("analysis_id"), ti.getColumn("lineages"));
            newCol3.setLabel("Matching Cached Haplotypes");
            ti.addColumn(newCol3);
        }
    }
}
