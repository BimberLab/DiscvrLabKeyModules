/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.QueryCache;
import org.labkey.api.security.User;

/**
 * User: bimber
 * Date: 4/14/13
 * Time: 9:33 AM
 */
public class QueryTabbedReportItem extends TabbedReportItem
{
    private String _schemaName;
    private String _queryName;

    public QueryTabbedReportItem(QueryCache cache, DataProvider provider, String schemaName, String queryName, String label, String reportCategory)
    {
        super(provider, queryName, label, reportCategory);
        _queryCache = cache;
        _schemaName = schemaName;
        _queryName = queryName;
    }

    public QueryTabbedReportItem(QueryCache cache, DataProvider provider, String label, String reportCategory, TableInfo ti)
    {
        this(cache, provider, ti.getUserSchema().getSchemaName(), ti.getName(), label, reportCategory);

        _queryCache.cache(ti);
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        Container targetContainer = getTargetContainer(c) == null ? c : getTargetContainer(c);
        TableInfo ti = _queryCache.getTableInfo(targetContainer, u, getSchemaName(), getQueryName());
        if (ti == null)
        {
            return null;
        }

        inferColumnsFromTable(ti);
        JSONObject json = super.toJSON(c, u);

        json.put("schemaName", getSchemaName());
        json.put("queryName", getQueryName());
        String viewName = getDefaultViewName(c, getOwnerKey());
        if (viewName != null)
        {
            json.put("viewName", viewName);
        }

        return json;
    }
}
