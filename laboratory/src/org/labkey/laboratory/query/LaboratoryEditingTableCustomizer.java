package org.labkey.laboratory.query;

import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;

/**
 * Created by bimber on 2/3/2015.
 */
public class LaboratoryEditingTableCustomizer implements TableCustomizer
{
    @Override
    public void customize(TableInfo tableInfo)
    {
        LDKService.get().getDefaultTableCustomizer().customize(tableInfo);
        new LaboratoryTableCustomizer().customize(tableInfo);
    }
}
