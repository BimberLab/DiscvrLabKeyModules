package org.labkey.GeneticsCore;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;

import java.util.List;

/**
 * Created by bimber on 12/1/2014.
 */
public class GeneticsTableCustomizer implements TableCustomizer
{
    @Override
    public void customize(TableInfo table)
    {
        if (table instanceof AbstractTableInfo)
        {
            AbstractTableInfo ti = (AbstractTableInfo) table;

            if (ti.getColumn("numCachedResults") == null)
            {
                AssayProvider ap = AssayService.get().getProvider("Genotype Assay");
                if (ap == null)
                {
                    return;
                }

                List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(ti.getUserSchema().getContainer(), ap);
                if (protocols.size() != 1)
                {
                    return;
                }

                Domain d = ap.getResultsDomain(protocols.get(0));
                SQLFragment sql = new SQLFragment("(select count(*) FROM assayresult." + d.getStorageTableName() + " a WHERE a.analysisId = " + ExprColumn.STR_TABLE_ALIAS + ".rowid)");
                ExprColumn newCol = new ExprColumn(ti, "numCachedResults", sql, JdbcType.INTEGER, ti.getColumn("rowid"));
                newCol.setLabel("# Cached Results");
                newCol.setURL(DetailsURL.fromString("/query/executeQuery.view?schemaName=assay." + ap.getName().replaceAll(" ", "") + "." + protocols.get(0).getName() + "&query.queryName=data&query.analysisId~eq=${rowid}"));
                ti.addColumn(newCol);
            }
        }
    }
}
