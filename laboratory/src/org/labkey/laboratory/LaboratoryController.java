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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.AbstractFileUploadAction;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.LaboratoryUrls;
import org.labkey.api.laboratory.TabbedReportItem;
import org.labkey.api.laboratory.assay.AbstractAssayDataProvider;
import org.labkey.api.laboratory.assay.AssayDataProvider;
import org.labkey.api.laboratory.assay.AssayImportMethod;
import org.labkey.api.laboratory.assay.AssayParser;
import org.labkey.api.laboratory.security.LaboratoryAdminPermission;
import org.labkey.api.laboratory.AbstractNavItem;
import org.labkey.api.laboratory.NavItem;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.laboratory.assay.AssayHelper;
import org.labkey.laboratory.query.ContainerIncrementingTable;
import org.labkey.laboratory.query.WorkbookModel;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


public class LaboratoryController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(LaboratoryController.class);
    private static final Logger _log = Logger.getLogger(LaboratoryController.class);

    public LaboratoryController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public class PrepareExptRunAction extends SimpleViewAction<PlanExptRunForm>
    {
        @Override
        public ModelAndView getView(PlanExptRunForm form, BindException errors) throws Exception
        {
            Integer assayId = form.getAssayId();
            if (assayId == null)
            {
                return new HtmlView("Error: must provide a rowId for the assay");
            }

            AssayDataProvider ad = LaboratoryService.get().getDataProviderForAssay(assayId);
            if (ad == null || !ad.supportsRunTemplates())
            {
                return new HtmlView("Error: this assay does not support requests");
            }

            Module labModule = ModuleLoader.getInstance().getModule(LaboratoryModule.NAME);
            HtmlView view = new ModuleHtmlView(labModule.getModuleResource(Path.parse("views/prepareExptRun.html")));

            Set<ClientDependency> cd = ad.getClientDependencies();
            if (cd != null)
            {
                view.addClientDependencies(cd);
            }
            view.setTitle("Prepare Assay Run");
            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Prepare Assay Run");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class EnsureIndexesAction extends ConfirmAction<Object>
    {
        public void validateCommand(Object form, Errors errors)
        {

        }

        public URLHelper getSuccessURL(Object form)
        {
            return getContainer().getStartURL(getUser());
        }

        public ModelAndView getConfirmView(Object form, BindException errors) throws Exception
        {
            if (!getUser().isSiteAdmin())
            {
                throw new UnauthorizedException("Only site admins can view this page");
            }

            StringBuilder msg = new StringBuilder();
            msg.append("Certain assays can have performance improved by the addition of indexes, which can be suggested by modules.  The following indexes are recommended for the assays installed on this server:<p>");

            List<String> msgs = LaboratoryManager.get().createIndexes(getUser(), false, false);
            msg.append(StringUtils.join(msgs, "<br>"));

            msg.append("<p>Do you want to continue?");

            return new HtmlView(msg.toString());
        }

        public boolean handlePost(Object form, BindException errors) throws Exception
        {
            LaboratoryManager.get().createIndexes(getUser(), true, true);
            return true;
        }
    }

    public static class PlanExptRunForm
    {
        private Integer _assayId;

        public Integer getAssayId()
        {
            return _assayId;
        }

        public void setAssayId(Integer assayId)
        {
            _assayId = assayId;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class EnsureAssayFieldsAction extends ConfirmAction<EnsureAssayFieldsForm>
    {
        public void validateCommand(EnsureAssayFieldsForm form, Errors errors)
        {
            if (!ContainerManager.getRoot().hasPermission(getUser(), AdminPermission.class))
                throw new UnauthorizedException("User must be a site admin");

            if (form.getProviderName() == null)
            {
                errors.reject(ERROR_MSG, "Assay provider cannot be null");
            }

            if (AssayService.get().getProvider(form.getProviderName()) == null)
            {
                errors.reject(ERROR_MSG, "Unknown assay provider: " + form.getProviderName());
            }
        }

        @Override
        public ModelAndView getConfirmView(EnsureAssayFieldsForm form, BindException errors) throws Exception
        {
            try
            {
                StringBuilder sb = new StringBuilder();
                sb.append("This action will iterate all protocols for the assay " + form.getProviderName() + " and append any columns present in the definition, but lacking from that instance of the assay.  The following changes will be made:<br><br>");
                List<String> messages = AssayHelper.ensureAssayFields(getUser(), form.getProviderName(), form.isRenameConflicts(), true);
                for (String msg : messages)
                {
                    sb.append(msg).append("<br><br>");
                }

                sb.append("<br>Do you want to continue?");

                return new HtmlView(sb.toString());
            }
            catch (ChangePropertyDescriptorException e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                return new HtmlView("There was an error inspecting this assay: " + e.getMessage());
            }
        }

        public boolean handlePost(EnsureAssayFieldsForm form, BindException errors) throws Exception
        {
            try
            {
                List<String> messages = AssayHelper.ensureAssayFields(getUser(), form.getProviderName(), form.isRenameConflicts(), false);
                boolean reload = false;
                for (String m : messages)
                {
                    if (m.contains("You will need to re-run this page"))
                    {
                        reload = true;
                        break;
                    }
                }

                if (reload)
                {
                    ActionURL url = new ActionURL(LaboratoryController.EnsureAssayFieldsAction.class, ContainerManager.getSharedContainer());
                    url.addParameter("renameConflicts", true);
                    url.addParameter("providerName", form.getProviderName());
                    url.addParameter("returnUrl", form.getReturnUrl().toString());
                    form.setReturnUrl(url.getLocalURIString());
                }

                return true;
            }
            catch (ChangePropertyDescriptorException e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                return false;
            }
        }

        public URLHelper getSuccessURL(EnsureAssayFieldsForm form)
        {
            if (form.getReturnActionURL() != null)
                return form.getReturnActionURL();

            return ContainerManager.getHomeContainer().getStartURL(getUser());
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class SetTableIncrementValueAction extends ConfirmAction<SetTableIncrementForm>
    {
        public void validateCommand(SetTableIncrementForm form, Errors errors)
        {

        }

        @Override
        public ModelAndView getConfirmView(SetTableIncrementForm form, BindException errors) throws Exception
        {
            StringBuilder sb = new StringBuilder();
            sb.append("This allows you to reset the current value for an auto-incrementing table<br><br>");
            sb.append("<table style='border-collapse: collapse;'>");

            String schema = form.getSchemaName() == null ? "" : form.getSchemaName();
            sb.append("<tr><td>Schema:</td><td><input name=\"schema\" value=\"" + schema + "\"></td></tr>");

            String query = form.getQueryName() == null ? "" : form.getQueryName();
            sb.append("<tr><td>Query:</td><td><input name=\"query\" value=\"" + query + "\"></td></tr>");

            ContainerIncrementingTable ti = getTable(schema, query, errors, false);
            Integer value = null;
            if (ti != null)
                value = ti.getCurrentId(getContainer());

            sb.append("<tr><td>Value:</td><td><input name=\"value\" value=\"" + (value == null ? "" :  value) + "\"></td></tr>");
            sb.append("</table><br>Do you want to continue?");

            return new HtmlView(sb.toString());
        }

        public boolean handlePost(SetTableIncrementForm form, BindException errors) throws Exception
        {
            ContainerIncrementingTable ti = getTable(form.getSchema(), form.getQuery(), errors, true);
            if (errors.hasErrors() || ti == null)
                return false;

            if (form.getValue() == null)
            {
                errors.reject(ERROR_MSG, "No value provided");
                return false;
            }

            if (form.getValue() < 1)
            {
                errors.reject(ERROR_MSG, "Invalid value for the table's key");
                return false;
            }

            ti.saveId(getContainer(), form.getValue());

            return true;
        }

        private ContainerIncrementingTable getTable(String schema, String query, BindException errors, boolean rejectOnError)
        {
            if (schema == null || query == null)
            {
                if (rejectOnError)
                    errors.reject(ERROR_MSG, "Must provide a schema and query");
                return null;
            }

            UserSchema us = QueryService.get().getUserSchema(getUser(), getContainer(), schema);
            if (us == null)
            {
                if (rejectOnError)
                    errors.reject(ERROR_MSG, "Unknown schema: " + schema);
                return null;
            }

            TableInfo ti = us.getTable(query);
            if (us == null)
            {
                if (rejectOnError)
                    errors.reject(ERROR_MSG, "Unknown query: " + query);
                return null;
            }

            if (!(ti instanceof ContainerIncrementingTable))
            {
                if (rejectOnError)
                    errors.reject(ERROR_MSG, "This table does not support incrementing");
                return null;
            }

            return (ContainerIncrementingTable)ti;
        }

        public URLHelper getSuccessURL(SetTableIncrementForm form)
        {
            return getContainer().getStartURL(getUser());
        }
    }

    public static class SetTableIncrementForm
    {
        private String _schema;
        private String _query;

        private String _schemaName;
        private String _queryName;
        private Integer _value;

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public Integer getValue()
        {
            return _value;
        }

        public void setValue(Integer value)
        {
            _value = value;
        }

        public String getSchema()
        {
            return _schema;
        }

        public void setSchema(String schema)
        {
            _schema = schema;
        }

        public String getQuery()
        {
            return _query;
        }

        public void setQuery(String query)
        {
            _query = query;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class InitWorkbooksAction extends ConfirmAction<Object>
    {
        public void validateCommand(Object form, Errors errors)
        {
            if (!ContainerManager.getRoot().hasPermission(getUser(), AdminPermission.class))
                throw new UnauthorizedException("User must be a site admin");
        }

        @Override
        public ModelAndView getConfirmView(Object form, BindException errors) throws Exception
        {
            return new HtmlView("This action will iterate all workbooks in the current folder and create laboratory experiments for them as needed");
        }

        public boolean handlePost(Object form, BindException errors) throws Exception
        {
            try
            {
                LaboratoryManager.get().initWorkbooksForContainer(getUser(), getContainer());

                return true;
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                return false;
            }
        }

        public URLHelper getSuccessURL(Object form)
        {
            return getContainer().getStartURL(getUser());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class InitContainerIncrementingTableAction extends ConfirmAction<SetTableIncrementForm>
    {
        public void validateCommand(SetTableIncrementForm form, Errors errors)
        {
            if (!ContainerManager.getRoot().hasPermission(getUser(), AdminPermission.class))
                throw new UnauthorizedException("User must be a site admin");
        }

        @Override
        public ModelAndView getConfirmView(SetTableIncrementForm form, BindException errors) throws Exception
        {
            StringBuilder sb = new StringBuilder();
            sb.append("This allows you to initialize the autoincrementing column for the provided schema/query<br><br>");
            sb.append("This is very rarely required and was created as a helper for admins with a good deal of knowledge about this module.  Under most cases these columns will be automatically populated and you will not need to worry about this.  If you are unsure about this page, please post on the LabKey help forums, which are read by the authors of this module.<br><br>");
            sb.append("<table style='border-collapse: collapse;'>");

            String schema = form.getSchemaName() == null ? "" : form.getSchemaName();
            sb.append("<tr><td>Schema:</td><td><input name=\"schema\" value=\"" + schema + "\"></td></tr>");

            String query = form.getQueryName() == null ? "" : form.getQueryName();
            sb.append("<tr><td>Query:</td><td><input name=\"query\" value=\"" + query + "\"></td></tr>");

            sb.append("</table><br>Do you want to continue?");

            return new HtmlView(sb.toString());
        }


        public boolean handlePost(SetTableIncrementForm form, BindException errors) throws Exception
        {
            try
            {
                processContainer(getContainer(), form.getSchema(), form.getQuery());

                return true;
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                return false;
            }
        }

        private void processContainer(Container c, String schema, String query)
        {
            try
            {
                LaboratoryManager.get().initContainerIncrementingTableIds(c, getUser(), schema, query);
            }
            catch (IllegalArgumentException e)
            {
                //ignore
                _log.warn(e.getMessage(), e);
            }

            for (Container child : c.getChildren())
            {
                if (!c.isWorkbook())
                    processContainer(child, schema, query);
            }
        }

        public URLHelper getSuccessURL(SetTableIncrementForm form)
        {
            return getContainer().getStartURL(getUser());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ResetLaboratoryFoldersAction extends ConfirmAction<Object>
    {
        public void validateCommand(Object form, Errors errors)
        {
            if (!ContainerManager.getRoot().hasPermission(getUser(), AdminPermission.class))
                throw new UnauthorizedException("User must be a site admin");
        }

        @Override
        public ModelAndView getConfirmView(Object form, BindException errors) throws Exception
        {
            return new HtmlView("This action will webparts and tabs for the current folder and all children to the default Laboratory FolderType, if these folders are either Laboratory Folders or Expt Workbooks");
        }

        public boolean handlePost(Object form, BindException errors) throws Exception
        {
            try
            {
                LaboratoryManager.get().resetLaboratoryFolderTypes(getUser(), getContainer(), true);

                return true;
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                return false;
            }
        }

        public URLHelper getSuccessURL(Object form)
        {
            return getContainer().getStartURL(getUser());
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class ProcessAssayDataAction extends AbstractFileUploadAction<ProcessAssayForm>
    {
        @Override
        public void export(ProcessAssayForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);
            super.export(form, response, errors);
        }

        protected File getTargetFile(String filename) throws IOException
        {
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
                throw new UploadException("Pipeline root must be configured before uploading assay files", HttpServletResponse.SC_NOT_FOUND);

            AssayFileWriter writer = new AssayFileWriter();
            try
            {
                File targetDirectory = writer.ensureUploadDirectory(getContainer());
                return writer.findUniqueFileName(filename, targetDirectory);
            }
            catch (ExperimentException e)
            {
                throw new UploadException(e.getMessage(), HttpServletResponse.SC_NOT_FOUND);
            }
        }

        protected String getResponse(Map<String, Pair<File, String>> files, ProcessAssayForm form) throws UploadException
        {
            JSONObject resp = new JSONObject();
            try
            {
                for (Map.Entry<String, Pair<File, String>> entry : files.entrySet())
                {
                    File file = entry.getValue().getKey();
                    String originalName = entry.getValue().getValue();

                    if (form.getLabkeyAssayId() == null)
                    {
                        throw new UploadException("No Assay Id Provided", HttpServletResponse.SC_BAD_REQUEST);
                    }

                    ExpProtocol protocol = ExperimentService.get().getExpProtocol(form.getLabkeyAssayId());
                    if (protocol == null)
                    {
                        throw new UploadException("Unable to find assay protocol with Id: " + form.getLabkeyAssayId(), HttpServletResponse.SC_BAD_REQUEST);
                    }
                    AssayProvider ap = AssayService.get().getProvider(protocol);

                    JSONObject json = new JSONObject(form.getJsonData());
                    String importMethod = json.getString("importMethod");

                    AssayImportMethod method = LaboratoryService.get().getDataProviderForAssay(ap).getImportMethodByName(importMethod);
                    if (method == null)
                    {
                        throw new UploadException("Import method not recognized: " + importMethod, HttpServletResponse.SC_BAD_REQUEST);
                    }

                    AssayParser parser = method.getFileParser(getContainer(), getUser(), form.getLabkeyAssayId());
                    if (form.isDoSave())
                    {
                        parser.saveBatch(json, file, originalName, getViewContext());

                    }
                    else
                    {
                        JSONObject preview = parser.getPreview(json, file, originalName, getViewContext());
                        assert file.delete();
                        resp.put("results", preview);
                    }

                    resp.put("success", true);
                }
            }
            catch (BatchValidationException e)
            {
                resp.put("success", false);
                resp.put("errors", e.getRowErrors());
                resp.put("exception", e.getMessage());// "There was an error during upload");
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                _log.error(e.getMessage(), e);
                resp.put("success", false);
                resp.put("exception", e.getMessage());
            }

            return resp.toString();
        }
    }


    @RequiresPermission(AdminPermission.class)
    @CSRF
    public class PopulateDefaultsAction extends ApiAction<PopulateDefaultsForm>
    {
        public ApiResponse execute(PopulateDefaultsForm form, BindException errors) throws Exception
        {
            if (getContainer().isWorkbook())
            {
                errors.reject(ERROR_MSG, "Cannot be run on workbooks");
                return null;
            }

            if (form.getTableNames() == null || form.getTableNames().length == 0)
            {
                errors.reject(ERROR_MSG, "No tables supplied");
                return null;
            }

            LaboratoryManager.get().populateDefaultData(getUser(), getContainer(), Arrays.asList(form.getTableNames()));

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class PopulateDefaultsForm
    {
        private String[] _tableNames;

        public String[] getTableNames()
        {
            return _tableNames;
        }

        public void setTableNames(String[] tableNames)
        {
            _tableNames = tableNames;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    @CSRF
    public class UpdateWorkbookAction extends ApiAction<UpdateWorkbookForm>
    {
        public ApiResponse execute(UpdateWorkbookForm form, BindException errors) throws Exception
        {
            Map<String, Object> results = new HashMap<>();

            if (!getContainer().isWorkbook())
            {
                errors.reject(ERROR_MSG, "This container is not a workbook");
                return null;
            }

            WorkbookModel model = LaboratoryManager.get().getWorkbookModel(getContainer());
            if (model == null)
            {
                errors.reject(ERROR_MSG, "Unable to find workbook record for this folder");
                return null;
            }

            model.setMaterials(form.getMaterials());
            model.setMethods(form.getMethods());
            model.setResults(form.getResults());
            model.setDescription(form.getDescription(), getUser());

            LaboratoryManager.get().updateWorkbook(getUser(), model);

            if (form.isForceTagUpdate() || form.getTags() != null)
            {
                LaboratoryManager.get().updateWorkbookTags(getUser(), getContainer(), (form.getTags() == null ? (Collection)Collections.emptyList() : Arrays.asList(form.getTags())));
            }

            results.put("success", true);

            return new ApiSimpleResponse(results);
        }
    }

    @RequiresPermission(UpdatePermission.class)
    @CSRF
    public class UpdateWorkbookTagsAction extends ApiAction<UpdateWorkbookForm>
    {
        public ApiResponse execute(UpdateWorkbookForm form, BindException errors) throws Exception
        {
            Map<String, Object> results = new HashMap<>();

            if (!getContainer().isWorkbook())
            {
                errors.reject(ERROR_MSG, "This container is not a workbook");
                return null;
            }

            LaboratoryManager.get().updateWorkbookTags(getUser(), getContainer(), Arrays.asList(form.getTags()), form.isMerge());

            results.put("success", true);

            return new ApiSimpleResponse(results);
        }
    }

    public static class UpdateWorkbookForm
    {
        private String _description;
        private String _title;
        private String _materials;
        private String _methods;
        private String _results;
        private String[] _tags;
        private boolean _forceTagUpdate;
        private boolean _merge = false;

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getTitle()
        {
            return _title;
        }

        public void setTitle(String title)
        {
            _title = title;
        }

        public String getMaterials()
        {
            return _materials;
        }

        public void setMaterials(String materials)
        {
            _materials = materials;
        }

        public String getMethods()
        {
            return _methods;
        }

        public void setMethods(String methods)
        {
            _methods = methods;
        }

        public String getResults()
        {
            return _results;
        }

        public void setResults(String results)
        {
            _results = results;
        }

        public String[] getTags()
        {
            return _tags;
        }

        public void setTags(String[] tags)
        {
            _tags = tags;
        }

        public boolean isMerge()
        {
            return _merge;
        }

        public void setMerge(boolean merge)
        {
            _merge = merge;
        }

        public boolean isForceTagUpdate()
        {
            return _forceTagUpdate;
        }

        public void setForceTagUpdate(boolean forceTagUpdate)
        {
            _forceTagUpdate = forceTagUpdate;
        }
    }


    @RequiresPermission(UpdatePermission.class)
    @CSRF
    public class SaveTemplateAction extends ApiAction<SaveTemplateForm>
    {
        public ApiResponse execute(SaveTemplateForm form, BindException errors) throws Exception
        {
            Map<String, Object> results = new HashMap<>();

            if (form.getJson() == null || form.getProtocolId() == null || form.getTitle() == null)
            {
                errors.reject(ERROR_MSG, "Must provide the assay Id, title, and template JSON");
                return null;
            }

            try
            {
                JSONObject json = new JSONObject(form.getJson());

                ExpProtocol protocol = ExperimentService.get().getExpProtocol(form.getProtocolId());
                if (protocol == null)
                {
                    errors.reject(ERROR_MSG, "Unknown assay: " + form.getProtocolId());
                    return null;
                }

                if (form.isValidateOnly())
                {
                    AssayHelper.get().validateTemplate(getUser(), getContainer(), protocol, form.getTemplateId(), form.getTitle(), form.getImportMethod(), json);
                }
                else
                {
                    Map<String, Object> row = AssayHelper.get().saveTemplate(getUser(), getContainer(), protocol, form.getTemplateId(), form.getTitle(), form.getImportMethod(), json);
                    results.put("template", row);
                }

                results.put("success", true);
            }
            catch (JSONException e)
            {
                errors.reject(ERROR_MSG, "Improper JSON object: " + e.getMessage());
                _log.error(e.getMessage(), e);
                return null;
            }

            return new ApiSimpleResponse(results);
        }
    }

    public static class SaveTemplateForm
    {
        private boolean _validateOnly = false;
        private Integer _templateId;
        private Integer _protocolId;
        private String _importMethod;
        private String _title;
        private String _json;

        public boolean isValidateOnly()
        {
            return _validateOnly;
        }

        public void setValidateOnly(boolean validateOnly)
        {
            _validateOnly = validateOnly;
        }

        public Integer getTemplateId()
        {
            return _templateId;
        }

        public void setTemplateId(Integer templateId)
        {
            _templateId = templateId;
        }

        public String getJson()
        {
            return _json;
        }

        public void setJson(String json)
        {
            _json = json;
        }

        public Integer getProtocolId()
        {
            return _protocolId;
        }

        public void setProtocolId(Integer protocolId)
        {
            _protocolId = protocolId;
        }

        public String getTitle()
        {
            return _title;
        }

        public void setTitle(String title)
        {
            _title = title;
        }

        public String getImportMethod()
        {
            return _importMethod;
        }

        public void setImportMethod(String importMethod)
        {
            _importMethod = importMethod;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class CreateTemplateAction extends ExportAction<ProcessAssayForm>
    {
        @Override
        public void validate(ProcessAssayForm form, BindException errors)
        {
            if (form.getLabkeyAssayId() == null)
                errors.reject(ERROR_MSG, "Must provide the rowId of the assay");

            if (form.getImportMethod() == null)
                errors.reject(ERROR_MSG, "Must provide the name of the import method");

            if (form.getJsonData() == null)
                errors.reject(ERROR_MSG, "Cannot create template, no jsonData provided");
        }

        public void export(ProcessAssayForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            try
            {
                if (errors.hasErrors())
                {
                    HttpView errorView = ExceptionUtil.getErrorView(HttpServletResponse.SC_BAD_REQUEST, "Failed to create template - invalid input", null, getViewContext().getRequest(), false);
                    errorView.render(getViewContext().getRequest(), getViewContext().getResponse());
                    return;
                }

                ExpProtocol protocol = ExperimentService.get().getExpProtocol(form.getLabkeyAssayId());
                if (protocol == null)
                {
                    throw new AbstractFileUploadAction.UploadException("Unable to find assay protocol with Id: " + form.getLabkeyAssayId(), HttpServletResponse.SC_BAD_REQUEST);
                }
                AssayProvider ap = AssayService.get().getProvider(protocol);

                JSONObject json = new JSONObject(form.getJsonData());
                String importMethod = form.getImportMethod();

                AssayImportMethod method = LaboratoryService.get().getDataProviderForAssay(ap).getImportMethodByName(importMethod);
                if (method == null)
                    throw new ValidationException("Import method not recognized: " + importMethod);
                if (!method.supportsRunTemplates())
                    throw new ValidationException("Import method does not support templates: " + importMethod);

                method.generateTemplate(getViewContext(), protocol, form.getTemplateId(), form.getTitle(), json);
            }
            catch (BatchValidationException e)
            {
                response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("errors", e.getRowErrors());
                resp.put("exception", e.getMessage());
                response.getWriter().write(resp.toString());
            }
            catch (JSONException e)
            {
                response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JSONObject resp = new JSONObject();
                resp.put("success", false);
                resp.put("exception", e.getMessage());
                response.getWriter().write(resp.toString());
            }
        }
    }

    @RequiresPermission(ReadPermission.class)
    @CSRF
    public class GetDemographicsSourcesAction extends ApiAction<DataSourcesForm>
    {
        public ApiResponse execute(DataSourcesForm form, BindException errors)
        {
            Map<String, Object> results = new HashMap<>();

            LaboratoryServiceImpl service = (LaboratoryServiceImpl)LaboratoryServiceImpl.get();

            Set<DemographicsSource> tables = service.getDemographicsSources(getContainer(), getUser());
            JSONArray sources = new JSONArray();
            for (DemographicsSource qd : tables)
            {
                JSONObject json = qd.toJSON(getContainer(), getUser(), form.isIncludeTotals());
                if (json == null){
                    errors.reject(ERROR_MSG, "Unable to create table for source: " + qd.getSchemaName() + "." + qd.getQueryName());
                    return null;
                }
                else
                {
                    sources.put(json);
                }
            }
            results.put("sources", sources);

            //append default sources from /Shared folder
            Set<DemographicsSource> sharedTables = service.getDemographicsSources(ContainerManager.getSharedContainer(), getUser());
            JSONArray sharedSources = new JSONArray();
            for (DemographicsSource qd : sharedTables)
            {
                JSONObject json = qd.toJSON(getContainer(), getUser(), false);
                if (json == null){
                    errors.reject(ERROR_MSG, "Unable to create table for source: " + qd.getSchemaName() + "." + qd.getQueryName() + " for container " + ContainerManager.getSharedContainer().getPath());
                    return null;
                }
                else
                {
                    sharedSources.put(json);
                }
            }
            results.put("sharedSources", sharedSources);

            if (form.isIncludeSiteSummary())
            {
                if (getUser().isSiteAdmin())
                {
                    Map<Container, Set<DemographicsSource>> map = service.getAllDemographicsSources(getUser());
                    Map<String, JSONArray> siteSummary = new HashMap<String, JSONArray>();
                    for (Container c : map.keySet())
                    {
                        JSONArray arr = siteSummary.get(c.getPath());
                        if (arr == null)
                            arr = new JSONArray();

                        for (DemographicsSource ds : map.get(c))
                        {
                            arr.put(ds.toJSON(c, getUser(), false));
                        }

                        siteSummary.put(c.getPath(), arr);
                    }

                    results.put("siteSummary", siteSummary);
                }
                else
                {
                    throw new UnauthorizedException("Only site admins can view the site-wide summary");
                }
            }

            results.put("success", true);

            return new ApiSimpleResponse(results);
        }
    }

    @RequiresPermission(ReadPermission.class)
    @CSRF
    public class GetAdditionalDataSourcesAction extends ApiAction<DataSourcesForm>
    {
        public ApiResponse execute(DataSourcesForm form, BindException errors)
        {
            Map<String, Object> results = new HashMap<>();

            LaboratoryServiceImpl service = (LaboratoryServiceImpl)LaboratoryServiceImpl.get();

            Set<AdditionalDataSource> tables = service.getAdditionalDataSources(getContainer(), getUser());
            JSONArray sources = new JSONArray();
            for (AdditionalDataSource qd : tables)
            {
                JSONObject json = qd.toJSON(getContainer(), getUser(), form.isIncludeTotals());
                if (json == null){
                    errors.reject(ERROR_MSG, "Unable to create table for source: " + qd.getSchemaName() + "." + qd.getQueryName());
                    return null;
                }
                else
                {
                    sources.put(json);
                }
            }
            results.put("sources", sources);

            //append default sources from /Shared folder
            Set<AdditionalDataSource> sharedTables = service.getAdditionalDataSources(ContainerManager.getSharedContainer(), getUser());
            JSONArray sharedSources = new JSONArray();
            for (AdditionalDataSource qd : sharedTables)
            {
                JSONObject json = qd.toJSON(getContainer(), getUser(), false);
                if (json == null){
                    errors.reject(ERROR_MSG, "Unable to create table for source: " + qd.getSchemaName() + "." + qd.getQueryName() + " for container " + ContainerManager.getSharedContainer().getPath());
                    return null;
                }
                else
                {
                    sharedSources.put(json);
                }
            }
            results.put("sharedSources", sharedSources);

            if (form.isIncludeSiteSummary())
            {
                if (getUser().isSiteAdmin())
                {
                    Map<Container, Set<AdditionalDataSource>> map = service.getAllAdditionalDataSources(getUser());
                    Map<String, JSONArray> siteSummary = new HashMap<String, JSONArray>();
                    for (Container c : map.keySet())
                    {
                        JSONArray arr = siteSummary.get(c.getPath());
                        if (arr == null)
                            arr = new JSONArray();

                        for (AdditionalDataSource ds : map.get(c))
                        {
                            arr.put(ds.toJSON(c, getUser(), false));
                        }

                        siteSummary.put(c.getPath(), arr);
                    }

                    results.put("siteSummary", siteSummary);
                }
                else
                {
                    throw new UnauthorizedException("Only site admins can view the site-wide summary");
                }
            }

            Set<URLDataSource> urls = service.getURLDataSources(getContainer(), getUser());
            JSONArray urlSources = new JSONArray();
            for (URLDataSource url : urls)
            {
                JSONObject json = url.toJSON(getContainer());
                if (json == null){
                    errors.reject(ERROR_MSG, "Invalid saved URL source: " + url.getUrl().toString());
                    return null;
                }
                else
                {
                    urlSources.put(json);
                }
            }
            results.put("urlSources", urlSources);

            results.put("success", true);

            return new ApiSimpleResponse(results);
        }
    }

    public static class DataSourcesForm
    {
        private boolean _includeTotals;
        private boolean _includeSiteSummary;

        public boolean isIncludeTotals()
        {
            return _includeTotals;
        }

        public void setIncludeTotals(boolean includeTotals)
        {
            _includeTotals = includeTotals;
        }

        public boolean isIncludeSiteSummary()
        {
            return _includeSiteSummary;
        }

        public void setIncludeSiteSummary(boolean includeSiteSummary)
        {
            _includeSiteSummary = includeSiteSummary;
        }
    }

    @RequiresPermission(LaboratoryAdminPermission.class)
    @CSRF
    public class SetDemographicsSourcesAction extends ApiAction<SetDataSourcesForm>
    {
        public ApiResponse execute(SetDataSourcesForm form, BindException errors)
        {
            Map<String, Object> results = new HashMap<>();

            if (getContainer().isWorkbookOrTab())
            {
                errors.reject(ERROR_MSG, "Demographics sources cannot be set at the workbook or tab level");
                return null;
            }

            LaboratoryServiceImpl service = (LaboratoryServiceImpl)LaboratoryServiceImpl.get();

            if (form.getTables() == null)
            {
                errors.reject(ERROR_MSG, "Form did not contain any table information");
                return null;
            }

            Set<DemographicsSource> sources = new HashSet<DemographicsSource>();

            JSONArray json = new JSONArray(form.getTables());
            for (JSONObject obj : json.toJSONObjectArray())
            {
                String containerId = obj.containsKey("containerId") ? StringUtils.trimToNull(obj.getString("containerId")) : null;
                String schemaName = obj.getString("schemaName");
                String queryName = obj.getString("queryName");
                String targetColumn = obj.getString("targetColumn");
                String label = obj.getString("label");

                Container c = containerId == null ? getContainer() : ContainerManager.getForId(containerId);
                if (!c.hasPermission(getUser(), ReadPermission.class))
                {
                    errors.reject(ERROR_MSG, "User does not have read permission on container " + c.getPath());
                    return null;
                }

                sources.add(DemographicsSource.getFromParts(getContainer(), getUser(), label, containerId, schemaName, queryName, targetColumn));
            }

            try
            {
                service.setDemographicsSources(getContainer(), getUser(), sources);
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            results.put("success", true);

            return new ApiSimpleResponse(results);
        }
    }

    @RequiresPermission(LaboratoryAdminPermission.class)
    @CSRF
    public class SetAdditionalDataSourcesAction extends ApiAction<SetDataSourcesForm>
    {
        public ApiResponse execute(SetDataSourcesForm form, BindException errors)
        {
            Map<String, Object> results = new HashMap<>();

            if (getContainer().isWorkbookOrTab())
            {
                errors.reject(ERROR_MSG, "Data sources cannot be set at the workbook or tab level");
                return null;
            }

            LaboratoryServiceImpl service = (LaboratoryServiceImpl)LaboratoryServiceImpl.get();

            if (form.getTables() == null)
            {
                errors.reject(ERROR_MSG, "Form did not contain any table information");
                return null;
            }

            Set<AdditionalDataSource> sources = new HashSet<AdditionalDataSource>();

            JSONArray json = new JSONArray(form.getTables());
            for (JSONObject obj : json.toJSONObjectArray())
            {
                String containerId = obj.containsKey("containerId") ? StringUtils.trimToNull(obj.getString("containerId")) : null;
                String schemaName = StringUtils.trimToNull(obj.getString("schemaName"));
                String queryName = StringUtils.trimToNull(obj.getString("queryName"));
                String reportCategory = StringUtils.trimToNull(obj.getString("reportCategory"));
                String itemType = StringUtils.trimToNull(obj.getString("itemType"));
                String label = StringUtils.trimToNull(obj.getString("label"));
                String subjectFieldKey = StringUtils.trimToNull(obj.getString("subjectFieldKey"));
                String sampleDateFieldKey = StringUtils.trimToNull(obj.getString("sampleDateFieldKey"));
                boolean importIntoWorkbooks = obj.containsKey("importIntoWorkbooks") ? obj.getBoolean("importIntoWorkbooks") : false;

                if (label == null || queryName == null || schemaName == null)
                {
                    errors.reject(ERROR_MSG, "Must contain a label, schemaName and queryName");
                    return null;
                }

                Container c = containerId == null ? getContainer() : ContainerManager.getForId(containerId);
                if (c == null)
                {
                    errors.reject(ERROR_MSG, "Unknown container: " + containerId);
                    return null;
                }

                if (!c.hasPermission(getUser(), ReadPermission.class))
                {
                    //NOTE: should i really do this check?  it will get disabled whenever we query it
                    errors.reject(ERROR_MSG, "User does not have read permission on container " + c.getPath());
                    return null;
                }

                sources.add(AdditionalDataSource.getFromParts(getContainer(), getUser(), itemType, label, containerId, schemaName, queryName, reportCategory, importIntoWorkbooks, subjectFieldKey, sampleDateFieldKey));
            }

            service.setAdditionalDataSources(getContainer(), getUser(), sources);
            results.put("success", true);

            return new ApiSimpleResponse(results);
        }
    }

    public static class SetDataSourcesForm
    {
        String _tables;

        public String getTables()
        {
            return _tables;
        }

        public void setTables(String tables)
        {
            _tables = tables;
        }
    }

    @RequiresPermission(LaboratoryAdminPermission.class)
    @CSRF
    public class SetUrlDataSourcesAction extends ApiAction<SetUrlDataSourcesForm>
    {
        public ApiResponse execute(SetUrlDataSourcesForm form, BindException errors)
        {
            Map<String, Object> results = new HashMap<>();

            if (getContainer().isWorkbookOrTab())
            {
                errors.reject(ERROR_MSG, "URL sources cannot be set at the workbook or tab level");
                return null;
            }

            LaboratoryServiceImpl service = (LaboratoryServiceImpl)LaboratoryServiceImpl.get();

            if (form.getSources() == null)
            {
                errors.reject(ERROR_MSG, "Form did not contain any URL information");
                return null;
            }

            Set<URLDataSource> sources = new HashSet<URLDataSource>();

            JSONArray json = new JSONArray(form.getSources());
            for (JSONObject obj : json.toJSONObjectArray())
            {
                String itemType = obj.getString("itemType");
                String label = obj.getString("label");
                String url = obj.getString("urlExpression");

                sources.add(URLDataSource.getFromParts(itemType, label, url));
            }

            service.setURLDataSources(getContainer(), getUser(), sources);
            results.put("success", true);

            return new ApiSimpleResponse(results);
        }
    }

    public static class SetUrlDataSourcesForm
    {
        String _sources;

        public String getSources()
        {
            return _sources;
        }

        public void setSources(String sources)
        {
            _sources = sources;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @CSRF
    public class GetAssayImportHeadersAction extends ApiAction<AssayImportHeadersForm>
    {
        public ApiResponse execute(AssayImportHeadersForm form, BindException errors)
        {
            Map<String, Object> results = new HashMap<>();

            if (form.getProtocol() == null)
            {
                errors.reject(ERROR_MSG, "Must provide a protocol Id");
                return new ApiSimpleResponse(results);
            }

            ExpProtocol protocol = ExperimentService.get().getExpProtocol(form.getProtocol());
            if (protocol == null)
            {
                errors.reject(ERROR_MSG, "Protocol not found: " + form.getProtocol());
                return new ApiSimpleResponse(results);
            }

            if (form.getImportMethod() == null)
            {
                errors.reject(ERROR_MSG, "Must provide an importMethod");
                return new ApiSimpleResponse(results);
            }

            AssayProvider ap = AssayService.get().getProvider(protocol);
            AssayDataProvider adp = LaboratoryService.get().getDataProviderForAssay(ap);
            AssayImportMethod method = adp.getImportMethodByName(form.getImportMethod());
            if (method == null)
            {
                errors.reject(ERROR_MSG, "Unknown import method: " + form.getImportMethod());
                return new ApiSimpleResponse(results);
            }

            results.put("columnNames", method.getImportColumns(getViewContext(), protocol));

            return new ApiSimpleResponse(results);
        }
    }

    @RequiresPermission(LaboratoryAdminPermission.class)
    @CSRF
    public class SetItemVisibilityAction extends ApiAction<JsonDataForm>
    {
        public ApiResponse execute(JsonDataForm form, BindException errors)
        {
            if (form.getJsonData() == null)
            {
                errors.reject(ERROR_MSG, "No JSON data provided");
                return null;
            }
            if (getContainer().isWorkbook())
            {
                errors.reject(ERROR_MSG, "Item visibility can only be set on the project or folder level, not in workbooks");
                return null;
            }

            Map<String, Object> results = new HashMap<>();
            PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(getContainer(), NavItem.PROPERTY_CATEGORY, true);
            map.clear();

            JSONObject json = new JSONObject(form.getJsonData());
            Set<Module> activeModules = getContainer().getActiveModules();
            Set<Module> toActivate = new HashSet<Module>();
            for (String key : json.keySet())
            {
                String providerName = AbstractNavItem.inferDataProviderNameFromKey(key);
                DataProvider provider = LaboratoryService.get().getDataProvider(providerName);

                //for some types, no DataProvider, was explicitly registered, such as many assays
                //in these cases we cannot infer the owning module.
                if (provider != null && provider.getOwningModule() != null)
                {
                    if (!activeModules.contains(provider.getOwningModule()))
                        toActivate.add(provider.getOwningModule());
                }

                map.put(key, json.getString(key));
            }

            if(toActivate.size() > 0)
            {
                toActivate.addAll(activeModules);
                getContainer().setActiveModules(toActivate);
            }

            map.save();

            results.put("success", true);
            return new ApiSimpleResponse(results);
        }
    }


    @RequiresPermission(LaboratoryAdminPermission.class)
    @CSRF
    public class SetItemDefaultViewAction extends ApiAction<JsonDataForm>
    {
        public ApiResponse execute(JsonDataForm form, BindException errors)
        {
            if (form.getJsonData() == null)
            {
                errors.reject(ERROR_MSG, "No JSON data provided");
                return null;
            }
            if (getContainer().isWorkbook())
            {
                errors.reject(ERROR_MSG, "Item views can only be set on the project or folder level, not in workbooks");
                return null;
            }

            Map<String, Object> results = new HashMap<>();
            PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(getContainer(), NavItem.VIEW_PROPERTY_CATEGORY, true);

            JSONObject json = new JSONObject(form.getJsonData());
            for (String key : json.keySet())
            {
                map.put(key, json.getString(key));
            }

            map.save();

            results.put("success", true);
            return new ApiSimpleResponse(results);
        }
    }


    @RequiresPermission(LaboratoryAdminPermission.class)
    @CSRF
    public class SetDataBrowserSettingsAction extends ApiAction<JsonDataForm>
    {
        public ApiResponse execute(JsonDataForm form, BindException errors)
        {
            if (form.getJsonData() == null)
            {
                errors.reject(ERROR_MSG, "No JSON data provided");
                return null;
            }
            if (getContainer().isWorkbook())
            {
                errors.reject(ERROR_MSG, "Data browser settings can only be customized on the project or folder level, not in workbooks");
                return null;
            }

            Map<String, Object> results = new HashMap<>();

            PropertyManager.PropertyMap propMap = PropertyManager.getWritableProperties(getContainer(), TabbedReportItem.OVERRIDES_PROP_KEY, true);

            List<TabbedReportItem> tabbedReports = LaboratoryService.get().getTabbedReportItems(getContainer(), getUser());
            Map<String, TabbedReportItem> reportMap = new HashMap<String, TabbedReportItem>();
            for (TabbedReportItem item : tabbedReports)
            {
                reportMap.put(TabbedReportItem.getOverridesPropertyKey(item), item);
            }

            JSONObject json = new JSONObject(form.getJsonData());
            for (String key : json.keySet())
            {
                JSONObject toSave= new JSONObject();

                TabbedReportItem ti = reportMap.get(key);
                if (ti == null)
                    continue;

                JSONObject props = json.getJSONObject(key);

                String label = StringUtils.trimToNull(props.getString("label"));
                if (label != null && !ti.getLabel().equals(label))
                    toSave.put("label", label);

                String reportCategory = StringUtils.trimToNull(props.getString("reportCategory"));
                if (reportCategory != null && !ti.getReportCategory().equals(reportCategory))
                    toSave.put("reportCategory", reportCategory);

                if (toSave.keySet().size() > 0)
                    propMap.put(key, toSave.toString());
                else if (propMap.containsKey(key))
                    propMap.remove(key);
            }

            propMap.save();

            results.put("success", true);
            return new ApiSimpleResponse(results);
        }
    }


    @RequiresPermission(LaboratoryAdminPermission.class)
    @CSRF
    public class SaveAssayDefaultsAction extends ApiAction<JsonDataForm>
    {
        public ApiResponse execute(JsonDataForm form, BindException errors)
        {
            if (form.getJsonData() == null)
            {
                errors.reject(ERROR_MSG, "No JSON data provided");
                return null;
            }
            if (getContainer().isWorkbook())
            {
                errors.reject(ERROR_MSG, "Assay defaults can only be set on the project or folder level, not in workbooks");
                return null;
            }

            Map<String, Object> results = new HashMap<>();
            PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(getContainer(), AbstractAssayDataProvider.PROPERTY_CATEGORY, true);

            JSONObject json = new JSONObject(form.getJsonData());
            for (String key : json.keySet())
            {
                map.put(key, json.getString(key));
            }

            map.save();

            results.put("success", true);
            return new ApiSimpleResponse(results);
        }
    }

    @RequiresPermission(ReadPermission.class)
    @CSRF
    public class GetDataItemsAction extends ApiAction<GetDataItemsForm>
    {
        public ApiResponse execute(GetDataItemsForm form, BindException errors)
        {
            Map<String, Object> results = new HashMap<>();

            if (form.getTypes() == null || ArrayUtils.contains(form.getTypes(), LaboratoryService.NavItemCategory.samples.name()))
            {
                List<JSONObject> json = new ArrayList<JSONObject>();
                for (NavItem item : LaboratoryService.get().getSampleItems(getContainer(), getUser()))
                {
                    ensureModuleActive(item);

                    if (form.isIncludeAll() || item.isVisible(getContainer(), getUser()))
                        json.add(item.toJSON(getContainer(), getUser()));
                }
                results.put(LaboratoryService.NavItemCategory.samples.name(), json);
            }

            if (form.getTypes() == null || ArrayUtils.contains(form.getTypes(), LaboratoryService.NavItemCategory.data.name()))
            {
                List<JSONObject> json = new ArrayList<JSONObject>();
                for (NavItem item : LaboratoryService.get().getDataItems(getContainer(), getUser()))
                {
                    ensureModuleActive(item);

                    if (form.isIncludeAll() || item.isVisible(getContainer(), getUser()))
                        json.add(item.toJSON(getContainer(), getUser()));
                }
                results.put(LaboratoryService.NavItemCategory.data.name(), json);
            }

            if (form.getTypes() == null || ArrayUtils.contains(form.getTypes(), LaboratoryService.NavItemCategory.settings.name()))
            {
                List<JSONObject> json = new ArrayList<JSONObject>();
                for (NavItem item : LaboratoryService.get().getSettingsItems(getContainer(), getUser()))
                {
                    ensureModuleActive(item);

                    if (form.isIncludeAll() || item.isVisible(getContainer(), getUser()))
                        json.add(item.toJSON(getContainer(), getUser()));
                }
                results.put(LaboratoryService.NavItemCategory.settings.name(), json);
            }

            if (form.getTypes() == null || ArrayUtils.contains(form.getTypes(), LaboratoryService.NavItemCategory.reports.name()))
            {
                List<JSONObject> json = new ArrayList<JSONObject>();
                for (NavItem item : LaboratoryService.get().getReportItems(getContainer(), getUser()))
                {
                    ensureModuleActive(item);

                    if (form.isIncludeAll() || item.isVisible(getContainer(), getUser()))
                        json.add(item.toJSON(getContainer(), getUser()));
                }
                results.put(LaboratoryService.NavItemCategory.reports.name(), json);
            }

            if (form.getTypes() == null || ArrayUtils.contains(form.getTypes(), LaboratoryService.NavItemCategory.tabbedReports.name()))
            {
                List<JSONObject> json = new ArrayList<JSONObject>();
                for (NavItem item : LaboratoryService.get().getTabbedReportItems(getContainer(), getUser()))
                {
                    ensureModuleActive(item);

                    if (form.isIncludeAll() || item.isVisible(getContainer(), getUser()))
                        json.add(item.toJSON(getContainer(), getUser()));
                }
                results.put(LaboratoryService.NavItemCategory.tabbedReports.name(), json);
            }

            if (form.getTypes() == null || ArrayUtils.contains(form.getTypes(), LaboratoryService.NavItemCategory.misc.name()))
            {
                List<JSONObject> json = new ArrayList<JSONObject>();
                for (NavItem item : LaboratoryService.get().getMiscItems(getContainer(), getUser()))
                {
                    ensureModuleActive(item);

                    if (form.isIncludeAll() || item.isVisible(getContainer(), getUser()))
                        json.add(item.toJSON(getContainer(), getUser()));
                }
                results.put(LaboratoryService.NavItemCategory.misc.name(), json);
            }

            results.put("success", true);

            return new ApiSimpleResponse(results);
        }

        private void ensureModuleActive(NavItem item)
        {
            if (getContainer().equals(ContainerManager.getSharedContainer()))
            {
                Module m = item.getDataProvider().getOwningModule();
                if (m != null)
                {
                    Set<Module> active = getContainer().getActiveModules();
                    if (!active.contains(m))
                    {
                        Set<Module> newActive = new HashSet<Module>();
                        newActive.addAll(active);
                        newActive.add(m);

                        _log.info("Enabling module " + m.getName() + " in shared container since getDataItems was called");

                        getContainer().setActiveModules(newActive);
                    }
                }

            }
        }
    }

    @RequiresPermission(ReadPermission.class)
    @CSRF
    public class GetImportMethodsAction extends ApiAction<ImportMethodsForm>
    {
        public ApiResponse execute(ImportMethodsForm form, BindException errors)
        {
            Map<String, Object> results = new HashMap<>();
            AssayProvider ap = null;
            List<ExpProtocol> protocols = new ArrayList<ExpProtocol>();
            if (form.getAssayId() != null)
            {
                protocols.add(ExperimentService.get().getExpProtocol(form.getAssayId()));
                ap = AssayService.get().getProvider(protocols.get(0));
            }
            else if (form.getAssayType() != null)
            {
                ap = AssayService.get().getProvider(form.getAssayType());
            }

            List<AssayProvider> providers = new ArrayList<AssayProvider>();
            if (ap == null)
            {
                providers.addAll(AssayService.get().getAssayProviders());
            }
            else
            {
                providers.add(ap);
            }

            JSONArray providerJson = new JSONArray();
            for (AssayProvider provider : providers)
            {
                JSONObject json = new JSONObject();
                AssayDataProvider adp = LaboratoryService.get().getDataProviderForAssay(provider);
                List<ExpProtocol> protocolsForProvider = new ArrayList<ExpProtocol>();
                if (protocols.size() == 0)
                    protocolsForProvider.addAll(adp.getProtocols(getContainer()));
                else
                    protocolsForProvider.addAll(protocols);

                JSONArray protocolJson = new JSONArray();
                for (ExpProtocol protocol : protocolsForProvider)
                {
                    JSONObject protocolObj = new JSONObject();
                    List<JSONObject> methods = new ArrayList<JSONObject>();
                    for (AssayImportMethod m : adp.getImportMethods())
                    {
                        methods.add(m.toJson(getViewContext(), protocol));
                    }
                    protocolObj.put("importMethods", methods);
                    protocolObj.put("rowId", protocol.getRowId());
                    protocolObj.put("name", protocol.getName());
                    protocolObj.put("container", protocol.getContainer());
                    protocolObj.put("containerPath", protocol.getContainer().getPath());
                    protocolObj.put("defaultImportMethod", adp.getDefaultImportMethodName(getContainer(), getUser(), protocol.getRowId()));
                    protocolJson.put(protocolObj);
                }
                json.put("name", adp.getName());
                json.put("key", adp.getKey());
                json.put("protocols", protocolJson);
                json.put("templateMetadata", adp.getTemplateMetadata(getViewContext()));
                providerJson.put(json);
            }
            results.put("providers", providerJson);
            results.put("success", true);

            return new ApiSimpleResponse(results);
        }
    }

    @RequiresPermission(ReadPermission.class)
    @CSRF
    public class GetDataSummaryAction extends ApiAction<DataSummaryForm>
    {
        public ApiResponse execute(DataSummaryForm form, BindException errors)
        {
            Map<String, Object> results = new HashMap<>();

            Set<DataProvider> providers = new HashSet<DataProvider>();
            if (form.getDataProviders() == null)
            {
                providers.addAll(LaboratoryService.get().getDataProviders());
            }
            else
            {
                for (String provider : form.getDataProviders())
                {
                    DataProvider dp = LaboratoryService.get().getDataProvider(provider);
                    if (dp == null)
                    {
                        results.put("success", false);
                        results.put("exception", "Unknown data provider: " + provider);
                        return new ApiSimpleResponse(results);
                    }
                    providers.add(dp);
                }
            }

            Map<String, List<NavItem>> items = new HashMap<String, List<NavItem>>();
            for (DataProvider dp : providers)
            {
                for (NavItem item : dp.getSummary(getContainer(), getUser()))
                {
                    List<NavItem> list = items.get(item.getReportCategory());
                    if (list == null)
                        list = new ArrayList<>();

                    list.add(item);

                    items.put(item.getReportCategory(), list);
                }
            }

            for (List<NavItem> list : items.values())
            {
                LaboratoryService.get().sortNavItems(list);
            }

            for (String key : items.keySet())
            {
                List<JSONObject> jsonItems = new ArrayList<JSONObject>();
                for (NavItem item : items.get(key))
                {
                    jsonItems.add(item.toJSON(getContainer(), getUser()));
                }
                results.put(key, jsonItems);
            }

            return new ApiSimpleResponse(results);
        }
    }

    public static class DataSummaryForm
    {
        String[] dataProviders;

        public String[] getDataProviders()
        {
            return dataProviders;
        }

        public void setDataProviders(String[] dataProviders)
        {
            this.dataProviders = dataProviders;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @CSRF
    public class GetSubjectIdSummaryAction extends ApiAction<SubjectSummaryForm>
    {
        public ApiResponse execute(SubjectSummaryForm form, BindException errors)
        {
            if (form.getSubjectIds() == null)
            {
                errors.reject(ERROR_MSG, "Must provide a subjectId");
                return null;
            }

            Set<DataProvider> providers = new HashSet<>();
            if (form.getDataProviders() == null)
            {
                providers.addAll(LaboratoryService.get().getDataProviders());
            }
            else
            {
                for (String provider : form.getDataProviders())
                {
                    DataProvider dp = LaboratoryService.get().getDataProvider(provider);
                    if (dp == null)
                    {
                        Map<String, Object> results = new HashMap<>();
                        results.put("success", false);
                        results.put("exception", "Unknown data provider: " + provider);
                        return new ApiSimpleResponse(results);
                    }
                    providers.add(dp);
                }
            }

            Map<String, Object> results = new HashMap<String, Object>();
            for (String subjectId : form.getSubjectIds())
            {
                Map<String, List<NavItem>> items = new HashMap<String, List<NavItem>>();

                for (DataProvider dp : providers)
                {
                    for (NavItem item : dp.getSubjectIdSummary(getContainer(), getUser(), subjectId))
                    {
                        List<NavItem> list = items.get(item.getReportCategory());
                        if (list == null)
                            list = new ArrayList<>();

                        list.add(item);

                        items.put(item.getReportCategory(), list);
                    }
                }

                for (List<NavItem> list : items.values())
                {
                    LaboratoryService.get().sortNavItems(list);
                }

                Map<String, List<JSONObject>> finalItems = new HashMap<>();
                for (String key : items.keySet())
                {
                    List<JSONObject> jsonItems = new ArrayList<>();
                    for (NavItem item : items.get(key))
                    {
                        jsonItems.add(item.toJSON(getContainer(), getUser()));
                    }
                    finalItems.put(key, jsonItems);
                }

                results.put(subjectId, finalItems);
            }

            Map<String, Object> apiResult = new HashMap<>();
            apiResult.put("results", results);
            apiResult.put("success", true);

            return new ApiSimpleResponse(apiResult);
        }
    }

    public static class SubjectSummaryForm
    {
        private String[] _subjectIds;
        private String[] dataProviders;

        public String[] getDataProviders()
        {
            return dataProviders;
        }

        public void setDataProviders(String[] dataProviders)
        {
            this.dataProviders = dataProviders;
        }

        public String[] getSubjectIds()
        {
            return _subjectIds;
        }

        public void setSubjectIds(String[] subjectIds)
        {
            _subjectIds = subjectIds;
        }
    }

    public static class ImportMethodsForm
    {
        String assayType;
        Integer assayId;

        public String getAssayType()
        {
            return assayType;
        }

        public void setAssayType(String assayType)
        {
            this.assayType = assayType;
        }

        public Integer getAssayId()
        {
            return assayId;
        }

        public void setAssayId(Integer assayId)
        {
            this.assayId = assayId;
        }
    }

    public static class JsonDataForm
    {
        private String jsonData;

        public String getJsonData()
        {
            return jsonData;
        }

        public void setJsonData(String jsonData)
        {
            this.jsonData = jsonData;
        }
    }

    public static class ProcessAssayForm extends AbstractFileUploadAction.FileUploadForm
    {
        private String _jsonData;
        private String _importMethod;
        private String _title;
        private int _templateId;
        private boolean _doSave = false;
        private Integer _labkeyAssayId;

        public String getJsonData()
        {
            return _jsonData;
        }

        public void setJsonData(String jsonData)
        {
            _jsonData = jsonData;
        }

        public boolean isDoSave()
        {
            return _doSave;
        }

        public void setDoSave(boolean doSave)
        {
            _doSave = doSave;
        }

        public String getImportMethod()
        {
            return _importMethod;
        }

        public void setImportMethod(String importMethod)
        {
            _importMethod = importMethod;
        }

        public Integer getLabkeyAssayId()
        {
            return _labkeyAssayId;
        }

        public void setLabkeyAssayId(Integer labkeyAssayId)
        {
            _labkeyAssayId = labkeyAssayId;
        }

        public String getTitle()
        {
            return _title;
        }

        public void setTitle(String title)
        {
            _title = title;
        }

        public int getTemplateId()
        {
            return _templateId;
        }

        public void setTemplateId(int templateId)
        {
            _templateId = templateId;
        }
    }

    public static class GetDataItemsForm
    {
        private String[] _types;
        private boolean _includeAll = false;

        public String[] getTypes()
        {
            return _types;
        }

        public void setTypes(String[] types)
        {
            _types = types;
        }

        public boolean isIncludeAll()
        {
            return _includeAll;
        }

        public void setIncludeAll(boolean includeAll)
        {
            _includeAll = includeAll;
        }
    }

    public static class LaboratoryUrlsImpl implements LaboratoryUrls
    {
        @Override
        public ActionURL getSearchUrl(Container c, String schemaName, String queryName)
        {
            ActionURL url = new ActionURL("query", "searchPanel", c);
            url.addParameter("schemaName", schemaName);
            url.addParameter("queryName", queryName);
            return url;
        }

        @Override
        public ActionURL getImportUrl(Container c, User u, String schemaName, String queryName)
        {
            UserSchema us = QueryService.get().getUserSchema(u, c, schemaName);
            if (us == null)
                return null;
            TableInfo ti = us.getTable(queryName);
            if (ti == null)
                return null;
            List<String> pks = ti.getPkColumnNames();
            if (pks.size() == 0)
                return null;

            ActionURL url = new ActionURL("query", "importData", c);
            url.addParameter("schemaName", schemaName);
            url.addParameter("query.queryName", queryName);
            url.addParameter("keyField", pks.get(0));
            return url;
        }

        @Override
        public ActionURL getAssayRunTemplateUrl(Container c, ExpProtocol protocol)
        {
            ActionURL url = new ActionURL(LaboratoryModule.CONTROLLER_NAME, "prepareExptRun", c);
            url.addParameter("assayId", protocol.getRowId());
            return url;
        }

        @Override
        public ActionURL getViewAssayRunTemplateUrl(Container c, User u, ExpProtocol protocol)
        {
            ActionURL url = QueryService.get().urlFor(u, c, QueryAction.executeQuery, LaboratoryModule.SCHEMA_NAME, "assay_run_templates");
            url.addParameter("query.assayId~eq", protocol.getRowId());
            url.addParameter("query.runid~isblank", "");
            return url;
        }
    }

    public static class EnsureAssayFieldsForm extends ReturnUrlForm
    {
        String providerName;
        boolean renameConflicts = false;

        public String getProviderName()
        {
            return providerName;
        }

        public void setProviderName(String providerName)
        {
            this.providerName = providerName;
        }

        public boolean isRenameConflicts()
        {
            return renameConflicts;
        }

        public void setRenameConflicts(boolean renameConflicts)
        {
            this.renameConflicts = renameConflicts;
        }
    }

    public static class AssayImportHeadersForm
    {
        private Integer _protocol;
        private String _importMethod;

        public String getImportMethod()
        {
            return _importMethod;
        }

        public void setImportMethod(String importMethod)
        {
            _importMethod = importMethod;
        }

        public Integer getProtocol()
        {
            return _protocol;
        }

        public void setProtocol(Integer protocol)
        {
            _protocol = protocol;
        }
    }

    @RequiresPermission(AdminPermission.class)
    @CSRF
    public class MigrateWorkbooksAction extends ApiAction<MigrateWorkbooksForm>
    {
        public ApiResponse execute(MigrateWorkbooksForm form, BindException errors) throws Exception
        {
            if (getContainer().isWorkbook())
            {
                errors.reject(ERROR_MSG, "Cannot be run on workbooks");
                return null;
            }

            //find current workbook #


            //find all workbooks where workbookId doesnt match LK ID
            TreeMap<Integer, Container> toFix = new TreeMap<>();
            for (Container c : ContainerManager.getChildren(getContainer()))
            {
                if (c.isWorkbook())
                {
                    WorkbookModel w = LaboratoryManager.get().getWorkbookModel(c);
                    if (w != null)
                    {
                        _log.warn("workbook model not found for: " + c.getName());
                    }
                    else if (!c.getName().equals(w.getWorkbookId().toString()))
                    {
                        toFix.put(w.getWorkbookId(), c);
                    }
                }
            }

            _log.info("workbooks to migrate: " + toFix.size());
            Set<Integer> list = form.getReverseOrder() == true ? toFix.keySet() : toFix.descendingKeySet();
            for (Integer id : list)
            {
                Container wb = toFix.get(id);
                Container target = ContainerManager.getChild(wb.getParent(), id.toString());
                if (target != null)
                {
                    _log.warn("target workbook exists, skipping: " + id);
                }
                else
                {
                    ContainerManager.rename(wb, getUser(), id.toString());
                }
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class MigrateWorkbooksForm
    {
        private Boolean reverseOrder = false;

        public Boolean getReverseOrder()
        {
            return reverseOrder;
        }

        public void setReverseOrder(Boolean reverseOrder)
        {
            this.reverseOrder = reverseOrder;
        }
    }
}
