package org.labkey.laboratory;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.QueryImportNavItem;
import org.labkey.api.security.User;

/**

 */
public class ExtraDataSourceImportNavItem extends QueryImportNavItem
{
    private AdditionalDataSource _source;

    public ExtraDataSourceImportNavItem(DataProvider provider, AdditionalDataSource source)
    {
        super(provider, source.getTargetContainer(null), source.getSchemaName(), source.getQueryName(), source.getItemType(), source.getLabel(), source.getReportCategory());
        _source = source;
    }

    public AdditionalDataSource getSource()
    {
        return _source;
    }

    @Override
    public boolean isImportIntoWorkbooks(Container c, User u)
    {
        //if disabled, dont attempt to use workbooks
        if (_source.isImportIntoWorkbooks() == false)
        {
            return false;
        }

        //otherwise defer to the table, since this table might not even support workbook import
        return super.isImportIntoWorkbooks(c, u);
    }
}
