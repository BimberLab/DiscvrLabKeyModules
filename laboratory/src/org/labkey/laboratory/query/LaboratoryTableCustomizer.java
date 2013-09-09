package org.labkey.laboratory.query;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ButtonBarConfig;
import org.labkey.api.data.ButtonConfig;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UserDefinedButtonConfig;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.ldk.table.ButtonConfigFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.laboratory.DemographicsSource;
import org.labkey.laboratory.LaboratoryModule;
import org.labkey.laboratory.LaboratorySchema;
import org.labkey.laboratory.LaboratoryServiceImpl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 1/3/13
 * Time: 9:23 PM
 */
public class LaboratoryTableCustomizer implements TableCustomizer
{
    private static final Logger _log = Logger.getLogger(LaboratoryTableCustomizer.class);
    private static final String MORE_ACTIONS = "More Actions";

    public void customize(TableInfo ti)
    {
        //apply defaults
        TableCustomizer tc = LDKService.get().getBuiltInColumnsCustomizer(true);
        tc.customize(ti);

        if (ti instanceof AbstractTableInfo)
        {
            customizeColumns((AbstractTableInfo) ti);
            appendCalculatedCols((AbstractTableInfo) ti);

            LDKService.get().getColumnsOrderCustomizer().customize(ti);

            if (ti instanceof AbstractTableInfo)
                ensureWorkbookCol((AbstractTableInfo)ti);

            customizeButtonBar((AbstractTableInfo)ti);

            if (LaboratorySchema.TABLE_SAMPLES.equalsIgnoreCase(ti.getName()) && LaboratoryModule.SCHEMA_NAME.equalsIgnoreCase(ti.getPublicSchemaName()))
            {
                customzieSamplesTable((AbstractTableInfo) ti);
            }
        }
    }

    public void ensureWorkbookCol(AbstractTableInfo ti)
    {
        ColumnInfo wrappedContainer = ti.getColumn("workbook");
        if (wrappedContainer != null)
        {
            List<FieldKey> cols = new ArrayList<FieldKey>();
            cols.addAll(ti.getDefaultVisibleColumns());
            if (!cols.contains(wrappedContainer.getFieldKey()))
            {
                cols.add(wrappedContainer.getFieldKey());
                ti.setDefaultVisibleColumns(cols);
            }
        }
    }

    public void customizeColumns(AbstractTableInfo  ti)
    {
        ColumnInfo container = ti.getColumn("container");
        if (container == null)
        {
            container = ti.getColumn("folder");
        }

        if (container != null && ti.getColumn("workbook") == null)
        {
            UserSchema us = getUserSchema(ti, "laboratory");
            if (us != null)
            {
                container.setHidden(true);

                ColumnInfo wrappedContainer = new WrappedColumn(container, "workbook");
                wrappedContainer.setLabel("Workbook");
                wrappedContainer.setFk(new QueryForeignKey(us, "workbooks", LaboratoryWorkbooksTable.WORKBOOK_COL, "workbookId"));
                wrappedContainer.setURL(DetailsURL.fromString("/project/start.view"));
                wrappedContainer.setShownInDetailsView(true);
                wrappedContainer.setFacetingBehaviorType(FacetingBehaviorType.ALWAYS_OFF);
                wrappedContainer.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    @Override
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new WorkbookIdDisplayColumn(colInfo);
                    }
                });

