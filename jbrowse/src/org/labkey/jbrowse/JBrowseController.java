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

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.Results;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.StopIteratingException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.resource.FileResource;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.jbrowse.model.Database;
import org.labkey.jbrowse.model.JsonFile;
import org.labkey.jbrowse.pipeline.JBrowseSessionPipelineJob;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import java.nio.file.Files;

public class JBrowseController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(JBrowseController.class);
    public static final String NAME = "jbrowse";

    public JBrowseController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(AdminPermission.class)
    public class GetSettingsAction extends ReadOnlyApiAction<Object>
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
    public class SetSettingsAction extends MutatingApiAction<SettingsForm>
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
    public class CreateDataBaseAction extends MutatingApiAction<DatabaseForm>
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
    public class AddDatabaseMemberAction extends MutatingApiAction<DatabaseForm>
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
    public class ReprocessResourcesAction extends MutatingApiAction<ReprocessResourcesForm>
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
        public void addNavTrail(NavTree root)
        {
            root.addChild(_title);
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
    public class ModifyAttributesAction extends MutatingApiAction<SimpleApiJsonForm>
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

    @RequiresPermission(ReadPermission.class)
    public class GetGenotypesAction extends ReadOnlyApiAction<GetGenotypesForm>
    {
        private List<JsonFile> getJsonFiles(GetGenotypesForm form)
        {
            String field;
            Integer rowId;
            if (form.getTrackId().startsWith("data"))
            {
                field = "outputfile";
                rowId = ConvertHelper.convert(form.getTrackId().replaceAll("^data(-)?", ""), Integer.class);

            }
            else if (form.getTrackId().startsWith("track"))
            {
                field = "trackid";
                rowId = ConvertHelper.convert(form.getTrackId().replaceAll("^track(-)?", ""), Integer.class);
            }
            else
            {
                return null;
            }

            Container targetContainer = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();
            UserSchema us = QueryService.get().getUserSchema(getUser(), targetContainer, JBrowseSchema.NAME);

            TableInfo ti = us.getTable(JBrowseSchema.TABLE_JSONFILES);
            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString(field), rowId, CompareType.EQUAL), null);

            return ts.getArrayList(JsonFile.class);
        }

        @Override
        public void validateForm(GetGenotypesForm form, Errors errors)
        {
            if (form.getTrackId() == null)
            {
                errors.reject(ERROR_MSG, "Must provide the trackId");
                return;
            }


            List<JsonFile> jsonFiles = getJsonFiles(form);
            if (jsonFiles == null)
            {
                errors.reject(ERROR_MSG, "Unknown trackId: " + form.getTrackId());
                return;
            }

            if (jsonFiles.isEmpty())
            {
                errors.reject(ERROR_MSG, "Unable to find trackId: " + form.getTrackId());
                return;
            }
            else if (jsonFiles.size() > 1)
            {
                logger.error("More than one jsonfile returned for: " + form.getTrackId());
            }

            JsonFile track = jsonFiles.get(0);
            ExpData d = track.getExpData();
            if (d == null)
            {
                errors.reject(ERROR_MSG, "Unable to find ExpData for: " + form.getTrackId());
                return;
            }

            if (d.getFile() == null)
            {
                errors.reject(ERROR_MSG, "Unable to find file for: " + form.getTrackId());
                return;
            }
            else if (!d.getFile().exists())
            {
                errors.reject(ERROR_MSG, "File does not exist for track: " + form.getTrackId());
                return;
            }

            if (form.getChr() == null || form.getStart() == null || form.getStop() == null)
            {
                errors.reject(ERROR_MSG, "Must provide the chromosome, start and stop");
            }
        }

        public ApiResponse execute(GetGenotypesForm form, BindException errors)
        {
            JSONArray ret = new JSONArray();
            Map<String, Object> resultProperties = new HashMap<>();

            List<JsonFile> jsonFiles = getJsonFiles(form);
            try (VCFFileReader reader = new VCFFileReader(jsonFiles.get(0).getExpData().getFile()))
            {
                Map<String, String> demographicsFields = JBrowseServiceImpl.get().getDemographicsFields(getUser(), getContainer());
                Map<String, Map<String, Object>> demographics = JBrowseServiceImpl.get().resolveSubjects(reader.getFileHeader().getSampleNamesInOrder(), getUser(), getContainer());

                try (CloseableIterator<VariantContext> it = reader.query(form.getChr(), form.getStart(), form.getStop()))
                {
                    while (it.hasNext())
                    {
                        VariantContext vc = it.next();
                        JSONObject pos = new JSONObject();
                        pos.put("contig", vc.getContig());
                        pos.put("start", vc.getStart());
                        pos.put("end", vc.getEnd());

                        JSONArray genotypes = new JSONArray();
                        for (Genotype g : vc.getGenotypes())
                        {
                            JSONObject gt = new JSONObject();
                            gt.put("sample", g.getSampleName());
                            gt.put("gt", g.getGenotypeString());

                            genotypes.put(gt);
                        }

                        pos.put("genotypes", genotypes);
                        ret.put(pos);
                    }
                }

                resultProperties.put("genotypes", ret);
                resultProperties.put("demographics", demographics);
                resultProperties.put("demographicsFields", demographicsFields);
            }

            return new ApiSimpleResponse(resultProperties);
        }
    }

    public static class GetGenotypesForm
    {
        private String _trackId;
        private String _chr;
        private Integer _start;
        private Integer _stop;

        public String getTrackId()
        {
            return _trackId;
        }

        public void setTrackId(String trackId)
        {
            _trackId = trackId;
        }

        public String getChr()
        {
            return _chr;
        }

        public void setChr(String chr)
        {
            _chr = chr;
        }

        public Integer getStart()
        {
            return _start;
        }

        public void setStart(Integer start)
        {
            _start = start;
        }

        public Integer getStop()
        {
            return _stop;
        }

        public void setStop(Integer stop)
        {
            _stop = stop;
        }
    }


    public static class GetSessionForm
    {
        private String session;

        public String getSession()
        {
            return session;
        }

        public void setSession(String session)
        {
            this.session = session;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetSessionAction extends ReadOnlyApiAction<GetSessionForm>
    {
        private static final String DEMO = "demo";

        @Override
        public void validateForm(GetSessionForm form, Errors errors)
        {
            if (StringUtils.isEmpty(form.getSession()))
            {
                errors.reject(ERROR_MSG, "Must provide the session Id");
            }
        }

        @Override
        public Object execute(GetSessionForm form, BindException errors) throws Exception
        {
            JSONObject resp;
            if (DEMO.equalsIgnoreCase(form.getSession()))
            {
                Module module = ModuleLoader.getInstance().getModule(JBrowseModule.class);
                FileResource r = (FileResource)module.getModuleResolver().lookup(Path.parse("external/minimalSession.json"));
                File jsonFile = r.getFile();
                if (jsonFile == null)
                {
                    throw new FileNotFoundException("Unable to find JSON file: external/minimalSession.json");
                }


                try (InputStream is = new FileInputStream(jsonFile))
                {
                    resp = new JSONObject(IOUtils.toString(is, StandardCharsets.UTF_8));
                }
            }
            else
            {
                errors.reject(ERROR_MSG, "Unknown session: " + form.getSession());
                return null;
            }

            return new ApiSimpleResponse(resp);
        }
    }
}

