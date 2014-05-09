package org.labkey.mergesync;

import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.Set;

/**

 */
public class MergeSyncUserSchema extends SimpleUserSchema
{
    public static final String TABLE_MERGE_RESULTS = "merge_results";
    public static final String TABLE_MERGE_RUNS = "merge_runs";

    private MergeSyncUserSchema(User user, Container container, DbSchema schema)
    {
        super(MergeSyncSchema.NAME, null, user, container, schema);
    }

    public static void register(final Module m)
    {
        final DbSchema dbSchema = DbSchema.get(MergeSyncSchema.NAME);

        DefaultSchema.registerProvider(MergeSyncSchema.NAME, new DefaultSchema.SchemaProvider(m)
        {
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new MergeSyncUserSchema(schema.getUser(), schema.getContainer(), dbSchema);
            }
        });
    }

    @Override
    public Set<String> getTableNames()
    {
        Set<String> ret = new CaseInsensitiveTreeSet();
        ret.addAll(super.getTableNames());
        ret.add(TABLE_MERGE_RESULTS);
        ret.add(TABLE_MERGE_RUNS);

        return Collections.unmodifiableSet(ret);
    }

    @Override
    public synchronized Set<String> getVisibleTableNames()
    {
        return getTableNames();
    }

    @Override
    protected TableInfo createTable(String name)
    {
        if (TABLE_MERGE_RESULTS.equalsIgnoreCase(name))
        {
            DbSchema schema = MergeSyncManager.get().getMergeSchema();
            if (schema != null)
                return MergeSyncUserSchema.getMergeDataTable(schema);
        }
        else if (TABLE_MERGE_RUNS.equalsIgnoreCase(name))
        {
            DbSchema schema = MergeSyncManager.get().getMergeSchema();
            if (schema != null)
                return MergeSyncUserSchema.getMergeRunsTable(schema);
        }

        return super.createTable(name);
    }

    public static TableInfo getMergeDataTable(DbSchema schema)
    {
        return new VirtualTable(schema, TABLE_MERGE_RESULTS)
        {
            {
                setTitle("Merge Raw Result Data");
                setupColumns();
            }

            @Override
            public SQLFragment getFromSQL()
            {
                SQLFragment ret  = new SQLFragment();
                ret.append("select\n");
                ret.append("r.RE_FINAL as isFinal,\n");
                ret.append("t.TS_Stat as status,\n");

                //is this needed?  it doesnt seem to connect back to a user name.  USERS has very generic names
                //if there a differnet field holding user name?
                //--o.O_DOCTOR as orderedby,

                ret.append("o.O_ACCNUM as accession,\n");
                ret.append("t.TS_INDEX as panelId,\n");
                ret.append("p.Pt_Lname as animalId,\n");
                ret.append("IsNumeric(p.Pt_Lname) as numericLastName,\n");
                ret.append(MergeSyncUserSchema.getDateConversionSql("o.O_DATE", "o.O_DTZ") + " as date,\n");
                ret.append(MergeSyncUserSchema.getDateConversionSql("o.O_VDT", "o.O_VTZ") + " as dateVerified,\n");
                ret.append("i.INS_NAME as project,\n");
                ret.append(MergeSyncUserSchema.getDateConversionSql("o.O_COLLDT", "o.O_CLTZ") + " as datecollected,\n");
                ret.append(MergeSyncUserSchema.getDateConversionSql("r.RE_DATE", "r.RE_TZ") + " as runDate,\n");
                ret.append("ti.T_ABBR as servicename_abbr,\n");
                ret.append("ti.T_NAME as servicename,\n");
                ret.append("rs.RT_ABBR as testid_abbr,\n");
                ret.append("rs.RT_RDSCR as testid,\n");
                ret.append("r.re_data as text_result,\n");
                ret.append("r.RE_FLVAL as numeric_result,\n");
                ret.append("r.re_Text as remark,\n");
                ret.append("cm.RSC_COMMENT as runRemark\n");

                //one row per batch of orders
                ret.append("FROM Orders o\n");

                //many panels could be ordered at a time
                //TODO: what about failures and repeats?
                ret.append("LEFT JOIN tests t ON (t.TS_ACCNR = o.O_ACCNUM)\n");

                //contains reference info about that panel
                ret.append("LEFT JOIN TESTINFO ti ON (ti.T_TSTNUM = t.TS_TNUM)\n");

                //there will usually be many results per panel
                ret.append("LEFT JOIN results r ON (\n");
                ret.append("r.RE_ACCNR = o.O_ACCNUM\n");
                ret.append("AND r.RE_TIDX = t.TS_INDEX\n");
                //ret.append("--AND o.O_ptnum = r.re_ptnum\n");
                ret.append(")\n");

                //translate test name
                ret.append("LEFT JOIN RSLTTYP rs ON (rs.RT_RIDX = r.RE_RIDX)\n");

                //append result comment.  is this guaranteed 1:1 with results??
                ret.append("left join RESULT_COMMENTS cm on (\n");
                ret.append("cm.RSC_ACCNR = r.RE_ACCNR\n");
                ret.append("and cm.RSC_RIDX = r.RE_RIDX\n");
                ret.append("and cm.RSC_COMMENT != '>^'\n"); //this might mean something
                ret.append("and cm.RSC_COMMENT != '<^'\n"); //this might mean something
                ret.append(")\n");

                //there should only be 1 patient per order
                ret.append("LEFT JOIN patients p ON (\n");
                ret.append("o.O_PTNUM = p.PT_NUM\n");

                ret.append(")\n");

                ret.append("LEFT JOIN PRSNL pr ON (o.O_DOCTOR = pr.pr_num)\n");

                //there should only be 1 visit per order
                ret.append("LEFT JOIN VISITS v ON (o.O_VID = v.V_ID)\n");

                //join to visit/insurance
                ret.append("LEFT JOIN Vis_Ins vi ON (vi.VINS_VID = v.V_ID)\n");

                //join to insurance for project
                ret.append("left join INSURANCE i ON (i.INS_INDEX = vi.VINS_INS1)\n");

                return ret;
            }

            protected void setupColumns()
            {
                addColumn(new ExprColumn(this, "accession", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".accession"), JdbcType.INTEGER));
                addColumn(new ExprColumn(this, "panelId", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".panelId"), JdbcType.INTEGER));
                addColumn(new ExprColumn(this, "animalId", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".animalId"), JdbcType.VARCHAR));
                addColumn(new ExprColumn(this, "dateVerified", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".dateVerified"), JdbcType.TIMESTAMP));
                ColumnInfo dateCol = addColumn(new ExprColumn(this, "date", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".date"), JdbcType.TIMESTAMP));
                dateCol.setFormat("yyyy-MM-dd HH:mm");

                addColumn(new ExprColumn(this, "projectName", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".project"), JdbcType.VARCHAR));
                ColumnInfo dateCollectedCol = addColumn(new ExprColumn(this, "datecollected", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".datecollected"), JdbcType.TIMESTAMP));
                dateCollectedCol.setFormat("yyyy-MM-dd HH:mm");

                addColumn(new ExprColumn(this, "rundate", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".rundate"), JdbcType.TIMESTAMP));
                addColumn(new ExprColumn(this, "servicename_abbr", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".servicename_abbr"), JdbcType.VARCHAR));
                addColumn(new ExprColumn(this, "testid_abbr", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".testid_abbr"), JdbcType.VARCHAR));
                addColumn(new ExprColumn(this, "testid", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".testid"), JdbcType.VARCHAR));
                addColumn(new ExprColumn(this, "text_result", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".text_result"), JdbcType.VARCHAR));
                addColumn(new ExprColumn(this, "numeric_result", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".numeric_result"), JdbcType.DOUBLE));
                addColumn(new ExprColumn(this, "remark", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".remark"), JdbcType.VARCHAR));
                addColumn(new ExprColumn(this, "runRemark", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".runRemark"), JdbcType.VARCHAR));

                addColumn(new ExprColumn(this, "isFinal", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".isFinal"), JdbcType.VARCHAR));
                addColumn(new ExprColumn(this, "status", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".status"), JdbcType.VARCHAR));
                addColumn(new ExprColumn(this, "numericLastName", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".numericLastName"), JdbcType.BOOLEAN));
            }
        };
    }

    public static TableInfo getMergeRunsTable(DbSchema schema)
    {
        return new VirtualTable(schema, TABLE_MERGE_RUNS)
        {
            {
                setTitle("Merge Run Data");
                setupColumns();
            }

            @Override
            public SQLFragment getFromSQL()
            {
                SQLFragment ret  = new SQLFragment();
                ret.append("select\n");
                ret.append("t.TS_Stat as status,\n");

                ret.append("o.O_ACCNUM as accession,\n");
                ret.append("t.TS_INDEX as panelId,\n");
                ret.append("p.Pt_Lname as animalId,\n");
                ret.append("IsNumeric(p.Pt_Lname) as numericLastName,\n");
                ret.append(MergeSyncUserSchema.getDateConversionSql("o.O_DATE", "o.O_DTZ") + " as date,\n");
                ret.append(MergeSyncUserSchema.getDateConversionSql("o.O_VDT", "o.O_VTZ") + " as dateVerified,\n");
                ret.append("i.INS_NAME as project,\n");
                ret.append(MergeSyncUserSchema.getDateConversionSql("o.O_COLLDT", "o.O_CLTZ") + " as datecollected,\n");
                ret.append("ti.T_ABBR as servicename_abbr,\n");
                ret.append("ti.T_NAME as servicename\n");

                //one row per batch of orders
                ret.append("FROM Orders o\n");

                //many panels could be ordered at a time
                ret.append("LEFT JOIN tests t ON (t.TS_ACCNR = o.O_ACCNUM)\n");

                //contains reference info about that panel
                ret.append("LEFT JOIN TESTINFO ti ON (ti.T_TSTNUM = t.TS_TNUM)\n");

                //there should only be 1 patient per order
                ret.append("LEFT JOIN patients p ON (\n");
                ret.append("o.O_PTNUM = p.PT_NUM\n");
                ret.append(")\n");

                ret.append("LEFT JOIN PRSNL pr ON (o.O_DOCTOR = pr.pr_num)\n");

                //there should only be 1 visit per order
                ret.append("LEFT JOIN VISITS v ON (o.O_VID = v.V_ID)\n");

                //join to visit/insurance
                ret.append("LEFT JOIN Vis_Ins vi ON (vi.VINS_VID = v.V_ID)\n");

                //join to insurance for project
                ret.append("left join INSURANCE i ON (i.INS_INDEX = vi.VINS_INS1)\n");

                return ret;
            }

            protected void setupColumns()
            {
                addColumn(new ExprColumn(this, "accession", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".accession"), JdbcType.INTEGER));
                addColumn(new ExprColumn(this, "panelId", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".panelId"), JdbcType.INTEGER));
                addColumn(new ExprColumn(this, "animalId", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".animalId"), JdbcType.VARCHAR));
                addColumn(new ExprColumn(this, "dateVerified", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".dateVerified"), JdbcType.TIMESTAMP));
                addColumn(new ExprColumn(this, "date", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".date"), JdbcType.TIMESTAMP));
                addColumn(new ExprColumn(this, "projectName", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".project"), JdbcType.VARCHAR));
                addColumn(new ExprColumn(this, "datecollected", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".datecollected"), JdbcType.TIMESTAMP));
                addColumn(new ExprColumn(this, "servicename_abbr", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".servicename_abbr"), JdbcType.VARCHAR));
                addColumn(new ExprColumn(this, "status", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".status"), JdbcType.VARCHAR));
                addColumn(new ExprColumn(this, "numericLastName", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".numericLastName"), JdbcType.BOOLEAN));
            }
        };
    }

    private static String getDateConversionSql(String colName, String tzColName)
    {
        //i dont know why merge doesnt store these offsets with the correct sign...
        return "DATEADD(mi, (SELECT -1 * t.TZ_OFFSET FROM TIMEZONES t WHERE t.ROWID = " + tzColName + "), " + colName + ")";
    }
}
