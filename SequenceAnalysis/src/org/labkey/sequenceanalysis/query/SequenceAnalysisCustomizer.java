package org.labkey.sequenceanalysis.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Set;

/**
 * Created by bimber on 1/4/2015.
 */
public class SequenceAnalysisCustomizer implements TableCustomizer
{
    @Override
    public void customize(TableInfo tableInfo)
    {
        if (tableInfo instanceof AbstractTableInfo ti)
        {
            LDKService.get().getDefaultTableCustomizer().customize(ti);

            if (tableInfo.getName().equalsIgnoreCase(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES))
            {
                //behaves slowly on postgres
                if (tableInfo.getSqlDialect().isSqlServer())
                {
                    LDKService.get().applyNaturalSort(ti, "name");
                }
            }
            else if (tableInfo.getName().equalsIgnoreCase(SequenceAnalysisSchema.TABLE_INSTRUMENT_RUNS))
            {
                if (tableInfo.getSqlDialect().isSqlServer())
                {
                    LDKService.get().applyNaturalSort(ti, "name");
                }
            }
            else if (tableInfo.getName().equalsIgnoreCase(SequenceAnalysisSchema.TABLE_OUTPUTFILES))
            {
                LaboratoryService.get().getLaboratoryTableCustomizer().customize(tableInfo);

                //behaves slowly on postgres
                if (tableInfo.getSqlDialect().isSqlServer())
                {
                    LDKService.get().applyNaturalSort(ti, "name");
                }

                addFileSetCol(ti);
            }

            customizeSharedCols(ti);
        }
    }

