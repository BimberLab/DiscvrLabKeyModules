package org.labkey.studies.query;

import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.AbstractTableCustomizer;
import org.labkey.api.study.DatasetTable;

public class StudiesCustomizer extends AbstractTableCustomizer
{
    @Override
    public void customize(TableInfo tableInfo)
    {
        if (tableInfo instanceof DatasetTable ds)
        {
            performDatasetCustomization(ds);
        }
    }

    public void performDatasetCustomization(DatasetTable ds)
    {

    }
}
