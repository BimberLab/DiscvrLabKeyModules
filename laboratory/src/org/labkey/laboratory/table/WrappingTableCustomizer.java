package org.labkey.laboratory.table;

import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.ldk.LDKService;

/**
 * User: bimber
 * Date: 9/22/13
 * Time: 1:43 PM
 */
public class WrappingTableCustomizer implements TableCustomizer
{
    public WrappingTableCustomizer()
    {

    }

    public void customize(TableInfo ti)
    {
        LaboratoryService.get().getLaboratoryTableCustomizer().customize(ti);

        if (ti.getPkColumnNames().size() > 0)
            LDKService.get().getDefaultTableCustomizer().customize(ti);
        else
            LDKService.get().getBuiltInColumnsCustomizer(true).customize(ti);
    }
}