    private void addFileSetCol(AbstractTableInfo ti)
    {
        String name = "fileSets";
        if (ti.getColumn(name) == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT ").append(ti.getSqlDialect().getGroupConcat(new SQLFragment("s.name"), true, true, ",")).append(" FROM sequenceanalysis.analysisSetMembers m JOIN sequenceanalysis.analysisSets s ON (s.rowid = m.analysisSet) WHERE m.outputFileId = " + ExprColumn.STR_TABLE_ALIAS + ".rowId)");
            ExprColumn newCol = new ExprColumn(ti, name, sql, JdbcType.VARCHAR, ti.getColumn("rowId"));
            newCol.setLabel("File Sets");
            newCol.setDisplayColumnFactory(new FileSetDisplayColumnFactory());

            ti.addColumn(newCol);
        }
    }

    private void customizeSharedCols(AbstractTableInfo ti)
    {
        for (var col : ti.getMutableColumns())
        {
            COL_ENUM.processColumn(col, ti);
        }
    }

    private enum COL_ENUM
    {
        refNtSequence(Integer.class, PageFlowUtil.set("sequenceid", "ref_nt_sequence")){
            @Override
            public void customizeColumn(MutableColumnInfo col, AbstractTableInfo ti)
            {
                col.setLabel("Ref NT Sequence");
                addFk(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), col, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES, "rowid", "name");
            }
        },
        libraryId(Integer.class, PageFlowUtil.set("genomeId", "genome_id", "library_id")){
            @Override
            public void customizeColumn(MutableColumnInfo col, AbstractTableInfo ti)
            {
                col.setLabel("Reference Genome");
                addFk(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), col, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_REF_LIBRARIES, "rowid", "rowid");
            }
        },
        runid(Integer.class, PageFlowUtil.set("run_id")){
            @Override
            public void customizeColumn(MutableColumnInfo col, AbstractTableInfo ti)
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
            @Override
            public void customizeColumn(MutableColumnInfo col, AbstractTableInfo ti)
            {
                col.setLabel("Job Id");
                col.setShownInInsertView(false);
                col.setShownInInsertView(true);
                col.setUserEditable(false);
                addFk(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), col, "pipeline", "Job", "RowId", "RowId");
            }
        },
        dataId(Integer.class, PageFlowUtil.set("data_id")){
            @Override
            public void customizeColumn(MutableColumnInfo col, AbstractTableInfo ti)
            {
                col.setLabel("File Id");
                addFk(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), col, "exp", "data", "rowid", null);
            }
        },
        readset(Integer.class, PageFlowUtil.set("readsetId", "readset_id")){
            @Override
            public void customizeColumn(MutableColumnInfo col, AbstractTableInfo ti)
            {
                col.setLabel("Readset");
                addFk(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), col, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READSETS, "rowid", null);
            }
        },
        analysisId(Integer.class, PageFlowUtil.set("analysis_id")){
            @Override
            public void customizeColumn(MutableColumnInfo col, AbstractTableInfo ti)
            {
                col.setLabel("Analysis Id");
                addFk(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), col, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_ANALYSES, "rowid", null);
            }
        };

        private final Class dataType;
        private final Collection<String> alternateNames = new CaseInsensitiveHashSet();

        COL_ENUM(Class dataType, @Nullable Collection<String> alternateNames){
            this.dataType = dataType;
            if (alternateNames != null)
                this.alternateNames.addAll(alternateNames);
        }

        public Collection<String> getAlternateNames()
        {
            return alternateNames;
        }

        private static void setNonEditable(MutableColumnInfo col)
        {
            col.setUserEditable(false);
            col.setShownInInsertView(false);
            col.setShownInUpdateView(false);
        }

        private static void addFk(Container c, User u, MutableColumnInfo col, String schema, String query, String pkCol, @Nullable String displayCol)
        {
            if (col.getFk() == null)
            {
                col.setFk(QueryForeignKey.from(DefaultSchema.get(u,c),null)
                        .schema(schema, c)
                        .to(query, pkCol, displayCol));
            }
        }

        abstract public void customizeColumn(MutableColumnInfo col, AbstractTableInfo ti);

        public static void processColumn(MutableColumnInfo col, AbstractTableInfo ti)
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

    public static class FileSetDisplayColumnFactory implements DisplayColumnFactory
    {
        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new DataColumn(colInfo)
            {
                private boolean _handlerRegistered = false;

                @Override
                public @NotNull Set<ClientDependency> getClientDependencies()
                {
                    Set<ClientDependency> ret = super.getClientDependencies();
                    ret.add(ClientDependency.fromPath("/sequenceanalysis/window/ManageFileSetsWindow.js"));
                    ret.add(ClientDependency.fromPath("/sequenceanalysis/window/AddFileSetsWindow.js"));

                    return ret;
                }

                @Override
                public void addQueryFieldKeys(Set<FieldKey> keys)
                {
                    super.addQueryFieldKeys(keys);
                    keys.add(getBoundKey("rowId"));
                }

                private FieldKey getBoundKey(String colName)
                {
                    return new FieldKey(getBoundColumn().getFieldKey().getParent(), colName);
                }

                @Override
                public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                {
                    Integer rowId = ctx.get(getBoundKey("rowId"), Integer.class);
                    if (rowId != null)
                    {
                        String value = StringUtils.trimToNull(ctx.get(getBoundKey("fileSets"), String.class));
                        if (value != null)
                        {
                            String[] tokens = value.split(",");
                            String delim = "";
                            for (String token : tokens)
                            {
                                ActionURL url = QueryService.get().urlFor(ctx.getViewContext().getUser(), ctx.getContainer(), QueryAction.executeQuery, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_OUTPUTFILES);
                                url.addParameter("query.fileSets~contains", token);

                                out.write(delim + "<a href=\"" + url.getURIString() + "\"" + ">" + token + "</a>");
                                delim = ",<br>";
                            }
                        }

                        out.write("<a class=\"fa fa-pencil lk-dr-action-icon sfs-row\" data-tt=\"tooltip\" data-rowid=\"" + rowId +"\" data-original-title=\"add/edit\"></a>");

                        if (!_handlerRegistered)
                        {
                            HttpView.currentPageConfig().addHandlerForQuerySelector("a.sfs-row", "click", "SequenceAnalysis.window.ManageFileSetsWindow.buttonHandlerForOutputFiles(this.attributes.getNamedItem('data-rowid').value, " + PageFlowUtil.jsString(ctx.getCurrentRegion().getName()) + "); return false;");
                            _handlerRegistered = true;
                        }
                    }
                }
            };
        }
    }
}