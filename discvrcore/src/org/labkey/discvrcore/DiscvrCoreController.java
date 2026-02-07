/*
 * Copyright (c) 2020 LabKey Corporation
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

package org.labkey.discvrcore;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.discvrcore.annotation.UtilityAction;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.DOM;
import org.labkey.api.util.GUID;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import static org.labkey.api.util.DOM.Attribute.valign;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.cl;

public class DiscvrCoreController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(DiscvrCoreController.class);
    public static final String NAME = "discvrcore";

    private static final Logger _log = LogHelper.getLogger(DiscvrCoreController.class, "Messages from DISCVR Core Controller");

    public DiscvrCoreController()
    {
        setActionResolver(_actionResolver);
    }

    @UtilityAction(label = "Truncate Query Audit Log", description = "Provides a mechanism to truncate the query and dataset audit tables for a container")
    @RequiresPermission(AdminPermission.class)
    public static class TruncateQueryAuditLogAction extends ConfirmAction<Object>
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors) throws Exception
        {
            setTitle("Truncate Query/Dataset Audit Logs");

            return HtmlView.of("This will truncate the query and dataset audit logs for this container. Do you want to continue?");
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            UserSchema us = AuditLogService.getAuditLogSchema(getUser(), getContainer());
            for (String tableName : Arrays.asList("DatasetAuditEvent", "QueryUpdateAuditEvent"))
            {
                TableInfo ti = us.getTable(tableName);
                ti.getUpdateService().truncateRows(getUser(), getContainer(), null, null);
            }

            return true;
        }

        @Override
        public void validateCommand(Object o, Errors errors)
        {

        }

        @NotNull
        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class ShowUtilityActionsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object form, BindException errors)
        {
            Map<String, DOM.Renderable> items = new TreeMap<>();
            Collection<Module> modules = getContainer().isRoot() ? ModuleLoader.getInstance().getModules() : getContainer().getActiveModules();
            for (Module m : modules)
            {
                m.getControllerNameToClass().forEach((key, controllerCls) -> {
                    Arrays.stream(controllerCls.getDeclaredClasses()).filter(x -> x.isAnnotationPresent(UtilityAction.class)).forEach(x -> {
                        if (Controller.class.isAssignableFrom(x))
                        {
                            UtilityAction annot = x.getAnnotation(UtilityAction.class);

                            Class<? extends Controller> actionClass = (Class<? extends Controller>)x;
                            items.put(annot.label(), DOM.TR(
                                DOM.TD(at(valign,"top"), LinkBuilder.labkeyLink(annot.label(), new ActionURL(actionClass, getContainer())).build()),
                                DOM.TD(at(valign,"top"), annot.description())
                            ));
                        }
                    });
                });
            }

            return new HtmlView(DOM.TABLE(cl("labkey-data-region-legacy","labkey-show-borders"), items.values()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Utility & Management Actions");
        }
    }

    @UtilityAction(label = "Move Workbook", description = "This will move this workbook to the selected folder, renaming this workbook to match the series in that container.  Note: there are many reasons this can be problematic, so please do this with great care")
    @RequiresPermission(AdminPermission.class)
    public static class MoveWorkbookAction extends ConfirmAction<MoveWorkbookForm>
    {
        private Container _movedWb = null;

        @Override
        public void validateCommand(MoveWorkbookForm form, Errors errors)
        {

        }

        @Override
        public ModelAndView getConfirmView(MoveWorkbookForm form, BindException errors) throws Exception
        {
            if (!getContainer().isWorkbook())
            {
                errors.reject(ERROR_MSG, "This is only supported for workbooks");
                return new SimpleErrorView(errors);
            }

            String sb = "This will move this workbook to the selected folder, renaming this workbook to match the series in that container.  Note: there are many reasons this can be problematic, so please do this with great care<p>" +
                    "<input name=\"targetContainer\" type=\"text\"></input>";

            return new HtmlView(sb);
        }

        @Override
        public boolean handlePost(MoveWorkbookForm form, BindException errors) throws Exception
        {
            Container toMove = getContainer();
            if (!toMove.isWorkbook())
            {
                errors.reject(ERROR_MSG, "This is only supported for workbooks");
                return false;
            }

            if (StringUtils.trimToNull(form.getTargetContainer()) == null)
            {
                errors.reject(ERROR_MSG, "Must provide target container");
                return false;
            }

            Container target = ContainerManager.getForPath(StringUtils.trimToNull(form.getTargetContainer()));
            if (target == null)
            {
                target = ContainerManager.getForId(StringUtils.trimToNull(form.getTargetContainer()));
            }

            if (target == null)
            {
                errors.reject(ERROR_MSG, "Unknown container: " + form.getTargetContainer());
                return false;
            }

            if (target.isWorkbook())
            {
                errors.reject(ERROR_MSG, "Target cannot be a workbook: " + form.getTargetContainer());
                return false;
            }

            if (ContainerManager.isSystemContainer(target))
            {
                errors.reject(ERROR_MSG, "Cannot move to system containers: " + form.getTargetContainer());
                return false;
            }

            if (target.equals(toMove.getParent()))
            {
                errors.reject(ERROR_MSG, "Cannot move the workbook to its current parent: " + form.getTargetContainer());
                return false;
            }

            //NOTE: transaction causing problems for larger sites?
            //try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            //{
            //first rename workbook to make unique
            String tempName = new GUID().toString();
            int sortOrder = (int) DbSequenceManager.get(target, ContainerManager.WORKBOOK_DBSEQUENCE_NAME).next();
            _log.info("renaming workbook to in preparation for move from: " + toMove.getPath() + "  to: " + tempName);
            ContainerManager.rename(toMove, getUser(), tempName);
            toMove = ContainerManager.getForId(toMove.getId());

            //then move parent
            _log.info("moving workbook from: " + toMove.getPath() + "  to: " + target.getPath());
            ContainerManager.move(toMove, target, getUser());
            toMove = ContainerManager.getForId(toMove.getId());

            //finally move to correct name
            _log.info("renaming workbook from: " + toMove.getPath() + "  to: " + sortOrder);
            ContainerManager.rename(toMove, getUser(), String.valueOf(sortOrder));
            toMove.setSortOrder(sortOrder);
            new SqlExecutor(CoreSchema.getInstance().getSchema()).execute("UPDATE core.containers SET SortOrder = ? WHERE EntityId = ?", toMove.getSortOrder(), toMove.getId());
            toMove = ContainerManager.getForId(toMove.getId());

            //transaction.commit();
            _log.info("workbook move finished");

            _movedWb = toMove;
            //}

            return true;
        }

        @NotNull
        @Override
        public URLHelper getSuccessURL(MoveWorkbookForm moveWorkbookForm)
        {
            if (_movedWb == null)
                return getContainer().getStartURL(getUser());
            else
                return _movedWb.getStartURL(getUser());
        }
    }

    public static class MoveWorkbookForm
    {
        private String _targetContainer;

        public String getTargetContainer()
        {
            return _targetContainer;
        }

        public void setTargetContainer(String targetContainer)
        {
            _targetContainer = targetContainer;
        }
    }

    // This allows registration of this action without creating a dependency between laboratory and discvrcore
    @UtilityAction(label = "Set Table Increment Value", description = "This allows you to reset the current value for an auto-incrementing table")
    @RequiresPermission(AdminPermission.class)
    public class SetTableIncrementValueAction extends SimpleRedirectAction<Object>
    {
        @Override
        public URLHelper getRedirectURL(Object o) throws Exception
        {
            return DetailsURL.fromString("laboratory/setTableIncrementValue.view", getContainer()).getActionURL();
        }
    }

    // This allows registration of this action without creating a dependency between laboratory and discvrcore
    @UtilityAction(label = "Manage File Roots", description = "This standalone file root management action can be used on folder types that do not support the normal 'Manage Folder' UI.")
    @RequiresPermission(AdminPermission.class)
    public class ManageFileRootAction extends SimpleRedirectAction<Object>
    {
        @Override
        public URLHelper getRedirectURL(Object o) throws Exception
        {
            return DetailsURL.fromString("admin/manageFileRoot.view", getContainer()).getActionURL();
        }
    }

    @UtilityAction(label = "Add Custom Core.Container Indexes", description = "Provides a mechanism to truncate the query and dataset audit tables for a container")
    @RequiresPermission(AdminPermission.class)
    public static class AddCustomIndexesAction extends ConfirmAction<Object>
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors) throws Exception
        {
            setTitle("Add Custom Core.Container Indexes");

            return HtmlView.of("This action will add custom indexes to core.contains. Only do this if you are absolutely certain about the consequences. Do you want to continue?");
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            return DiscvrCoreManager.get().addCoreContainersIndexes();
        }

        @Override
        public void validateCommand(Object o, Errors errors)
        {

        }

        @NotNull
        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }
    }
}
