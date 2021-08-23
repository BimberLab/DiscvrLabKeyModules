package org.labkey.sequenceanalysis.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.ldk.LDKService;
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
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
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
        final DbSchema dbSchema = SequenceAnalysisSchema.getInstance().getSchema();

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
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo sourceTable, ContainerFilter cf)
    {
        if (SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES.equalsIgnoreCase(name))
        {
            return createRefSequencesTable(sourceTable);
        }
        else if (SequenceAnalysisSchema.TABLE_READSETS.equalsIgnoreCase(name))
        {
            return createReadsetsTable(sourceTable, cf);
        }
        else if (SequenceAnalysisSchema.TABLE_READ_DATA.equalsIgnoreCase(name))
        {
            return createReadDataTable(sourceTable, cf);
        }
        else if (SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable).init();
        else if (SequenceAnalysisSchema.TABLE_REF_AA_FEATURES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable).init();
        else if (SequenceAnalysisSchema.TABLE_REF_NT_FEATURES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable).init();
        else if (SequenceAnalysisSchema.TABLE_SAVED_ANALYSES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable).init();
        else if (SequenceAnalysisSchema.TABLE_REF_LIBRARIES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable).init();
        else if (SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable).init();
        else if (SequenceAnalysisSchema.TABLE_LIBRARY_TRACKS.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable).init();
        else if (SequenceAnalysisSchema.TABLE_AA_FEATURES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable).init();
        else if (SequenceAnalysisSchema.TABLE_NT_FEATURES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable).init();
        else if (SequenceAnalysisSchema.TABLE_CHAIN_FILES.equalsIgnoreCase(name))
            return new SharedDataTable(this, sourceTable).init();
        else if (SequenceAnalysisSchema.TABLE_READSET_STATUS.equalsIgnoreCase(name))
            return new ContainerScopedTable<>(this, sourceTable, cf, "status").init();
        else if (SequenceAnalysisSchema.TABLE_LIBRARY_TYPES.equalsIgnoreCase(name))
            return new ContainerScopedTable<>(this, sourceTable, cf, "type").init();
        else if (SequenceAnalysisSchema.TABLE_OUTPUTFILES.equalsIgnoreCase(name))
        {
            return createOutputFiles(sourceTable, cf);
        }
        else if (SequenceAnalysisSchema.TABLE_BARCODES.equalsIgnoreCase(name))
        {
            TableInfo ret = super.createWrappedTable(name, sourceTable, cf);
            LDKService.get().applyNaturalSort((AbstractTableInfo)ret, "tag_name");

            return ret;
        }
        else
            return super.createWrappedTable(name, sourceTable, cf);
    }

    private TableInfo createOutputFiles(TableInfo sourceTable, ContainerFilter cf)
    {
        SimpleTable ret = new SimpleTable(this, sourceTable, cf).init();
        LinkedHashSet<String> scriptIncludes = new LinkedHashSet<>();
        if (ret.getButtonBarConfig().getScriptIncludes() != null)
        {
            scriptIncludes.addAll(Arrays.asList(ret.getButtonBarConfig().getScriptIncludes()));
        }

        scriptIncludes.add("sequenceanalysis/sequenceanalysisButtons.js");
        for (SequenceOutputHandler handler : SequenceAnalysisServiceImpl.get().getFileHandlers(SequenceOutputHandler.TYPE.OutputFile))
        {
            if (handler.getOwningModule() != null && getContainer().getActiveModules().contains(handler.getOwningModule()) && handler.getClientDependencies() != null)
            {
                scriptIncludes.addAll(handler.getClientDependencies());
            }
        }

        for (SequenceOutputHandler handler : SequenceAnalysisServiceImpl.get().getFileHandlers(SequenceOutputHandler.TYPE.Readset))
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
            SQLFragment sql = new SQLFragment("(SELECT ").append(ret.getSqlDialect().getGroupConcat(new SQLFragment("a.name"), true, true, chr + "(10)")).append(" as expr FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSIS_SET_MEMBERS + " asm JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSIS_SETS + " a ON (asm.analysisSet = a.rowid) WHERE asm.dataid = " + ExprColumn.STR_TABLE_ALIAS + ".dataid)");
            ExprColumn newCol = new ExprColumn(ret, "analysisSets", sql, JdbcType.VARCHAR, sourceTable.getColumn("rowid"));
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=analysisSetMembers&query.dataid~eq=${dataid}", ret.getContainer().isWorkbook() ? ret.getContainer().getParent() : ret.getContainer()));
            newCol.setLabel("Analyses Using This File");
            ret.addColumn(newCol);
        }

        return ret;
    }

    private TableInfo createReadDataTable(TableInfo sourceTable, ContainerFilter cf)
    {
        SimpleTable ret = new SimpleTable<>(this, sourceTable, cf).init();

        if (ret.getColumn("totalForwardReads") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT SUM(q.metricvalue) as expr FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READ_DATA + " rd " +
                    " LEFT JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_QUALITY_METRICS + " q ON (rd.fileid1 = q.dataid AND q.metricname = 'Total Reads' AND (rd.readset IS NULL OR rd.readset = q.readset)) " +
                    " WHERE rd.rowid = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ret, "totalForwardReads", sql, JdbcType.INTEGER, sourceTable.getColumn("rowid"));
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=quality_metrics&query.dataid~eq=${fileid1}&query.metricname~eq=Total Reads"));
            newCol.setLabel("Total Forward Reads");
            newCol.setFormat("#,##0.##");

            ret.addColumn(newCol);
        }

        if (ret.getColumn("totalReverseReads") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT SUM(q.metricvalue) as expr FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READ_DATA + " rd " +
                    " LEFT JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_QUALITY_METRICS + " q ON (rd.fileid2 = q.dataid AND q.metricname = 'Total Reads' AND (rd.readset IS NULL OR rd.readset = q.readset)) " +
                    " WHERE rd.rowid = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ret, "totalReverseReads", sql, JdbcType.INTEGER, sourceTable.getColumn("rowid"));
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=quality_metrics&query.dataid~eq=${fileid2}&query.metricname~eq=Total Reads"));
            newCol.setLabel("Total Reverse Reads");
            newCol.setFormat("#,##0.##");
            ret.addColumn(newCol);
        }

        return ret;
    }

    private TableInfo createReadsetsTable(TableInfo sourceTable, ContainerFilter cf)
    {
        SimpleTable ret = new SimpleTable<>(this, sourceTable, cf).init();
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

            SQLFragment sql2 = new SQLFragment("(SELECT ").append(sourceTable.getSqlDialect().getGroupConcat(new SQLFragment("d.Name"), true, true, "','")).append(new SQLFragment(" as expr FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READ_DATA + " rd JOIN exp.data d ON (d.RowId = rd.fileId1 OR d.RowId = rd.fileId2) WHERE rd.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)"));
            ExprColumn newCol2 = new ExprColumn(ret, "fileNames", sql2, JdbcType.VARCHAR, sourceTable.getColumn("rowid"));
            newCol2.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=readData&query.readset~eq=${rowid}"));
            newCol2.setLabel("File Names");
            newCol2.setDescription("This will display a comma-separated list of all file names with reads.  This can be useful for SRA submissions.");
            ret.addColumn(newCol2);

            SQLFragment sql3 = new SQLFragment("(SELECT ").append(sourceTable.getSqlDialect().getGroupConcat(new SQLFragment("rd.sra_accession"), true, true, "','")).append(new SQLFragment(" as expr FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READ_DATA + " rd WHERE rd.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)"));
            ExprColumn newCol3 = new ExprColumn(ret, "sraRuns", sql3, JdbcType.VARCHAR, sourceTable.getColumn("rowid"));
            newCol3.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=readData&query.readset~eq=${rowid}"));
            newCol3.setLabel("SRA Runs");
            newCol3.setDescription("This will display a comma-separated list of all distinct SRA runs associated with this readset.");
            ret.addColumn(newCol3);

            SQLFragment sql4 = new SQLFragment("(SELECT count(*) as expr FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READ_DATA + " rd WHERE rd.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid AND rd.sra_accession IS NULL)");
            ExprColumn newCol4 = new ExprColumn(ret, "readdataWithoutSra", sql4, JdbcType.VARCHAR, sourceTable.getColumn("rowid"));
            newCol4.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=readData&query.readset~eq=${rowid}"));
            newCol4.setLabel("Read Pairs Not In SRA");
            newCol4.setDescription("This will show the total number of read pairs for this readset that do not like an SRA Run.");
            ret.addColumn(newCol4);
        }

        if (ret.getColumn("isArchived") == null)
        {
            SQLFragment sql = new SQLFragment("CASE WHEN (select count(*) as expr from " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READ_DATA + " rd where rd.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid and rd.archived = " + ret.getSqlDialect().getBooleanTRUE() + ") > 0 THEN " + ret.getSqlDialect().getBooleanTRUE() + " ELSE " + ret.getSqlDialect().getBooleanFALSE() + " END");
            ExprColumn newCol = new ExprColumn(ret, "isArchived", sql, JdbcType.INTEGER, sourceTable.getColumn("rowid"));
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=readData&query.readset~eq=${rowid}"));
            newCol.setLabel("Archived To SRA?");
            ret.addColumn(newCol);
        }

        if (ret.getColumn("totalAlignments") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT COUNT(rd.rowid) FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSES + " rd WHERE rd.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ret, "totalAlignments", sql, JdbcType.INTEGER, sourceTable.getColumn("rowid"));
            newCol.setLabel("Alignments Using This Readset");
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=sequence_analyses&query.readset~eq=${rowid}", ret.getContainer().isWorkbook() ? ret.getContainer().getParent() : ret.getContainer()));

            ret.addColumn(newCol);
        }

        if (ret.getColumn("distinctGenomes") == null)
        {
            String chr = ret.getSqlDialect().isPostgreSQL() ? "chr" : "char";
            SQLFragment sql = new SQLFragment("(SELECT ").append(ret.getSqlDialect().getGroupConcat(new SQLFragment("l.name"), true, true, (chr + "(10)"))).append(new SQLFragment(" as expr FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_ANALYSES + " a JOIN " + SequenceAnalysisSchema.SCHEMA_NAME  + "." + SequenceAnalysisSchema.TABLE_REF_LIBRARIES + " l ON (a.library_id = l.rowid) WHERE a.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)"));
            ExprColumn newCol = new ExprColumn(ret, "distinctGenomes", sql, JdbcType.VARCHAR, sourceTable.getColumn("rowid"));
            newCol.setLabel("Genomes With Alignments For Readset");
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=sequence_analyses&query.readset~eq=${rowid}&query.library_id~isnonblank", ret.getContainer().isWorkbook() ? ret.getContainer().getParent() : ret.getContainer()));

            ret.addColumn(newCol);
        }

        if (ret.getColumn("totalOutputs") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT COUNT(rd.rowid) FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_OUTPUTFILES + " rd WHERE rd.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ret, "totalOutputs", sql, JdbcType.INTEGER, sourceTable.getColumn("rowid"));
            newCol.setLabel("Output Files From This Readset");
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=outputfiles&query.readset~eq=${rowid}", ret.getContainer().isWorkbook() ? ret.getContainer().getParent() : ret.getContainer()));
            newCol.setUserEditable(false);
            newCol.setCalculated(true);
            ret.addColumn(newCol);
        }

        if (ret.getColumn("totalForwardReads") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT SUM(q.metricvalue) as expr FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READ_DATA + " rd " +
                    " LEFT JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_QUALITY_METRICS + " q ON (rd.fileid1 = q.dataid AND rd.readset = q.readset AND q.metricname = 'Total Reads') " +
                    " WHERE rd.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ret, "totalForwardReads", sql, JdbcType.INTEGER, sourceTable.getColumn("rowid"));
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=quality_metrics&query.readset~eq=${rowid}&query.metricname~eq=Total Reads", ret.getContainer().isWorkbook() ? ret.getContainer().getParent() : ret.getContainer()));
            newCol.setLabel("Total Forward Reads");
            newCol.setFormat("#,##0.##");

            ret.addColumn(newCol);
        }

        if (ret.getColumn("totalReverseReads") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT SUM(q.metricvalue) as expr FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READ_DATA + " rd " +
                    " LEFT JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_QUALITY_METRICS + " q ON (rd.fileid2 = q.dataid AND rd.readset = q.readset AND q.metricname = 'Total Reads') " +
                    " WHERE rd.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ret, "totalReverseReads", sql, JdbcType.INTEGER, sourceTable.getColumn("rowid"));
            newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=sequenceanalysis&query.queryName=quality_metrics&query.readset~eq=${rowid}&query.metricname~eq=Total Reads", ret.getContainer().isWorkbook() ? ret.getContainer().getParent() : ret.getContainer()));
            newCol.setLabel("Total Reverse Reads");
            newCol.setFormat("#,##0.##");

            ret.addColumn(newCol);
        }

        if (ret.getColumn("runIds") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT ").append(sourceTable.getSqlDialect().getGroupConcat(new SQLFragment("rd.runId"), true, true, "','")).append(" FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READ_DATA + " rd WHERE rd.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ret, "runIds", sql, JdbcType.VARCHAR, sourceTable.getColumn("rowid"));
            newCol.setLabel("Run(s)");

            newCol.setDisplayColumnFactory(new PipelineDisplayColumnFactory("/experiment/showRunGraphDetail.view?rowId="));
            ret.addColumn(newCol);
        }

        if (ret.getColumn("jobIds") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT ").append(sourceTable.getSqlDialect().getGroupConcat(new SQLFragment("runs.jobId"), true, true, "','")).append(" FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_READ_DATA + " rd JOIN exp.experimentrun runs ON (rd.runId = runs.rowId) WHERE rd.readset = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
            ExprColumn newCol = new ExprColumn(ret, "jobIds", sql, JdbcType.VARCHAR, sourceTable.getColumn("rowid"));
            newCol.setLabel("Job(s)");

            newCol.setDisplayColumnFactory(new PipelineDisplayColumnFactory("/pipeline-status/details.view?rowId="));
            ret.addColumn(newCol);
        }

        LDKService.get().applyNaturalSort(ret, "name");
        LDKService.get().applyNaturalSort(ret, "subjectid");

        return ret;
    }

    private static class PipelineDisplayColumnFactory implements DisplayColumnFactory
    {
        String _baseUrl;

        public PipelineDisplayColumnFactory(String baseUrl)
        {
            _baseUrl = baseUrl;
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new DataColumn(colInfo)
            {
                @Override
                public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                {
                    String result = StringUtils.trimToNull(super.getFormattedHtml(ctx).toString());
                    String delim = "";
                    if (result != null)
                    {
                        String[] tokens = result.split(",");
                        for (String token : tokens)
                        {
                            String url = DetailsURL.fromString(_baseUrl + PageFlowUtil.encode(token), ctx.getContainer()).getActionURL().toString();

                            out.write(delim + "<a href=\"" + url + "\">" + token + "</a>");
                            delim = "<br>";
                        }
                    }
                }
            };
        }
    }

    private TableInfo createRefSequencesTable(TableInfo sourceTable)
    {
        SharedDataTable ret = new SharedDataTable<>(this, sourceTable);
        String chr = sourceTable.getSqlDialect().isPostgreSQL() ? "chr" : "char";
        SQLFragment sql = new SQLFragment("(SELECT ").append(sourceTable.getSqlDialect().getGroupConcat(new SQLFragment("r.name"), true, true, chr + "(10)")).append(" as expr FROM " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS + " rm JOIN " + SequenceAnalysisSchema.SCHEMA_NAME + "." + SequenceAnalysisSchema.TABLE_REF_LIBRARIES + " r ON (rm.library_id = r.rowid) WHERE rm.ref_nt_id = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
        ExprColumn newCol = new ExprColumn(ret, "genomes", sql, JdbcType.VARCHAR, sourceTable.getColumn("rowid"));
        newCol.setLabel("Genome(s) Using Sequence");
        ret.addColumn(newCol);

        return ret.init();
    }
}
