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

package org.labkey.sla;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.sla.model.IACUCProject;
import org.labkey.sla.model.Investigator;
import org.labkey.sla.model.PurchaseDetails;
import org.labkey.sla.model.PurchaseDraftForm;
import org.labkey.sla.model.PurchaseForm;
import org.labkey.sla.model.Requestor;
import org.labkey.sla.model.Vendor;

import java.util.ArrayList;
import java.util.List;

public class SLAManager
{
    private static final SLAManager _instance = new SLAManager();

    private SLAManager()
    {

    }

    public static SLAManager get()
    {
        return _instance;
    }

    public PurchaseForm insertPurchaseOrder(User user, PurchaseForm input)
    {
        PurchaseForm insertedOrder = Table.insert(user, SLASchema.getInstance().getTableInfoPurchase(), input);
        for (PurchaseDetails inputDetail : input.getPurchaseDetails())
            Table.insert(user, SLASchema.getInstance().getTableInfoPurchaseDetails(), inputDetail);

        return insertedOrder;
    }

    public PurchaseForm getPurchaseOrder(Container container, String objectid)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(SLAManager.get().getAdminContainer(container));
        filter.addCondition(FieldKey.fromParts("objectid"), objectid);
        TableSelector selector = new TableSelector(SLASchema.getInstance().getTableInfoPurchase(), filter, null);

        PurchaseForm order = selector.getObject(PurchaseForm.class);
        if (order != null)
            order.setPurchaseDetails(getPurchaseOrderDetails(container, objectid));

        return order;
    }

    public List<PurchaseDetails> getPurchaseOrderDetails(Container container, String purchaseid)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(SLAManager.get().getAdminContainer(container));
        filter.addCondition(FieldKey.fromParts("purchaseid"), purchaseid);
        Sort sort = new Sort(FieldKey.fromParts("rowid"));
        TableSelector selector = new TableSelector(SLASchema.getInstance().getTableInfoPurchaseDetails(), filter, sort);
        return selector.getArrayList(PurchaseDetails.class);
    }

    public PurchaseDraftForm getPurchaseOrderDraft(Container container, Integer rowid)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(SLAManager.get().getAdminContainer(container));
        filter.addCondition(FieldKey.fromParts("rowid"), rowid);
        TableSelector selector = new TableSelector(SLASchema.getInstance().getTableInfoPurchaseDrafts(), filter, null);
        return selector.getObject(PurchaseDraftForm.class);
    }

    public int deletePurchaseOrderDraft(Container container, Integer rowid)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(SLAManager.get().getAdminContainer(container));
        filter.addCondition(FieldKey.fromParts("rowid"), rowid);
        return Table.delete(SLASchema.getInstance().getTableInfoPurchaseDrafts(), filter);
    }

    public PurchaseDraftForm savePurchaseOrderDraft(User user, PurchaseDraftForm input)
    {
        PurchaseDraftForm draft;

        if (input.getRowid() == null)
            draft = Table.insert(user, SLASchema.getInstance().getTableInfoPurchaseDrafts(), input);
        else
            draft = Table.update(user, SLASchema.getInstance().getTableInfoPurchaseDrafts(), input, input.getRowid());

        return draft;
    }

    public Requestor getRequestor(Container container, String objectid)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(SLAManager.get().getAdminContainer(container));
        filter.addCondition(FieldKey.fromParts("objectid"), objectid);
        TableSelector selector = new TableSelector(SLASchema.getInstance().getTableInfoRequestors(), filter, null);
        return selector.getObject(Requestor.class);
    }

    public Vendor getVendor(Container container, String objectid)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(SLAManager.get().getAdminContainer(container));
        filter.addCondition(FieldKey.fromParts("objectid"), objectid);
        TableSelector selector = new TableSelector(SLASchema.getInstance().getTableInfoVendors(), filter, null);
        return selector.getObject(Vendor.class);
    }

    public IACUCProject getProject(Integer project)
    {
        // Note: the project info is in the "/onprc/ehr" container
        Container container = ContainerManager.getForPath("/onprc/ehr");
        if (container != null)
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addCondition(FieldKey.fromParts("project"), project);

            DbSchema schema = DbSchema.get("ehr");
            TableInfo ti = schema.getTable("project");
            TableSelector selector = new TableSelector(ti, filter, null);
            return selector.getObject(IACUCProject.class);
        }

        return null;
    }

    public Investigator getInvestigator(Integer rowId)
    {
        // Note: the investigator table does not have a container column
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("rowid"), rowId);

        DbSchema schema = DbSchema.get("onprc_ehr");
        TableInfo ti = schema.getTable("investigators");
        TableSelector selector = new TableSelector(ti, filter, null);
        return selector.getObject(Investigator.class);
    }

    public ModuleProperty getModuleProperty(String propName)
    {
        Module slaModule = ModuleLoader.getInstance().getModule(SLAModule.class);
        return slaModule.getModuleProperties().get(propName);
    }

    public Container getAdminContainer(Container c)
    {
        ModuleProperty adminContainerPath = SLAManager.get().getModuleProperty("SLAPurchaseOrderAdminContainer");
        if (adminContainerPath != null)
            return ContainerManager.getForPath(adminContainerPath.getEffectiveValue(c));;

        return null;
    }
}