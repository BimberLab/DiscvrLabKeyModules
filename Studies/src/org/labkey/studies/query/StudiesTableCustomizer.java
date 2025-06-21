package org.labkey.studies.query;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.util.logging.LogHelper;

public class StudiesTableCustomizer implements TableCustomizer
{
    private static final Logger _log = LogHelper.getLogger(StudiesTableCustomizer.class, "Messages from StudiesTableCustomizer");

    @Override
    public void customize(TableInfo tableInfo)
    {
        LDKService.get().getDefaultTableCustomizer().customize(tableInfo);
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
        // Overlapping studies/cohorts
        // TimepointLabel
    }
}
