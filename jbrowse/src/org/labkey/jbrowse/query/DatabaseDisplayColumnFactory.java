package org.labkey.jbrowse.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.UrlColumn;
import org.labkey.jbrowse.JBrowseManager;

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
        String url = JBrowseManager.get().getJBrowseBaseUrl();
        if (url != null)
        {
            url += "?data=";

            if (JBrowseManager.get().getJBrowseDbPrefix() != null)
                url += JBrowseManager.get().getJBrowseDbPrefix();

            url += "databases/${objectid}";

        }

        DisplayColumn ret;
        if (url != null)
        {
            ret = new UrlColumn(url, "View In JBrowse");
        }
        else
        {
            ret = new UrlColumn("javascript:void(0);", "JBrowse URL Not Configured");
        }

        ret.setName(colInfo.getName());
        ret.setCaption(colInfo.getLabel());

        return ret;
    }
}
