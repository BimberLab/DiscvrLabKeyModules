package org.labkey.sequenceanalysis.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

/**
 * The purpose of this table is to perform several important joins without the LK overhead of containers, providing a simpler pre-joined view of the data.
 */
public class AlignmentSummaryGroupedTableInfo extends AbstractTableInfo
{
    private final UserSchema _userSchema;

    public AlignmentSummaryGroupedTableInfo(UserSchema schema)
    {
        super(schema.getDbSchema(), SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_COMBINED);
        _userSchema = schema;

        setTitle("Alignment Summary Data Combined");
        setupColumns();
    }

    private void setupColumns()
    {
        MutableColumnInfo analysisIdCol = new BaseColumnInfo("analysis_id", this, JdbcType.BIGINT);
        analysisIdCol.setLabel("Analysis Id");
        //analysisIdCol.setFk(new QueryForeignKey.Builder(getUserSchema(), getContainerFilter()).schema(SequenceAnalysisSchema.SCHEMA_NAME).table(SequenceAnalysisSchema.TABLE_ANALYSES).key("rowid").display("rowid"));
        addColumn(analysisIdCol);

        MutableColumnInfo rowIdCol = new BaseColumnInfo("rowid", this, JdbcType.BIGINT);
        rowIdCol.setLabel("Alignment Id");
        //analysisIdCol.setFk(new QueryForeignKey.Builder(getUserSchema(), getContainerFilter()).schema(SequenceAnalysisSchema.SCHEMA_NAME).table(SequenceAnalysisSchema.TABLE_ANALYSES).key("rowid").display("rowid"));
        addColumn(rowIdCol);

        MutableColumnInfo readsetCol = new BaseColumnInfo("readset", this, JdbcType.BIGINT);
        readsetCol.setLabel("Readset");
        //readsetCol.setFk(new QueryForeignKey.Builder(getUserSchema(), getContainerFilter()).schema(SequenceAnalysisSchema.SCHEMA_NAME).table(SequenceAnalysisSchema.TABLE_READSETS).key("rowid").display("name"));
        addColumn(readsetCol);

        MutableColumnInfo ntId = new BaseColumnInfo("ref_nt_id", this, JdbcType.BIGINT);
        ntId.setLabel("NT Sequence");
        //ntId.setFk(new QueryForeignKey.Builder(getUserSchema(), getContainerFilter()).schema(SequenceAnalysisSchema.SCHEMA_NAME).table(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES).key("rowid").display("name"));
        addColumn(ntId);

        MutableColumnInfo ntName = new BaseColumnInfo("ntName", this, JdbcType.VARCHAR);
        ntName.setLabel("NT Sequence Name");
        addColumn(ntName);

        MutableColumnInfo ntLineage = new BaseColumnInfo("lineage", this, JdbcType.VARCHAR);
        ntLineage.setLabel("NT Lineage");
        addColumn(ntLineage);

        MutableColumnInfo ntLocus = new BaseColumnInfo("locus", this, JdbcType.VARCHAR);
        ntLocus.setLabel("NT Locus");
        addColumn(ntLocus);

        MutableColumnInfo totalCol = new BaseColumnInfo("total", this, JdbcType.BIGINT);
        totalCol.setLabel("Total Reads");
        addColumn(totalCol);

        MutableColumnInfo totalForwardCol = new BaseColumnInfo("total_forward", this, JdbcType.BIGINT);
        totalForwardCol.setLabel("Total Forward Reads");
        addColumn(totalForwardCol);

        MutableColumnInfo totalReverseCol = new BaseColumnInfo("total_reverse", this, JdbcType.BIGINT);
        totalReverseCol.setLabel("Total Reverse Reads");
        addColumn(totalReverseCol);

        MutableColumnInfo validPairsCol = new BaseColumnInfo("valid_pairs", this, JdbcType.BIGINT);
        validPairsCol.setLabel("Total Valid Pairs");
        addColumn(validPairsCol);

        MutableColumnInfo totalReadsInAnalysis = new BaseColumnInfo("total_reads_in_analysis", this, JdbcType.BIGINT);
        totalReadsInAnalysis.setLabel("Total Reads In Analysis");
        addColumn(totalReadsInAnalysis);

        SQLFragment sql = new SQLFragment("(SELECT sum(xs.total) as expr FROM (SELECT xs.total \n" +
                "FROM sequenceanalysis.sequence_analyses xa\n" +
                "JOIN sequenceanalysis.alignment_summary xs ON (xa.rowid = xs.analysis_id) \n" +
                "JOIN sequenceanalysis.alignment_summary_junction xasj ON (xasj.status = " + getSqlDialect().getBooleanTRUE() + " AND xs.rowid = xasj.alignment_id)\n" +
                "JOIN sequenceanalysis.ref_nt_sequences xnt ON (xnt.rowid = xasj.ref_nt_id)\n" +
                "WHERE xa.rowid = " + ExprColumn.STR_TABLE_ALIAS + ".analysis_id AND xnt.locus = " + ExprColumn.STR_TABLE_ALIAS + ".locus\n" +
                "GROUP BY xs.rowid, xs.total) xs)");

        ExprColumn totalReadsFromLocus = new ExprColumn(this, "total_reads_from_locus", sql, JdbcType.BIGINT, getColumn("analysis_id"), getColumn("locus"));
        totalReadsFromLocus.setLabel("Total Reads From Locus");
        addColumn(totalReadsFromLocus);

        MutableColumnInfo haplotypesWithAlleleCol = new BaseColumnInfo("haplotypesWithAllele", this, JdbcType.VARCHAR);
        haplotypesWithAlleleCol.setLabel("Haplotypes With Allele");
        addColumn(haplotypesWithAlleleCol);

        MutableColumnInfo containerCol = new BaseColumnInfo("container", this, JdbcType.GUID);
        containerCol.setLabel("Folder");
        containerCol.setFk(new QueryForeignKey.Builder(getUserSchema(), getContainerFilter()).schema("core").table("containers"));
        addColumn(containerCol);

        MutableColumnInfo createdCol = new BaseColumnInfo("created", this, JdbcType.TIMESTAMP);
        createdCol.setLabel("Created");
        addColumn(createdCol);

        MutableColumnInfo createdByCol = new BaseColumnInfo("createdby", this, JdbcType.BIGINT);
        createdByCol.setLabel("Created By");
        createdByCol.setFk(new QueryForeignKey.Builder(getUserSchema(), getContainerFilter()).schema("core").table("users").key("rowid"));
        addColumn(createdByCol);

        MutableColumnInfo modifiedCol = new BaseColumnInfo("modified", this, JdbcType.TIMESTAMP);
        modifiedCol.setLabel("Modified");
        addColumn(modifiedCol);

        MutableColumnInfo modifiedByCol = new BaseColumnInfo("modifiedby", this, JdbcType.BIGINT);
        modifiedByCol.setLabel("Modified By");
        modifiedByCol.setFk(new QueryForeignKey.Builder(getUserSchema(), getContainerFilter()).schema("core").table("users").key("rowid"));
        addColumn(modifiedByCol);
    }

