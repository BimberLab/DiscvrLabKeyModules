package org.labkey.laboratory.query;

import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.query.DetailsURL;

/**
 * User: bimber
 * Date: 11/9/12
 * Time: 9:21 AM
 */
public class FreezersCustomizer implements TableCustomizer
{
    public FreezersCustomizer()
    {

    }

    public void customize(TableInfo ti)
    {
        //apply defaults
        TableCustomizer tc = LDKService.get().getBuiltInColumnsCustomizer(true);
        tc.customize(ti);

        if (ti instanceof AbstractTableInfo)
        {
            AbstractTableInfo ati = (AbstractTableInfo)ti;

            String schemaName = ati.getPublicSchemaName();
            assert schemaName != null;

            String queryName = ati.getPublicName();
            assert queryName != null;

            String keyField = "rowid";
            ati.setDetailsURL(DetailsURL.fromString("/laboratory/freezerDetails.view?freezerName=${name}"));
            ati.setImportURL(DetailsURL.fromString("/query/importData.view?schemaName=" + schemaName + "&query.queryName=" + queryName + "&keyField=" + keyField + "&columns=*"));
            ati.setInsertURL(AbstractTableInfo.LINK_DISABLER);
            ati.setUpdateURL(DetailsURL.fromString("/query/manageRecord.view?schemaName=" + schemaName + "&query.queryName=" + queryName + "&keyField=" + keyField + "&key=${" + keyField + "}&columns=*"));
        }
    }
}