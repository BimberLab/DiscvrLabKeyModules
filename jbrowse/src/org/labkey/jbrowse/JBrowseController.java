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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.jbrowse.pipeline.JBrowseSessionPipelineJob;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JBrowseController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(JBrowseController.class);
    public static final String NAME = "jbrowse";

    public JBrowseController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class GetSettingsAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();
            String[] etlConfigKeys = {JBrowseManager.JBROWSE_URL, JBrowseManager.JBROWSE_ROOT, JBrowseManager.JBROWSE_BIN, JBrowseManager.JBROWSE_DB_PREFIX, JBrowseManager.JBROWSE_COMPRESS_JSON};

            resultProperties.put("configKeys", etlConfigKeys);
            resultProperties.put("config", PropertyManager.getProperties(JBrowseManager.CONFIG_PROPERTY_DOMAIN));

            return new ApiSimpleResponse(resultProperties);
        }
    }

    @RequiresSiteAdmin
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
            if (form.getJbrowseURL() != null)
                configMap.put(JBrowseManager.JBROWSE_URL, form.getJbrowseURL());

            if (form.getJbrowseRoot() != null)
                configMap.put(JBrowseManager.JBROWSE_ROOT, form.getJbrowseRoot());

            if (form.getJbrowseBinDir() != null)
                configMap.put(JBrowseManager.JBROWSE_BIN, form.getJbrowseBinDir());

            if (form.getCompressJson() != null)
                configMap.put(JBrowseManager.JBROWSE_COMPRESS_JSON, form.getCompressJson().toString());

            if (form.getJbrowseDatabasePrefix() != null)
                configMap.put(JBrowseManager.JBROWSE_DB_PREFIX, form.getJbrowseDatabasePrefix());

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
        private String _jbrowseURL;
        private String _jbrowseRoot;
        private String _jbrowseBinDir;
        private String _jbrowseDatabasePrefix;
        private Boolean _compressJson;

        public String getJbrowseURL()
        {
            return _jbrowseURL;
        }

        public void setJbrowseURL(String jbrowseURL)
        {
            _jbrowseURL = jbrowseURL;
        }

        public String getJbrowseRoot()
        {
            return _jbrowseRoot;
        }

        public void setJbrowseRoot(String jbrowseRoot)
        {
            _jbrowseRoot = jbrowseRoot;
        }

        public String getJbrowseBinDir()
        {
            return _jbrowseBinDir;
        }

        public void setJbrowseBinDir(String jbrowseBinDir)
        {
            _jbrowseBinDir = jbrowseBinDir;
        }

        public Boolean getCompressJson()
        {
            return _compressJson;
        }

        public void setCompressJson(Boolean compressJson)
        {
            _compressJson = compressJson;
        }

        public String getJbrowseDatabasePrefix()
        {
            return _jbrowseDatabasePrefix;
        }

        public void setJbrowseDatabasePrefix(String jbrowseDatabasePrefix)
        {
            _jbrowseDatabasePrefix = jbrowseDatabasePrefix;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
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
                JBrowseManager.get().createDatabase(getContainer(), getUser(), form.getName(), form.getDescription(), form.getLibraryId(), form.getTrackIdList(), form.getDataIdList());
            }
            catch (IOException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class AddDatabaseMemberAction extends ApiAction<DatabaseForm>
    {
        public ApiResponse execute(DatabaseForm form, BindException errors)
        {
            if (form.getName() == null)
            {
                errors.reject(ERROR_MSG, "Must provide the database name");
                return null;
            }

            if (form.getTrackIdList().isEmpty() && form.getDataIdList().isEmpty())
            {
                errors.reject(ERROR_MSG, "Must provide either a list track or file IDs to include");
                return null;
            }

            try
            {
                JBrowseManager.get().addDatabaseMember(getContainer(), getUser(), form.getDatabaseId(), form.getTrackIdList(), form.getDataIdList());
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
        Integer[] _dataIds;

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
        public List<Integer> getDataIdList()
        {
            if (_dataIds == null)
                return Collections.emptyList();

            List<Integer> ret = new ArrayList<>();
            for (Integer o : _dataIds)
            {
                ret.add(o);
            }

            return ret;
        }

        public void setDataIds(Integer[] dataIds)
        {
            _dataIds = dataIds;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
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

    @RequiresPermissionClass(InsertPermission.class)
    public class CheckFileStatusAction extends ApiAction<CheckFileStatusForm>
    {
        public ApiResponse execute(CheckFileStatusForm form, BindException errors)
        {
            Map<String, Object> ret = new HashMap<>();

            JSONArray arr = new JSONArray();
            if (form.getDataIds() != null)
            {
                for (int dataId : form.getDataIds())
                {
                    arr.put(getDataJson(dataId, null));
                }
            }

            if (form.getOutputFileIds() != null)
            {
                TableInfo ti = DbSchema.get("sequenceanalysis").getTable("outputfiles");
                for (int outputFileId : form.getOutputFileIds())
                {
                    Map rowMap = new TableSelector(ti, PageFlowUtil.set("dataId", "library_id"), new SimpleFilter(FieldKey.fromString("rowid"), outputFileId), null).getObject(Map.class);
                    if (rowMap == null || rowMap.get("dataid") == null)
                    {
                        JSONObject o = new JSONObject();
                        o.put("outputFileId", outputFileId);
                        o.put("fileExists", false);
                        o.put("error", true);
                        arr.put(o);
                        continue;
                    }

                    Integer dataId = (Integer)rowMap.get("dataid");
                    Integer libraryId = (Integer)rowMap.get("library_id");
                    ExpData d = ExperimentService.get().getExpData(dataId);
                    if (dataId == null)
                    {
                        JSONObject o = new JSONObject();
                        o.put("outputFileId", outputFileId);
                        o.put("fileExists", false);
                        o.put("error", true);
                        arr.put(o);
                        continue;
                    }

                    JSONObject o = getDataJson(d.getRowId(), outputFileId);
                    o.put("libraryId", libraryId);
                    arr.put(o);
                }
            }

            ret.put("files", arr);

            return new ApiSimpleResponse(ret);
        }

        private JSONObject getDataJson(int dataId, @Nullable Integer outputFileId)
        {
            JSONObject o = new JSONObject();
            o.put("dataId", dataId);
            o.put("outputFileId", outputFileId);

            ExpData d = ExperimentService.get().getExpData(dataId);
            if (d.getFile() == null || !d.getFile().exists())
            {
                o.put("fileExists", false);
                o.put("error", true);
                return o;
            }

            o.put("fileName", d.getFile().getName());
            o.put("fileExists", true);
            o.put("extension", FileUtil.getExtension(d.getFile()));

            boolean canDisplay = JBrowseManager.get().canDisplayAsTrack(d.getFile());
            o.put("canDisplay", canDisplay);

            return o;
        }
    }

    public static class CheckFileStatusForm
    {
        int[] _outputFileIds;
        int[] _dataIds;

        public int[] getOutputFileIds()
        {
            return _outputFileIds;
        }

        public void setOutputFileIds(int[] outputFileIds)
        {
            _outputFileIds = outputFileIds;
        }

        public int[] getDataIds()
        {
            return _dataIds;
        }

        public void setDataIds(int[] dataIds)
        {
            _dataIds = dataIds;
        }
    }
}