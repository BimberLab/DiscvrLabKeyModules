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

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.laboratory.AbstractQueryNavItem;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.LaboratoryUrls;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

/**
 * User: bimber
 * Date: 10/1/12
 * Time: 8:51 AM
 */
public class SampleSetNavItem extends AbstractQueryNavItem
{
    private ExpSampleSet _sampleSet;
    private DataProvider _dataProvider;

    public SampleSetNavItem(DataProvider provider, ExpSampleSet sampleSet)
    {
        _sampleSet = sampleSet;
        _dataProvider = provider;
    }

    public String getName()
    {
        return _sampleSet.getName();
    }

    public String getLabel()
    {
        return _sampleSet.getName();
    }

    public String getCategory()
    {
        return LaboratoryService.NavItemCategory.samples.name();
    }

    public boolean isImportIntoWorkbooks(Container c, User u)
    {
        return true;
    }

    public boolean getDefaultVisibility(Container c, User u)
    {
        //by default, only show if defined in current container
        Container toCompare = c.isWorkbook() ? c.getParent() : c;
        return _sampleSet.getContainer().equals(toCompare);
    }

    public ActionURL getImportUrl(Container c, User u)
    {
        if (!c.hasPermission(u, InsertPermission.class))
            return null;

        ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getShowUploadMaterialsURL(c);
        url.addParameter("name", _sampleSet.getName());

        return url;
    }

    public ActionURL getSearchUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(LaboratoryUrls.class).getSearchUrl(c, SamplesSchema.SCHEMA_NAME, _sampleSet.getName());
    }

    public ActionURL getBrowseUrl(Container c, User u)
    {
        return QueryService.get().urlFor(u, c, QueryAction.executeQuery, SamplesSchema.SCHEMA_NAME, _sampleSet.getName());
    }

    public DataProvider getDataProvider()
    {
        return _dataProvider;
    }
}
