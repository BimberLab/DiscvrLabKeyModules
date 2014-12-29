/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.laboratory;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.security.LaboratoryAdminPermission;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.ldk.notification.NotificationService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.laboratory.notification.LabSummaryNotification;
import org.labkey.laboratory.query.WorkbookModel;
import org.labkey.laboratory.security.LaboratoryAdminRole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 9/1/12
 * Time: 8:14 PM
 */
public class LaboratoryModule extends ExtendedSimpleModule
{
    public static final String NAME = "Laboratory";
    public static final String CONTROLLER_NAME = "laboratory";
    public static final String SCHEMA_NAME = "laboratory";

    public String getName()
    {
        return NAME;
    }

    public double getVersion()
    {
        return 12.301;
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(
            new BaseWebPartFactory("Workbook Header")
            {
                public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart)
                {
                    WorkbookModel model = LaboratoryManager.get().getWorkbookModel(portalCtx.getContainer());
                    if (model == null)
                    {
                        return new HtmlView("This container is not a workbook");
                    }

                    JspView<WorkbookModel> view = new JspView<>("/org/labkey/laboratory/view/workbookHeader.jsp", model);
                    view.setTitle("Workbook Header");
                    view.setFrame(WebPartView.FrameType.NONE);
                    return view;
                }

                @Override
                public boolean isAvailable(Container c, String location)
                {
                    return false;
                }
            }
        ));
    }

    protected void init()
    {
        addController(CONTROLLER_NAME, LaboratoryController.class);

        LaboratoryServiceImpl impl = new LaboratoryServiceImpl();
        LaboratoryService.setInstance(impl);
    }

    @Override
    protected void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        RoleManager.registerRole(new LaboratoryAdminRole());

        LaboratoryService.get().registerDataProvider(new LaboratoryDataProvider(this));
        LaboratoryService.get().registerDataProvider(new SampleSetDataProvider());
        LaboratoryService.get().registerDataProvider(new ExtraDataSourcesDataProvider(this));

        DetailsURL details = DetailsURL.fromString("/laboratory/siteLabSettings.view");
        details.setContainerContext(ContainerManager.getSharedContainer());
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "laboratory module admin", details.getActionURL());
        NotificationService.get().registerNotification(new LabSummaryNotification(this));

        LaboratoryService.get().registerTableIndex("core", "containers", Arrays.asList("RowId", "Parent", "EntityId", "Type"));
        LaboratoryService.get().registerTableIndex("exp", "data", Arrays.asList("RowId", "RunId", "Container"));

        LDKService.get().registerContainerScopedTable(SCHEMA_NAME, LaboratorySchema.TABLE_SAMPLE_TYPE, "type");
        LDKService.get().registerContainerScopedTable(SCHEMA_NAME, LaboratorySchema.TABLE_FREEZERS, "name");
        LDKService.get().registerContainerScopedTable(SCHEMA_NAME, LaboratorySchema.TABLE_SUBJECTS, "subjectname");
    }

    @Override
    protected void registerContainerListeners()
    {
        ContainerManager.addContainerListener(new LaboratoryContainerListener(this));
    }

    @Override
    protected void registerSchemas()
    {
        LaboratoryUserSchema.register(this);
        ExtraDataSourcesUserSchema.register(this);
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(SCHEMA_NAME);
    }

    @NotNull
    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies(Container c)
    {
        // allow other modules to register with EHR service, and include their dependencies automatically
        // whenever laboratory context is requested
        LinkedHashSet<ClientDependency> ret = new LinkedHashSet<>();
        ret.addAll(super.getClientDependencies(c));
        ret.addAll(LaboratoryService.get().getRegisteredClientDependencies(c));

        return ret;
    }

    @Override
    public @NotNull JSONObject getPageContextJson(ViewContext context)
    {
        Map<String, Object> ret = new HashMap<>();
        ret.putAll(super.getPageContextJson(context));

        ret.put("isLaboratoryAdmin", context.getContainer().hasPermission(context.getUser(), LaboratoryAdminPermission.class));

        return new JSONObject(ret);
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new LaboratoryUpgradeCode();
    }
}
