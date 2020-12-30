package org.labkey.singlecell;

import org.labkey.api.data.Container;
import org.labkey.api.laboratory.AbstractImportingNavItem;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

public class SingleCellBulkImportNavItem extends AbstractImportingNavItem
{
    public static final String NAME = "TCR/10x Import";

    private String _url;

    public SingleCellBulkImportNavItem(DataProvider provider, String label, LaboratoryService.NavItemCategory itemType, String reportCategory, String url)
    {
        super(provider, NAME, label, itemType, (reportCategory == null ? "Single Cell" : reportCategory));
        _url = url;
    }

    @Override
    public ActionURL getImportUrl(Container c, User u)
    {
        return DetailsURL.fromString(_url).getActionURL();
    }

    @Override
    public ActionURL getSearchUrl(Container c, User u)
    {
        return null;
    }

    @Override
    public ActionURL getBrowseUrl(Container c, User u)
    {
        return null;
    }

    @Override
    public boolean isImportIntoWorkbooks(Container c, User u)
    {
        return true;
    }

    @Override
    public boolean getDefaultVisibility(Container c, User u)
    {
        return getTargetContainer(c).getActiveModules().contains(ModuleLoader.getInstance().getModule(SingleCellModule.NAME));
    }
}
