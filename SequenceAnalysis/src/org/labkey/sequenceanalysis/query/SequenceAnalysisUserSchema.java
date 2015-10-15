package org.labkey.sequenceanalysis.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.ldk.table.ContainerScopedTable;
import org.labkey.api.ldk.table.SharedDataTable;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.view.ActionURL;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.SequenceAnalysisServiceImpl;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * User: bimber
 * Date: 7/22/2014
 * Time: 1:49 PM
 */
public class SequenceAnalysisUserSchema extends SimpleUserSchema
{
    private SequenceAnalysisUserSchema(User user, Container container, DbSchema schema)
    {
        super(SequenceAnalysisSchema.SCHEMA_NAME, null, user, container, schema);
    }

    public static void register(final Module m)
    {
        final DbSchema dbSchema = DbSchema.get(SequenceAnalysisSchema.SCHEMA_NAME);

        DefaultSchema.registerProvider(SequenceAnalysisSchema.SCHEMA_NAME, new DefaultSchema.SchemaProvider(m)
        {
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new SequenceAnalysisUserSchema(schema.getUser(), schema.getContainer(), dbSchema);
            }
        });
    }

    @Override
    @Nullable
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable)
    {
        if (SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES.equalsIgnoreCase(name))
        {
            return createRefSequencesTable(sourceTable);
        }
        else if (SequenceAnalysisSchema.TABLE_READSETS.equalsIgnoreCase(name))
        {
            return createReadsetsTable(sourceTable);
        }
        else if (SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_REF_LIBRARIES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_LIBRARY_TRACKS.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_AA_FEATURES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_NT_FEATURES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_CHAIN_FILES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable, true).init();
        else if (SequenceAnalysisSchema.TABLE_READSET_STATUS.equalsIgnoreCase(name))
            return new ContainerScopedTable<>(this, sourceTable, "status").init();
        else if (SequenceAnalysisSchema.TABLE_LIBRARY_TYPES.equalsIgnoreCase(name))
            return new ContainerScopedTable<>(this, sourceTable, "type").init();
        else if (SequenceAnalysisSchema.TABLE_OUTPUTFILES.equalsIgnoreCase(name))
        {
            return createOutputFiles(sourceTable);
        }
        else
            return super.createWrappedTable(name, sourceTable);
    }

    private TableInfo createOutputFiles(TableInfo sourceTable)
    {
        SimpleTable ret = new SimpleTable(this, sourceTable).init();
        LinkedHashSet<String> scriptIncludes = new LinkedHashSet<>();
        if (ret.getButtonBarConfig().getScriptIncludes() != null)
        {
            scriptIncludes.addAll(Arrays.asList(ret.getButtonBarConfig().getScriptIncludes()));
        }

        scriptIncludes.add("sequenceanalysis/sequenceanalysisButtons.js");
        for (SequenceOutputHandler handler : SequenceAnalysisServiceImpl.get().getFileHandlers())
        {
            if (handler.getOwningModule() != null && getContainer().getActiveModules().contains(handler.getOwningModule()) && handler.getClientDependencies() != null)
            {
                scriptIncludes.addAll(handler.getClientDependencies());
            }
        }

        ret.getButtonBarConfig().setScriptIncludes(scriptIncludes.toArray(new String[scriptIncludes.size()]));

        if (ret.getColumn("analysisSets") == null)
        {
            String chr = sourceTable.getSqlDialect().isPostgreSQL() ? "chr" : "char";
            SQLFragment sql = new SQLFragment("(SELECT " + ret.getSqlDialect().getGroupConcat(new SQLFragment("a.name"), true, true, chr + "(10)").getSqlCharSequence() + " as expr FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSIS_SET_MEMBERS + " asm JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSIS_SETS + " a ON (asm.analysisSet = a.rowid) WHERE asm.dataid = " + ExprColumn.STR_TABLE_ALIAS + ".dataid)");
            ExprColumn newCol = new ExprColumn(ret, "analysisSets", sql, JdbcType.VARCHAR, sourceTable.getColumn("rowid"));
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=analysisSetMembers&query.dataid~eq=${dataid}"));
            newCol.setLabel("Analyses Using This File");
            ret.addColumn(newCol);
        }

        return ret;
    }

    private TableInfo createReadsetsTable(TableInfo sourceTable)
    {
        SimpleTable ret = new SimpleTable(this, sourceTable).init();
        if (ret.getColumn("files") == null)
        {
            WrappedColumn newCol = new WrappedColumn(ret.getColumn("rowid"), "files");
            newCol.setLabel("File(s)");
            newCol.setKeyField(false);
            newCol.setHidden(true);
            newCol.setUserEditable(false);
            newCol.setShownInInsertView(false);
            newCol.setShownInUpdateView(false);
            newCol.setCalculated(true);
            newCol.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                @Override
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new DataColumn(colInfo)
                    {
                        @Override
                        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                        {
                            Object o = getValue(ctx);
                            if (o != null)
                            {
                                ActionURL url = QueryService.get().urlFor(getUser(), ctx.getContainer(), QueryAction.executeQuery, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_READ_DATA);
                                url.addFilter("query", FieldKey.fromString("readset"), CompareType.EQUAL, o);

                                out.write("<a class=\"labkey-text-link\" href=\"" + url.toString() + "\">");
                                out.write("View File(s)");
                                out.write("</a>");
                            }
                            else
                            {
                                out.write("No Files");
                            }
                        }
                    };
                }
            });
            ret.addColumn(newCol);
        }

        if (ret.getColumn("totalFiles") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT COUNT(rd.rowid) FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READ_DATA + " rd WHERE rd.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ret, "totalFiles", sql, JdbcType.INTEGER, sourceTable.getColumn("rowid"));
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=readData&query.readset~eq=${rowid}"));
            newCol.setLabel("Total Files");
            ret.addColumn(newCol);
        }

        if (ret.getColumn("totalAlignments") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT COUNT(rd.rowid) FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSES + " rd WHERE rd.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ret, "totalAlignments", sql, JdbcType.INTEGER, sourceTable.getColumn("rowid"));
            newCol.setLabel("Alignments Using This Readset");
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=sequence_analyses&query.readset~eq=${rowid}"));

            ret.addColumn(newCol);
        }

        if (ret.getColumn("totalOutputs") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT COUNT(rd.rowid) FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_OUTPUTFILES + " rd WHERE rd.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ret, "totalOutputs", sql, JdbcType.INTEGER, sourceTable.getColumn("rowid"));
            newCol.setLabel("Output Files From This Readset");
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=outputfiles&query.readset~eq=${rowid}"));
            newCol.setUserEditable(false);
            newCol.setCalculated(true);
            ret.addColumn(newCol);
        }

        return ret;
    }

    private TableInfo createRefSequencesTable(TableInfo sourceTable)
    {
        SharedDataTable ret = new SharedDataTable(this, sourceTable, true);
        String chr = sourceTable.getSqlDialect().isPostgreSQL() ? "chr" : "char";
        SQLFragment sql = new SQLFragment("(SELECT " + sourceTable.getSqlDialect().getGroupConcat(new SQLFragment("r.name"), true, true, chr + "(10)").getSqlCharSequence()+ " as expr FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS + " rm JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_LIBRARIES + " r ON (rm.library_id = r.rowid) WHERE rm.ref_nt_id = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
        ExprColumn newCol = new ExprColumn(ret, "genomes", sql, JdbcType.VARCHAR, sourceTable.getColumn("rowid"));
        newCol.setLabel("Genome(s) Using Sequence");
        ret.addColumn(newCol);

        return ret.init();
    }
}
