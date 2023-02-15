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

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.AbstractNavItem;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.DetailsUrlWithLabelNavItem;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.SummaryNavItem;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: bimber
 * Date: 10/1/12
 * Time: 9:46 AM
 */
public class SequenceOutputsNavItem extends AbstractNavItem implements SummaryNavItem
{
    public static final String NAME = "Outputs";

    public SequenceOutputsNavItem(DataProvider provider)
    {
        super(provider, LaboratoryService.NavItemCategory.data, "data");
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getLabel()
    {
        return NAME;
    }

    @Override
    public String getRendererName()
    {
        return "linkWithChildren";
    }

    @Override
    public boolean getDefaultVisibility(Container c, User u)
    {
        return getTargetContainer(c).getActiveModules().contains(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.NAME));
    }

    private TreeMap<String, Long> _categories = null;

    private Map<String, Long> getCategories(Container c, User u)
    {
        if (_categories == null)
        {
            UserSchema us = _queryCache.getUserSchema(getTargetContainer(c), u, SequenceAnalysisSchema.SCHEMA_NAME);
            if (us == null)
                return null;

            TableInfo ti = _queryCache.getTableInfo(us.getContainer(), u, SequenceAnalysisSchema.SCHEMA_NAME, SequenceAnalysisSchema.TABLE_OUTPUTFILES);
            SQLFragment sql = new SQLFragment("SELECT t.category, count(*) as total FROM ").append(ti.getFromSQL("t")).append(" GROUP BY t.category");
            SqlSelector ss = new SqlSelector(ti.getSchema(), sql);
            final TreeMap<String, Long> categories = new TreeMap<>();
            ss.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet object) throws SQLException
                {
                    categories.put(object.getObject("category") == null ? "No Category" : object.getString("category"), object.getLong("total"));
                }
            });

            _categories = categories;
        }

        return _categories;

    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject ret = super.toJSON(c, u);
        JSONArray children = new JSONArray();
        Map<String, Long> categories = getCategories(c, u);
        for (String category : categories.keySet())
        {
            children.put(new DetailsUrlWithLabelNavItem(getDataProvider(), category, categories.get(category).toString(), DetailsURL.fromString("/query/executeQuery.view?schemaName=" + SequenceAnalysisSchema.SCHEMA_NAME + "&queryName=" + SequenceAnalysisSchema.TABLE_OUTPUTFILES + ("No Category".equals(category) ?  "&query.category~isblank" : "&query.category~eq=" + category), c), LaboratoryService.NavItemCategory.data, LaboratoryService.NavItemCategory.data.name()).toJSON(c, u));
        }
        ret.put("children", children);

        return ret;
    }

    @Override
    public Long getRowCount(Container c, User u)
    {
        return null;
    }
}
