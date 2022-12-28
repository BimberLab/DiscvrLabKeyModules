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
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.old.JSONArray;
import org.json.old.JSONObject;
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
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Results;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.StopIteratingException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.Readers;
import org.labkey.api.resource.FileResource;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.jbrowse.model.JBrowseSession;
import org.labkey.jbrowse.model.JsonFile;
import org.labkey.jbrowse.pipeline.JBrowseSessionPipelineJob;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JBrowseController extends SpringActionController
{
    private static final Logger _log = LogManager.getLogger(JBrowseController.class);

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(JBrowseController.class);
    public static final String NAME = "jbrowse";

    public JBrowseController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(InsertPermission.class)
    public static class CreateDataBaseAction extends MutatingApiAction<DatabaseForm>
    {
        @Override
        public ApiResponse execute(DatabaseForm form, BindException errors)
        {
            if (form.getName() == null || form.getLibraryId() == null)
            {
                errors.reject(ERROR_MSG, "Must provide the database name and reference genome");
                return null;
            }

            try
            {
                JBrowseManager.get().createDatabase(getContainer(), getUser(), form.getName(), form.getDescription(), form.getLibraryId(), form.getTrackIdList(), form.getOutputFileIdList(), form.getIsTemporary());
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
    public static class AddDatabaseMemberAction extends MutatingApiAction<DatabaseForm>
    {
        @Override
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
                JBrowseManager.get().addDatabaseMember(getUser(), form.getDatabaseId(), form.getTrackIdList(), form.getOutputFileIdList());
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
    public static class ReprocessResourcesAction extends MutatingApiAction<ReprocessResourcesForm>
    {
        @Override
        public ApiResponse execute(ReprocessResourcesForm form, BindException errors)
        {
            if ((form.getJsonFiles() == null || form.getJsonFiles().length == 0) && (form.getDatabaseIds() == null || form.getDatabaseIds().length == 0))
            {
                errors.reject(ERROR_MSG, "Must provide a list of sessions or JSON files to re-process");
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
                        JBrowseService.get().reprocessDatabase(getUser(), databaseGuid);
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

        @Override
        public void validateForm(ReprocessResourcesForm form, Errors errors)
        {
            if ((form.getJsonFiles() == null || form.getJsonFiles().length == 0) && (form.getDatabaseIds() == null || form.getDatabaseIds().length == 0))
            {
                errors.reject("Must provide a list of sessions or JSON files to re-process");
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

    // Based on: https://www.code4copy.com/java/validate-uuid-string-java/
    private final static Pattern UUID_REGEX_PATTERN = Pattern.compile("^[{]?[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}[}]?$");

    private static boolean isValidUUID(String str)
    {
        if (str == null)
        {
            return false;
        }

        return UUID_REGEX_PATTERN.matcher(str).matches();
    }

    @RequiresPermission(ReadPermission.class)
    public static class JBrowseAction extends SimpleViewAction<BrowserForm>
    {
        private String _title;

        @Override
        public ModelAndView getView(BrowserForm form, BindException errors) throws Exception
        {
            String guid = form.getEffectiveSessionId();
            JBrowseSession db = isValidUUID(guid) ? new TableSelector(JBrowseSchema.getInstance().getTable(JBrowseSchema.TABLE_DATABASES), new SimpleFilter(FieldKey.fromString("objectid"), form.getEffectiveSessionId()), null).getObject(JBrowseSession.class) : null;
            _title = db == null ? "JBrowse" : db.getName();
            form.setPageTitle(_title);

            ModuleHtmlView view = ModuleHtmlView.get(ModuleLoader.getInstance().getModule(JBrowseModule.class), Path.parse("views/gen/jbrowseBrowser.html"));
            assert view != null;
            getPageConfig().setIncludePostParameters(true);

            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(_title);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class BrowserAction extends JBrowseAction
    {

    }

    public static class BrowserForm
    {
        private String _database;
        private String _session;
        private String _pageTitle;

        public String getDatabase()
        {
            return _database;
        }

        public void setDatabase(String database)
        {
            _database = database;
        }

        public String getSession()
        {
            return _session;
        }

        public String getEffectiveSessionId()
        {
            return _session == null ? _database : _session;
        }

        public void setSession(String session)
        {
            _session = session;
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
    public static class ModifyAttributesAction extends MutatingApiAction<SimpleApiJsonForm>
    {
        @Override
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
            ts.forEachResults(rs -> {
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
            Container targetContainer = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();
            UserSchema us = QueryService.get().getUserSchema(getUser(), targetContainer, JBrowseSchema.NAME);

            TableInfo ti = us.getTable(JBrowseSchema.TABLE_JSONFILES);
            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("objectId"), form.getTrackId(), CompareType.EQUAL), null);

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
            else
            {
                if (!isValidUUID(form.getTrackId()))
                {
                    errors.reject(ERROR_MSG, "Invalid track ID: " + form.getTrackId());
                    return;
                }
            }

            List<JsonFile> jsonFiles = getJsonFiles(form);
            if (jsonFiles == null || jsonFiles.isEmpty())
            {
                errors.reject(ERROR_MSG, "Unknown trackId: " + form.getTrackId());
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

        @Override
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
        private String _session;
        private String _activeTracks;

        public String getSession()
        {
            return _session;
        }

        public void setSession(String session)
        {
            _session = session;
        }

        public String getActiveTracks()
        {
            return _activeTracks;
        }

        public void setActiveTracks(String activeTracks)
        {
            _activeTracks = activeTracks;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetSessionAction extends ReadOnlyApiAction<GetSessionForm>
    {
        private static final String DEMO = "demo";
        private static final String MGAP = "mGAP";
        private static final String MGAP_FILTERED = "mGAPF";

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
                resp = getDemoSession("external/minimalSession.json");
            }
            else if (MGAP.equalsIgnoreCase(form.getSession()))
            {
                resp = getDemoSession("external/mGAPSession.json");
            }
            else if (MGAP_FILTERED.equalsIgnoreCase(form.getSession()))
            {
                resp = getDemoSession("external/mGAPSession.json");
                resp.getJSONArray("tracks").getJSONObject(0).getJSONArray("displays").getJSONObject(0).getJSONObject("renderer").put("activeSamples", "m00004,m00005");
                resp.getJSONArray("tracks").getJSONObject(0).getJSONArray("displays").getJSONObject(0).getJSONObject("renderer").put("palette", "AF");
                resp.getJSONArray("tracks").getJSONObject(0).getJSONArray("displays").getJSONObject(0).getJSONObject("renderer").put("infoFilters", new JSONArray(){{
                    put("AF:gt:0.1");
                }});

                resp.getJSONArray("tracks").getJSONObject(0).getJSONArray("displays").getJSONObject(0).remove("detailsConfig");
            }
            else
            {
                JBrowseSession db = JBrowseSession.getForId(form.getSession());
                if (db == null)
                {
                    errors.reject(ERROR_MSG, "Unknown session: " + form.getSession());
                    return null;
                }

                List<String> additionalActiveTracks = null;
                if (form.getActiveTracks() != null)
                {
                    String val = StringUtils.trimToNull(form.getActiveTracks());
                    if (val != null)
                    {
                        additionalActiveTracks = Arrays.asList(val.split(","));
                    }
                }

                resp = db.getConfigJson(getUser(), _log, additionalActiveTracks);
            }

            // The rationale of this is to take the site theme, and map this to the primary site color.
            // The client-side JBrowse LinearGenomeView will use this to make a theme. For the time being, three of four JBrowse
            // theme colors are hard-coded, and we only update the JBrowse secondary color to match the LabKey one
            LookAndFeelProperties props = LookAndFeelProperties.getInstance(getContainer());
            String lightColor;
            String darkColor;
            switch (props.getThemeName())
            {
                case "Blue" -> {darkColor = "#21309A";lightColor = "#21309A";}
                case "Brown" -> {darkColor = "#682B16";lightColor = "#682B16";}
                case "Leaf" -> {darkColor = "#597530";lightColor = "#789E47";}
                case "Harvest" -> {darkColor = "#e86130";lightColor = "#F7862A";}
                case "Madison" -> {darkColor = "#990000";lightColor = "#C5050C";}
                case "Mono" -> {darkColor = "#4c4c4c";lightColor = "#7f7f7f";}
                case "Ocean" -> {darkColor = "#307272";lightColor = "#208e8b";}
                case "Overcast" -> {darkColor = "#116596";lightColor = "#3495d2";}
                case "Sage" -> {darkColor = "#0F4F0B";lightColor = "#0F4F0B";}
                case "Seattle" -> {darkColor = "#116596";lightColor = "#116596";} //NOTE: Seattle technically uses #73b6e0 as the light color
                default -> {
                    _log.error("Unexpected theme name: " + props.getThemeName());
                    // This will result in the client using the JBrowse defaults:
                    lightColor = null;
                    darkColor = null;
                }
            }

            resp.put("themeLightColor", lightColor);
            resp.put("themeDarkColor", darkColor);

            return new ApiSimpleResponse(resp);
        }

        private JSONObject getDemoSession(String path) throws IOException
        {
            final String contextPath = AppProps.getInstance().getContextPath();

            Module module = ModuleLoader.getInstance().getModule(JBrowseModule.class);
            FileResource r = (FileResource)module.getModuleResolver().lookup(Path.parse(path));
            File jsonFile = r.getFile();
            if (jsonFile == null)
            {
                throw new FileNotFoundException("Unable to find JSON file: " + path);
            }

            try (BufferedReader reader = Readers.getReader(jsonFile))
            {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null)
                {
                    line = line.replaceAll("<%=\\s*contextPath\\s*%>", Matcher.quoteReplacement(contextPath));
                    sb.append(line);
                }

                return new JSONObject(sb.toString());
            }
        }
    }
}

