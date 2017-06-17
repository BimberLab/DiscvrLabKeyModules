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

import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.MimeMap.MimeType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.sla.etl.ETL;
import org.labkey.sla.etl.ETLRunnable;
import org.labkey.sla.model.IACUCProject;
import org.labkey.sla.model.Investigator;
import org.labkey.sla.model.PurchaseDetails;
import org.labkey.sla.model.PurchaseDraftForm;
import org.labkey.sla.model.PurchaseForm;
import org.labkey.sla.model.Requestor;
import org.labkey.sla.model.Vendor;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.mail.Message;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SLAController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SLAController.class);

    public SLAController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminPermission.class)
    public class RunEtlAction extends RedirectAction<Object>
    {
        public boolean doAction(Object form, BindException errors) throws Exception
        {
            ETL.run();
            return true;
        }

        public void validateCommand(Object form, Errors errors)
        {

        }

        public ActionURL getSuccessURL(Object form)
        {
            return DetailsURL.fromString("/sla/etlAdmin.view", getContainer()).getActionURL();
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class ValidateEtlAction extends ConfirmAction<ValidateEtlSyncForm>
    {
        public boolean handlePost(ValidateEtlSyncForm form, BindException errors) throws Exception
        {
            ETLRunnable runnable = new ETLRunnable();
            runnable.validateEtlSync(form.isAttemptRepair());
            return true;
        }

        public ModelAndView getConfirmView(ValidateEtlSyncForm form, BindException errors) throws Exception
        {
            StringBuilder sb = new StringBuilder();
            sb.append("The following text describes the results of comparing the LabKey data with the MSSQL records from the production instance on the same server as this DB instance.  Clicking OK will cause the system to attempt to repair any differences.  Please do this very carefully.<br>");
            sb.append("<br><br>");

            ETLRunnable runnable = new ETLRunnable();
            String msg = runnable.validateEtlSync(false);
            if (msg != null)
                sb.append(msg);
            else
                sb.append("There are no discrepancies<br>");

            return new HtmlView(sb.toString());
        }

        public void validateCommand(ValidateEtlSyncForm form, Errors errors)
        {

        }

        public ActionURL getSuccessURL(ValidateEtlSyncForm form)
        {
            return getContainer().getStartURL(getUser());
        }
    }

    public static class ValidateEtlSyncForm
    {
        private boolean _attemptRepair = false;

        public boolean isAttemptRepair()
        {
            return _attemptRepair;
        }

        public void setAttemptRepair(boolean attemptRepair)
        {
            _attemptRepair = attemptRepair;
        }
    }

    @RequiresPermission(AdminPermission.class)
    @CSRF
    public class SetEtlDetailsAction extends ApiAction<EtlAdminForm>
    {
        public ApiResponse execute(EtlAdminForm form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();

            PropertyManager.PropertyMap configMap = PropertyManager.getWritableProperties(ETLRunnable.CONFIG_PROPERTY_DOMAIN, true);

            boolean shouldReschedule = false;
            if (form.getLabkeyUser() != null)
                configMap.put("labkeyUser", form.getLabkeyUser());

            if (form.getLabkeyContainer() != null)
                configMap.put("labkeyContainer", form.getLabkeyContainer());

            if (form.getJdbcUrl() != null)
                configMap.put("jdbcUrl", form.getJdbcUrl());

            if (form.getJdbcDriver() != null)
                configMap.put("jdbcDriver", form.getJdbcDriver());

            if (form.getRunIntervalInMinutes() != null)
            {
                String oldValue = configMap.get("runIntervalInMinutes");
                if (!form.getRunIntervalInMinutes().equals(oldValue))
                    shouldReschedule = true;

                configMap.put("runIntervalInMinutes", form.getRunIntervalInMinutes());
            }

            if (form.getEtlStatus() != null)
                configMap.put("etlStatus", form.getEtlStatus().toString());

            configMap.save();

            PropertyManager.PropertyMap rowVersionMap = PropertyManager.getWritableProperties(ETLRunnable.ROWVERSION_PROPERTY_DOMAIN, true);
            PropertyManager.PropertyMap timestampMap = PropertyManager.getWritableProperties(ETLRunnable.TIMESTAMP_PROPERTY_DOMAIN, true);

            if (form.getTimestamps() != null)
            {
                JSONObject json = new JSONObject(form.getTimestamps());
                for (String key : rowVersionMap.keySet())
                {
                    if (json.get(key) != null)
                    {
                        //this key corresponds to the rowId of the row in the etl_runs table
                        Integer value = json.getInt(key);
                        if (value == -1)
                        {
                            rowVersionMap.put(key, null);
                            timestampMap.put(key, null);
                        }
                        else
                        {
                            TableInfo ti = SLASchema.getInstance().getSchema().getTable(SLASchema.TABLE_ETL_RUNS);
                            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowid"), value), null);
                            Map<String, Object>[] rows = ts.getMapArray();
                            if (rows.length != 1)
                                continue;

                            rowVersionMap.put(key, (String)rows[0].get("rowversion"));
                            Long date = ((Date)rows[0].get("date")).getTime();
                            timestampMap.put(key, date.toString());
                        }
                    }
                }
                rowVersionMap.save();
                timestampMap.save();
            }

            //if config was changed and the ETL is current scheduled to run, we need to restart it
            if (form.getEtlStatus() && shouldReschedule)
            {
                ETL.stop();
                ETL.start(0);
            }
            else
            {
                if (form.getEtlStatus())
                    ETL.start(0);
                else
                    ETL.stop();
            }

            resultProperties.put("success", true);

            return new ApiSimpleResponse(resultProperties);
        }
    }

    public static class EtlAdminForm
    {
        private Boolean etlStatus;
        private String config;
        private String timestamps;
        private String labkeyUser;
        private String labkeyContainer;
        private String jdbcUrl;
        private String jdbcDriver;
        private String runIntervalInMinutes;

        public Boolean getEtlStatus()
        {
            return etlStatus;
        }

        public void setEtlStatus(Boolean etlStatus)
        {
            this.etlStatus = etlStatus;
        }

        public String getConfig()
        {
            return config;
        }

        public void setConfig(String config)
        {
            this.config = config;
        }

        public String getTimestamps()
        {
            return timestamps;
        }

        public void setTimestamps(String timestamps)
        {
            this.timestamps = timestamps;
        }

        public String getLabkeyUser()
        {
            return labkeyUser;
        }

        public void setLabkeyUser(String labkeyUser)
        {
            this.labkeyUser = labkeyUser;
        }

        public String getLabkeyContainer()
        {
            return labkeyContainer;
        }

        public void setLabkeyContainer(String labkeyContainer)
        {
            this.labkeyContainer = labkeyContainer;
        }

        public String getJdbcUrl()
        {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl)
        {
            this.jdbcUrl = jdbcUrl;
        }

        public String getJdbcDriver()
        {
            return jdbcDriver;
        }

        public void setJdbcDriver(String jdbcDriver)
        {
            this.jdbcDriver = jdbcDriver;
        }

        public String getRunIntervalInMinutes()
        {
            return runIntervalInMinutes;
        }

        public void setRunIntervalInMinutes(String runIntervalInMinutes)
        {
            this.runIntervalInMinutes = runIntervalInMinutes;
        }
    }

    @AdminConsoleAction
    public class ShowEtlLogAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            PageFlowUtil.streamLogFile(response, 0, getLogFile("sla-etl.log"));
        }
    }

    private File getLogFile(String name)
    {
        File tomcatHome = new File(System.getProperty("catalina.home"));
        return new File(tomcatHome, "logs/" + name);
    }

    @RequiresPermission(AdminPermission.class)
    @CSRF
    public class GetEtlDetailsAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();
            resultProperties.put("enabled", ETL.isEnabled());
            resultProperties.put("active", ETL.isRunning());
            resultProperties.put("scheduled", ETL.isScheduled());
            resultProperties.put("nextSync", ETL.nextSync());
            resultProperties.put("cannotTruncate", ETLRunnable.CANNOT_TRUNCATE);

            String[] etlConfigKeys = {"labkeyUser", "labkeyContainer", "jdbcUrl", "jdbcDriver", "runIntervalInMinutes"};

            resultProperties.put("configKeys", etlConfigKeys);
            resultProperties.put("config", PropertyManager.getProperties(ETLRunnable.CONFIG_PROPERTY_DOMAIN));
            resultProperties.put("rowversions", PropertyManager.getProperties(ETLRunnable.ROWVERSION_PROPERTY_DOMAIN));
            Map<String, String> map = PropertyManager.getProperties(ETLRunnable.TIMESTAMP_PROPERTY_DOMAIN);
            Map<String, Date> timestamps = new TreeMap<>();
            for (String key : map.keySet())
            {
                timestamps.put(key, new Date(Long.parseLong(map.get(key))));
            }
            resultProperties.put("timestamps", timestamps);

            return new ApiSimpleResponse(resultProperties);
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class SendPurchaseOrderNotificationAction extends ApiAction<PurchaseOrderEmailForm>
    {
        @Override
        public Object execute(PurchaseOrderEmailForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DateFormat df = new SimpleDateFormat("MM/dd/yyyy");

            PurchaseForm order = SLAManager.get().getPurchaseOrder(getContainer(), form.getObjectId());
            Requestor requestor = SLAManager.get().getRequestor(getContainer(), order.getRequestorid());
            User requestUser = UserManager.getUser(requestor.getUserid());
            Vendor vendor = SLAManager.get().getVendor(getContainer(), order.getVendorid());

            IACUCProject project = SLAManager.get().getProject(order.getProject());
            String projectDisplay = "";
            String investigatorDisplay = "";
            String projectNum ="";
            if (project != null)
            {
                projectDisplay = project.getTitle() + " (" + project.getName() + ")";
                projectNum = project.getName();

                Investigator investigator = SLAManager.get().getInvestigator(project.getInvestigatorid());
                if (investigator != null)
                    investigatorDisplay = investigator.getFirstname() + " " + investigator.getLastname();
            }

            // first look for the SLA Admin group as a Project Group, and then look in the Site Groups
            String slaAdminGroupName = SLAManager.get().getModuleProperty("SLAAdminGroupName").getEffectiveValue(getContainer());
            Integer slaAdminGroupId = SecurityManager.getGroupId(getContainer().getProject(), slaAdminGroupName, false);
            if (slaAdminGroupId == null)
                slaAdminGroupId = SecurityManager.getGroupId(ContainerManager.getRoot(), slaAdminGroupName, false);

            List<User> slaAdminUsers = new ArrayList<>();
            if (slaAdminGroupId != null)
            {
                slaAdminUsers.addAll(SecurityManager.getAllGroupMembers(SecurityManager.getGroup(slaAdminGroupId), MemberType.ACTIVE_USERS, true));
            }

            String url = ActionURL.getBaseServerURL() + "/sla" + getContainer().getPath() + "/reviewPurchaseOrder.view?rowId=" + order.getRowid();
            //String subject = "TEST EMAIL: SLA Purchase Order " + (form.getAction().equals("SUBMITTED") ? "Submission" : "Updated") + ": " + order.getRowid();
            //String subject = "SLA Purchase Order " + (form.getAction().equals("SUBMITTED") ? "Submission" : "Updated") + ": " + order.getRowid();
            String subject = "SLA Purchase Order " + (form.getAction().equals("SUBMITTED") ? "Submitted" : "Updated") + ". Project: " + projectNum + ", Requestor: " + requestor.getFirstname() + " " + requestor.getLastname();
            //String msgHtml = "***PLEASE IGNORE THIS EMAIL. THIS IS A TEST EMAIL***<br/><br/>"
            String msgHtml = "A SMALL LAB ANIMAL PURCHASE ORDER HAS BEEN " + form.getAction() + "!<br/><br/>"
                    + "The order included the following information:<br/>"
                    + "IACUC PROJECT: " + projectDisplay + "<br/>"
                    + "PRIMARY INVESTIGATOR: " + investigatorDisplay + "<br/>"
                    + "REQUESTOR: " + requestor.getFirstname() + " " + requestor.getLastname() + "<br/>"
                    + "REQUESTOR PHONE: " + (requestor.getPhone() != null ? requestor.getPhone() : "")
                    + "  REQUESTOR EMAIL: " + (requestUser != null ? requestUser.getEmail() : requestor.getEmail()) + "<br/>"
                    + "VENDOR NAME: " + vendor.getName() + "<br/>"
                    + (order.getHousingconfirmed() != null && order.getHousingconfirmed() == 3 ? "HOUSING AVAILABILITY: Denied<br/>" : "")
                    + "<br/>";

            for (PurchaseDetails orderDetail : order.getPurchaseDetails())
            {
                String orderDetailStatus = getOrderDetailStatus(order, orderDetail);
                msgHtml += "The following purchase order has been " + orderDetailStatus + ".<br/>";

                msgHtml += "SPECIES: " + (orderDetail.getSpecies() != null ? orderDetail.getSpecies() : "")
                        + "  SEX: " + (orderDetail.getGender() != null ? orderDetail.getGender() : "")
                        + "  STRAIN or STOCK NUM: " + (orderDetail.getStrain() != null ? orderDetail.getStrain() : "") + "<br/>"
                        + "AGE: " + (orderDetail.getAge() != null ? orderDetail.getAge() : "")
                        + "  WEIGHT: " + (orderDetail.getWeight() != null ? orderDetail.getWeight() : "")
                        + "  GESTATION: " + (orderDetail.getGestation() != null ? orderDetail.getGestation() : "") + "<br/>"
                        + "NUM OF ANIMALS ORDERED: " + (orderDetail.getAnimalsordered() != null ? orderDetail.getAnimalsordered() : "")
                        + (orderDetail.getAnimalsreceived() != null ? " NUM OF ANIMALS RECEIVED: " + orderDetail.getAnimalsreceived() : "") + "<br/>"
                        + "REQUESTED ARRIVAL DATE: " + (orderDetail.getRequestedarrivaldate() != null ? df.format(orderDetail.getRequestedarrivaldate()) : "") + "<br/>"
                        + (orderDetail.getReceiveddate() != null ? "RECEIVED BY: " + (orderDetail.getReceivedby() != null ? orderDetail.getReceivedby() : "")
                        + "  RECEIVED DATE: " + df.format(orderDetail.getReceiveddate()) + "<br/>" : "")
                        + (orderDetail.getDatecancelled() != null ? "DATE CANCELLED: " + df.format(orderDetail.getDatecancelled()) + "<br/>" : "")
                        + "<br/>";
            }

            msgHtml += "Please check the <a href='" + url + "'>purchase order</a> for more details!<br/><br/>"
                    + "THANK YOU!";

            try
            {
                MailHelper.MultipartMessage message = MailHelper.createMultipartMessage();
                message.setSubject(subject);
                message.setContent(msgHtml, MimeType.HTML.getContentType());
                message.setFrom(LookAndFeelProperties.getInstance(getContainer()).getSystemEmailAddress());

                if (requestUser != null)
                {
                    message.setRecipients(Message.RecipientType.TO, requestUser.getEmail());
                    MailHelper.send(message, getUser(), getContainer());
                }

                for (User slaAdminUser : slaAdminUsers)
                {
                    message.setRecipients(Message.RecipientType.TO, slaAdminUser.getEmail());
                    MailHelper.send(message, getUser(), getContainer());
                }
            }
            catch (javax.mail.MessagingException | ConfigurationException e)
            {
                errors.reject(ERROR_MSG, "Error: " + e.getMessage());
            }

            response.put("success", true);
            return response;
        }

        private String getOrderDetailStatus(PurchaseForm order, PurchaseDetails details)
        {
            // Email notifications sent:
            // 1. Order submitted – If ("ConfirmationNum is NULL") - DEFAULT
            // 2. Order placed with vendor – If ("ConfirmationNum is not NULL" AND "OrderDate is not NULL)
            // 3. Order received – If ("ReceivedDate is not NULL" AND "Receivedby is not NULL") AND ("OrderDate is not NULL") AND ("DateCancelled is NULL" AND "Cancelledby is null")
            // 4. Order cancelled –If ("DateCancelled is not NULL" AND "Cancelledby is not null")
            if (details.getDatecancelled() != null && details.getCancelledby() != null)
            {
                return "CANCELLED";
            }
            else if (order.getOrderdate() != null && details.getReceiveddate() != null && details.getReceivedby() != null
                    && details.getCancelledby() == null && details.getDatecancelled() == null)
            {
                return "RECEIVED";
            }
            else if (order.getConfirmationnum() != null && order.getOrderdate() != null)
            {
                return "PLACED WITH VENDOR";
            }

            return "SUBMITTED";
        }
    }

    public static class PurchaseOrderEmailForm
    {
        private int _rowId;
        private String _objectId;
        private String _action = "SUBMITTED";

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public int getRowId()
        {
            return _rowId;
        }

        public void setObjectId(String objectId)
        {
            _objectId = objectId;
        }

        public String getObjectId()
        {
            return _objectId;
        }

        public void setAction(String action)
        {
            _action = action;
        }

        public String getAction()
        {
            return _action;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class InsertPurchaseOrderAction extends ApiAction<PurchaseForm>
    {
        private Container _adminContainer;

        @Override
        public void validateForm(PurchaseForm form, Errors errors)
        {
            _adminContainer = SLAManager.get().getAdminContainer(getContainer());
            if (_adminContainer == null)
                errors.reject(ERROR_MSG, "No SLAPurchaseOrderAdminContainer module property configured.");

            if (form.getPurchaseDetails().isEmpty())
                errors.reject(ERROR_MSG, "No purchaseDetails records provided.");
        }

        @Override
        public Object execute(PurchaseForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            // set the records containerId values to the adminContainer
            form.setContainerid(_adminContainer.getEntityId());
            for (PurchaseDetails purchaseDetail : form.getPurchaseDetails())
                purchaseDetail.setContainerid(_adminContainer.getEntityId());

            try (DbScope.Transaction transaction = SLASchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                PurchaseForm order = SLAManager.get().insertPurchaseOrder(getUser(), form);
                response.put("rowid", order.getRowid());
                response.put("objectid", order.getObjectid());
                transaction.commit();
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "Error creating purchase order: " + e.getMessage());
            }

            response.put("success", true);
            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetPurchaseOrderDraftAction extends ApiAction<PurchaseDraftForm>
    {
        private Container _adminContainer;
        private PurchaseDraftForm _draft;

        @Override
        public void validateForm(PurchaseDraftForm form, Errors errors)
        {
            _adminContainer = SLAManager.get().getAdminContainer(getContainer());
            if (_adminContainer == null)
                errors.reject(ERROR_MSG, "No SLAPurchaseOrderAdminContainer module property configured.");

            if (form.getRowid() == null)
                errors.reject(ERROR_MSG, "No purchase draft rowid provided.");

            _draft = SLAManager.get().getPurchaseOrderDraft(getContainer(), form.getRowid());
            if (_draft == null)
                errors.reject(ERROR_MSG, "No purchase draft found for rowid: " + form.getRowid());
            else if (!getUser().hasRootAdminPermission() && _draft.getOwner() != getUser().getUserId())
                errors.reject(ERROR_MSG, "You do not have permissions to view the purchase draft for rowid: " + form.getRowid());
        }

        @Override
        public ApiResponse execute(PurchaseDraftForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("rowid", _draft.getRowid());
            response.put("owner", _draft.getOwner());
            response.put("content", _draft.getContent());
            response.put("success", true);
            return response;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class SavePurchaseOrderDraftAction extends ApiAction<PurchaseDraftForm>
    {
        private Container _adminContainer;

        @Override
        public void validateForm(PurchaseDraftForm form, Errors errors)
        {
            _adminContainer = SLAManager.get().getAdminContainer(getContainer());
            if (_adminContainer == null)
                errors.reject(ERROR_MSG, "No SLAPurchaseOrderAdminContainer module property configured.");

            // validate that required fields are provided
            if (form.isToBeDeleted() && form.getRowid() == null)
                errors.reject(ERROR_MSG, "No rowid provided for delete purchase draft.");
            if (!form.isToBeDeleted() && (form.getOwner() == null || form.getContent() == null))
                errors.reject(ERROR_MSG, "Owner and Content values required for purchase draft.");

            // if this is an update, validate that the user is allowed to update this draft (i.e. admin or owner)
            if (form.getRowid() != null)
            {
                PurchaseDraftForm existingDraft = SLAManager.get().getPurchaseOrderDraft(getContainer(), form.getRowid());
                if (existingDraft == null)
                    errors.reject(ERROR_MSG, "No purchase draft found for rowid: " + form.getRowid());
                else if (!getUser().hasRootAdminPermission() && existingDraft.getOwner() != getUser().getUserId())
                    errors.reject(ERROR_MSG, "You do not have permissions to update the purchase draft for rowid: " + form.getRowid());
            }
        }

        @Override
        public Object execute(PurchaseDraftForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            // set the records containerId values to the adminContainer
            form.setContainerid(_adminContainer.getEntityId());

            try (DbScope.Transaction transaction = SLASchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                if (form.isToBeDeleted())
                {
                    int numDeleted = SLAManager.get().deletePurchaseOrderDraft(getContainer(), form.getRowid());
                    response.put("deletedRowid", form.getRowid());
                }
                else
                {
                    PurchaseDraftForm draft = SLAManager.get().savePurchaseOrderDraft(getUser(), form);
                    response.put("rowid", draft.getRowid());
                }

                transaction.commit();
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "Error saving purchase order draft: " + e.getMessage());
            }

            response.put("success", true);
            return response;
        }
    }
}