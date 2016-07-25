package org.labkey.su2c;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

/**
 * Created by bimber on 11/18/2015.
 */
public class Su2cCustomizer extends AbstractTableCustomizer
{
    @Override
    public void customize(TableInfo ti)
    {
        LDKService.get().getDefaultTableCustomizer().customize(ti);

        //this allows us to apply standard customization to any table configured to use this customizer
        if (ti instanceof AbstractTableInfo)
        {
            AbstractTableInfo ati = (AbstractTableInfo)ti;

            doSharedCustomization(ati);

            //now table-specific customization:
            if (matches(ti, "study", "demographics"))
            {
                customizeDemographicsTable((AbstractTableInfo) ti);
            }
            else if (matches(ti, "study", "IHC Images"))
            {
                customizeImagesTable((AbstractTableInfo) ti);
            }
        }
    }

    private void doSharedCustomization(AbstractTableInfo ti)
    {
        if (ti.getColumn("patientId") != null)
        {
            ti.getColumn("patientId").setRequired(true);
            ti.getColumn("patientId").setFacetingBehaviorType(FacetingBehaviorType.ALWAYS_OFF);
//            UserSchema studySchema = QueryService.get().getUserSchema(ti.getUserSchema().getUser(), ti.getUserSchema().getContainer(), "study");
//            if (studySchema != null)
//            {
//                ti.getColumn("patientId").setFk(new QueryForeignKey(studySchema, ti.getUserSchema().getContainer(), "Demographics", "patientId", "patientId", true));
//            }
        }

        //add more code below, to test for columns and apply other standardizations, such as always hiding built-in fields
        if (ti.getColumn("lsid") != null)
        {
            ti.getColumn("lsid").setHidden(true);
        }

        if (ti.getColumn("StudyId") != null)
        {
            ti.getColumn("StudyId").setLabel("Study ID");
        }

        if (ti.getColumn("objectid") != null)
        {
            ti.getColumn("objectid").setHidden(true);
        }

        if (ti instanceof DatasetTable)
        {
            customizeDataset((DatasetTable)ti);
        }
    }

    private void customizeDataset(DatasetTable ti)
    {

    }

    private void customizeDemographicsTable(AbstractTableInfo ti)
    {
        if (ti.getColumn("somaticMutations") == null)
        {
            UserSchema us = getUserSchema(ti, "study");
            ColumnInfo col = getWrappedIdCol(us, ti, "somaticMutations", "demographicsSomaticMutations");
            col.setLabel("Somatic Mutation Summary");
            col.setDescription("Provides summaries of somatic mutation data");
            col.setDisplayWidth("150");
            ti.addColumn(col);
        }

        addDatasetCountCols(ti);
    }

    private void customizeImagesTable(AbstractTableInfo ti)
    {
        if (ti.getColumn("imageAnalyses") == null)
        {
            UserSchema us = getUserSchema(ti, "study");
            ColumnInfo col = getWrappedCol(us, ti, "imageAnalyses", "imageAnalyses", "objectid", "imageId");
            col.setLabel("Quantitative Analyses");
            col.setDescription("Provides a summary of an quanitative analyses");
            col.setDisplayWidth("250");
            ti.addColumn(col);
        }
    }

    private void addDatasetCountCols(AbstractTableInfo ti)
    {
        Study s = StudyService.get().getStudy(ti.getUserSchema().getContainer());
        if (s == null)
        {
            return;
        }

        for (Dataset ds : s.getDatasets())
        {
            if (ds.getName().equalsIgnoreCase(ti.getName()))
            {
                //skip demographics
                continue;
            }

            String name = ds.getName() + "_count";
            if (ti.getColumn(name) == null)
            {
                TableInfo dti = ds.getTableInfo(ti.getUserSchema().getUser());
                if (dti != null)
                {
                    String colName = StudyService.get().getSubjectColumnName(ds.getContainer());
                    SQLFragment sql = new SQLFragment("(SELECT count(*) as expr FROM studydataset." + ds.getDomain().getStorageTableName() + " d WHERE d." + dti.getColumn(colName).getSelectName() + " = " + ExprColumn.STR_TABLE_ALIAS + "." + dti.getColumn(colName).getSelectName() + ")");
                    ExprColumn newCol = new ExprColumn(ti, name, sql, JdbcType.INTEGER, dti.getColumn(colName));
                    newCol.setLabel(ds.getLabel());
                    newCol.setDescription("This shows the total number of records in the dataset " + ds.getLabel() + " for each Id");
                    newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=study&queryName=" + ds.getLabel() + "&query." + colName + "~eq=${" + colName + "}"));
                    newCol.setHidden(true);
                    ti.addColumn(newCol);

                    //also simple yes/no version:
                    String name2 = ds.getName() + "_hasData";
                    SQLFragment sql2 = new SQLFragment("(SELECT CASE WHEN count(*) > 0 THEN 'Y' ELSE null END as expr FROM studydataset." + ds.getDomain().getStorageTableName() + " d WHERE d." + dti.getColumn(colName).getSelectName() + " = " + ExprColumn.STR_TABLE_ALIAS + "." + dti.getColumn(colName).getSelectName() + ")");
                    ExprColumn newCol2 = new ExprColumn(ti, name2, sql2, JdbcType.VARCHAR, dti.getColumn(colName));
                    newCol2.setLabel(ds.getLabel() + "?");
                    newCol2.setDescription("This shows whether any records exist in the dataset " + ds.getLabel() + " for each Id");
                    newCol2.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=study&queryName=" + ds.getLabel() + "&query." + colName + "~eq=${" + colName + "}"));
                    ti.addColumn(newCol2);
                }
            }
        }
    }

    private ColumnInfo getWrappedIdCol(UserSchema us, AbstractTableInfo ds, String name, String queryName)
    {
        return getWrappedCol(us, ds, name, queryName, "patientId", "patientId");
    }

    private ColumnInfo getWrappedCol(UserSchema us, AbstractTableInfo ds, String name, String queryName, String colName, String targetCol)
    {

        WrappedColumn col = new WrappedColumn(ds.getColumn(colName), name);
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        col.setUserEditable(false);
        col.setFk(new QueryForeignKey(us, null, queryName, targetCol, targetCol));

        return col;
    }
}