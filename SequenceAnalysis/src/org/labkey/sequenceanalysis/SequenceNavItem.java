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
package org.labkey.sequenceanalysis;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.laboratory.AbstractQueryNavItem;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/1/12
 * Time: 9:46 AM
 */
public class SequenceNavItem extends AbstractQueryNavItem
{
    public static final String NAME = "Sequence";
    private DataProvider _dp;
    private String _label;
    private String _category;

    public SequenceNavItem(DataProvider dp)
    {
        _dp = dp;
        _label = NAME;
    }

    public SequenceNavItem(DataProvider dp, String label)
    {
        this(dp, label, null);
    }

    public SequenceNavItem(DataProvider dp, String label, String category)
    {
        _dp = dp;
        _label = label;
        _category = (category == null) ? "Sequence" : category;
    }

    public DataProvider getDataProvider()
    {
        return _dp;
    }

    public String getName()
    {
        return NAME;
    }

    public String getLabel()
    {
        return _label;
    }

    public boolean isImportIntoWorkbooks(Container c, User u)
    {
        return true;
    }

    public boolean getDefaultVisibility(Container c, User u)
    {
        return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME));
    }

    public String getCategory()
    {
        return _category;
    }

    public ActionURL getImportUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(c);
    }

    public ActionURL getSearchUrl(Container c, User u)
    {
        return new ActionURL(SequenceAnalysisModule.CONTROLLER_NAME, "search", c);
    }

    public ActionURL getBrowseUrl(Container c, User u)
    {
        return new ActionURL(SequenceAnalysisModule.CONTROLLER_NAME, "dataBrowser", c);
    }

    public ActionURL getAssayRunTemplateUrl(Container c, User u)
    {
        return QueryService.get().urlFor(u, c, QueryAction.importData, SequenceAnalysisModule.CONTROLLER_NAME, SequenceAnalysisSchema.TABLE_READSETS);
    }

    public ActionURL getViewAssayRunTemplateUrl(Container c, User u)
    {
        ActionURL url = QueryService.get().urlFor(u, c, QueryAction.executeQuery, SequenceAnalysisModule.CONTROLLER_NAME, SequenceAnalysisSchema.TABLE_READSETS);
        url.addParameter("query.viewName", "Data Not Imported");
        return url;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = super.toJSON(c, u);
        ret.put("assayRunTemplateText", "Create Readsets");
        ret.put("uploadResultsText", "Upload Raw Data");
        ret.put("supportsRunTemplates", true);
        ret.put("assayRunTemplateUrl", getUrlObject(getAssayRunTemplateUrl(c, u)));

        ret.put("viewRunTemplateText", "View Readsets");
        ret.put("viewRunTemplateUrl", getUrlObject(getViewAssayRunTemplateUrl(c, u)));

        return ret;
    }
}
