package org.labkey.jbrowse.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.UrlColumn;
import org.labkey.api.query.DetailsURL;

/**
 * User: bimber
 * Date: 7/18/2014
 * Time: 6:02 AM
 */
public class DatabaseDisplayColumnFactory implements DisplayColumnFactory
{
    public DatabaseDisplayColumnFactory()
    {

    }

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        DisplayColumn ret = new UrlColumn(DetailsURL.fromString("/jbrowse/browser.view?database=${objectid}", colInfo.getParentTable().getContainerContext()), "View In JBrowse");
        ret.setName(colInfo.getName());
        ret.setCaption(colInfo.getLabel());

        return ret;
    }
}