                ti.addColumn(wrappedContainer);
            }

            if (ti.getColumn("samplename") != null)
            {
                LDKService.get().applyNaturalSort(ti, "samplename");
            }

            if (ti.getColumn("subjectId") != null)
            {
                LDKService.get().applyNaturalSort(ti, "subjectId");
            }

            if (ti.getColumn("well") != null)
            {
                LDKService.get().applyNaturalSort(ti, "well");
            }
        }
    }

    public void appendCalculatedCols(AbstractTableInfo ti)
    {
        UserSchema us = ti.getUserSchema();
        if (us == null)
        {
            _log.error("Table does have has a UserSchema: " + ti.getName());
            return;
        }

        Container c = us.getContainer();
        if (!c.getActiveModules(true).contains(ModuleLoader.getInstance().getModule(LaboratoryModule.class)))
        {
            _log.warn("Laboratory module not enabled in container: " + c.getPath() + ", so LaboratoryTableCustomizer is aborting for table: " + ti.getSelectName());
            return;
        }

        ColumnInfo dateCol = null;
        ColumnInfo subjectCol = null;
        for (ColumnInfo ci : ti.getColumns())
        {
            if (LaboratoryService.PARTICIPANT_CONCEPT_URI.equals(ci.getConceptURI()))
            {
                subjectCol = ci;
            }
            else if (LaboratoryService.SAMPLEDATE_CONCEPT_URI.equals(ci.getConceptURI()))
            {
                dateCol = ci;
            }
        }

        if (dateCol != null && subjectCol != null)
        {
            appendOverlapingProjectsCol(us, ti, dateCol.getName(), subjectCol.getName());
            appendRelativeDatesCol(us, ti, dateCol.getName(), subjectCol.getName());
            appendMajorEventsCol(us, ti, dateCol.getName(), subjectCol.getName());
        }

        if (subjectCol != null)
        {
            appendDemographicsCols(us, ti, subjectCol);
            appendProjectsCol(us, ti, subjectCol.getName());
        }
    }

    private void appendDemographicsCols(final UserSchema us, AbstractTableInfo ti, ColumnInfo subjectCol)
    {
       LaboratoryServiceImpl service = (LaboratoryServiceImpl)LaboratoryServiceImpl.get();
        Set<DemographicsSource> qds = service.getDemographicsSources(us.getContainer(), us.getUser());
        if (qds != null)
        {
            for (final DemographicsSource qd : qds)
            {
                TableInfo target = qd.getTableInfo(us.getContainer(), us.getUser());

                //TODO: push this into LaboratoryService and also cache them?
                if (target != null)
                {
                    String name = ColumnInfo.legalNameFromName(qd.getLabel());

                    if (ti.getColumn(name) != null)
                        continue;

                    WrappedColumn col = new WrappedColumn(subjectCol, name);
                    col.setLabel(qd.getLabel());
                    col.setReadOnly(true);
                    col.setIsUnselectable(true);
                    col.setUserEditable(false);

                    col.setFk(new QueryForeignKey(qd.getQueryDef(us.getContainer(), us.getUser()).getSchema(), qd.getQueryName(), qd.getTargetColumn(), qd.getTargetColumn()){
                        public TableInfo getLookupTableInfo()
                        {
                            AbstractTableInfo ti = (AbstractTableInfo)super.getLookupTableInfo();
                            ti.getColumn(qd.getTargetColumn()).setKeyField(true);
                            for (ColumnInfo ci : ti.getColumns())
                            {
                                if (LaboratoryService.BIRTHDATE_CONCEPT_URI.equalsIgnoreCase(ci.getConceptURI()))
                                {
                                    addAgeCols(ti, ci);
                                }
                            }

                            return ti;
                        }
                    });

                    ti.addColumn(col);
                }
            }
        }
    }

    private void appendMajorEventsCol(final UserSchema us, AbstractTableInfo ds, final String dateColName, final String subjectColName)
    {
        String name = "majorEvents";
        if (ds.getColumn(name) != null)
            return;

        List<ColumnInfo> pks = ds.getPkColumns();
        if (pks.size() != 1){
            _log.error("Table does not have a single PK column: " + ds.getName());
            return;
        }

        final String tableName = ds.getName();
        final String queryName = ds.getPublicName();
        final String schemaName = ds.getUserSchema().getSchemaPath().toSQLString();

        ColumnInfo pk = pks.get(0);
        final String pkColName = pk.getSelectName();
        WrappedColumn col = new WrappedColumn(pk, name);
        col.setLabel("Major Events");
        col.setDescription("This column shows all major events recorded in this subject\'s history and will calculate the time elapsed between the current sample and these dates.");
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        col.setUserEditable(false);
        col.setFk(new LookupForeignKey(){
            public TableInfo getLookupTableInfo()
            {
                String name = tableName + "_majorEvents";
                QueryDefinition qd = QueryService.get().createQueryDef(us.getUser(), us.getContainer(), us, name);

                qd.setSql(getMajorEventsSql(schemaName, queryName, pkColName, subjectColName, dateColName));
                qd.setIsTemporary(true);

                List<QueryException> errors = new ArrayList<QueryException>();
                TableInfo ti = qd.getTable(errors, true);

                if (errors.size() > 0){
                    _log.error("Problem with table customizer: " + tableName);
                    for (QueryException e : errors)
                    {
                        _log.error(e.getMessage());
                    }
                }

                if (ti != null)
                {
                    ti.getColumn(pkColName).setKeyField(true);
                    ti.getColumn(pkColName).setHidden(true);
                }

                return ti;
            }
        });

        ds.addColumn(col);
    }

    private void appendOverlapingProjectsCol(final UserSchema us, AbstractTableInfo ds, final String dateColName, final String subjectColName)
    {
        String name = "overlappingProjects";
        if (ds.getColumn(name) != null)
            return;

        List<ColumnInfo> pks = ds.getPkColumns();
        if (pks.size() != 1){
            _log.error("Table does not have a single PK column: " + ds.getName());
            return;
        }
        ColumnInfo pk = pks.get(0);
        final String pkColName = pk.getSelectName();

        final String tableName = ds.getName();
        final String queryName = ds.getPublicName();
        final String schemaName = ds.getUserSchema().getSchemaPath().toSQLString();

        WrappedColumn col = new WrappedColumn(pk, name);
        col.setLabel("Overlapping Groups");
        col.setDescription("This column shows all groups to which this subject belonged at the time of this sample.");
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        col.setUserEditable(false);
        col.setFk(new LookupForeignKey(){
            public TableInfo getLookupTableInfo()
            {
                String name = tableName + "_overlappingProjects";
                QueryDefinition qd = QueryService.get().createQueryDef(us.getUser(), us.getContainer(), us, name);

                qd.setSql(getOverlapSql(schemaName, queryName, pkColName, subjectColName, dateColName));
                qd.setIsTemporary(true);

                List<QueryException> errors = new ArrayList<QueryException>();
                TableInfo ti = qd.getTable(errors, true);

                if (errors.size() > 0){
                    _log.error("Problem with table customizer: " + tableName);
                    for (QueryException e : errors)
                    {
                        _log.error(e.getMessage());
                    }
                }

                if (ti != null)
                {
                    ti.getColumn(pkColName).setKeyField(true);
                    ti.getColumn(pkColName).setHidden(true);

                    ti.getColumn("projects").setLabel("Overlapping Groups");
                    ti.getColumn("groups").setLabel("Overlapping Sub-groups");
                }

                return ti;
            }
        });

        ds.addColumn(col);

        //add pivot column
        String colName = "overlappingProjectsPivot";
        WrappedColumn col2 = new WrappedColumn(pk, colName);
        col2.setLabel("Overlapping Group List");
        col2.setDescription("Shows groups to which this subject belonged at the time of this sample.");
        col2.setHidden(true);
        col2.setReadOnly(true);
        col2.setIsUnselectable(true);
        col2.setUserEditable(false);
        col2.setFk(new LookupForeignKey(){
            public TableInfo getLookupTableInfo()
            {
                String name = tableName + "_overlappingProjectsPivot";
                QueryDefinition qd = QueryService.get().createQueryDef(us.getUser(), us.getContainer(), us, name);

                qd.setSql(getOverlapPivotSql(schemaName, queryName, pkColName, subjectColName, dateColName));
                qd.setIsTemporary(true);

                List<QueryException> errors = new ArrayList<QueryException>();
                TableInfo ti = qd.getTable(errors, true);

                if (errors.size() > 0){
                    _log.error("Problem with table customizer: " + tableName);
                    for (QueryException e : errors)
                    {
                        _log.error(e.getMessage());
                    }
                }

                if (ti != null)
                {
                    ti.getColumn(pkColName).setKeyField(true);
                    ti.getColumn(pkColName).setHidden(true);

                    ti.getColumn("lastStartDate").setLabel("Most Recent Start Date");
                }

                return ti;
            }
        });

        ds.addColumn(col2);
    }

    private void appendProjectsCol(final UserSchema us, AbstractTableInfo ds, final String subjectColName)
    {
        String name = "allProjects";
        if (ds.getColumn(name) != null)
            return;

        List<ColumnInfo> pks = ds.getPkColumns();
        if (pks.size() != 1){
            _log.error("Table does not have a single PK column: " + ds.getName());
            return;
        }
        ColumnInfo pk = pks.get(0);
        final String pkColName = pk.getSelectName();

        final String tableName = ds.getName();
        final String queryName = ds.getPublicName();
        final String schemaName = ds.getUserSchema().getSchemaPath().toSQLString();

        WrappedColumn col = new WrappedColumn(pk, name);
        col.setLabel("Groups");
        col.setDescription("This column shows all groups to which this subject has ever been a member, regardless of whether that assignment overlaps with the current data point");
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        col.setUserEditable(false);
        col.setFk(new LookupForeignKey(){
            public TableInfo getLookupTableInfo()
            {
                String name = tableName + "_allProjects";
                QueryDefinition qd = QueryService.get().createQueryDef(us.getUser(), us.getContainer(), us, name);

                qd.setSql(getOverlapSql(schemaName, queryName, pkColName, subjectColName, null));
                qd.setIsTemporary(true);

                List<QueryException> errors = new ArrayList<QueryException>();
                TableInfo ti = qd.getTable(errors, true);

                if (errors.size() > 0){
                    _log.error("Problem with table customizer: " + tableName);
                    for (QueryException e : errors)
                    {
                        _log.error(e.getMessage());
                    }
                }

                if (ti != null)
                {
                    ColumnInfo pkCol = ti.getColumn(pkColName);
                    if (pkCol != null)
                    {
                        pkCol.setKeyField(true);
                        pkCol.setHidden(true);
                    }

                    ti.getColumn("projects").setLabel("All Groups/Projects");
                    ti.getColumn("groups").setLabel("All Sub-Groups");
                }

                return ti;
            }
        });

        ds.addColumn(col);

        //add pivot column
        String colName = "allProjectsPivot";
        WrappedColumn col2 = new WrappedColumn(pk, colName);
        col2.setLabel("Group Summary List");
        col2.setDescription("Shows groups to which this subject belonged at any point in time.");
        col2.setHidden(true);
        col2.setReadOnly(true);
        col2.setIsUnselectable(true);
        col2.setUserEditable(false);
        col2.setFk(new LookupForeignKey(){
            public TableInfo getLookupTableInfo()
            {
                String name = tableName + "_allProjectsPivot";
                QueryDefinition qd = QueryService.get().createQueryDef(us.getUser(), us.getContainer(), us, name);

                qd.setSql(getOverlapPivotSql(schemaName, queryName, pkColName, subjectColName, null));
                qd.setIsTemporary(true);

                List<QueryException> errors = new ArrayList<QueryException>();
                TableInfo ti = qd.getTable(errors, true);

                if (errors.size() > 0){
                    _log.error("Problem with table customizer: " + tableName);
                    for (QueryException e : errors)
                    {
                        _log.error(e.getMessage());
                    }
                }

                if (ti != null)
                {
                    ti.getColumn(pkColName).setKeyField(true);
                    ti.getColumn(pkColName).setHidden(true);

                    ti.getColumn("lastStartDate").setLabel("Most Recent Start Date");
                }

                return ti;
            }
        });

        ds.addColumn(col2);
    }

    private String getOverlapSql(String schemaName, String queryName, String pkColName, String subjectColName, @Nullable String dateColName)
    {
        return "SELECT\n" +
                "s.\"" + pkColName + "\",\n" +
                "group_concat(DISTINCT s.project, chr(10)) as projects,\n" +
                "group_concat(DISTINCT s.projectGroup, chr(10)) as groups,\n" +
                "\n" +
                "FROM (\n" +
                "\n" +
                "SELECT\n" +
                "s.\"" + pkColName + "\",\n" +
                "p.project,\n" +
                "CASE\n" +
                "  WHEN p.groupname IS NULL then NULL\n" +
                "  ELSE (p.project || ' (' || p.groupname || ')')\n" +
                "END as projectGroup,\n" +
                "\n" +
                "FROM " + schemaName + ".\"" + queryName + "\" s\n" +
                "JOIN laboratory.project_usage p\n" +
                "ON (s.\"" + subjectColName + "\" = p.subjectId" +
                (dateColName != null ? " AND p.startdate <= s.\"" + dateColName + "\" AND s.\"" + dateColName + "\" <= COALESCE(p.enddate, {fn curdate()})" : "") +
                ")\n" +
                "WHERE s.\"" + subjectColName + "\" IS NOT NULL\n" +
                (dateColName != null ? " AND s.\"" + dateColName + "\" IS NOT NULL " : "") +
                "\n" +
                ") s\n" +
                "\n" +
                "GROUP BY s.\"" + pkColName + "\"";
    }

    private String getOverlapPivotSql(String schemaName, String queryName, String pkColName, String subjectColName, @Nullable String dateColName)
    {
        return "SELECT\n" +
                "s.\"" + pkColName + "\",\n" +
                "p.project,\n" +
                "max(p.startdate) as lastStartDate\n" +
                "\n" +
                "FROM " + schemaName + ".\"" + queryName + "\" s\n" +
                "JOIN laboratory.project_usage p\n" +
                "ON (s.\"" + subjectColName + "\" = p.subjectId" +
                (dateColName != null ? " AND p.startdate <= s.\"" + dateColName + "\" AND s.\"" + dateColName + "\" <= COALESCE(p.enddate, {fn curdate()})" : "") +
                ")\n" +
                "WHERE s.\"" + subjectColName + "\" IS NOT NULL\n" +
                (dateColName != null ? " AND s.\"" + dateColName + "\" IS NOT NULL " : "") +
                "\n" +
                "GROUP BY s.\"" + pkColName + "\", p.project\n" +
                "PIVOT lastStartDate by project IN (select distinct project from laboratory.project_usage)";
    }

    private String getMajorEventsSql(String schemaName, String queryName, String pkColName, String subjectColName, @Nullable String dateColName)
    {
        return "SELECT\n" +
            "t.\"" + pkColName + "\",\n" +
            "t.event,\n" +
            "max(date) as eventDate,\n" +
            "max(DaysPostEvent) as DaysPostEvent,\n" +
            "max(WeeksPostEvent) as WeeksPostEvent,\n" +
            "max(WeeksPostEventDecimal) as WeeksPostEventDecimal,\n" +
            "max(MonthsPostEvent) as MonthsPostEvent,\n" +
            "max(YearsPostEvent) as YearsPostEvent,\n" +
            "max(YearsPostEventDecimal) as YearsPostEventDecimal,\n" +
            "\n" +
            "FROM (\n" +
            "\n" +
            "SELECT\n" +
            "s.\"" + pkColName + "\",\n" +
            "p.event,\n" +
            "p.date,\n" +
            "\n" +
            "TIMESTAMPDIFF('SQL_TSI_DAY', p.date, s.\"" + dateColName + "\") as DaysPostEvent,\n" +
            "TIMESTAMPDIFF('SQL_TSI_DAY', p.date, s.\"" + dateColName + "\") / 7 as WeeksPostEvent,\n" +
            "ROUND(CONVERT(TIMESTAMPDIFF('SQL_TSI_DAY', p.date, s.\"" + dateColName + "\"), DOUBLE) / 7.0, 1) as WeeksPostEventDecimal,\n" +
            "ROUND(CONVERT(age_in_months(p.date, s.\"" + dateColName + "\"), DOUBLE), 1) AS MonthsPostEvent,\n" +
            "floor(age(p.date, s.\"" + dateColName + "\")) AS YearsPostEvent,\n" +
                "ROUND(CONVERT(age_in_months(p.date, s.\"" + dateColName + "\"), DOUBLE) / 12, 1) AS YearsPostEventDecimal,\n" +
            "\n" +
            "FROM " + schemaName + ".\"" + queryName + "\" s\n" +
            "JOIN laboratory.major_events p\n" +
            "ON (s.\"" + subjectColName + "\" = p.subjectId)\n" +
            "WHERE s.\"" + subjectColName + "\" IS NOT NULL\n" +
            "\n" +
            ") t\n" +
            "\n" +
            "GROUP BY t.\"" + pkColName + "\", t.event\n" +
            "PIVOT DaysPostEvent, WeeksPostEvent, WeeksPostEventDecimal, MonthsPostEvent, YearsPostEvent, YearsPostEventDecimal by event";
    }

    private void appendRelativeDatesCol(final UserSchema us, AbstractTableInfo ds, final String dateColName, final String subjectColName)
    {
        String name = "relativeDates";
        if (ds.getColumn(name) != null)
            return;

        List<ColumnInfo> pks = ds.getPkColumns();
        if (pks.size() != 1){
            _log.error("Table does not have a single PK column: " + ds.getName());
            return;
        }
        ColumnInfo pk = pks.get(0);
        final String pkColName = pk.getSelectName();

        final String tableName = ds.getName();
        final String queryName = ds.getPublicName();
        final String schemaName = ds.getUserSchema().getSchemaPath().toSQLString();

        WrappedColumn col = new WrappedColumn(pk, name);
        col.setLabel("Relative Dates");
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        col.setUserEditable(false);
        col.setFk(new LookupForeignKey(){
            public TableInfo getLookupTableInfo()
            {
                String name = tableName + "_relativeDates";
                QueryDefinition qd = QueryService.get().createQueryDef(us.getUser(), us.getContainer(), us, name);

                qd.setSql("SELECT\n" +
                "t.\"" + pkColName + "\",\n" +
                "t.project,\n" +
                "max(startdate) as startDate,\n" +
                "max(DaysPostStart) as DaysPostStart,\n" +
                "max(WeeksPostStart) as WeeksPostStart,\n" +
                "max(WeeksPostStartDecimal) as WeeksPostStartDecimal,\n" +
                "max(MonthsPostStart) as MonthsPostStart,\n" +
                "max(YearsPostStart) as YearsPostStart,\n" +
                "max(YearsPostStartDecimal) as YearsPostStartDecimal,\n" +
                "\n" +
                "FROM (\n" +
                "\n" +
                "SELECT\n" +
                "s.\"" + pkColName + "\",\n" +
                "p.project,\n" +
                "p.startdate,\n" +
                "\n" +
                "TIMESTAMPDIFF('SQL_TSI_DAY', p.startdate, s.\"" + dateColName + "\") as DaysPostStart,\n" +
                "TIMESTAMPDIFF('SQL_TSI_DAY', p.startdate, s.\"" + dateColName + "\") / 7 as WeeksPostStart,\n" +
                "ROUND(CONVERT(TIMESTAMPDIFF('SQL_TSI_DAY', p.startdate, s.\"" + dateColName + "\"), DOUBLE) / 7.0, 1) as WeeksPostStartDecimal,\n" +
                "ROUND(CONVERT(age_in_months(p.startdate, s.\"" + dateColName + "\"), DOUBLE), 1) AS MonthsPostStart,\n" +
                "floor(age(p.startdate, s.\"" + dateColName + "\")) AS YearsPostStart,\n" +
                "ROUND(CONVERT(age_in_months(p.startdate, s.\"" + dateColName + "\"), DOUBLE) / 12, 1) AS YearsPostStartDecimal,\n" +
                "\n" +
                "FROM " + schemaName + ".\"" + queryName + "\" s\n" +
                "JOIN laboratory.project_usage p\n" +
                "ON (s.\"" + subjectColName + "\" = p.subjectId AND CONVERT(p.startdate, DATE) <= CONVERT(s.\"" + dateColName + "\", DATE) AND CONVERT(s.\"" + dateColName + "\", DATE) <= CONVERT(COALESCE(p.enddate, {fn curdate()}), DATE))\n" +
                "WHERE s.\"" + dateColName + "\" IS NOT NULL and s.\"" + subjectColName + "\" IS NOT NULL\n" +
                "\n" +
                ") t\n" +
                "\n" +
                "GROUP BY t.\"" + pkColName + "\", t.project\n" +
                "PIVOT DaysPostStart, WeeksPostStart, WeeksPostStartDecimal, MonthsPostStart, YearsPostStart, YearsPostStartDecimal by project");

                qd.setIsTemporary(true);

                List<QueryException> errors = new ArrayList<QueryException>();
                TableInfo ti = qd.getTable(errors, true);
                if (errors.size() > 0){
                    _log.error("Problem with table customizer: " + tableName);
                    for (QueryException e : errors)
                    {
                        _log.error(e.getMessage());
                    }
                }

                if (ti != null)
                {
                    ti.getColumn(pkColName).setKeyField(true);
                    ti.getColumn(pkColName).setHidden(true);
                }

                return ti;
            }
        });

        ds.addColumn(col);
    }

    public UserSchema getUserSchema(AbstractTableInfo ds, String name)
    {
        UserSchema us = ds.getUserSchema();
        if (us != null)
        {
            if (name.equalsIgnoreCase(us.getName()))
                return us;

            return QueryService.get().getUserSchema(us.getUser(), us.getContainer(), name);
        }

        return null;
    }

    public void addAgeCols(AbstractTableInfo ti, ColumnInfo birthCol)
    {
        //NOTE: years will round to the nearest integer, meaning if you're 31.6 years old it reports it as 32.  not what you typically want.
        appendTimeDiffCol(ti, birthCol, "ageInYears", "Age In Years", Calendar.DATE, 365.0, true);
        appendTimeDiffCol(ti, birthCol, "ageInYearsDecimal", "Age In Years, Decimal", Calendar.DATE, 365.0);
        appendAgeInMonthsCol(ti, birthCol, "ageInMonths", "Age In Months");
        appendTimeDiffCol(ti, birthCol, "ageInDays", "Age In Days", Calendar.DATE);
    }

    private void appendAgeInMonthsCol(AbstractTableInfo ti, ColumnInfo birthCol, String name, String label)
    {
        if (ti.getColumn(name) == null)
        {
            SQLFragment sql = getAgeInMonthsSQL(ti.getSchema(), birthCol);
            JdbcType type = JdbcType.INTEGER;

            ColumnInfo col = new ExprColumn(ti, name, sql, type, birthCol);
            col.setLabel(label);
            col.setShownInDetailsView(false);
            col.setFormat("0.##");
            ti.addColumn(col);
        }
    }

    //NOTE: patterned off of AgeInMonthsMethodInfo
    public SQLFragment getAgeInMonthsSQL(DbSchema schema, ColumnInfo column)
    {
        SQLFragment yearA = new SQLFragment(schema.getSqlDialect().getDatePart(Calendar.YEAR, ExprColumn.STR_TABLE_ALIAS + "." + column.getSelectName()));
        SQLFragment monthA = new SQLFragment(schema.getSqlDialect().getDatePart(Calendar.MONTH, ExprColumn.STR_TABLE_ALIAS + "." + column.getSelectName()));
        SQLFragment dayA = new SQLFragment(schema.getSqlDialect().getDatePart(Calendar.DATE, ExprColumn.STR_TABLE_ALIAS + "." + column.getSelectName()));

        SQLFragment yearB = new SQLFragment(schema.getSqlDialect().getDatePart(Calendar.YEAR, "{fn curdate()}"));
        SQLFragment monthB = new SQLFragment(schema.getSqlDialect().getDatePart(Calendar.MONTH, "{fn curdate()}"));
        SQLFragment dayB = new SQLFragment(schema.getSqlDialect().getDatePart(Calendar.DATE, "{fn curdate()}"));

        SQLFragment ret = new SQLFragment();
        ret.append("(CASE WHEN (")
                .append(dayA).append(">").append(dayB)
                .append(") THEN (")
                .append("12*(").append(yearB).append("-").append(yearA).append(")")
                .append("+")
                .append(monthB).append("-").append(monthA).append("-1")
                .append(") ELSE (")
                .append("12*(").append(yearB).append("-").append(yearA).append(")")
                .append("+")
                .append(monthB).append("-").append(monthA)
                .append(") END)");
        return ret;
    }

    private void appendTimeDiffCol(AbstractTableInfo ti, ColumnInfo parent, String name, String label, int part)
    {
        appendTimeDiffCol(ti, parent, name, label, part, null);
    }

    private void appendTimeDiffCol(AbstractTableInfo ti, ColumnInfo parent, String name, String label, int part, Double divisor)
    {
        appendTimeDiffCol(ti, parent, name, label, part, divisor, false);
    }

    private void appendTimeDiffCol(AbstractTableInfo ti, ColumnInfo parent, String name, String label, int part, Double divisor, boolean floored)
    {
        if (ti.getColumn(name) == null)
        {
            JdbcType type;
            SQLFragment sql;
            if (divisor == null)
            {
                sql = new SQLFragment(ti.getSqlDialect().getDateDiff(part, "{fn curdate()}", ExprColumn.STR_TABLE_ALIAS + "." + parent.getName()));
                type = JdbcType.INTEGER;
            }
            else
            {
                String fragment = "((" + ti.getSqlDialect().getDateDiff(part, "{fn curdate()}", ExprColumn.STR_TABLE_ALIAS + "." + parent.getName()) + ") / ? )";
                if (floored)
                {
                    fragment = "floor(" + fragment + ")";
                    type = JdbcType.INTEGER;
                }
                else
                {
                    type = JdbcType.DOUBLE;
                }

                sql = new SQLFragment(fragment, divisor);
            }

            ColumnInfo col = new ExprColumn(ti, name, sql, type, parent);
            col.setLabel(label);
            col.setShownInDetailsView(false);
            col.setFormat("0.##");
            ti.addColumn(col);
        }
    }

    public static void customizeButtonBar(AbstractTableInfo ti, List<ButtonConfigFactory> buttons)
    {
        UserSchema us = ti.getUserSchema();
        if (us == null)
            return;

        ButtonBarConfig cfg = ti.getButtonBarConfig();
        if (cfg == null)
        {
            cfg = new ButtonBarConfig(new JSONObject());
            cfg.setIncludeStandardButtons(true);
        }

        //ensure client dependencies
        Set<String> scripts = new LinkedHashSet<String>();
        scripts.add("laboratory.context");
        String[] existingScripts = cfg.getScriptIncludes();
        if (existingScripts != null)
        {
            for (String s : existingScripts)
            {
                scripts.add(s);
            }
        }

        configureMoreActionsBtn(ti, buttons, cfg, scripts);

        cfg.setScriptIncludes(scripts.toArray(new String[scripts.size()]));

        ti.setButtonBarConfig(cfg);
    }

    private static void configureMoreActionsBtn(TableInfo ti, List<ButtonConfigFactory> buttons, ButtonBarConfig cfg, Set<String> scripts)
    {
        List<ButtonConfig> existingBtns = cfg.getItems();
        UserDefinedButtonConfig moreActionsBtn = null;
        if (existingBtns != null)
        {
            for (ButtonConfig btn : existingBtns)
            {
                if (btn instanceof UserDefinedButtonConfig)
                {
                    UserDefinedButtonConfig ub = (UserDefinedButtonConfig)btn;
                    if (MORE_ACTIONS.equals(ub.getText()))
                    {
                        moreActionsBtn = ub;
                        break;
                    }
                }
            }
        }

        if (moreActionsBtn == null)
        {
            //abort if there are no custom buttons
            if (buttons.size() == 0)
                return;

            moreActionsBtn = new UserDefinedButtonConfig();
            moreActionsBtn.setText(MORE_ACTIONS);
            moreActionsBtn.setInsertPosition(-1);
            existingBtns.add(moreActionsBtn);
            cfg.setItems(existingBtns);
        }

        List<NavTree> menuItems = new ArrayList<NavTree>();
        if (moreActionsBtn.getMenuItems() != null)
            menuItems.addAll(moreActionsBtn.getMenuItems());

        //create map of existing item names
        Map<String, NavTree> btnNameMap = new HashMap<String, NavTree>();
        for (NavTree item : menuItems)
        {
            btnNameMap.put(item.getText(), item);
        }

        for (ButtonConfigFactory fact : buttons)
        {
            NavTree newButton = fact.create(ti);
            if (!btnNameMap.containsKey(newButton.getText()))
            {
                btnNameMap.put(newButton.getText(), newButton);
                menuItems.add(newButton);

                for (ClientDependency cd : fact.getClientDependencies(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser()))
                {
                    scripts.add(cd.getScriptString());
                }
            }
        }

        moreActionsBtn.setMenuItems(menuItems);
    }

    public void customizeButtonBar(AbstractTableInfo ti)
    {
        UserSchema us = ti.getUserSchema();
        if (us == null)
            return;

        List<ButtonConfigFactory> buttons = LaboratoryService.get().getQueryButtons(ti);
        if (buttons != null)
            LaboratoryTableCustomizer.customizeButtonBar(ti, buttons);
    }

    private void customzieSamplesTable(AbstractTableInfo ti)
    {
        String name = "sampleCount";
        if (ti.getColumn(name) == null)
        {
            Container c = ti.getUserSchema().getContainer();
            c = c.isWorkbook() ? c.getParent() : c;
            SQLFragment containerSql = ContainerFilter.CURRENT.getSQLFragment(LaboratorySchema.getInstance().getSchema(), ti.getContainerFieldKey(), c);

            SQLFragment sql = new SQLFragment("(SELECT count(*) as _expr FROM laboratory.samples s WHERE " +
                    " (s." + containerSql + ")" +
                    " AND " + getNullSafeEqual("s.subjectid", ExprColumn.STR_TABLE_ALIAS + ".subjectid") +
                    " AND " + getNullSafeEqual("CAST(s.sampledate as DATE)", "CAST(" + ExprColumn.STR_TABLE_ALIAS + ".sampledate as DATE)") +
                    " AND " + getNullSafeEqual("s.sampletype", ExprColumn.STR_TABLE_ALIAS + ".sampletype") +
                    " AND " + getNullSafeEqual("s.samplesubtype", ExprColumn.STR_TABLE_ALIAS + ".samplesubtype") +
                    " AND " + ExprColumn.STR_TABLE_ALIAS + ".dateremoved IS NULL" +
                    " AND s.rowid != " + ExprColumn.STR_TABLE_ALIAS + ".rowid" +
                    ")");

            ExprColumn col = new ExprColumn(ti, name, sql, JdbcType.INTEGER, ti.getColumn("subjectid"), ti.getColumn("sampledate"), ti.getColumn("sampletype"), ti.getColumn("samplesubtype"));
            col.setLabel("Matching Samples");
            col.setFacetingBehaviorType(FacetingBehaviorType.ALWAYS_OFF);
            col.setDescription("This column show the total number of active freezer samples with the same subjectId, sample date, sample type and sample subtype as the current row");
            col.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=laboratory&query.queryName=samples&query.subjectId~eq=${subjectId}&query.sampledate~eq=${sampledate}&query.sampletype~eq=${sampletype}&query.samplesubtype~eq=${samplesubtype}", c));
            ti.addColumn(col);
        }

        LDKService.get().applyNaturalSort(ti, "freezer");
        LDKService.get().applyNaturalSort(ti, "cane");
        LDKService.get().applyNaturalSort(ti, "box");
        LDKService.get().applyNaturalSort(ti, "box_row");
        LDKService.get().applyNaturalSort(ti, "box_column");

        LDKService.get().applyNaturalSort(ti, "subjectId");
    }

    private String getNullSafeEqual(String col1, String col2)
    {
        return "(" + col1 + " = " + col2 + " OR (" + col1 + " IS NULL AND " + col2 + " IS NULL))";
    }
}
