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
package org.labkey.vbdsearch;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.vbdsearch.email.RepositoryContactEmailTemplate;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.MessagingException;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VBDSearchController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(VBDSearchController.class);
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(VBDSearchController.class);
    private static final String LIST_NAME = "Samples";
    private static final String SPECIMEN_CATEGORY_COLUMN = "specimenCategory";
    private static final String PERSON_COUNT_COLUMN = "personCount";
    private static final String SPECIMEN_COUNT_COLUMN = "specimenCount";
    private static final String PERSON_CATEGORY_COLUMN = "personCategory";
    private static final String REPOSITORY_COLUMN = "repositoryShortName";

    private static final String CELL_FORMAT = "%d (%d people)";

    private static final Cache<String, List<Map<String, Object>>> VBD_CACHE = CacheManager.getCache(50, CacheManager.HOUR, "VBD Search Cache");
    private static final String SPECIMEN_SUMMARY = "SpecimenSummaryKey";
    private static final String FACETED_FILTER_VALUES = "FacetedFilterValuesKey";


    public VBDSearchController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends RedirectAction
    {
        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return getContainer().getStartURL(getUser());
        }

        @Override
        public boolean doAction(Object o, BindException errors) throws Exception
        {
            return true;
        }

        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }
    }
    
    @RequiresPermissionClass(ReadPermission.class)
    public class FacetedSpecimenSearchAction extends SimpleViewAction<Object>
    {
        boolean _isMatchedSearch;

        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            _isMatchedSearch = BooleanUtils.toBoolean(getViewContext().getActionURL().getParameter("matchedSpecimenSearch"));
            return new JspView<Object>("/org/labkey/vbdsearch/view/facetedSearch.jsp");
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            if (_isMatchedSearch)
                root.addChild("Matched Specimen Search");
            else
                root.addChild("Faceted Specimen Search");
            return root;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetSummaryResultsAction extends ApiAction<FacetedSearchResults.FacetedSearchForm>
    {
        @Override
        public ApiResponse execute(FacetedSearchResults.FacetedSearchForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            String key = SPECIMEN_SUMMARY + "-" + getContainer().getId();
            List<Map<String, Object>> data = VBD_CACHE.get(key, form, new CacheLoader<String, List<Map<String, Object>>>()
            {
                @Override
                public List<Map<String, Object>> load(String key, @Nullable Object argument)
                {
                    try
                    {
                        // map of person categories to sample results
                        Map<String, Map<String, Object>> rollupMap = new LinkedHashMap<>();
                        FacetedSearchResults.FacetedSearchForm form = (FacetedSearchResults.FacetedSearchForm)argument;
                        UserSchema listSchema = QueryService.get().getUserSchema(getUser(), getContainer(), "lists");

                        try (ResultSet rs = SummarySearchResults.getResults(form, listSchema))
                        {
                            generateRollup(rs, form.getFilters(), rollupMap, FacetedSpecimenSearchAction.class, PERSON_CATEGORY_COLUMN, false);
                        }

                        try (ResultSet rs = SummarySearchResults.getSpecimenTotals(form, listSchema))
                        {
                            generateRollup(rs, form.getFilters(), rollupMap, FacetedSpecimenSearchAction.class, PERSON_CATEGORY_COLUMN, true);
                        }

                        try (ResultSet rs = SummarySearchResults.getPersonCategoryTotals(form, listSchema))
                        {
                            generateRollupTotals(rs, form.getFilters(), rollupMap, FacetedSpecimenSearchAction.class, PERSON_CATEGORY_COLUMN);
                        }

                        Pair<Integer, Integer> allTotals = FacetedSearchResults.getAllTotals(form, listSchema);
                        if (allTotals != null)
                        {
                            Map<String, Object> row = rollupMap.get("total");
                            if (row != null)
                            {
                                row.put("total", String.format(CELL_FORMAT, allTotals.getKey(), allTotals.getValue()));
                                row.put("totalUrl", new ActionURL(FacetedSpecimenSearchAction.class, getContainer()).getLocalURIString());
                            }
                        }
                        return new ArrayList<>(rollupMap.values());
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            });
            resp.put("rows", data);
            resp.put("success", true);

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetFacetedSearchResultsAction extends ApiAction<FacetedSearchResults.FacetedSearchForm>
    {
        @Override
        public ApiResponse execute(FacetedSearchResults.FacetedSearchForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            UserSchema listSchema = QueryService.get().getUserSchema(getUser(), getContainer(), "lists");

            // map of repository names to sample results
            Map<String, Map<String, Object>> rollupMap = new LinkedHashMap<>();

            try (ResultSet rs = FacetedSearchResults.getResults(form, listSchema))
            {
                generateRollup(rs, form.getFilters(), rollupMap, SpecimenSearchResultsAction.class, REPOSITORY_COLUMN, false);
            }

            try (ResultSet rs = FacetedSearchResults.getSpecimenTotals(form, listSchema))
            {
                generateRollup(rs, form.getFilters(), rollupMap, SpecimenSearchResultsAction.class, REPOSITORY_COLUMN, true);
            }

            try (ResultSet rs = FacetedSearchResults.getRepositoryTotals(form, listSchema))
            {
                generateRollupTotals(rs, form.getFilters(), rollupMap, SpecimenSearchResultsAction.class, REPOSITORY_COLUMN);
            }

            Pair<Integer, Integer> allTotals = FacetedSearchResults.getAllTotals(form, listSchema);
            if (allTotals != null)
            {
                Map<String, Object> row = rollupMap.get("total");
                if (row != null)
                {
                    row.put("total", String.format(CELL_FORMAT, allTotals.getKey(), allTotals.getValue()));

                    ActionURL url = getBaseDetailsUrl(getContainer(), SpecimenSearchResultsAction.class, form.getFilters(), true);
                    row.put("totalUrl", url.getLocalURIString());
                }
            }
            resp.put("rows", new ArrayList<>(rollupMap.values()));
            resp.put("success", true);

            return resp;
        }
    }

    /**
     * Generate the rolled up information for each specimen category and specified key intersection
     * @param urlAction The action class to link to for each of the details links
     * @param keyField The column name which is the row key field
     * @throws SQLException
     */
    private void generateRollup(ResultSet rs, Map<String, Object> filters, Map<String, Map<String, Object>> rollupMap,
                                Class<? extends Controller> urlAction, String keyField, boolean isTotalRow) throws SQLException
    {
        while (rs.next())
        {
            String specimenCategory = rs.getString("specimenCategory");
            String specimenCategoryKey = specimenCategory.replaceAll(" ","");
            String key = rs.getString(keyField);
            int personCount = rs.getInt("personCount");
            int specimenCount = rs.getInt("specimenCount");

            if (key == null)
                continue;

            Map<String, Object> row;
            if (!rollupMap.containsKey(key))
            {
                row = new HashMap<>();
                row.put(keyField, key);
                rollupMap.put(key, row);
            }
            else
                row = rollupMap.get(key);

            row.put(specimenCategoryKey, String.format(CELL_FORMAT, specimenCount, personCount));

            ActionURL url = getBaseDetailsUrl(getContainer(), urlAction, filters, false).replaceParameter("specimenCategory", specimenCategory);
            if (!isTotalRow)
                url.replaceParameter(keyField, key);
            row.put(specimenCategoryKey + "Url", url.getLocalURIString());
        }
    }

    /**
     * Generate the rolled up totals for each specimen category
     * @param urlAction The action class to link to for each of the details links
     * @param keyField The column name which is the row key field
     * @throws SQLException
     */
    private void generateRollupTotals(ResultSet rs, Map<String, Object> filters, Map<String, Map<String, Object>> rollupMap,
                                      Class<? extends Controller> urlAction, String keyField) throws SQLException
    {
        while (rs.next())
        {
            String key = rs.getString(keyField);
            int personCount = rs.getInt("personCount");
            int specimenCount = rs.getInt("specimenCount");

            if (key == null)
                continue;

            Map<String, Object> row;
            if (!rollupMap.containsKey(key))
            {
                row = new HashMap<>();
                row.put(keyField, key);
                rollupMap.put(key, row);
            }
            else
                row = rollupMap.get(key);

            row.put("total", String.format(CELL_FORMAT, specimenCount, personCount));

            ActionURL url = getBaseDetailsUrl(getContainer(), urlAction, filters, true).
                    replaceParameter(keyField, key);
            row.put("totalUrl", url.getLocalURIString());
        }
    }

    private ActionURL getBaseDetailsUrl(Container c, Class<? extends Controller> actionClass, Map<String, Object> filters, boolean includeSpecimenFilter)
    {
        ActionURL url = new ActionURL(actionClass, c);

        for (Map.Entry<String, Object> entry : filters.entrySet())
        {
            if (includeSpecimenFilter && (entry.getValue() instanceof JSONArray))
            {
                List<String> values = new ArrayList<>();
                for (Object value : ((JSONArray)entry.getValue()).toArray())
                        values.add(value.toString());

                url.addFilter("vbd", FieldKey.fromString(entry.getKey()), CompareType.IN, ((JSONArray)entry.getValue()).join(";").replaceAll("\\\"", ""));
            }
            else if (entry.getValue() instanceof String)
                url.addParameter(entry.getKey(), (String)entry.getValue());
        }
        return url;
    }

/*
    @RequiresPermissionClass(ReadPermission.class)
    public class GetFacetedFilterValuesAction extends ApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            String key = FACETED_FILTER_VALUES + "-" + getContainer().getId();
            Map<String, Object> data = VBD_CACHE.get(key, null, new CacheLoader<String, Map<String, Object>>()
            {
                @Override
                public Map<String, Object> load(String key, @Nullable Object argument)
                {
                    try {

                        UserSchema listSchema = QueryService.get().getUserSchema(getUser(), getContainer(), "lists");
                        Map<String, List<String>> personFilters = new HashMap<String, List<String>>();
                        Map<String, List<String>> specimenFilters = new HashMap<String, List<String>>();

                        personFilters.put("personCategory", getDistinctValues(listSchema, "personCategory"));
                        personFilters.put("personGender", getDistinctValues(listSchema, "personGender"));
                        personFilters.put("personRace", getDistinctValues(listSchema, "personRace"));
                        personFilters.put("personEthnicity", getDistinctValues(listSchema, "personEthnicity"));
                        personFilters.put("personPathStage", getDistinctValues(listSchema, "personPathStage"));
                        personFilters.put("personPathDiagnosis", getDistinctValues(listSchema, "personPathDiagnosis"));
                        personFilters.put("personPrimaryHistDiagnosis", getDistinctValues(listSchema, "personPrimaryHistDiagnosis"));
                        personFilters.put("personPrimarySite", getDistinctValues(listSchema, "personPrimarySite"));

                        specimenFilters.put("specimenType", getDistinctValues(listSchema, "specimenType"));
                        specimenFilters.put("specimenPreservationMethod", getDistinctValues(listSchema, "specimenPreservationMethod"));
                        specimenFilters.put("specimenPathDiagnosis", getDistinctValues(listSchema, "specimenPathDiagnosis"));
                        specimenFilters.put("specimenHistDiagnosis", getDistinctValues(listSchema, "specimenHistDiagnosis"));
                        specimenFilters.put("specimenPathGrade", getDistinctValues(listSchema, "specimenPathGrade"));
                        specimenFilters.put("specimenTumorMarkers", getDistinctValues(listSchema, "specimenTumorMarkers"));
                        specimenFilters.put("specimenPriorTx", getDistinctValues(listSchema, "specimenPriorTx"));
                        specimenFilters.put("specimenSite", getDistinctValues(listSchema, "specimenSite"));
                        specimenFilters.put("repositoryShortName", getDistinctValues(listSchema, "repositoryShortName"));
                        specimenFilters.put("specimenCategory", getDistinctValues(listSchema, "specimenCategory"));

                        Map<String, Object> filters = new HashMap<String, Object>();

                        filters.put("personFilters", personFilters);
                        filters.put("specimenFilters", specimenFilters);

                        return filters;
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeSQLException(e);
                    }
                }
            });

            resp.put("personFilters", data.get("personFilters"));
            resp.put("specimenFilters", data.get("specimenFilters"));
            resp.put("success", true);
            return resp;
        }
    }
*/

    private List<String> getDistinctValues(UserSchema listSchema, String columnName) throws SQLException
    {
        ArrayList<String> values = new ArrayList<String>();
        SQLFragment sqlFrag = new SQLFragment("SELECT DISTINCT ");
        sqlFrag.append(LIST_NAME).append(".").append(columnName).append(" ");
        sqlFrag.append("FROM " + LIST_NAME);
        ResultSet rs = QueryService.get().select(listSchema, sqlFrag.getSQL());

        while(rs.next())
        {
            values.add(rs.getString(columnName));
        }

        rs.close();

        return values;
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SpecimenSearchResultsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<Object>("/org/labkey/vbdsearch/view/specimenResults.jsp");
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Search Results");
            return root;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SpecimenRequestAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<Object>("/org/labkey/vbdsearch/view/specimenRequest.jsp");
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Contact Repository");
            return root;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class RequestSpecimensAction extends ApiAction<SpecimenRequestForm>
    {
        private ActionURL _sampleLink;

        @Override
        public ApiResponse execute(SpecimenRequestForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            RepositoryContactEmailTemplate template = EmailTemplateService.get().getEmailTemplate(RepositoryContactEmailTemplate.class);
            LookAndFeelProperties lafProps = LookAndFeelProperties.getInstance(getContainer());
            String fromEmail = lafProps.getSystemEmailAddress();

            if (!getUser().isGuest())
                fromEmail = getUser().getEmail();

            template.setComments(form.getComments());
            template.setFilters(getFilterDescription(form));
            template.setSampleLink(_sampleLink);

            EmailMessage message = EmailService.get().createMessage(fromEmail,
                    new String[]{form.getRepositoryEmail()},
                    template.renderSubject(getContainer()),
                    template.renderBody(getContainer()));

            try
            {
                EmailService.get().sendMessage(message, getUser(), getContainer());
            }
            catch(MessagingException | ConfigurationException e)
            {
                _log.warn("Could not send email notifications.", e);
            }

/*
            File tempTSV = createTempFile(form);
            message.setFiles(Arrays.asList(tempTSV)); // Since we don't have the email changes we need to generate a link instead.
            EmailService.get().sendMessage(message, getUser(), getContainer());
            tempTSV.delete();
*/

            resp.put("success", true);
            resp.put("returnURL", _sampleLink.getLocalURIString());

            return resp;
        }

        private String getFilterDescription(SpecimenRequestForm form)
        {
            StringBuilder sb = new StringBuilder();

            List<Pair<String, String>> params = PageFlowUtil.fromQueryString(form.getSearchString());

            for (Pair<String, String> param : params)
            {
                if (!"returnURL".equalsIgnoreCase(param.getKey()) && param.getValue() != null)
                {
                    sb.append(param.getKey()).append("\t");
                    sb.append(param.getValue()).append("\n");
                }
            }
            _sampleLink = new ActionURL(SpecimenSearchResultsAction.class, getContainer());
            _sampleLink.addParameters(params);

            return sb.toString();
        }

        private File createTempFile(SpecimenRequestForm form) throws Exception
        {
            File tempTSV;
            List<Integer> filterValues = new ArrayList<Integer>();
            QueryView qv;
            QuerySettings qs = form.getQuerySettings();

            for (String value : form.getSelectedRows().split(";"))
            {
                filterValues.add(Integer.parseInt(value));
            }

            qs.setBaseFilter(new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromParts("Key"), filterValues)));
            qv = new QueryView(form.getSchema(), qs, null);
            qv.setShowUpdateColumn(false);
            tempTSV = File.createTempFile("~attachment", "specimens.tsv");
            qv.getTsvWriter().write(tempTSV);

            return tempTSV;
        }
    }

    public static class SpecimenRequestForm extends QueryForm
    {
        private String _repositoryShortName;
        private String _repositoryEmail;
        private String _comments;
        private String _selectedRows;
        private String _searchString;

        public String getComments()
        {
            return _comments;
        }

        public void setComments(String comments)
        {
            _comments = comments;
        }

        public String getRepositoryEmail()
        {
            return _repositoryEmail;
        }

        public void setRepositoryEmail(String email)
        {
            _repositoryEmail = email;
        }

        public String getRepositoryShortName()
        {
            return _repositoryShortName;
        }

        public void setRepositoryShortName(String repositoryShortName)
        {
            _repositoryShortName = repositoryShortName;
        }

        public String getSelectedRows()
        {
            return _selectedRows;
        }

        public void setSelectedRows(String selectedRows)
        {
            _selectedRows = selectedRows;
        }

        public String getSearchString()
        {
            return _searchString;
        }

        public void setSearchString(String searchString)
        {
            _searchString = searchString;
        }
    }
}