    @Override
    protected SQLFragment getFromSQL()
    {
        final SqlDialect sd = getUserSchema().getDbSchema().getSqlDialect();

        return new SQLFragment("SELECT\n" +
                "a.rowid as analysis_id,\n" +
                "al.rowid,\n" +
                "a.readset,\n" +
                "nt.rowid as ref_nt_id,\n" +
                "nt.name as ntName,\n" +
                "nt.lineage as lineage,\n" +
                "nt.locus as locus,\n" +
                "a.container,\n" +
                "a.created,\n" +
                "a.createdBy,\n" +
                "a.modified,\n" +
                "a.modifiedBy,\n" +
                "al.total,\n" +
                "al.total_forward,\n" +
                "al.total_reverse,\n" +
                "al.valid_pairs,\n" +
                "(SELECT sum(s.total) as expr FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY + " s WHERE s.analysis_id = a.rowId) as total_reads_in_analysis,\n" +
                "(SELECT ").append(sd.getGroupConcat(new SQLFragment("hs.haplotype"), true, true, new SQLFragment("chr(10)"))).
                append(" as expr FROM sequenceanalysis.haplotype_sequences hs JOIN sequenceanalysis.haplotypes ht ON (hs.haplotype = ht.name) WHERE ht.datedisabled IS NULL AND ((hs.type = 'Lineage' AND hs.name = nt.lineage) OR (hs.type = 'Allele' AND hs.name = nt.name))) as haplotypesWithAllele\n" + "\n" +
                        "FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSES + " a\n" +
                        "JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY + " al ON (a.RowId = al.analysis_id)\n" +
                        "LEFT JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION + " asj ON (al.rowid = asj.alignment_id)\n" +
                        "LEFT JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES + " nt ON (nt.rowid = asj.ref_nt_id)\n" +
                        "WHERE asj.status = ").append(sd.getBooleanTRUE());
    }

    @Override
    public @NotNull UserSchema getUserSchema()
    {
        return _userSchema;
    }
}
