/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.onprc_billing;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ehr.buttons.MarkCompletedButton;
import org.labkey.api.ehr.dataentry.DefaultDataEntryFormFactory;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.ldk.notification.NotificationService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.onprc_billing.button.ChangeBillDateButton;
import org.labkey.onprc_billing.button.ChargeEditButton;
import org.labkey.onprc_billing.button.ProjectEditButton;
import org.labkey.onprc_billing.dataentry.ChargesAdvancedFormType;
import org.labkey.onprc_billing.dataentry.ChargesFormType;
import org.labkey.onprc_billing.dataentry.ReversalFormType;
import org.labkey.onprc_billing.notification.BillingValidationNotification;
import org.labkey.onprc_billing.notification.DCMFinanceNotification;
import org.labkey.onprc_billing.notification.FinanceNotification;
import org.labkey.onprc_billing.pipeline.BillingPipelineProvider;
import org.labkey.onprc_billing.query.BillingAuditProvider;
import org.labkey.onprc_billing.query.ONPRC_EHRBillingUserSchema;
import org.labkey.onprc_billing.security.ONPRCAliasEditorPermission;
import org.labkey.onprc_billing.security.ONPRCAliasEditorRole;
import org.labkey.onprc_billing.security.ONPRCBillingAdminPermission;
import org.labkey.onprc_billing.security.ONPRCBillingAdminRole;
import org.labkey.onprc_billing.table.ChargeableItemsCustomizer;
import org.labkey.onprc_billing.table.ONPRC_BillingCustomizer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ONPRC_BillingModule extends ExtendedSimpleModule
{
    public static final String NAME = "ONPRC_Billing";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 12.372;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void init()
    {
        addController(ONPRC_BillingController.NAME, ONPRC_BillingController.class);

        RoleManager.registerRole(new ONPRCBillingAdminRole());
        RoleManager.registerRole(new ONPRCAliasEditorRole());
    }

    @Override
    protected void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        AuditLogService.get().registerAuditType(new BillingAuditProvider());

        PipelineService.get().registerPipelineProvider(new BillingPipelineProvider(this));

        NotificationService.get().registerNotification(new FinanceNotification());
        NotificationService.get().registerNotification(new DCMFinanceNotification());
        NotificationService.get().registerNotification(new BillingValidationNotification());

        EHRService.get().registerTableCustomizer(this, ONPRC_BillingCustomizer.class);
        EHRService.get().registerTableCustomizer(this, ChargeableItemsCustomizer.class, "onprc_billing", "chargeableItems");
        EHRService.get().registerFormType(new DefaultDataEntryFormFactory(ChargesAdvancedFormType.class, this));
        EHRService.get().registerFormType(new DefaultDataEntryFormFactory(ChargesFormType.class, this));
        EHRService.get().registerFormType(new DefaultDataEntryFormFactory(ReversalFormType.class, this));

        //NOTE: not really being used, so have disabled
        //Resource billingTriggers = getModuleResource("/scripts/onprc_billing/billing_triggers.js");
        //assert billingTriggers != null;
        //EHRService.get().registerTriggerScript(this, billingTriggers);

        LDKService.get().registerContainerScopedTable(ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_CREDIT_GRANTS, "grantNumber");
        LDKService.get().registerContainerScopedTable(ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_ALIASES, "alias");

        EHRService.get().registerMoreActionsButton(new ProjectEditButton(this, ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_PROJECT_ACCOUNT_HISTORY, ONPRCAliasEditorPermission.class), ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_PROJECT_ACCOUNT_HISTORY);

        EHRService.get().registerMoreActionsButton(new ChangeBillDateButton(this), ONPRC_BillingSchema.NAME, "miscCharges");
        EHRService.get().registerMoreActionsButton(new ChangeBillDateButton(this), ONPRC_BillingSchema.NAME, "miscChargesWithRates");
        EHRService.get().registerMoreActionsButton(new MarkCompletedButton(this, ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_CHARGE_RATE_EXEMPTIONS, "Set End Date", ONPRCBillingAdminPermission.class), ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_CHARGE_RATE_EXEMPTIONS);
        EHRService.get().registerMoreActionsButton(new MarkCompletedButton(this, ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_CHARGE_RATES, "Set End Date", ONPRCBillingAdminPermission.class), ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_CHARGE_RATES);
        EHRService.get().registerMoreActionsButton(new MarkCompletedButton(this, ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_CREDIT_ACCOUNT, "Set End Date", ONPRCBillingAdminPermission.class), ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_CREDIT_ACCOUNT);

        EHRService.get().registerMoreActionsButton(new ChargeEditButton(this, ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_CHARGE_RATE_EXEMPTIONS), ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_CHARGE_RATE_EXEMPTIONS);
        EHRService.get().registerMoreActionsButton(new ChargeEditButton(this, ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_CHARGE_RATES), ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_CHARGE_RATES);
        EHRService.get().registerMoreActionsButton(new ChargeEditButton(this, ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_CREDIT_ACCOUNT), ONPRC_BillingSchema.NAME, ONPRC_BillingSchema.TABLE_CREDIT_ACCOUNT);
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(ONPRC_BillingSchema.NAME);
    }

    @Override
    protected void registerSchemas()
    {
        DefaultSchema.registerProvider(ONPRC_BillingSchema.NAME, new DefaultSchema.SchemaProvider(this)
        {
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new ONPRC_EHRBillingUserSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    @NotNull
    @Override
    public JSONObject getPageContextJson(ViewContext ctx)
    {
        Map<String, Object> ret = new HashMap<>();
        Map<String, String> map = getDefaultPageContextJson(ctx.getContainer());
        ret.putAll(getDefaultPageContextJson(ctx.getContainer()));

        if (map.containsKey(ONPRC_BillingManager.BillingContainerPropName) && map.get(ONPRC_BillingManager.BillingContainerPropName) != null)
        {
            //normalize line endings
            String newPath = map.get(ONPRC_BillingManager.BillingContainerPropName);
            newPath = "/" + newPath.replaceAll("^/|/$", "");
            ret.put(ONPRC_BillingManager.BillingContainerPropName, newPath);

            Container billingContainer = ContainerManager.getForPath(map.get(ONPRC_BillingManager.BillingContainerPropName));
            if(billingContainer != null)
            {
                ret.put("BillingContainerInfo", billingContainer.toJSON(ctx.getUser()));
            }
        }

        return new JSONObject(ret);
    }
}