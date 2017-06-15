/*
 * Copyright (c) 2014 LabKey Corporation
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

package org.labkey.jbrowse;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.Results;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.StopIteratingException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.jbrowse.model.Database;
import org.labkey.jbrowse.pipeline.JBrowseSessionPipelineJob;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JBrowseController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(JBrowseController.class);
    public static final String NAME = "jbrowse";

    public JBrowseController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminPermission.class)
    @CSRF
    public class GetSettingsAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();
            String[] etlConfigKeys = {JBrowseManager.JBROWSE_BIN};

            resultProperties.put("configKeys", etlConfigKeys);
            resultProperties.put("config", PropertyManager.getProperties(JBrowseManager.CONFIG_PROPERTY_DOMAIN));

            return new ApiSimpleResponse(resultProperties);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    @CSRF
    public class SetSettingsAction extends ApiAction<SettingsForm>
    {
        public ApiResponse execute(SettingsForm form, BindException errors)
        {
            if (!getContainer().isRoot())
            {
                errors.reject(ERROR_MSG, "JBrowse settings can only be set at the site level");
                return null;
            }

            Map<String, String> configMap = new HashMap<>();
            if (form.getJbrowseBinDir() != null)
                configMap.put(JBrowseManager.JBROWSE_BIN, form.getJbrowseBinDir());

            try
            {
                JBrowseManager.get().saveSettings(configMap);
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class SettingsForm
    {
        private String _jbrowseBinDir;

        public String getJbrowseBinDir()
        {
            return _jbrowseBinDir;
        }

        public void setJbrowseBinDir(String jbrowseBinDir)
        {
            _jbrowseBinDir = jbrowseBinDir;
        }
    }

    @RequiresPermission(InsertPermission.class)
    @CSRF
    public class CreateDataBaseAction extends ApiAction<DatabaseForm>
    {
        public ApiResponse execute(DatabaseForm form, BindException errors)
        {
            if (form.getName() == null || form.getLibraryId() == null)
            {
                errors.reject(ERROR_MSG, "Must provide the database name and reference genome");
                return null;
            }

            try
            {
                JBrowseManager.get().createDatabase(getContainer(), getUser(), form.getName(), form.getDescription(), form.getLibraryId(), form.getTrackIdList(), form.getOutputFileIdList(), form.getIsPrimaryDb(), form.getShouldCreateOwnIndex(), form.getIsTemporary());
            }
            catch (IOException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermission(InsertPermission.class)
    @CSRF
    public class AddDatabaseMemberAction extends ApiAction<DatabaseForm>
    {
        public ApiResponse execute(DatabaseForm form, BindException errors)
        {
            if (form.getDatabaseId() == null)
            {
                errors.reject(ERROR_MSG, "Must provide the database Id");
                return null;
            }

            if (form.getTrackIdList().isEmpty() && form.getOutputFileIdList().isEmpty())
            {
                errors.reject(ERROR_MSG, "Must provide either a list track or file IDs to include");
                return null;
            }

            try
            {
                JBrowseManager.get().addDatabaseMember(getContainer(), getUser(), form.getDatabaseId(), form.getTrackIdList(), form.getOutputFileIdList());
            }
            catch (IOException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class DatabaseForm
    {
        String _databaseId;
        String _name;
        String _description;
        Integer[] _trackIds;
        Integer _libraryId;
        Integer[] _outputFileIds;
        Boolean _isPrimaryDb;
        Boolean _shouldCreateOwnIndex;
        Boolean _isTemporary;

        public String getDatabaseId()
        {
            return _databaseId;
        }

        public void setDatabaseId(String databaseId)
        {
            _databaseId = databaseId;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        @NotNull
        public List<Integer> getTrackIdList()
        {
            if (_trackIds == null)
                return Collections.emptyList();

            List<Integer> ret = new ArrayList<>();
            for (Integer o : _trackIds)
            {
                ret.add(o);
            }

            return ret;
        }

        public void setTrackIds(Integer[] trackIds)
        {
            _trackIds = trackIds;
        }

        public Integer getLibraryId()
        {
            return _libraryId;
        }

        public void setLibraryId(Integer libraryId)
        {
            _libraryId = libraryId;
        }

        @NotNull
        public List<Integer> getOutputFileIdList()
        {
            if (_outputFileIds == null)
                return Collections.emptyList();

            List<Integer> ret = new ArrayList<>();
            for (Integer o : _outputFileIds)
            {
                ret.add(o);
            }

            return ret;
        }

        public void setOutputFileIds(Integer[] outputFileIds)
        {
            _outputFileIds = outputFileIds;
        }

        public Boolean getIsPrimaryDb()
        {
            return _isPrimaryDb == null ? false : _isPrimaryDb;
        }

        public void setIsPrimaryDb(Boolean isPrimaryDb)
        {
            _isPrimaryDb = isPrimaryDb;
        }

        public Boolean getShouldCreateOwnIndex()
        {
            return _shouldCreateOwnIndex == null ? false : _shouldCreateOwnIndex;
        }

        public void setShouldCreateOwnIndex(Boolean shouldCreateOwnIndex)
        {
            _shouldCreateOwnIndex = shouldCreateOwnIndex;
        }

        public Boolean getIsTemporary()
        {
            return _isTemporary == null ? false : _isTemporary;
        }

        public void setIsTemporary(Boolean isTemporary)
        {
            _isTemporary = isTemporary;
        }
    }

    @RequiresPermission(AdminPermission.class)
    @CSRF
    public class ReprocessResourcesAction extends ApiAction<ReprocessResourcesForm>
    {
        public ApiResponse execute(ReprocessResourcesForm form, BindException errors)
        {
            if ((form.getJsonFiles() == null || form.getJsonFiles().length == 0) && (form.getDatabaseIds() == null || form.getDatabaseIds().length == 0))
            {
                errors.reject("Must provide a list of sessions or JSON files to re-process");
                return null;
            }

            try
            {
                PipeRoot root = PipelineService.get().getPipelineRootSetting(getContainer());
                if (form.getJsonFiles() != null)
                {
                    PipelineService.get().queueJob(JBrowseSessionPipelineJob.refreshResources(getContainer(), getUser(), root, Arrays.asList(form.getJsonFiles())));
                }

                if (form.getDatabaseIds() != null)
                {
                    for (String databaseGuid : form.getDatabaseIds())
                    {
                        PipelineService.get().queueJob(JBrowseSessionPipelineJob.recreateDatabase(getContainer(), getUser(), root, databaseGuid));
                    }
                }

                return new ApiSimpleResponse("success", true);
            }
            catch (PipelineValidationException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }
        }
    }

    public static class ReprocessResourcesForm
    {
        String[] _jsonFiles;
        String[] _databaseIds;

        public String[] getJsonFiles()
        {
            return _jsonFiles;
        }

        public void setJsonFiles(String[] jsonFiles)
        {
            _jsonFiles = jsonFiles;
        }

        public String[] getDatabaseIds()
        {
            return _databaseIds;
        }

        public void setDatabaseIds(String[] databaseIds)
        {
            _databaseIds = databaseIds;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class BrowserAction extends SimpleViewAction<BrowserForm>
    {
        private String _title;

        @Override
        public ModelAndView getView(BrowserForm form, BindException errors) throws Exception
        {
            Database db = new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES), new SimpleFilter(FieldKey.fromString("objectid"), form.getDatabase()), null).getObject(Database.class);
            _title = db == null ? "Not Found" : db.getName();
            form.setPageTitle(_title);

            JspView<BrowserForm> view = new JspView<>("/org/labkey/jbrowse/view/browser.jsp", form);
            view.setTitle(_title);
            view.setHidePageTitle(true);
            view.setFrame(WebPartView.FrameType.NONE);
            getPageConfig().setTemplate(PageConfig.Template.None);

            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_title);
        }
    }

    public static class BrowserForm
    {
        private String _database;
        private String _pageTitle;

        public String getDatabase()
        {
            return _database;
        }

        public void setDatabase(String database)
        {
            _database = database;
        }

        public String getPageTitle()
        {
            return _pageTitle;
        }

        public void setPageTitle(String pageTitle)
        {
            _pageTitle = pageTitle;
        }
    }

    @RequiresPermission(AdminPermission.class)
    @CSRF
    public class ModifyAttributesAction extends ApiAction<SimpleApiJsonForm>
    {
        public ApiResponse execute(SimpleApiJsonForm form, BindException errors)
        {
            JSONArray jsonFiles = form.getJsonObject().getJSONArray("jsonFiles");
            Set<String> objectIds = new HashSet<>();
            for (Object o : jsonFiles.toArray())
            {
                objectIds.add(o.toString());
            }

            JSONObject attributes = form.getJsonObject().getJSONObject("attributes");

            final Map<Container, List<Map<String, Object>>> rows = new HashMap<>();
            TableSelector ts = new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_JSONFILES), PageFlowUtil.set("objectid", "container", "trackJson"), new SimpleFilter(FieldKey.fromString("objectid"), objectIds, CompareType.IN), null);
            ts.forEachResults(new Selector.ForEachBlock<Results>()
            {
                @Override
                public void exec(Results rs) throws SQLException, StopIteratingException
                {
                    Container c = ContainerManager.getForId(rs.getString(FieldKey.fromString("container")));
                    if (!c.hasPermission(getUser(), UpdatePermission.class))
                    {
                        throw new UnauthorizedException("User does not have permission to update records in the folder: " + c.getPath());
                    }

                    JSONObject json = rs.getString("trackJson") == null ? new JSONObject() : new JSONObject(rs.getString("trackJson"));
                    for (String key : attributes.keySet())
                    {
                        if (StringUtils.trimToNull(attributes.getString(key)) == null)
                        {
                            json.remove(key);
                        }
                        else
                        {
                            json.put(key, attributes.get(key));
                        }
                    }

                    if (!rows.containsKey(c))
                    {
                        rows.put(c, new ArrayList<>());
                    }

                    Map<String, Object> row = new CaseInsensitiveHashMap<>();
                    row.put("objectid", rs.getString("objectid"));
                    row.put("trackJson", json.isEmpty() ? null : json.toString());

                    rows.get(c).add(row);
                }
            });

            try (DbScope.Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
            {
                for (Container c : rows.keySet())
                {
                    TableInfo ti = QueryService.get().getUserSchema(getUser(), c, JBrowseSchema.NAME).getTable(JBrowseSchema.TABLE_JSONFILES);
                    List<Map<String, Object>> oldKeys = new ArrayList<>();
                    for (Map<String, Object> row : rows.get(c))
                    {
                        Map<String, Object> k = new CaseInsensitiveHashMap<>();
                        k.put("objectid", row.get("objectid"));
                        oldKeys.add(k);
                    }
                    ti.getUpdateService().updateRows(getUser(), c, rows.get(c), oldKeys, null, new HashMap<>());
                }

                transaction.commit();
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            return new ApiSimpleResponse("success", true);
        }
    }
}