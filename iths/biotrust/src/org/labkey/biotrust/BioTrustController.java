/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

package org.labkey.biotrust;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONArray;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiQueryResponse;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.ExtendedApiQueryResponse;
import org.labkey.api.action.FormApiAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.Entity;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ResultSetSelector;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.Group;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.MemberType;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.survey.SurveyService;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.survey.model.SurveyDesign;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.ViewContext;
import org.labkey.biotrust.audit.BioTrustAuditViewFactory;
import org.labkey.biotrust.email.BioTrustNotificationManager;
import org.labkey.biotrust.model.DocumentProperties;
import org.labkey.biotrust.model.DocumentType;
import org.labkey.biotrust.model.SampleRequest;
import org.labkey.biotrust.model.SpecimenRequestAttachment;
import org.labkey.biotrust.model.TissueRecord;
import org.labkey.biotrust.query.BioTrustQuerySchema;
import org.labkey.biotrust.query.RequestResponsesDomain;
import org.labkey.biotrust.security.AbstractBioTrustRole;
import org.labkey.biotrust.security.CreateContactsPermission;
import org.labkey.biotrust.security.PrincipalInvestigatorRole;
import org.labkey.biotrust.security.SubmitRequestsPermission;
import org.labkey.biotrust.security.UpdateReviewPermission;
import org.labkey.biotrust.security.UpdateWorkflowPermission;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BioTrustController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(BioTrustController.class);

    private static final String SETTINGS_DASHBOARD = "biotrustDashboard";
    private static final String SETTINGS_SURVEY_DESIGN_IDS = "surveyDesignIds";

    public BioTrustController()
    {
        setActionResolver(_actionResolver);
    }

    /**
     * This is the home for a user. If a user has access to one customer site, we redirect them to that site.
     */
    @RequiresNoPermission
    public class UserHomeAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container projectRoot = BioTrustManager.get().getBioTrustProject(getContainer());


            if (projectRoot != null)
            {
                Container homeContainer = null;

                // either a project, site admin, RC ....
                if (projectRoot.hasPermission(getUser(), ReadPermission.class))
                {
                    homeContainer = projectRoot;
                }
                else
                {
                    for (Container c : projectRoot.getChildren())
                    {
                        if (c.hasPermission(getUser(), ReadPermission.class))
                        {
                            homeContainer = c;
                            break;
                        }
                    }
                }

                if (homeContainer != null)
                {
                    ActionURL url = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(homeContainer);
                    throw new RedirectException(url);
                }
            }

            // just redirect back to the home page
            throw new RedirectException(AppProps.getInstance().getHomePageActionURL());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("NWBioTrust Home");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public static class EditSpecimenRequestDefinitionAction extends ApiAction<QueryForm>
    {
        @Override
        public ApiResponse execute(QueryForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (form.getQueryName() != null)
            {
                DomainKind kind = PropertyService.get().getDomainKindByName(RequestResponsesDomain.NAME);

                if (kind != null)
                {
                    ActionURL url = kind.urlCreateDefinition(BioTrustSchema.getInstance().getSchema().getName(), form.getQueryName(),
                            getContainer(), getUser());

                    if (url != null)
                    {
                        response.put("success", true);
                        response.put("url", url.getLocalURIString());
                    }
                }
            }
            return response;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public static class DeleteSpecimenRequestDefinitionAction extends MutatingApiAction<SpecimenRequestForm>
    {
        @Override
        public ApiResponse execute(SpecimenRequestForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            Domain domain = PropertyService.get().getDomain(getContainer(), form.getDomainURI());

            if (domain != null)
            {
                domain.delete(getUser());
                response.put("success", true);
                response.put("returnUrl", form.getReturnUrl());
            }
            return response;
        }
    }

    public static class SpecimenRequestForm extends ReturnUrlForm
    {
        private String _domainURI;

        public String getDomainURI()
        {
            return _domainURI;
        }

        public void setDomainURI(String domainURI)
        {
            _domainURI = domainURI;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public static class NewRequestorFolderAction extends MutatingApiAction<NewRequestorForm>
    {
        @Override
        public ApiResponse execute(NewRequestorForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (form.getFolderName() != null)
            {
                Container siteRoot = BioTrustManager.get().getBioTrustProject(getContainer());

                if (siteRoot != null)
                {
                    Container target = ContainerManager.ensureContainer(siteRoot, form.getFolderName());
                    target.setFolderType(ModuleLoader.getInstance().getFolderType(SpecimenRequestorFolderType.NAME), getUser());

                    response.put("success", true);
                }
            }
            return response;
        }
    }

    public static class NewRequestorForm
    {
        private String _folderName;

        public String getFolderName()
        {
            return _folderName;
        }

        public void setFolderName(String folderName)
        {
            _folderName = folderName;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetStudyDashboardDataAction extends ApiAction<DashboardDataForm>
    {
        public ApiResponse execute(DashboardDataForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            // use the module query to get the base set of data for this action,
            // this allows us to easily include the values from the expected extensible Surveys query
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), BioTrustQuerySchema.NAME);

            // Junit test can get in a state where the RC Dashboard webpart is in the Shared container without the biotrust module enabled
            if (schema == null || schema.getTable(BioTrustQuerySchema.STUDY_DASHBOARD_DATA) == null)
                return response;

            QuerySettings settings = new QuerySettings(getViewContext(), "dashboard", BioTrustQuerySchema.STUDY_DASHBOARD_DATA);
            settings.setContainerFilterName(ContainerFilter.Type.CurrentAndSubfolders.name());

            // filter for the StudyRegistration survey design ID (for the NWBT setup, we expect the survey designs to exist at the project level)
            SimpleFilter surveyFilter = new SimpleFilter(FieldKey.fromParts("Container"), getContainer().getParent());
            surveyFilter.addCondition(FieldKey.fromParts("Label"), "StudyRegistration");
            SurveyDesign[] studyDesigns = SurveyService.get().getSurveyDesigns(surveyFilter);
            SimpleFilter filter = new SimpleFilter();
            if (studyDesigns.length > 0)
            {
                String idFilterStr = "";
                String sep = "";
                for (SurveyDesign sd : studyDesigns)
                {
                    idFilterStr += sep + sd.getRowId();
                    sep = ";";
                }
                filter.addCondition(FieldKey.fromParts("SurveyDesignId"), idFilterStr, CompareType.IN);
            }
            else
                filter.addCondition(FieldKey.fromParts("SurveyDesignId"), null, CompareType.ISBLANK);

            // filter for just the submitted/pending requests based on the form params
            if (form.isSubmittedOnly())
                filter.addCondition(FieldKey.fromParts("Submitted"), null, CompareType.NONBLANK);
            if (form.isPendingOnly())
                filter.addCondition(FieldKey.fromParts("Submitted"), null, CompareType.ISBLANK);

            settings.setBaseFilter(filter);

            QueryView view = new QueryView(schema, settings, null);
            ResultSet rs = null;
            try
            {
                JSONArray dashboardDataArr = new JSONArray();

                rs = view.getResultSet();
                while(rs.next())
                {
                    Map<String, Object> requestProps = new HashMap<>();
                    requestProps.put("RowId", rs.getInt("RowId"));
                    requestProps.put("SurveyRowId", rs.getInt("SurveyRowId"));
                    requestProps.put("SurveyDesignId", rs.getInt("SurveyDesignId"));
                    requestProps.put("Label", rs.getString("Label"));
                    requestProps.put("CreatedBy", rs.getString("CreatedBy"));
                    requestProps.put("Created", rs.getDate("Created"));
                    requestProps.put("ModifiedBy", rs.getString("ModifiedBy"));
                    requestProps.put("Modified", rs.getDate("Modified"));
                    requestProps.put("IRBNumber", rs.getString("IRBNumber"));
                    requestProps.put("Status", rs.getString("Status"));
                    requestProps.put("StatusSortOrder", rs.getInt("StatusSortOrder"));
                    requestProps.put("LastStatusChange", rs.getDate("LastStatusChange"));
                    requestProps.put("NumRecords", rs.getInt("NumRecords"));
                    requestProps.put("CategoryId", rs.getInt("CategoryId"));
                    requestProps.put("Category", rs.getString("Category"));
                    requestProps.put("CategorySortOrder", rs.getDouble("CategorySortOrder"));

                    // add information about the required document types for each request
                    // and container it was created in (since this view includes subfolders)
                    BioTrustManager mgr = BioTrustManager.get();
                    Container surveyContainer = ContainerManager.getForId(rs.getString("Container"));
                    Survey survey = SurveyService.get().getSurvey(surveyContainer, getUser(), rs.getInt("SurveyRowId"));
                    if (survey != null)
                    {
                        // container path and id from the survey
                        requestProps.put("Container", survey.getContainerId());
                        requestProps.put("ContainerPath", survey.getContainerPath());
                        // total count of required document types for the survey
                        requestProps.put("RequiredDocumentTypes", mgr.getRequiredDocumentTypes(surveyContainer, getUser(), survey.getRowId()).size());
                        // count of the required document types that have documents in this set for this survey
                        List<SpecimenRequestAttachment> documentSet = mgr.getDocumentSet(surveyContainer, getUser(), survey.getRowId(), SpecimenRequestAttachment.OwnerType.study);
                        int reqCount = 0;
                        for (SpecimenRequestAttachment parent : documentSet)
                            reqCount += parent.isRequired() ? 1 : 0;
                        requestProps.put("AvailableRequiredDocumentTypes", reqCount);
                        // total number of document types in this set for this survey
                        requestProps.put("TotalDocumentTypes", documentSet.size());
                    }

                    dashboardDataArr.put(requestProps);
                }

                response.put("dashboard", dashboardDataArr);
                response.put("success", true);
                return response;

            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }
    }

    public static class DashboardDataForm
    {
        private boolean _submittedOnly = false;
        private boolean _pendingOnly = false;

        public boolean isSubmittedOnly()
        {
            return _submittedOnly;
        }

        public void setSubmittedOnly(boolean submittedOnly)
        {
            _submittedOnly = submittedOnly;
        }

        public boolean isPendingOnly()
        {
            return _pendingOnly;
        }

        public void setPendingOnly(boolean pendingOnly)
        {
            _pendingOnly = pendingOnly;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ManageDocumentSetAction extends SimpleViewAction<RequestForm>
    {
        private String _title = "Manage Document Set";

        @Override
        public ModelAndView getView(RequestForm form, BindException errors) throws Exception
        {
            if (!getContainer().hasPermission(getUser(), UpdatePermission.class))
                _title = "View Document Set";

            if (form.getRowId() <= 0)
                return new HtmlView("<span class='labkey-error'>Error: request rowId required.</span>");
            else
                return new JspView<>("/org/labkey/biotrust/view/manageDocumentSet.jsp", form);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_title);
        }
    }

    public static class RequestForm
    {
        private int _rowId;
        private int _studyId;
        private String _documentTypeName;
        private SpecimenRequestAttachment.OwnerType _ownerType = SpecimenRequestAttachment.OwnerType.study;
        private ReturnURLString _srcURL;

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public SpecimenRequestAttachment.OwnerType getOwnerType()
        {
            return _ownerType;
        }

        public void setOwnerType(SpecimenRequestAttachment.OwnerType ownerType)
        {
            _ownerType = ownerType;
        }

        public ReturnURLString getSrcURL()
        {
            return _srcURL;
        }

        public void setSrcURL(ReturnURLString srcURL)
        {
            _srcURL = srcURL;
        }

        public int getStudyId()
        {
            return _studyId;
        }

        public void setStudyId(int studyId)
        {
            _studyId = studyId;
        }

        public String getDocumentTypeName()
        {
            return _documentTypeName;
        }

        public void setDocumentTypeName(String documentTypeName)
        {
            _documentTypeName = documentTypeName;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetDocumentSetAction extends ApiAction<RequestForm>
    {
        @Override
        public ApiResponse execute(RequestForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (form.getRowId() > 0)
            {
                JSONArray docArr = new JSONArray();

                BioTrustManager mgr = BioTrustManager.get();
                List<SpecimenRequestAttachment> documentSet = mgr.getDocumentSet(getContainer(), getUser(), form.getRowId(), form.getOwnerType());
                addDocumentsToJSONArray(docArr, documentSet, form.getDocumentTypeName(), form.getRowId(), form.getOwnerType().name());
                response.put("documentSet", docArr);

                // add the required document type information to the response
                JSONArray reqArr = new JSONArray();
                for (DocumentType documentType : mgr.getRequiredDocumentTypes(getContainer(), getUser(), form.getRowId()))
                {
                    Map<String, Object> props = new HashMap<>();
                    props.put("id", documentType.getTypeId());
                    props.put("name", documentType.getName());
                    reqArr.put(props);
                }
                response.put("requiredDocumentTypes", reqArr);

                response.put("success", true);
            }
            else
                response.put("success", false);

            return response;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetSampleRequestDocumentSetAction extends ApiAction<RequestForm>
    {
        @Override
        public ApiResponse execute(RequestForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (form.getStudyId() > 0)
            {
                JSONArray docArr = new JSONArray();

                List<SampleRequest> sampleRequests = BioTrustSampleManager.get().getSampleRequests(getContainer(), getUser(), form.getStudyId());
                for (SampleRequest sampleRequest : sampleRequests)
                {
                    List<SpecimenRequestAttachment> documentSet = BioTrustManager.get().getDocumentSet(getContainer(), getUser(), sampleRequest.getRowId(), SpecimenRequestAttachment.OwnerType.samplerequest);
                    addDocumentsToJSONArray(docArr, documentSet, form.getDocumentTypeName(), sampleRequest.getRowId(), SpecimenRequestAttachment.OwnerType.samplerequest.name());
                }
                response.put("documentSet", docArr);
                response.put("success", true);
            }
            else
                response.put("success", false);

            return response;
        }
    }

    private void addDocumentsToJSONArray(JSONArray docArr, List<SpecimenRequestAttachment> documentSet, String docTypeName, int ownerId, String ownerTypeStr)
    {
        for (SpecimenRequestAttachment attachment : documentSet)
        {
            // filter based on the documentTypeName specified in the form (if specified)
            DocumentType documentType = BioTrustManager.get().getDocumentType(getContainer(), getUser(), attachment.getDocumentTypeId());
            if (documentType != null && (docTypeName == null || documentType.getName().equals(docTypeName)))
            {
                // get the document attachments which are files
                for (Attachment doc : attachment.getAttachments())
                {
                    Map<String, Object> docProps = new HashMap<>();
                    docProps.put("attachmentParentId", attachment.getEntityId());
                    docProps.put("name", doc.getName());
                    docProps.put("typeId", documentType.getTypeId());
                    docProps.put("type", documentType.getName());
                    docProps.put("created", doc.getCreated());
                    docProps.put("createdBy", doc.getCreatedByName(getUser()));

                    // the downloadURL for this document needs to surveyId and the documentTypeId
                    ActionURL downloadURL = doc.getDownloadUrl(DocumentDownloadAction.class);
                    downloadURL.addParameter("ownerId", ownerId);
                    downloadURL.addParameter("ownerType", ownerTypeStr);
                    downloadURL.addParameter("documentTypeId", documentType.getTypeId());
                    docProps.put("downloadURL", downloadURL);

                    DocumentProperties dp = BioTrustManager.get().getDocumentProperties(attachment.getEntityId(), doc.getName());
                    docProps.put("active", dp == null || dp.isActive());
                    docProps.put("isLinkedDocument", false);

                    docArr.put(docProps);
                }

                // get the document attachments which are URLs
                for (DocumentProperties dp : BioTrustManager.get().getLinkedDocuments(attachment.getEntityId()))
                {
                    Map<String, Object> docProps = new HashMap<>();
                    docProps.put("attachmentParentId", attachment.getEntityId());
                    docProps.put("name", dp.getDocumentName());
                    docProps.put("typeId", documentType.getTypeId());
                    docProps.put("type", documentType.getName());
                    docProps.put("created", dp.getCreated());
                    docProps.put("createdBy", UserManager.getDisplayName(dp.getCreatedBy(), getUser()));
                    docProps.put("downloadURL", dp.getLinkedDocumentUrl());
                    docProps.put("active", dp.isActive());
                    docProps.put("isLinkedDocument", true);

                    docArr.put(docProps);
                }
            }
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DocumentDownloadAction extends SimpleViewAction<DocumentAttachmentForm>
    {
        public ModelAndView getView(DocumentAttachmentForm form, BindException errors) throws Exception
        {
            SpecimenRequestAttachment attachment = new SpecimenRequestAttachment(getContainer(), form.getEntityId(),
                    form.getOwnerId(), form.getOwnerType(), form.getDocumentTypeId());
            AttachmentService.get().download(getViewContext().getResponse(), attachment, form.getName());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class DocumentAttachmentForm extends AttachmentForm
    {
        private int _ownerId;
        private SpecimenRequestAttachment.OwnerType _ownerType = SpecimenRequestAttachment.OwnerType.study;
        private int _documentTypeId;
        private String _documentTypeName;
        private String _fileName;
        private GUID _recordContainerId;
        private boolean _active;
        private String _linkedDocumentUrl;

        public int getOwnerId()
        {
            return _ownerId;
        }

        public void setOwnerId(int ownerId)
        {
            _ownerId = ownerId;
        }

        public SpecimenRequestAttachment.OwnerType getOwnerType()
        {
            return _ownerType;
        }

        public void setOwnerType(SpecimenRequestAttachment.OwnerType ownerType)
        {
            _ownerType = ownerType;
        }

        public int getDocumentTypeId()
        {
            return _documentTypeId;
        }

        public void setDocumentTypeId(int documentTypeId)
        {
            _documentTypeId = documentTypeId;
        }

        public String getFileName()
        {
            return _fileName;
        }

        public void setFileName(String fileName)
        {
            _fileName = fileName;
        }

        public String getDocumentTypeName()
        {
            return _documentTypeName;
        }

        public void setDocumentTypeName(String documentTypeName)
        {
            _documentTypeName = documentTypeName;
        }

        public String getRecordContainerId()
        {
            return null == _recordContainerId ? null : _recordContainerId.toString();
        }

        public void setRecordContainerId(String recordContainerId)
        {
            this._recordContainerId = new GUID(recordContainerId);
        }

        public boolean isActive()
        {
            return _active;
        }

        public void setActive(boolean active)
        {
            _active = active;
        }

        public String getLinkedDocumentUrl()
        {
            return _linkedDocumentUrl;
        }

        public void setLinkedDocumentUrl(String linkedDocumentUrl)
        {
            _linkedDocumentUrl = linkedDocumentUrl;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class SaveDocumentsAction extends FormApiAction<DocumentAttachmentForm>
    {
        @Override
        public ApiResponse execute(DocumentAttachmentForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            BioTrustManager mgr = BioTrustManager.get();
            BioTrustSampleManager sampleMgr = BioTrustSampleManager.get();

            if (form.getOwnerType() == SpecimenRequestAttachment.OwnerType.study)
            {
                Survey survey = SurveyService.get().getSurvey(getContainer(), getUser(), form.getOwnerId());
                if (survey == null)
                {
                    response.put("errorInfo", "The study survey record could not be found.");
                    response.put("success", false);
                    return response;
                }
            }
            else if (form.getOwnerType() == SpecimenRequestAttachment.OwnerType.tissue)
            {
                // form may have specified a container to look for the tissue record
                Container tissueContainer = getContainer();
                if (form.getRecordContainerId() != null)
                    tissueContainer = ContainerManager.getForId(form.getRecordContainerId());

                TissueRecord tissueRecord = sampleMgr.getTissueRecord(tissueContainer, getUser(), form.getOwnerId());
                if (tissueRecord == null)
                {
                    response.put("errorInfo", "The tissue record could not be found.");
                    response.put("success", false);
                    return response;
                }
            }
            else if (form.getOwnerType() == SpecimenRequestAttachment.OwnerType.samplerequest)
            {
                // form may have specified a container to look for the sample request record
                Container srContainer = getContainer();
                if (form.getRecordContainerId() != null)
                    srContainer = ContainerManager.getForId(form.getRecordContainerId());

                SampleRequest srRecord = sampleMgr.getSampleRequest(srContainer, getUser(), form.getOwnerId());
                if (srRecord == null)
                {
                    response.put("errorInfo", "The sample request record could not be found.");
                    response.put("success", false);
                    return response;
                }
            }

            DocumentType documentType = mgr.getDocumentType(getContainer(), getUser(), form.getDocumentTypeId());
            if (documentType == null)
            {
                // try getting the document type by name
                documentType = mgr.getDocumentTypeByName(getContainer(), getUser(), form.getDocumentTypeName());

                if (documentType == null)
                {
                    response.put("errorInfo", "Document type could not be found.");
                    response.put("success", false);
                    return response;
                }
            }

            SpecimenRequestAttachment currentParent = mgr.getAttachmentParent(getContainer(), getUser(),
                    documentType, form.getOwnerId(), form.getOwnerType());

            // check if this document type allows for multiple files
            if (!documentType.isAllowMultipleUpload())
            {
                if (currentParent != null && (currentParent.getAttachments().size() > 0 || mgr.getLinkedDocuments(currentParent.getEntityId()).size() > 0))
                {
                    response.put("errorInfo", "This document type does not allow multiple files and one already exists in this document set.");
                    response.put("success", false);
                    return response;
                }
            }

            // the submission will either have a list of files or a name/url for a linked document
            if (form.getFileName() != null && form.getLinkedDocumentUrl() != null)
            {
                // verify that the file names don't already exist for this type
                if (currentParent != null && (currentParent.getAttachmentByName(form.getFileName()) != null || mgr.getLinkedDocumentByName(currentParent.getEntityId(), form.getFileName()) != null))
                {
                    response.put("errorInfo", "A document with the following name already exists for this document type: " + form.getFileName());
                    response.put("success", false);
                    return response;
                }

                mgr.saveLinkedDocumentUrl(getContainer(), getUser(), form.getOwnerId(), form.getOwnerType(),
                        documentType, form.getFileName(), form.getLinkedDocumentUrl());
            }
            else
            {
                List<AttachmentFile> files = getAttachmentFileList();
                if (files.isEmpty())
                {
                    response.put("errorInfo", "No file(s) provided.");
                    response.put("success", false);
                    return response;
                }

                // verify that the file names don't already exist for this type
                for (AttachmentFile file : files)
                {
                    if (currentParent != null && (currentParent.getAttachmentByName(file.getFilename()) != null || mgr.getLinkedDocumentByName(currentParent.getEntityId(), file.getFilename()) != null))
                    {
                        response.put("errorInfo", "A document with the following name already exists for this document type: " + file.getFilename());
                        response.put("success", false);
                        return response;
                    }
                }

                // check if this document type allows for multiple files
                if (!documentType.isAllowMultipleUpload() && files.size() > 1)
                {
                    response.put("errorInfo", "This document type does not allow multiple files.");
                    response.put("success", false);
                    return response;
                }

                // add the files to the document type attachment parent and save
                Map<DocumentType, List<AttachmentFile>> documents = new HashMap<>();
                documents.put(documentType, files);
                mgr.saveDocuments(getContainer(), getUser(), documents, form.getOwnerId(), form.getOwnerType());

                response.put("filesAffected", files.size());
            }

            response.put("success", true);
            return response;
        }

        @Override
        public ModelAndView getView(DocumentAttachmentForm form, BindException errors) throws Exception
        {
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class UpdateDocumentProperties extends ApiAction<DocumentAttachmentForm>
    {
        @Override
        public ApiResponse execute(DocumentAttachmentForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (form.getEntityId() != null && form.getFileName() != null)
            {
                BioTrustManager.get().saveDocumentProperties(getUser(), form.getEntityId(),
                        form.getFileName(), form.isActive(), null);
                response.put("success", true);
                return response;
            }
            else
                errors.reject(ERROR_MSG, "AttachmentParent entityId and document name required.");

            response.put("success", false);
            return response;
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteSingleDocumentAction extends ApiAction<DocumentAttachmentForm>
    {
        @Override
        public ApiResponse execute(DocumentAttachmentForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            BioTrustManager mgr = BioTrustManager.get();
            BioTrustSampleManager sampleMgr = BioTrustSampleManager.get();

            if (form.getOwnerType() == SpecimenRequestAttachment.OwnerType.study)
            {
                Survey survey = SurveyService.get().getSurvey(getContainer(), getUser(), form.getOwnerId());
                if (survey == null)
                {
                    errors.reject(ERROR_MSG, "The study survey record could not be found.");
                    response.put("success", false);
                    return response;
                }
            }
            else if (form.getOwnerType() == SpecimenRequestAttachment.OwnerType.tissue)
            {
                // form may have specified a container to look for the tissue record
                Container tissueContainer = getContainer();
                if (form.getRecordContainerId() != null)
                    tissueContainer = ContainerManager.getForId(form.getRecordContainerId());

                TissueRecord tissueRecord = sampleMgr.getTissueRecord(tissueContainer, getUser(), form.getOwnerId());
                if (tissueRecord == null)
                {
                    errors.reject(ERROR_MSG, "The tissue record could not be found.");
                    response.put("success", false);
                    return response;
                }
            }
            else if (form.getOwnerType() == SpecimenRequestAttachment.OwnerType.samplerequest)
            {
                // form may have specified a container to look for the sample request record
                Container srContainer = getContainer();
                if (form.getRecordContainerId() != null)
                    srContainer = ContainerManager.getForId(form.getRecordContainerId());

                SampleRequest srRecord = sampleMgr.getSampleRequest(srContainer, getUser(), form.getOwnerId());
                if (srRecord == null)
                {
                    errors.reject(ERROR_MSG, "The sample request record could not be found.");
                    response.put("success", false);
                    return response;
                }
            }

            DocumentType docType = mgr.getDocumentType(getContainer(), getUser(), form.getDocumentTypeId());
            if (docType == null)
            {
                errors.reject(ERROR_MSG, "Document type could not be found.");
                response.put("success", false);
                return response;
            }

            if (form.getFileName() == null)
            {
                errors.reject(ERROR_MSG, "No file name provided.");
                response.put("success", false);
                return response;
            }

            Map<DocumentType, List<String>> toDelete = new HashMap<>();
            toDelete.put(docType, Collections.singletonList(form.getFileName()));
            mgr.deleteDocuments(getContainer(), getUser(), toDelete, form.getOwnerId(), form.getOwnerType());

            response.put("fileDeleted", form.getFileName());
            response.put("success", true);
            return response;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetSampleRequestsAction extends ApiAction<StudyRegistrationForm>
    {
        @Override
        public ApiResponse execute(StudyRegistrationForm form, BindException errors) throws Exception
        {
            ViewContext context = getViewContext();

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), BioTrustQuerySchema.NAME);

            if (schema != null)
            {
                QuerySettings settings = schema.getSettings(context, QueryView.DATAREGIONNAME_DEFAULT, BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME);

                // sample requests for a specific study
                if (form.getStudyId() !=  0)
                    settings.setBaseFilter(new SimpleFilter(FieldKey.fromParts("StudyId"), form.getStudyId()));

                QueryView view = schema.createView(context, settings, errors);
                if (view != null)
                {
                    ApiQueryResponse response = new ExtendedApiQueryResponse(view, false, true,
                            BioTrustQuerySchema.NAME, BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME, settings.getOffset(), null,
                            false, false, false);

                    return response;
                }
            }
            return new ApiSimpleResponse("success", false);
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteSampleRequestAction extends ApiAction<SampleRequestForm>
    {
        @Override
        public ApiResponse execute(SampleRequestForm form, BindException errors) throws Exception
        {
            if (form.getSampleId() != 0)
            {
                BioTrustSampleManager.get().deleteSampleRequest(getContainer(), getUser(), form.getSampleId());
                return new ApiSimpleResponse("success", true);
            }
            return new ApiSimpleResponse("success", false);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetTissueRecordsAction extends ApiAction<SampleRequestForm>
    {
        @Override
        public ApiResponse execute(SampleRequestForm form, BindException errors) throws Exception
        {
            ViewContext context = getViewContext();

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), BioTrustQuerySchema.NAME);

            if (schema != null)
            {
                QuerySettings settings = schema.getSettings(context, QueryView.DATAREGIONNAME_DEFAULT, BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME);

                // filter for tissue records for a given for a specific sample request
                if (form.getSampleId() !=  0)
                    settings.setBaseFilter(new SimpleFilter(FieldKey.fromParts("SampleId"), form.getSampleId()));
                // filter for tissue records for a given request type
                if (form.getRequestType() != null)
                    settings.setBaseFilter(new SimpleFilter(FieldKey.fromParts("RequestType"), form.getRequestType()));
                // filter for a specific tissue record
                if (form.getTissueId() != 0)
                    settings.setBaseFilter(new SimpleFilter(FieldKey.fromParts("RowId"), form.getTissueId()));

                List<FieldKey> columns = new ArrayList<FieldKey>();
                TableInfo tissueTable = schema.getTable(BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME);
                if (null != tissueTable)
                {
                    columns.addAll(tissueTable.getDefaultVisibleColumns());
                }
                settings.setFieldKeys(columns);

                QueryView view = schema.createView(context, settings, errors);
                if (view != null)
                {
                    return new ApiQueryResponse(view, false, true,
                            BioTrustQuerySchema.NAME, BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME, settings.getOffset(), null,
                            false, false, false);
                }
            }
            return new ApiSimpleResponse("success", false);
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class CreateTissueRecordAction extends MutatingApiAction<SampleRequestForm>
    {
        @Override
        public ApiResponse execute(SampleRequestForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            User user = getUser();
            Container c = getContainer();
            DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();
            UserSchema schema = QueryService.get().getUserSchema(user, c, BioTrustQuerySchema.NAME);
            if (schema == null)
                return new ApiSimpleResponse("success", false);

            TableInfo tissueTable = schema.getTable(BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME);
            if (tissueTable == null)
                return new ApiSimpleResponse("success", false);

            if (form.getStudyId() != 0 && form.getSampleId() != 0 && form.getRequestType() != null)
            {
                Map<String, Object> row = new HashMap<>();
                row.put("sampleid",form.getSampleId());
                row.put("requesttype", form.getRequestType());
                row.put("container", c);

                // calculate the next study record Id based on the biotrust.LastStudyTissueRecord query
                SQLFragment sql = new SQLFragment("SELECT LastId FROM (SELECT sr.StudyId, MAX(tr.StudyRecordId) AS LastId FROM ");
                sql.append(schema.getTable(BioTrustQuerySchema.TISSUE_RECORD_TABLE_NAME), "tr");
                sql.append(" LEFT JOIN ").append(schema.getTable(BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME), "sr");
                sql.append(" ON sr.RowId = tr.SampleId GROUP BY sr.StudyId ) AS LastRecords WHERE StudyId = ?");
                sql.add(form.getStudyId());

                Integer studyRecordId = new SqlSelector(scope, sql).getObject(Integer.class);
                row.put("studyrecordid", (studyRecordId == null ? 1 : studyRecordId + 1));

                QueryUpdateService tQus = tissueTable.getUpdateService();
                List<Map<String, Object>> tissues = tQus.insertRows(user, c, Collections.singletonList(row), new BatchValidationException(), null);
                int tissueId = Integer.parseInt(tissues.get(0).get("rowid").toString());
                response.put("tissueId", tissueId);

                response.put("success", true);
                return response;
            }
            return new ApiSimpleResponse("success", false);
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteTissueRecordAction extends ApiAction<SampleRequestForm>
    {
        @Override
        public ApiResponse execute(SampleRequestForm form, BindException errors) throws Exception
        {
            ViewContext context = getViewContext();

            if (form.getTissueId() != 0)
            {
                BioTrustSampleManager.get().deleteTissueRecord(getContainer(), getUser(), form.getTissueId());
                return new ApiSimpleResponse("success", true);
            }
            return new ApiSimpleResponse("success", false);
        }
    }

    public static class StudyRegistrationForm
    {
        private int _studyId;

        public int getStudyId()
        {
            return _studyId;
        }

        public void setStudyId(int studyId)
        {
            _studyId = studyId;
        }
    }

    public static class SampleRequestForm implements CustomApiForm
    {
        private Map<String, Object> _values = new HashMap<String, Object>();
        private int _studyId;
        private int _sampleId;
        private int _tissueId;
        private String _requestType;
        private boolean _removeSelection;

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            _values = props;

            if (_values.containsKey("studyId"))
                _studyId = NumberUtils.toInt(_values.get("studyId").toString());
            if (_values.containsKey("sampleId"))
                _sampleId = NumberUtils.toInt(_values.get("sampleId").toString());
            if (_values.containsKey("tissueId"))
                _tissueId = NumberUtils.toInt(_values.get("tissueId").toString());
            if (_values.containsKey("requestType"))
                _requestType = _values.get("requestType").toString();
            if (_values.containsKey("removeSelection"))
                _removeSelection = BooleanUtils.toBoolean(_values.get("removeSelection").toString());
        }

        public boolean isRemoveSelection()
        {
            return _removeSelection;
        }

        public void setRemoveSelection(boolean removeSelection)
        {
            _removeSelection = removeSelection;
        }

        public int getSampleId()
        {
            return _sampleId;
        }

        public void setSampleId(int sampleId)
        {
            _sampleId = sampleId;
        }

        public int getTissueId()
        {
            return _tissueId;
        }

        public void setTissueId(int tissueId)
        {
            _tissueId = tissueId;
        }

        public String getRequestType()
        {
            return _requestType;
        }

        public void setRequestType(String requestType)
        {
            _requestType = requestType;
        }

        public int getStudyId()
        {
            return _studyId;
        }

        public void setStudyId(int studyId)
        {
            _studyId = studyId;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class UpdateSampleRequestAction extends SimpleViewAction<RequestForm>
    {
        private String _title = "Create New Sample Request";

        @Override
        public ModelAndView getView(RequestForm form, BindException errors) throws Exception
        {
            if (form.getRowId() > 0)
            {
                _title = "Edit Existing Sample Request";

                // get the sample request record for this rowId
                SampleRequest sampleRequest = BioTrustSampleManager.get().getSampleRequest(getContainer(), getUser(), form.getRowId());
                if (sampleRequest != null)
                {
                    form.setStudyId(sampleRequest.getStudyId());

                    Survey surveyRecord = SurveyService.get().getSurvey(getContainer(), getUser(), BioTrustQuerySchema.NAME, BioTrustQuerySchema.SPECIMEN_REQUEST_TABLE_NAME, String.valueOf(form.getRowId()));
                    if (surveyRecord != null)
                    {
                        if (surveyRecord.getSubmitted() != null)
                            _title = "Review Sample Request";
                    }
                }
            }

            return new JspView<>("/org/labkey/biotrust/view/updateSampleRequest.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_title);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class UpdateStudyRegistrationAction extends SimpleViewAction<RequestForm>
    {
        private String _title = "Create New Study Registration";

        @Override
        public ModelAndView getView(RequestForm form, BindException errors) throws Exception
        {
            if (form.getRowId() > 0)
            {
                _title = "Edit Existing Study Registration";
            }

            return new JspView<>("/org/labkey/biotrust/view/updateStudyRegistration.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_title);
        }
    }

    @RequiresPermissionClass(UpdateReviewPermission.class)
    public class ApproverReviewAction extends SimpleViewAction<Object>
    {
        private String _title = "Approver Review";

        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/biotrust/view/approverReview.jsp", o, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_title);
        }
    }

    @RequiresPermissionClass(UpdateReviewPermission.class)
    public class ApproverReviewDetailsAction extends SimpleViewAction<RequestForm>
    {
        private String _title = "Approver Review Details";

        @Override
        public ModelAndView getView(RequestForm form, BindException errors) throws Exception
        {
            if (form.getRowId() <= 0)
                return new HtmlView("<span class='labkey-error'>Error: request rowId required.</span>");
            else
                return new JspView<>("/org/labkey/biotrust/view/approverReviewDetails.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_title);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetContactsAction extends ApiAction<ReturnUrlForm>
    {
        @Override
        public ApiResponse execute(ReturnUrlForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            final List<Map<String, Object>> contacts = new ArrayList<>();
            List<Integer> userIds = new ArrayList<>();

            // get active in-system users for the contact list from the core.Users table
            for (User user : UserManager.getActiveUsers())
            {
                if (getContainer().hasPermission(user, ReadPermission.class))
                {
                    if (BioTrustContactsManager.get().hasBioTrustContactRole(getContainer(), user))
                        userIds.add(user.getUserId());
                }
            }

            QuerySettings settings = new QuerySettings(getViewContext(), "users", "Users");
            SimpleFilter filter = new SimpleFilter();
            filter.addInClause(FieldKey.fromParts("UserId"), userIds);
            settings.setBaseFilter(filter);

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "core");
            QueryView view = schema.createView(getViewContext(), settings, errors);

            if (view != null)
            {
                final Map<Integer, Map<String, Object>> userIdToRowMap = new HashMap<>();

                try (ResultSet rs = view.getResultSet())
                {
                    new ResultSetSelector(view.getSchema().getDbSchema().getScope(), rs).forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
                    {
                        @Override
                        public void exec(Map<String, Object> map) throws SQLException
                        {
                            if (map.containsKey("UserId"))
                            {
                                Integer userId = (Integer)map.get("UserId");
                                Map<String, Object> row = new HashMap<>();

                                userIdToRowMap.put(userId, row);

                                for (Map.Entry<String, Object> entry : map.entrySet())
                                {
                                    row.put(entry.getKey().toLowerCase(), entry.getValue());
                                }

                                row.put("systemuser", true);
                            }
                        }
                    });
                }

                // get the nwbt role assignments and map them to the proper user record
                for (RoleAssignment assignment : BioTrustContactsManager.get().getRoleAssignments(getContainer()))
                {
                    Role role = assignment.getRole();
                    int userId = assignment.getUserId();
                    User user = UserManager.getUser(userId);

                    if (user != null)
                    {
                        if (userIdToRowMap.containsKey(userId))
                        {
                            Map<String, Object> userRecord = userIdToRowMap.get(userId);

                            if (!userRecord.containsKey("role"))
                                userRecord.put("role", new ArrayList<String>());

                            ((List<String>)userRecord.get("role")).add(role.getName());
                        }
                    }
                    else
                    {
                        // if it's a group assignment, add all direct members of the group
                        Group group = SecurityManager.getGroup(userId);
                        if (group != null)
                        {
                            for (User groupUser : SecurityManager.getGroupMembers(group, MemberType.ACTIVE_AND_INACTIVE_USERS))
                            {
                                Map<String, Object> userRecord = userIdToRowMap.get(groupUser.getUserId());

                                if (userRecord == null)
                                    continue;
                                if (!userRecord.containsKey("role"))
                                    userRecord.put("role", new ArrayList<String>());

                                ((List<String>)userRecord.get("role")).add(role.getName());
                            }
                        }
                    }

                    contacts.addAll(userIdToRowMap.values());
                }
            }

            // get out-of-system users for the contact list from the biotrust.Contacts table
            schema = QueryService.get().getUserSchema(getUser(), getContainer(), BioTrustQuerySchema.NAME);
            TableInfo contactTable = schema.getTable(BioTrustQuerySchema.CONTACT_TABLE_NAME);
            new TableSelector(contactTable).forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
            {
                @Override
                public void exec(Map<String, Object> row) throws SQLException
                {
                    // the model for the contacts store required lower case keys
                    Map<String, Object> newRow = new HashMap<>();
                    for (String key : row.keySet())
                    {
                        if ("Role".equalsIgnoreCase(key) && (row.get(key) != null))
                            newRow.put(key.toLowerCase(), Collections.singletonList(row.get(key)));
                        else
                            newRow.put(key.toLowerCase(), row.get(key));
                    }

                    newRow.put("systemuser", false);
                    contacts.add(newRow);
                }
            });

            response.put("contacts", contacts);
            return response;
        }
    }

    public static class ContactForm implements CustomApiForm
    {
        private Map<String, Object> _values = new HashMap<>();
        private boolean _systemUser;
        private String _email;
        private int _userId;
        private int _rowId;
        private String _firstName;
        private String _lastName;
        private String[] _role = new String[0];

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            _values = props;

            if (_values.containsKey("userid"))
                _userId = NumberUtils.toInt(_values.get("userid").toString());
            if (_values.containsKey("rowId"))
                _rowId = NumberUtils.toInt(_values.get("rowId").toString());
            if (_values.containsKey("email"))
                _email = _values.get("email").toString();
            if (_values.containsKey("firstName"))
                _firstName = _values.get("firstName").toString();
            if (_values.containsKey("lastName"))
                _lastName = _values.get("lastName").toString();

            if (_values.containsKey("systemUser"))
                _systemUser = BooleanUtils.toBoolean(_values.get("systemUser").toString());

            if (props.containsKey("role"))
            {
                Object role = props.get("role");
                if (role instanceof JSONArray)
                {
                    JSONArray jsonArray = (JSONArray)role;
                    _role = new String[jsonArray.length()];

                    for (int i=0; i < jsonArray.length(); i++)
                        _role[i] = jsonArray.getString(i);
                }
                else if (role instanceof String)
                {
                    _role = new String[1];
                    _role[0] = (String)role;
                }
            }
        }

        public Map<String, Object> getValues()
        {
            return _values;
        }

        public int getUserId()
        {
            return _userId;
        }

        public void setUserId(int userId)
        {
            _userId = userId;
        }

        public String getEmail()
        {
            return _email;
        }

        public boolean isSystemUser()
        {
            return _systemUser;
        }

        public String[] getRole()
        {
            return _role;
        }

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public String getFirstName()
        {
            return _firstName;
        }

        public String getLastName()
        {
            return _lastName;
        }
    }

    @RequiresPermissionClass(CreateContactsPermission.class)
    public class CreateContactAction extends MutatingApiAction<ContactForm>
    {
        private boolean _systemUser;
        private ValidEmail _email;
        private User _user;
        List<Role> _roles = new ArrayList<>();

        @Override
        public void validateForm(ContactForm form, Errors errors)
        {
            // check for valid email address, this is a required field
            try
            {
                if (form.getEmail() == null)
                    errors.reject(ERROR_MSG, "A valid email must be provided.");

                _email = new ValidEmail(form.getEmail());
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }

            Map<String, String> roleNameMap = new HashMap<>();
            for (Role role : BioTrustContactsManager.get().getBioTrustRoles(true))
                roleNameMap.put(role.getName(), role.getUniqueName());

            for (String roleName : form.getRole())
            {
                Role role = RoleManager.getRole(roleNameMap.get(roleName));
                if (role == null || !AbstractBioTrustRole.class.isAssignableFrom(role.getClass()))
                    errors.reject(ERROR_MSG, "Invalid role, must be a NWBioTrust related role.");
                else
                    _roles.add(role);
            }

            _systemUser = form.isSystemUser();
            if (_systemUser)
            {
                if (form.getUserId() == 0)
                {
                    if (UserManager.getUser(_email) != null)
                        errors.reject(ERROR_MSG, "The specified email address is already being used.");
                }
                else
                {
                    _user = UserManager.getUser(form.getUserId());
                    if (_user == null)
                        errors.reject(ERROR_MSG, "The specified user cannot be found.");
                }
            }
            else
            {
                if (form.getRowId() == 0)
                {
                    UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), BioTrustQuerySchema.NAME);
                    TableInfo ti = schema.getTable(BioTrustQuerySchema.CONTACT_TABLE_NAME);
                    if (new TableSelector(ti, new SimpleFilter(FieldKey.fromParts("Email"), _email.getEmailAddress()), null).exists())
                        errors.reject(ERROR_MSG, "The specified email address is already being used.");
                }
            }
        }

        @Override
        public ApiResponse execute(ContactForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (_systemUser)
            {
                DbScope scope = CoreSchema.getInstance().getSchema().getScope();
                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    if (_user == null)
                    {
                        SecurityManager.NewUserStatus status = org.labkey.api.security.SecurityManager.addUser(_email);
                        _user = status.getUser();

                        User newUser = UserManager.getUser(_user.getUserId());

                        if (newUser != null)
                        {
                            Role reader = RoleManager.getRole(ReaderRole.class);
                            BioTrustContactsManager.get().addRoleAssignment(getContainer(), newUser, reader);
                        }
                    }

                    //if (!_roles.isEmpty())
                    BioTrustContactsManager.get().updateRoleAssignments(getContainer(), _user, _roles, false);

                    updateUserProperties(_user, form.getValues());
                    response.put("userId", _user.getUserId());
                    response.put("success", true);

                    transaction.commit();
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
            else
            {
                // out-of-system users, persist in the biotrust.Contacts table
                DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();
                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), BioTrustQuerySchema.NAME);
                    TableInfo contactsTable = schema.getTable(BioTrustQuerySchema.CONTACT_TABLE_NAME);
                    if (contactsTable != null)
                    {
                        Map<String, Object> keys = new HashMap<>();
                        // TODO: why does the casing matter for this biotrust.Contacts table but not other tables in this schema?
                        if (schema.getDbSchema().getSqlDialect().isCaseSensitive())
                            keys.put("rowid", form.getRowId());
                        else
                            keys.put("RowId", form.getRowId());

                        TableViewForm tvf = new TableViewForm(contactsTable);
                        tvf.setViewContext(getViewContext());
                        tvf.setTypedValues(form.getValues(), false);
                        Map<String, Object> values = tvf.getTypedColumns();

                        QueryUpdateService qus = contactsTable.getUpdateService();
                        if (qus != null)
                        {
                            List<Map<String,Object>> rows;
                            if (form.getRowId() > 0)
                                rows = qus.updateRows(getUser(), getContainer(), Collections.singletonList(values), Collections.singletonList(keys), null);
                            else
                                rows = qus.insertRows(getUser(), getContainer(), Collections.singletonList(values), new BatchValidationException(), null);

                            if (rows.size() > 0)
                            {
                                response.put("rowId", rows.get(0).get("RowId"));
                                response.put("success", true);
                            }
                        }
                    }
                    transaction.commit();
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
            return response;
        }
    }

    /**
     * Update the core.users table with the specified custom properties
     * @param user
     * @param props
     * @throws Exception
     */
    private void updateUserProperties(User user, Map<String, Object> props) throws Exception
    {
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), "core");
        TableInfo usersTable = schema.getTable("Users");

        if (usersTable != null)
        {
            TableViewForm tvf = new TableViewForm(usersTable);
            tvf.setViewContext(getViewContext());
            tvf.setTypedValues(props, false);
            Map<String, Object> values = tvf.getTypedColumns();

            if (values.containsKey("DisplayName"))
            {
                if (StringUtils.isBlank((String)values.get("DisplayName")))
                    values.remove("DisplayName");
            }

            Map<String, Object> keys = new HashMap<>();
            keys.put("UserId", user.getUserId());

            QueryUpdateService qus = usersTable.getUpdateService();
            qus.updateRows(getUser(), getContainer(), Collections.singletonList(values), Collections.singletonList(keys), null);
        }
    }

    public static class UpdateRolesForm extends ContactForm implements CustomApiForm
    {
        List<String> _roles = new ArrayList<>();
        boolean _removeAll = false;

        public List<String> getRoles()
        {
            return _roles;
        }

        public boolean isRemoveAll()
        {
            return _removeAll;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            Object rolesProp = props.get("role");
            if (rolesProp != null)
            {
                if (rolesProp instanceof JSONArray)
                {
                    for (Object role : ((JSONArray) rolesProp).toArray())
                    {
                        _roles.add(role.toString());
                    }
                }
                else
                    _roles.add((String)rolesProp);
            }

            Object userIdProp = props.get("userId");
            if (userIdProp != null)
                setUserId((Integer)userIdProp);

            if (props.containsKey("removeAll"))
                _removeAll = BooleanUtils.toBoolean(props.get("removeAll").toString());
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class UpdateRolesAction extends MutatingApiAction<UpdateRolesForm>
    {
        User _user;
        List<Role> _roles = new ArrayList<>();

        @Override
        public void validateForm(UpdateRolesForm form, Errors errors)
        {
            _user = UserManager.getUser(form.getUserId());
            if (_user == null)
                errors.reject(ERROR_MSG, "A valid user ID must be provided.");

            for (String roleName : form.getRoles())
            {
                Role role = RoleManager.getRole(roleName);
                if (role == null || !AbstractBioTrustRole.class.isAssignableFrom(role.getClass()))
                    errors.reject(ERROR_MSG, "Invalid role, must be a NWBioTrust related role.");
                else
                    _roles.add(role);
            }
        }

        @Override
        public ApiResponse execute(UpdateRolesForm updateRolesForm, BindException errors) throws Exception
        {
            BioTrustContactsManager.get().updateRoleAssignments(getContainer(), _user, _roles, updateRolesForm.isRemoveAll());
            return new ApiSimpleResponse("success", true);
        }
    }

    public static class SurveyForm
    {
        private int _rowId;
        private String _status;
        private int _category;
        private String _comment;
        private Date _registered;
        private boolean _study;
        private Integer[] _reviewers;
        private boolean _notifyinvestigator;

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public String getStatus()
        {
            return _status;
        }

        public void setStatus(String status)
        {
            _status = status;
        }

        public int getCategory()
        {
            return _category;
        }

        public void setCategory(int category)
        {
            _category = category;
        }

        public String getComment()
        {
            return _comment;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }

        public boolean isStudy()
        {
            return _study;
        }

        public void setStudy(boolean study)
        {
            _study = study;
        }

        public Date getRegistered()
        {
            return _registered;
        }

        public void setRegistered(Date registered)
        {
            _registered = registered;
        }

        public Integer[] getReviewers()
        {
            return _reviewers;
        }

        public void setReviewers(Integer[] reviewers)
        {
            _reviewers = reviewers;
        }

        public boolean isNotifyinvestigator()
        {
            return _notifyinvestigator;
        }

        public void setNotifyinvestigator(boolean notifyinvestigator)
        {
            _notifyinvestigator = notifyinvestigator;
        }
    }

    @RequiresPermissionClass(UpdateWorkflowPermission.class)
    public class UpdateSurveyAction extends MutatingApiAction<SurveyForm>
    {
        @Override
        public ApiResponse execute(SurveyForm form, BindException errors) throws Exception
        {
            Survey survey = SurveyService.get().getSurvey(getContainer(), getUser(), form.getRowId());
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (survey != null)
            {
                DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();
                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    Container surveyContainer = ContainerManager.getForId(survey.getContainerId());
                    UserSchema schema = QueryService.get().getUserSchema(getUser(), surveyContainer, "survey");
                    TableInfo surveyTable = schema.getTable("Surveys");

                    if (surveyTable != null)
                    {
                        String prevStatus = survey.getStatus();
                        String newStatus = form.getStatus();
                        Map<String, Object> values = new HashMap<>();
                        Map<String, Object> keys = new HashMap<>();

                        keys.put("RowId", form.getRowId());
                        values.put("Status", newStatus);
                        values.put("Category", form.getCategory());
                        if (form.getRegistered() != null)
                            values.put("Registered", form.getRegistered());

                        if (form.getReviewers() != null)
                        {
                            List<User> reviewers = new ArrayList<>();
                            for (Integer reviewerId : form.getReviewers())
                            {
                                if (reviewerId != null)
                                    reviewers.add(UserManager.getUser(reviewerId));
                            }

                            BioTrustSampleManager.get().setSampleReviewers(surveyContainer, form.getRowId(), newStatus, reviewers);

                            // if the status has changed to an approval review status, send an email notification to the set of reviewers selected
                            if (!StringUtils.equals(prevStatus, newStatus) && BioTrustManager.get().isApprovalRequestStatus(newStatus))
                            {
                                BioTrustNotificationManager.get().sendApprovalReviewEmail(getContainer(), getUser(), survey, newStatus, form.getComment(), form.isNotifyinvestigator());
                            }
                        }

                        QueryUpdateService qus = surveyTable.getUpdateService();
                        if (qus != null)
                        {
                            qus.updateRows(getUser(), surveyContainer, Collections.singletonList(values), Collections.singletonList(keys), null);

                            if (form.isStudy())
                            {
                                BioTrustAuditViewFactory.addAuditEvent(getContainer(), getUser(), form.getRowId(),
                                        BioTrustAuditViewFactory.Actions.StudyStatusChange, newStatus, prevStatus, form.getComment());
                            }
                            else
                            {
                                BioTrustAuditViewFactory.addAuditEvent(getContainer(), getUser(), 0, form.getRowId(),
                                        BioTrustAuditViewFactory.Actions.SampleRequestStatusChange, newStatus, prevStatus, form.getComment());
                            }

                            response.put("success", true);
                        }
                    }
                    transaction.commit();
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
            return response;
        }
    }

    @RequiresPermissionClass(UpdateWorkflowPermission.class)
    public class GetSampleReviewers extends ApiAction<SurveyForm>
    {
        @Override
        public ApiResponse execute(SurveyForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (form.getRowId() > 0)
            {
                JSONArray reviewerArr = new JSONArray();
                Set<User> reviewers = BioTrustSampleManager.get().getSampleReviewers(getContainer(), form.getRowId(), form.getStatus());
                for (User reviewer : reviewers)
                    reviewerArr.put(reviewer.getUserId());

                response.put("userIds", reviewerArr);
                response.put("success", true);
            }
            else
                response.put("success", false);

            return response;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class FindSurveyDesignAction extends MutatingApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            String label = (String)getViewContext().get("label");
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (label != null && c != null)
            {
                // for the NWBT setup, we expect the survey designs to exist at the project level
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), c.getProject());
                filter.addCondition(FieldKey.fromParts("Label"), label);
                SurveyDesign[] designs = SurveyService.get().getSurveyDesigns(filter);
                if (designs.length >= 1)
                {
                    response.put("success", true);
                    response.put("surveyDesignId", designs[0].getRowId());
                    return response;
                }
            }

            response.put("errorInfo", "Could not find a survey design by that label: " + label + ".");
            response.put("success", false);
            return response;
        }
    }

    public static class ReviewerResponseForm extends Entity
    {
        private int _rowId;
        private int _surveyId;
        private String _status;
        private String _actionType;
        private String _comment;

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public int getSurveyId()
        {
            return _surveyId;
        }

        public void setSurveyId(int surveyId)
        {
            _surveyId = surveyId;
        }

        public String getActionType()
        {
            return _actionType;
        }

        public void setActionType(String actionType)
        {
            _actionType = actionType;
        }

        public String getComment()
        {
            return _comment;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }

        public String getStatus()
        {
            return _status;
        }

        public void setStatus(String status)
        {
            _status = status;
        }
    }

    @RequiresPermissionClass(UpdateReviewPermission.class)
    public class SaveReviewerResponseAction extends MutatingApiAction<ReviewerResponseForm>
    {
        @Override
        public ApiResponse execute(ReviewerResponseForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            Container tissueRecordContainer = ContainerManager.getForId(form.getContainerId());
            TissueRecord tissueRecord = BioTrustSampleManager.get().getTissueRecord(tissueRecordContainer, getUser(), form.getRowId());
            if (tissueRecord != null && form.getSurveyId() > 0 && form.getComment() != null)
            {
                BioTrustAuditViewFactory.Actions action = null;
                for (BioTrustAuditViewFactory.Actions factoryAction : BioTrustAuditViewFactory.Actions.values())
                {
                    if (factoryAction.getLabel().equals(form.getActionType()))
                    {
                        action = factoryAction;
                        break;
                    }
                }

                if (action != null)
                {
                    BioTrustNotificationManager.get().sendSampleReviewUpdatedEmail(getContainer(), getUser(), form.getSurveyId(), tissueRecord, action, form.getComment());
                    // store the audit event in the current container (not the tissue record container)
                    BioTrustAuditViewFactory.addAuditEvent(getContainer(), getUser(), 0, form.getSurveyId(),
                        action, form.getStatus(), null, form.getComment());

                    response.put("success", true);
                    return response;
                }
            }

            response.put("success", false);
            return response;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetRolesAction extends ApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<Map<String, String>> roles = new ArrayList<>();

            for (Role role : BioTrustContactsManager.get().getBioTrustRoles(false))
            {
                Map<String, String> roleInfo = new HashMap<>();

                roleInfo.put("name", role.getName());
                roleInfo.put("uniqueName", role.getUniqueName());

                roles.add(roleInfo);
            }
            response.put("roles", roles);
            response.put("success", true);

            return response;
        }
    }

    @RequiresPermissionClass(SubmitRequestsPermission.class)
    public class RegisterStudyAction extends MutatingApiAction<SurveyForm>
    {
        @Override
        public ApiResponse execute(SurveyForm form, BindException errors) throws Exception
        {
            Survey survey = SurveyService.get().getSurvey(getContainer(), getUser(), form.getRowId());
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (survey != null && form.getStatus() != null)
            {
                DbScope scope = BioTrustSchema.getInstance().getSchema().getScope();
                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    Container surveyContainer = ContainerManager.getForId(survey.getContainerId());
                    UserSchema schema = QueryService.get().getUserSchema(getUser(), surveyContainer, "survey");
                    TableInfo surveyTable = schema.getTable("Surveys");
                    if (surveyTable != null)
                    {
                        Map<String, Object> values = new HashMap<>();
                        Map<String, Object> keys = new HashMap<>();

                        keys.put("RowId", form.getRowId());
                        values.put("Status", form.getStatus());
                        values.put("Registered", new Date());

                        QueryUpdateService qus = surveyTable.getUpdateService();
                        if (qus != null)
                        {
                            qus.updateRows(getUser(), surveyContainer, Collections.singletonList(values), Collections.singletonList(keys), null);
                            response.put("success", true);
                        }
                    }
                    transaction.commit();
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }

            return response;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetRCDashboardRequests extends ApiAction<QueryForm>
    {
        @Override
        public ApiResponse execute(QueryForm queryForm, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), BioTrustQuerySchema.NAME);
            List<Map<String, Object>> rows = new ArrayList<>();

            getStudySampleRequests(schema, rows, errors);

            Set<String> studyStatus = new HashSet<>();
            Set<String> studyCategory = new HashSet<>();
            getStudiesWithoutSampleRequests(schema, rows, studyStatus, studyCategory, errors);

            response.put("rows", rows);
            response.put("studyCategory", studyCategory);
            response.put("studyStatus", studyStatus);
            response.put("success", true);

            return response;
        }

        private void getStudySampleRequests(UserSchema schema, List<Map<String, Object>> rows, BindException errors) throws Exception
        {
            QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);

            settings.setSchemaName(BioTrustQuerySchema.NAME);
            settings.setQueryName("StudySampleRequests");
            settings.setContainerFilterName(ContainerFilter.Type.CurrentAndSubfolders.name());

            Sort sort = new Sort("StudyId");
            sort.appendSortColumn(FieldKey.fromParts("SampleId"), Sort.SortDirection.ASC, false);
            sort.appendSortColumn(FieldKey.fromParts("StudyRecordId"), Sort.SortDirection.ASC, false);
            settings.setBaseSort(sort);

            QueryView view = schema.createView(getViewContext(), settings, errors);

            DataView dataView = view.createDataView();
            List<DisplayColumn> columns = dataView.getDataRegion().getDisplayColumns();
            try (ResultSet rs = view.getResultSet())
            {
                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
                RenderContext ctx = dataView.getRenderContext();

                while (rs.next())
                {
                    Map<String, Object> row = new HashMap<>();
                    ctx.setRow(factory.getRowMap(rs));

                    for (DisplayColumn dc : columns)
                    {
                        String name = dc.getColumnInfo() != null ? dc.getColumnInfo().getName() : dc.getName();
                        row.put(name, dc.getJsonValue(ctx));
                    }
                    rows.add(row);
                }
            }
        }

        private void getStudiesWithoutSampleRequests(UserSchema schema, List<Map<String, Object>> rows, Set<String> studyStatus,
                                                     Set<String> studyCategory, BindException errors) throws Exception
        {
            QuerySettings settings = new QuerySettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT);

            settings.setSchemaName(BioTrustQuerySchema.NAME);
            settings.setQueryName("StudiesWithoutSampleRequest");
            settings.setContainerFilterName(ContainerFilter.Type.CurrentAndSubfolders.name());
            settings.setBaseSort(new Sort("StudyId"));

            QueryView view = schema.createView(getViewContext(), settings, errors);

            DataView dataView = view.createDataView();
            List<DisplayColumn> columns = dataView.getDataRegion().getDisplayColumns();

            try (ResultSet rs = view.getResultSet())
            {
                ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
                RenderContext ctx = dataView.getRenderContext();

                while (rs.next())
                {
                    Map<String, Object> row = new HashMap<>();
                    ctx.setRow(factory.getRowMap(rs));

                    for (DisplayColumn dc : columns)
                    {
                        String name = dc.getColumnInfo() != null ? dc.getColumnInfo().getName() : dc.getName();
                        Object value = dc.getJsonValue(ctx);

                        row.put(name, value);

                        if ("studystatus".equalsIgnoreCase(name))
                            studyStatus.add(String.valueOf(value));
                        else if ("studycategory".equalsIgnoreCase(name))
                            studyCategory.add(String.valueOf(value));
                    }
                    rows.add(row);
                }
            }
        }
    }

    @RequiresLogin
    public class CreateInvestigatorFolderAction extends MutatingApiAction<ContactForm>
    {
        private User _user;

        @Override
        public void validateForm(ContactForm form, Errors errors)
        {
            try
            {
                if (form.getEmail() == null)
                    errors.reject(ERROR_MSG, "A valid email must be provided.");

                ValidEmail email  = new ValidEmail(form.getEmail());
                _user = UserManager.getUser(email);
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }

            if (_user == null)
                errors.reject(ERROR_MSG, "The specified user cannot be found.");

            if (StringUtils.isBlank(form.getFirstName()))
                errors.reject(ERROR_MSG, "First name must be provided.");

            if (StringUtils.isBlank(form.getLastName()))
                errors.reject(ERROR_MSG, "Last name must be provided.");
        }

        @Override
        public ApiResponse execute(ContactForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            Container project = BioTrustManager.get().getBioTrustProject(getContainer());
            if (_user != null && project != null)
            {
                // update the contacts fields
                DbScope scope = CoreSchema.getInstance().getSchema().getScope();
                try (DbScope.Transaction transaction = scope.ensureTransaction())
                {
                    User elevatedUser = getUser();
                    if (!getContainer().hasPermission(getUser(), AdminPermission.class))
                    {
                        User currentUser = getUser();
                        Set<Role> contextualRoles = new HashSet<>(currentUser.getStandardContextualRoles());
                        contextualRoles.add(RoleManager.getRole(FolderAdminRole.class));
                        elevatedUser = new LimitedUser(currentUser, currentUser.getGroups(), contextualRoles, false);
                    }

                    String folderName = getFolderName(project, form);
                    Container newFolder = ContainerManager.createContainer(project, folderName);
                    newFolder.setFolderType(ModuleLoader.getInstance().getFolderType(SpecimenRequestorFolderType.NAME), getUser());

                    List<Role> roles = new ArrayList<>();

                    roles.add(RoleManager.getRole(PrincipalInvestigatorRole.class));
                    BioTrustContactsManager.get().updateRoleAssignments(newFolder, getUser(), roles, false);

                    UserSchema schema = QueryService.get().getUserSchema(elevatedUser, getContainer(), "core");
                    TableInfo usersTable = schema.getTable("Users");

                    if (usersTable != null)
                    {
                        TableViewForm tvf = new TableViewForm(usersTable);
                        tvf.setViewContext(getViewContext());
                        tvf.setTypedValues(form.getValues(), false);
                        Map<String, Object> values = tvf.getTypedColumns();

                        if (values.containsKey("DisplayName"))
                        {
                            if (StringUtils.isBlank((String)values.get("DisplayName")))
                                values.remove("DisplayName");
                        }

                        Map<String, Object> keys = new HashMap<>();
                        keys.put("UserId", _user.getUserId());

                        QueryUpdateService qus = usersTable.getUpdateService();
                        qus.updateRows(elevatedUser, getContainer(), Collections.singletonList(values), Collections.singletonList(keys), null);

                        // send the email notification
                        BioTrustNotificationManager.get().sendInvestigatorAccountCreatedEmail(newFolder, elevatedUser, _user);
                        response.put("success", true);
                    }
                    transaction.commit();
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
            return  response;
        }

        private String getFolderName(Container project, ContactForm form)
        {
            String name = String.format("%s, %s", form.getLastName(), form.getFirstName());

            int number = 1;
            while (project.hasChild(name))
            {
                name = String.format("%s, %s-%02d", form.getLastName(), form.getFirstName(), number++);
            }
            return name;
        }
    }

    /**
     * Check if an existing in-system or out-of-system account exists for a given email address
     */
    @RequiresPermissionClass(CreateContactsPermission.class)
    public class CheckExistingContactAction extends ApiAction<ContactForm>
    {
        private ValidEmail _email;

        @Override
        public void validateForm(ContactForm form, Errors errors)
        {
            // check for valid email address, this is a required field
            try
            {
                if (form.getEmail() == null)
                    errors.reject(ERROR_MSG, "A valid email must be provided.");

                _email = new ValidEmail(form.getEmail());
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
        }

        @Override
        public ApiResponse execute(ContactForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            if (form.isSystemUser())
            {
                User user = null;
                if (form.getUserId() == 0)
                    user = UserManager.getUser(_email);
                else
                    user = UserManager.getUser(form.getUserId());

                if (user != null)
                {
                    response.put("exists", true);
                    response.put("userId", user.getUserId());
                    response.put("displayName", user.getDisplayName(getUser()));
                    response.put("success", true);
                    return response;
                }
            }
            else
            {
                SimpleFilter filter = new SimpleFilter();
                if (form.getRowId() > 0)
                    filter.addCondition(FieldKey.fromParts("RowId"), form.getRowId());
                else
                    filter.addCondition(FieldKey.fromParts("Email"), _email.getEmailAddress());

                UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), BioTrustQuerySchema.NAME);
                TableInfo ti = schema.getTable(BioTrustQuerySchema.CONTACT_TABLE_NAME);
                Map<String, Object> contactRecord = new TableSelector(ti, filter, null).getMap();
                if (contactRecord != null)
                {
                    response.put("exists", true);
                    response.put("rowId", contactRecord.get("RowId"));
                    response.put("displayName", contactRecord.get("DisplayName"));
                    response.put("success", true);
                    return response;
                }
            }

            response.put("exists", false);
            response.put("success", true);
            return response;
        }
    }


    /**
     * Converts an out of system contact into an in system contact
     */
    @RequiresPermissionClass(CreateContactsPermission.class)
    public class ConvertContactAction extends MutatingApiAction<ContactForm>
    {
        private ValidEmail _email;
        private List<Role> _roles = new ArrayList<>();

        @Override
        public void validateForm(ContactForm form, Errors errors)
        {
            if (form.isSystemUser())
                errors.reject(ERROR_MSG, "The specified user is already a system user");

            // check for valid email address, this is a required field
            try
            {
                if (form.getEmail() == null)
                    errors.reject(ERROR_MSG, "A valid email must be provided.");

                _email = new ValidEmail(form.getEmail());
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }

            Map<String, String> roleNameMap = new HashMap<>();
            for (Role role : BioTrustContactsManager.get().getBioTrustRoles(true))
                roleNameMap.put(role.getName(), role.getUniqueName());

            for (String roleName : form.getRole())
            {
                Role role = RoleManager.getRole(roleNameMap.get(roleName));
                if (role == null || !AbstractBioTrustRole.class.isAssignableFrom(role.getClass()))
                    errors.reject(ERROR_MSG, "Invalid role, must be a NWBioTrust related role.");
                else
                    _roles.add(role);
            }

            if (UserManager.getUser(_email) != null)
                errors.reject(ERROR_MSG, "The specified email address is already being used. Please select a unique email address");
        }

        @Override
        public ApiResponse execute(ContactForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            DbScope scope = CoreSchema.getInstance().getSchema().getScope();
            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                SecurityManager.NewUserStatus status = org.labkey.api.security.SecurityManager.addUser(_email);
                User user = status.getUser();

                User newUser = UserManager.getUser(user.getUserId());

                if (newUser != null)
                {
                    Role reader = RoleManager.getRole(ReaderRole.class);
                    BioTrustContactsManager.get().addRoleAssignment(getContainer(), newUser, reader);
                }

                BioTrustContactsManager.get().updateRoleAssignments(getContainer(), newUser, _roles, false);

                updateUserProperties(newUser, form.getValues());

                response.put("userId", newUser.getUserId());
                response.put("success", true);

                // delete the out of system contact
                if (form.getRowId() != 0)
                {
                    UserSchema btSchema = QueryService.get().getUserSchema(getUser(), getContainer(), BioTrustQuerySchema.NAME);
                    TableInfo contactsTable = btSchema.getTable(BioTrustQuerySchema.CONTACT_TABLE_NAME);
                    QueryUpdateService qus = contactsTable.getUpdateService();

                    Map<String, Object> keys = new HashMap<>();
                    if (btSchema.getDbSchema().getSqlDialect().isCaseSensitive())
                        keys.put("rowid", form.getRowId());
                    else
                        keys.put("RowId", form.getRowId());
                    qus.deleteRows(getUser(), getContainer(), Collections.singletonList(keys), null);
                }

                transaction.commit();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
            return response;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class UpdateEmailNotifications extends MutatingApiAction<EmailNotificationForm>
    {
        @Override
        public ApiResponse execute(EmailNotificationForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(getContainer().getProject(), BioTrustNotificationManager.NWBT_EMAIL_NOTIFICATIONS, true);

            map.put(BioTrustNotificationManager.NotificationType.studyRegistered.name(), form.isStudyRegistered() ? null : "false");
            map.put(BioTrustNotificationManager.NotificationType.sampleRequestSubmitted.name(), form.isSampleRequestSubmitted() ? null : "false");
            map.put(BioTrustNotificationManager.NotificationType.approverReviewRequested.name(), form.isApproverReviewRequested() ? null : "false");
            map.put(BioTrustNotificationManager.NotificationType.approverReviewSubmitted.name(), form.isApproverReviewSubmitted() ? null : "false");
            map.put(BioTrustNotificationManager.NotificationType.investigatorFolderCreated.name(), form.isInvestigatorFolderCreated() ? null : "false");

            PropertyManager.saveProperties(map);

            response.put("success", true);
            return response;
        }
    }

    public static class EmailNotificationForm
    {
        boolean _studyRegistered;
        boolean _sampleRequestSubmitted;
        boolean _approverReviewRequested;
        boolean _approverReviewSubmitted;
        boolean _investigatorFolderCreated;

        public boolean isStudyRegistered()
        {
            return _studyRegistered;
        }

        public void setStudyRegistered(boolean studyRegistered)
        {
            _studyRegistered = studyRegistered;
        }

        public boolean isSampleRequestSubmitted()
        {
            return _sampleRequestSubmitted;
        }

        public void setSampleRequestSubmitted(boolean sampleRequestSubmitted)
        {
            _sampleRequestSubmitted = sampleRequestSubmitted;
        }

        public boolean isApproverReviewRequested()
        {
            return _approverReviewRequested;
        }

        public void setApproverReviewRequested(boolean approverReviewRequested)
        {
            _approverReviewRequested = approverReviewRequested;
        }

        public boolean isApproverReviewSubmitted()
        {
            return _approverReviewSubmitted;
        }

        public void setApproverReviewSubmitted(boolean approverReviewSubmitted)
        {
            _approverReviewSubmitted = approverReviewSubmitted;
        }

        public boolean isInvestigatorFolderCreated()
        {
            return _investigatorFolderCreated;
        }

        public void setInvestigatorFolderCreated(boolean investigatorFolderCreated)
        {
            _investigatorFolderCreated = investigatorFolderCreated;
        }
    }
}