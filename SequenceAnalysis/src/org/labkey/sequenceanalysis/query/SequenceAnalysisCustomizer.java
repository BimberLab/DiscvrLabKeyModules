package org.labkey.sequenceanalysis.query;

import com.drew.lang.annotations.Nullable;
import org.labkey.api.arrays.IntegerArray;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.sql.Timestamp;
import java.util.Collection;

/**
 * Created by bimber on 1/4/2015.
 */
public class SequenceAnalysisCustomizer implements TableCustomizer
{
    @Override
    public void customize(TableInfo tableInfo)
    {
        if (tableInfo instanceof AbstractTableInfo)
        {
            AbstractTableInfo ti = (AbstractTableInfo)tableInfo;
            LDKService.get().getDefaultTableCustomizer().customize(ti);

            if (tableInfo.getName().equalsIgnoreCase(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES))
            {
                //behaves slowly on postgres
                if (tableInfo.getSqlDialect().isSqlServer())
                {
                    LDKService.get().applyNaturalSort(ti, "name");
                }
            }

            customizeSharedCols(ti);
        }
    }

    private void customizeSharedCols(AbstractTableInfo ti)
    {
        for (ColumnInfo col : ti.getColumns())
        {
            COL_ENUM.processColumn(col, ti);
        }
    }

    private enum COL_ENUM
    {
        refNtSequence(Integer.class, PageFlowUtil.set("sequenceid", "ref_nt_sequence")){
            public void customizeColumn(ColumnInfo col, AbstractTableInfo ti)
            {
                col.setLabel("Ref NT Sequence");
                addFk(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), col, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES, "rowid", "name");
            }
        },
        libraryId(Integer.class, PageFlowUtil.set("genomeId", "genome_id", "library_id")){
            public void customizeColumn(ColumnInfo col, AbstractTableInfo ti)
            {
                col.setLabel("Reference Genome");
                addFk(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), col, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_LIBRARIES, "rowid", "rowid");
            }
        },
        runid(Integer.class, PageFlowUtil.set("run_id")){
            public void customizeColumn(ColumnInfo col, AbstractTableInfo ti)
            {
                col.setLabel("Run");
                col.setShownInInsertView(false);
                col.setShownInInsertView(true);
                col.setUserEditable(false);
                col.setURL(DetailsURL.fromString("/experiment/showRunGraphDetail.view?rowId=${runid}"));
                addFk(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), col, "exp", "runs", "RowId", "RowId");
            }
        },
        jobid(Integer.class, PageFlowUtil.set("job_id")){
            public void customizeColumn(ColumnInfo col, AbstractTableInfo ti)
            {
                col.setLabel("Job Id");
                col.setShownInInsertView(false);
                col.setShownInInsertView(true);
                col.setUserEditable(false);
                addFk(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), col, "pipeline", "Job", "RowId", "RowId");
            }
        },
        dataId(Integer.class, PageFlowUtil.set("data_id")){
            public void customizeColumn(ColumnInfo col, AbstractTableInfo ti)
            {
                col.setLabel("File Id");
                addFk(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), col, "exp", "data", "rowid", null);
            }
        },
        readset(Integer.class, PageFlowUtil.set("readsetId", "readset_id")){
            public void customizeColumn(ColumnInfo col, AbstractTableInfo ti)
            {
                col.setLabel("Readset");
                addFk(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), col, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READSETS, "rowid", null);
            }
        },
        analysisId(Integer.class, PageFlowUtil.set("analysis_id")){
            public void customizeColumn(ColumnInfo col, AbstractTableInfo ti)
            {
                col.setLabel("Analysis Id");
                addFk(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), col, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_ANALYSES, "rowid", null);
            }
        };

        private Class dataType;
        private Collection<String> alternateNames = new CaseInsensitiveHashSet();

        COL_ENUM(Class dataType, @Nullable Collection<String> alternateNames){
            this.dataType = dataType;
            if (alternateNames != null)
            this.alternateNames.addAll(alternateNames);
        }

        public Collection<String> getAlternateNames()
        {
            return alternateNames;
        }

        private static void setNonEditable(ColumnInfo col)
        {
            col.setUserEditable(false);
            col.setShownInInsertView(false);
            col.setShownInUpdateView(false);
        }

        private static void addFk(Container c, User u, ColumnInfo col, String schema, String query, String pkCol, @Nullable String displayCol)
        {
            if (col.getFk() == null)
            {
                col.setFk(new QueryForeignKey(schema, c, null, u, query, pkCol, displayCol));
            }
        }

        abstract public void customizeColumn(ColumnInfo col, AbstractTableInfo ti);

        public static void processColumn(ColumnInfo col, AbstractTableInfo ti)
        {
            for (COL_ENUM colEnum : COL_ENUM.values())
            {
                if (colEnum.name().equalsIgnoreCase(col.getName()) || colEnum.getAlternateNames().contains(col.getName()))
                {
                    if (col.getJdbcType().getJavaClass() == colEnum.dataType)
                    {
                        colEnum.customizeColumn(col, ti);
                    }

                    if (col.isAutoIncrement())
                    {
                        col.setUserEditable(false);
                        col.setShownInInsertView(false);
                        col.setShownInUpdateView(false);
                    }

                    break;
                }
            }
        }
    }
}
