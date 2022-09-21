package org.labkey.blast;

import org.jetbrains.annotations.NotNull;
import org.json.old.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.laboratory.DetailsUrlWithoutLabelNavItem;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.SimpleSettingsItem;
import org.labkey.api.laboratory.SummaryNavItem;
import org.labkey.api.laboratory.TabbedReportItem;
import org.labkey.api.laboratory.NavItem;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.AbstractSequenceDataProvider;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 7/20/2014
 * Time: 11:56 AM
 */
public class BLASTDataProvider extends AbstractSequenceDataProvider
{
    public static final String NAME = "BLAST";
    private Module _module;

    public BLASTDataProvider(Module m)
    {
        _module = m;
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public ActionURL getInstructionsUrl(Container c, User u)
    {
        return null;
    }

    @Override
    public List<NavItem> getDataNavItems(Container c, User u)
    {
//        List<NavItem> items = new ArrayList<>();
//        if (ContainerManager.getSharedContainer().equals(c))
//        {
//            items.add(new SimpleSettingsItem(this, BLASTSchema.NAME, BLASTSchema.TABLE_DATABASES, NAME, "BLAST Databases"));
//        }

        return Collections.emptyList();
    }

    @Override
    public List<NavItem> getSampleNavItems(Container c, User u)
    {
        return Collections.emptyList();
    }

    @Override
    public List<NavItem> getReportItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<>();
        if (ContainerManager.getSharedContainer().equals(c))
        {
            items.add(new SimpleSettingsItem(this, BLASTSchema.NAME, BLASTSchema.TABLE_DATABASES, NAME, "Databases"));
        }

        return items;
    }

    @Override
    public List<NavItem> getSettingsItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<>();
        if (ContainerManager.getSharedContainer().equals(c))
        {
            items.add(new DetailsUrlWithoutLabelNavItem(this, "BLAST Settings", DetailsURL.fromString("/blast/settings.view"), LaboratoryService.NavItemCategory.settings, NAME));
        }

        return items;
    }

    @Override
    public List<NavItem> getMiscItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<>();
        if (c.getActiveModules().contains(ModuleLoader.getInstance().getModule(BLASTModule.class)))
        {
            items.add(new DetailsUrlWithoutLabelNavItem(this, "Run BLAST", DetailsURL.fromString("/blast/blast.view"), LaboratoryService.NavItemCategory.misc, NAME));
        }

        return items;
    }

    @Override
    public JSONObject getTemplateMetadata(ViewContext ctx)
    {
        return new JSONObject();
    }

    @Override @NotNull
    public Set<ClientDependency> getClientDependencies()
    {
        return Collections.emptySet();
    }

    @Override
    public Module getOwningModule()
    {
        return _module;
    }

    @Override
    public List<SummaryNavItem> getSummary(Container c, User u)
    {
        return Collections.emptyList();
    }

    @Override
    public List<NavItem> getSubjectIdSummary(Container c, User u, String subjectId)
    {
        return Collections.emptyList();
    }

    @Override
    public List<TabbedReportItem> getTabbedReportItems(Container c, User u)
    {
        return Collections.emptyList();
    }

    @Override
    public List<NavItem> getSequenceNavItems(Container c, User u, SequenceNavItemCategory category)
    {
        List<NavItem> ret = new ArrayList<>();
        if (c.getActiveModules().contains(ModuleLoader.getInstance().getModule(BLASTModule.class)))
        {
            if (category == SequenceNavItemCategory.misc)
            {
                ret.add(new DetailsUrlWithoutLabelNavItem(this, "Run BLAST", DetailsURL.fromString("/blast/blast.view"), LaboratoryService.NavItemCategory.misc, NAME));
            }
            else if (category == SequenceNavItemCategory.references)
            {
                ret.add(DetailsUrlWithoutLabelNavItem.createForQuery(this, u, c, BLASTSchema.NAME, BLASTSchema.TABLE_DATABASES, "BLAST Databases", LaboratoryService.NavItemCategory.misc, NAME));
            }
        }

        return Collections.unmodifiableList(ret);
    }
}
