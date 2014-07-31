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

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
            if (form.getName() == null)
            {
                errors.reject(ERROR_MSG, "Must provide the database name");
                return null;
            }

            if (form.getSequenceIdList().isEmpty() && form.getTrackIdList().isEmpty() && form.getLibraryIdList().isEmpty())
            {
                errors.reject(ERROR_MSG, "Must provide either a list of NT IDs or Track IDs to include");
                return null;
            }

            try
            {
                JBrowseManager.get().createDatabase(getContainer(), getUser(), form.getName(), form.getDescription(), form.getLibraryIdList(), form.getSequenceIdList(), form.getTrackIdList());
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
            if (form.getDatabaseId() == null)
            {
                errors.reject(ERROR_MSG, "Must provide the database Id");
                return null;
            }

            if (form.getSequenceIdList().isEmpty() && form.getTrackIdList().isEmpty())
            {
                errors.reject(ERROR_MSG, "Must provide either a list of NT IDs or Track IDs to add");
                return null;
            }

            try
            {
                JBrowseManager.get().addDatabaseMember(getContainer(), getUser(), form.getDatabaseId(), form.getLibraryIdList(), form.getSequenceIdList(), form.getTrackIdList());
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
        Integer[] _sequenceIds;
        Integer[] _trackIds;
        Integer[] _libraryIds;

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
        public List<Integer> getSequenceIdList()
        {
            if (_sequenceIds == null)
                return Collections.emptyList();

            List<Integer> ret = new ArrayList<>();
            for (Integer o : _sequenceIds)
            {
                ret.add(o);
            }

            return ret;
        }

        public void setSequenceIds(Integer[] sequenceIds)
        {
            _sequenceIds = sequenceIds;
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

        @NotNull
        public List<Integer> getLibraryIdList()
        {
            if (_libraryIds == null)
                return Collections.emptyList();

            List<Integer> ret = new ArrayList<>();
            for (Integer o : _libraryIds)
            {
                ret.add(o);
            }

            return ret;
        }

        public void setLibraryIds(Integer[] libraryIds)
        {
            _libraryIds = libraryIds;
        }
    }
}