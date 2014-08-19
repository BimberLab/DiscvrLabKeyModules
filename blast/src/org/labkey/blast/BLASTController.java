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

package org.labkey.blast;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.action.AbstractFileUploadAction;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.blast.model.BlastJob;
import org.labkey.blast.pipeline.BlastDatabasePipelineJob;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BLASTController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(BLASTController.class);
    public static final String NAME = "blast";

    public BLASTController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(AdminPermission.class) @CSRF
    public class GetSettingsAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();
            String[] configKeys = {BLASTManager.BLAST_BIN_DIR, BLASTManager.BLAST_DB_DIR};

            resultProperties.put("configKeys", configKeys);
            resultProperties.put("config", PropertyManager.getProperties(BLASTManager.CONFIG_PROPERTY_DOMAIN));

            return new ApiSimpleResponse(resultProperties);
        }
    }

    @RequiresSiteAdmin @CSRF
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
            if (form.getBlastBinDir() != null)
                configMap.put(BLASTManager.BLAST_BIN_DIR, form.getBlastBinDir());

            if (form.getBlastDbDir() != null)
                configMap.put(BLASTManager.BLAST_DB_DIR, form.getBlastDbDir());

            try
            {
                BLASTManager.get().saveSettings(configMap);
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
        private String _blastBinDir;
        private String _blastDbDir;

        public String getBlastBinDir()
        {
            return _blastBinDir;
        }

        public void setBlastBinDir(String blastBinDir)
        {
            _blastBinDir = blastBinDir;
        }

        public String getBlastDbDir()
        {
            return _blastDbDir;
        }

        public void setBlastDbDir(String blastDbDir)
        {
            _blastDbDir = blastDbDir;
        }
    }

    @RequiresPermissionClass(InsertPermission.class) @CSRF
    public class CreateDatabaseAction extends ApiAction<DatabaseForm>
    {
        public ApiResponse execute(DatabaseForm form, BindException errors)
        {
            if (form.getLibraryIdList().isEmpty())
            {
                errors.reject(ERROR_MSG, "Must provide the sequence library Id");
                return null;
            }

            try
            {
                for (Integer libraryId : form.getLibraryIdList())
                {
                    BLASTManager.get().createDatabase(getContainer(), getUser(), libraryId);
                }
            }
            catch (IOException | IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class RunBlastAction extends AbstractFileUploadAction<RunBlastForm>
    {
        @Override
        public void export(RunBlastForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            if (errors.hasErrors())
                throw new UploadException("Error", HttpServletResponse.SC_BAD_REQUEST);

            response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);

            super.export(form, response, errors);
        }

        @Override
        public void validate(RunBlastForm form, BindException errors)
        {
            if (BLASTManager.get().getDatabaseDir() == null)
                errors.reject(ERROR_MSG, "BLAST DB folder has not been set or is not valid");

            if (form.getDatabase() == null)
                errors.reject(ERROR_MSG, "No BLAST DB provided");

            if (BLASTManager.get().getDatabaseDir() == null)
                errors.reject(ERROR_MSG, "BLAST DB folder has not been set or is not valid");

            boolean hasSequence = !StringUtils.isEmpty(form.getQuery());
            if (!hasSequence)
            {
                HttpServletRequest basicRequest = getViewContext().getRequest();
                if (basicRequest instanceof MultipartHttpServletRequest)
                {
                    MultipartHttpServletRequest request = (MultipartHttpServletRequest) basicRequest;
                    hasSequence = request.getFileNames().hasNext();
                }
            }

            if (!hasSequence)
                errors.reject(ERROR_MSG, "Must provide either a file or query sequence");
        }

        protected File getTargetFile(String filename) throws IOException
        {

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

        protected String getResponse(Map<String, Pair<File, String>> files, RunBlastForm form) throws UploadException
        {
            JSONObject resp = new JSONObject();
            Map<String, String> params = new HashMap<>();
            if (form.getQueryFrom() != null && form.getQueryTo() != null)
            {
                params.put("query_loc", form.getQueryFrom() + "-" + form.getQueryTo());
            }

            List<File> inputFiles = new ArrayList<>();
            try
            {
                for (Map.Entry<String, Pair<File, String>> entry : files.entrySet())
                {
                    inputFiles.add(entry.getValue().getKey());
                }

                if (files.isEmpty())
                {
                    //save query to string
                    AssayFileWriter writer = new AssayFileWriter();
                    try
                    {
                        File targetDirectory = writer.ensureUploadDirectory(getContainer());
                        File input = writer.findUniqueFileName("blast", targetDirectory);
                        input.createNewFile();
                        try (BufferedWriter fw = new BufferedWriter(new FileWriter(input)))
                        {
                            fw.write(form.getQuery());
                        }
                        inputFiles.add(input);
                    }
                    catch (ExperimentException e)
                    {
                        throw new UploadException(e.getMessage(), HttpServletResponse.SC_NOT_FOUND);
                    }
                }

                if (!inputFiles.isEmpty())
                {
                    for (File input : inputFiles)
                    {
                        String jobId = BLASTManager.get().runBLASTN(getContainer(), getUser(), form.getDatabase(), input, form.getTask(), form.getTitle(), form.getSaveResults(), params, true);
                        resp.put("jobId", jobId);
                    }
                }

                resp.put("success", true);
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                getViewContext().getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
                logger.error(e.getMessage(), e);
                resp.put("success", false);
                resp.put("exception", e.getMessage());
            }

            return resp.toString();
        }
    }

    public static class RunBlastForm extends AbstractFileUploadAction.FileUploadForm
    {
        private String _database;
        private String _query;
        private String _title;
        private boolean _saveResults = false;
        private String _queryFrom;
        private String _queryTo;
        private String _task;

        public String getQuery()
        {
            return _query;
        }

        public void setQuery(String query)
        {
            _query = query;
        }

        public String getTitle()
        {
            return _title;
        }

        public void setTitle(String title)
        {
            _title = title;
        }

        public String getDatabase()
        {
            return _database;
        }

        public void setDatabase(String database)
        {
            _database = database;
        }

        public boolean getSaveResults()
        {
            return _saveResults;
        }

        public void setSaveResults(boolean saveResults)
        {
            _saveResults = saveResults;
        }

        public String getQueryFrom()
        {
            return _queryFrom;
        }

        public void setQueryFrom(String queryFrom)
        {
            _queryFrom = queryFrom;
        }

        public String getQueryTo()
        {
            return _queryTo;
        }

        public void setQueryTo(String queryTo)
        {
            _queryTo = queryTo;
        }

        public String getTask()
        {
            return _task;
        }

        public void setTask(String task)
        {
            _task = task;
        }
    }

    public static class DatabaseForm
    {
        String _name;
        String _description;
        Integer[] _libraryIds;

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

    @RequiresPermissionClass(ReadPermission.class) @CSRF
    public class GetBlastResultAction extends ApiAction<BlastResultForm>
    {
        public ApiResponse execute(BlastResultForm form, BindException errors)
        {
            Map<String, Object> resultProperties = new HashMap<>();

            BlastJob j = BLASTManager.get().getBlastResults(getContainer(), getUser(), form.getJobId());
            if (j == null)
            {
                errors.reject(ERROR_MSG, "Unable to find job: " + form.getJobId());
                return null;
            }

            resultProperties.putAll(j.toJSON(getUser(), true));

            return new ApiSimpleResponse(resultProperties);
        }
    }

    public static class BlastResultForm
    {
        private String _jobId;

        public String getJobId()
        {
            return _jobId;
        }

        public void setJobId(String jobId)
        {
            _jobId = jobId;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class RecreateDatabaseAction extends ApiAction<RecreateDatabaseForm>
    {
        public ApiResponse execute(RecreateDatabaseForm form, BindException errors)
        {
            if (form.getDatabaseIds() == null || form.getDatabaseIds().length == 0)
            {
                errors.reject("Must provide a list of databases to re-process");
                return null;
            }

            try
            {
                PipeRoot root = PipelineService.get().getPipelineRootSetting(getContainer());
                for (String databaseGuid : form.getDatabaseIds())
                {
                    TableInfo databases = DbSchema.get(BLASTSchema.NAME).getTable(BLASTSchema.TABLE_DATABASES);
                    String containerId = new TableSelector(databases, PageFlowUtil.set("container"), new SimpleFilter(FieldKey.fromString("objectid"), databaseGuid), null).getObject(String.class);
                    if (containerId == null)
                    {
                        throw new PipelineValidationException("Unknown BLAST database: " + databaseGuid);
                    }

                    Container c = ContainerManager.getForId(containerId);
                    if (c == null)
                    {
                        throw new PipelineValidationException("Unknown container: " + containerId);
                    }

                    if (!c.hasPermission(getUser(), UpdatePermission.class))
                    {
                        throw new PipelineValidationException("Insufficient permissions to update BLAST database: " + databaseGuid);
                    }

                    PipelineService.get().queueJob(new BlastDatabasePipelineJob(c, getUser(), null, root, databaseGuid));
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

    public static class RecreateDatabaseForm
    {
        private String[] _databaseIds;

        public String[] getDatabaseIds()
        {
            return _databaseIds;
        }

        public void setDatabaseIds(String[] databaseIds)
        {
            _databaseIds = databaseIds;
        }
    }
}
