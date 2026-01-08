package org.labkey.studies.query;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveKeyedHashSetValuedMap;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.studies.StudiesServiceImpl;

import java.util.ArrayList;
import java.util.List;

public class StudiesTableCustomizer implements TableCustomizer
{
    private static final Logger _log = LogHelper.getLogger(StudiesTableCustomizer.class, "Messages from StudiesTableCustomizer");

    @Override
    public void customize(TableInfo tableInfo)
    {
        MultiValuedMap<String, String> props = new CaseInsensitiveKeyedHashSetValuedMap<>();
        if (tableInfo.getPkColumnNames().size() > 1)
        {
            final CaseInsensitiveHashMap<String> keys = new CaseInsensitiveHashMap<>();
            tableInfo.getPkColumnNames().forEach(x -> keys.put(x, x));

            if (keys.containsKey("objectId"))
            {
                props.put("primaryKeyField", keys.get("objectId"));
            }
            else if (tableInfo instanceof DatasetTable ds)
            {
                if (ds.getDataset().isDemographicData())
                {
                    String subjectCol = ds.getDataset().getStudy().getSubjectColumnName();
                    if (keys.containsKey(subjectCol))
                    {
                        props.put("primaryKeyField", keys.get(subjectCol));
                    }
                    else
                    {
                        _log.error("Demographics dataset does not list subject col (" + subjectCol + ") as a PK. Table: " + tableInfo.getName());
                    }
                }
            }
        }

        LDKService.get().getDefaultTableCustomizer(props).customize(tableInfo);
        if (tableInfo instanceof AbstractTableInfo ati)
        {
            doCustomize(ati);
        }
        else
        {
            _log.error("Expected table to be instance of AbstractTableInfo. Table: " + tableInfo.getName());
        }
    }

    private void doCustomize(AbstractTableInfo ati)
    {
        // TODO:
        // TimepointLabel

        addProjectAssignmentColumns(ati);
    }

    private String getSubjectColName(Container c)
    {
        Study s = StudyService.get().getStudy(c.isWorkbookOrTab() ? c.getParent() : c);
        if (s == null)
        {
            return null;
        }

        return s.getSubjectColumnName();
    }

    private void addProjectAssignmentColumns(AbstractTableInfo ati)
    {
        final String pivotColName = "allProjectsPivot";
        if (ati.getColumn(pivotColName) != null)
            return;

        List<ColumnInfo> pks = ati.getPkColumns();
        ColumnInfo pk;
        if (pks.size() == 1)
        {
            pk = pks.get(0);
        }
        else
        {
            if (! (ati instanceof DatasetTable))
            {
                _log.error("Table does not have a single PK column: " + ati.getName());
                return;
            }
            else
            {
                pk = pks.get(0);
            }
        }

        if (!StudiesServiceImpl.get().hasAssignmentDataset(ati.getUserSchema().getContainer()))
        {
            return;
        }

        final String subjectSelectName = getSubjectColName(ati.getUserSchema().getContainer());
        if (subjectSelectName == null)
        {
            _log.error("Unable to find subjectSelectName in StudiesTableCustomizer");
            return;
        }

        final String pkColSelectName = pk.getFieldKey().toSQLString();

        final String lookupName = ati.getName() + "_allProjectsPivot";
        BaseColumnInfo col2 = new ExprColumn(ati, FieldKey.fromString(pivotColName), pk.getValueSql(ExprColumn.STR_TABLE_ALIAS), pk.getJdbcType(), pk);
        col2.setLabel("Assignment By Study");
        col2.setName(pivotColName);
        col2.setCalculated(true);
        col2.setShownInInsertView(false);
        col2.setShownInUpdateView(false);
        col2.setDescription("Shows groups to which this subject belonged at any point in time.");
        col2.setHidden(true);
        col2.setReadOnly(true);
        col2.setIsUnselectable(true);
        col2.setUserEditable(false);
        col2.setKeyField(false);
        col2.setFk(new LookupForeignKey(){
            @Override
            public TableInfo getLookupTableInfo()
            {
                final UserSchema us = ati.getUserSchema();
                Container target = us.getContainer().isWorkbookOrTab() ? us.getContainer().getParent() : us.getContainer();
                QueryDefinition qd = createQueryDef(us, lookupName);

                qd.setSql(getAssignmentPivotSql(target, ati, pkColSelectName, subjectSelectName));
                qd.setIsTemporary(true);

                List<QueryException> errors = new ArrayList<>();
                TableInfo ti = qd.getTable(errors, true);

                if (!errors.isEmpty()){
                    _log.error("Problem with table customizer: " + ati.getPublicName());
                    for (QueryException e : errors)
                    {
                        _log.error(e.getMessage());
                    }
                }

                if (ti != null)
                {
                    MutableColumnInfo col = (MutableColumnInfo) ti.getColumn(pk.getName());
                    col.setKeyField(true);
                    col.setHidden(true);

                    ((MutableColumnInfo)ti.getColumn("lastStartDate")).setLabel("Most Recent Assignment Date");
                }

                return ti;
            }
        });

        ati.addColumn(col2);
    }

    private String getAssignmentPivotSql(Container source, final AbstractTableInfo ati, String pkColSelectName, String subjectSelectName)
    {
        return "SELECT\n" +
                "s." + pkColSelectName + ",\n" +
                "p.study,\n" +
                "max(p.date) as lastStartDate\n" +
                "\n" +
                "FROM " + ati.getPublicSchemaName() + "." + ati.getPublicName() + " s\n" +
                "JOIN \"" + source.getPath() + "\".study.assignment p\n" +
                "ON (s." + subjectSelectName + " = p." + subjectSelectName + ")\n" +
                "WHERE s." + subjectSelectName + " IS NOT NULL\n" +
                "\n" +
                "GROUP BY s." + pkColSelectName + ", p.study\n" +
                "PIVOT lastStartDate by study IN (select distinct studyName from studies.studies)";
    }

    // TODO: move to parent class
    protected QueryDefinition createQueryDef(UserSchema us, String queryName)
    {
        if (!us.getContainer().isWorkbook())
        {
            return QueryService.get().createQueryDef(us.getUser(), us.getContainer(), us, queryName);
        }

        // The rationale is that if we are querying from a workbook, preferentially translate to the parent US
        // However, there are situations like workbook-scoped lists, where that query might not exist on the parent
        UserSchema parentUserSchema = QueryService.get().getUserSchema(us.getUser(), us.getContainer().getParent(), us.getSchemaPath());
        assert parentUserSchema != null;

        if (parentUserSchema.getTableNames().contains(queryName))
        {
            return QueryService.get().createQueryDef(parentUserSchema.getUser(), parentUserSchema.getContainer(), parentUserSchema, queryName);
        }
        else
        {
            return QueryService.get().createQueryDef(us.getUser(), us.getContainer(), us, queryName);
        }
    }
}
