package org.labkey.studies.query;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveKeyedHashSetValuedMap;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.studies.StudiesSchema;
import org.labkey.studies.StudiesServiceImpl;

import java.util.Objects;

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

    private void addProjectAssignmentColumns(AbstractTableInfo ati)
    {
        final String pivotColName = "allProjectsPivot";
        if (ati.getColumn(pivotColName) != null)
        {
            return;
        }

        if (!StudiesServiceImpl.get().hasAssignmentDataset(ati.getUserSchema().getContainer()))
        {
            return;
        }

        final String subjectColumnName = Objects.requireNonNull(StudyService.get()).getSubjectColumnName(ati.getUserSchema().getContainer().isWorkbookOrTab() ? ati.getUserSchema().getContainer().getParent() : ati.getUserSchema().getContainer());
        if (subjectColumnName == null)
        {
            _log.error("Unable to find the study's subjectColumn in StudiesTableCustomizer");
            return;
        }

        ColumnInfo subjectCol = ati.getColumn(subjectColumnName);
        if (subjectCol == null)
        {
            _log.error("Table lacks the column " + subjectColumnName + ", " + ati.getName());
            return;
        }

        Container target = ati.getUserSchema().getContainer().isWorkbookOrTab() ? ati.getUserSchema().getContainer().getParent() : ati.getUserSchema().getContainer();

        UserSchema studiesUs = QueryService.get().getUserSchema(ati.getUserSchema().getUser(), target, StudiesSchema.NAME);
        BaseColumnInfo col2 = new ExprColumn(ati, FieldKey.fromString(pivotColName), subjectCol.getValueSql(ExprColumn.STR_TABLE_ALIAS), subjectCol.getJdbcType(), subjectCol);
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
        col2.setFk(new QueryForeignKey(studiesUs, null, studiesUs, target, StudiesUserSchema.TABLE_ASSIGNMENT_BY_STUDY, subjectColumnName, subjectColumnName));

        ati.addColumn(col2);
    }
}
