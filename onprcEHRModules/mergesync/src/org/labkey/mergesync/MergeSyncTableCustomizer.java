package org.labkey.mergesync;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.WrappedColumn;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;

/**

 */
public class MergeSyncTableCustomizer extends AbstractTableCustomizer
{
    public MergeSyncTableCustomizer()
    {

    }

    public void customize(TableInfo ti)
    {
        if (ti instanceof AbstractTableInfo && (matches(ti, "study", "Clinpath Runs") || matches(ti, "study", "clinpathRuns")))
        {
            customizeClinpathRuns((AbstractTableInfo)ti);
        }
    }

    private void customizeClinpathRuns(AbstractTableInfo ti)
    {
        if (ti.getUserSchema().getContainer().getActiveModules().contains(ModuleLoader.getInstance().getModule(MergeSyncModule.NAME)))
        {
            String name = "mergeSyncInfo";
            if (ti.getColumn(name) == null && ti.getColumn("servicerequested") != null)
            {
                final UserSchema us = getUserSchema(ti, MergeSyncSchema.NAME);
                ColumnInfo ci = new WrappedColumn(ti.getColumn("servicerequested"), name);
                LookupForeignKey fk = new LookupForeignKey()
                {
                    @Override
                    public TableInfo getLookupTableInfo()
                    {
                        return us.getTable(MergeSyncManager.TABLE_TESTNAMEMAPPING);
                    }
                };

                ci.setFk(fk);
                ci.setUserEditable(false);
                ci.setIsUnselectable(true);
                ci.setLabel("Merge Sync Info");
                ti.addColumn(ci);
            }
        }
    }
}
