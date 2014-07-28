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

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.Pair;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: klum
 * Date: 10/23/13
 */
public abstract class AbstractSearchResults
{
    public static final String LIST_NAME = "Samples";

    @Nullable
    public static Pair<Integer, Integer> getAllTotals(FacetedSearchForm form, UserSchema listSchema) throws SQLException
    {
        SQLFragment sqlFrag = new SQLFragment("SELECT ");
        sqlFrag.append("COUNT(DISTINCT " + LIST_NAME + ".personID) as personCount, ");
        sqlFrag.append("COUNT(*) as specimenCount ");
        sqlFrag.append("FROM " + LIST_NAME);
        sqlFrag.append(getFacetedSearchWhereClause(form));

        try (ResultSet rs = QueryService.get().select(listSchema, sqlFrag.getSQL()))
        {
            while (rs.next())
            {
                int personCount = rs.getInt("personCount");
                int specimenCount = rs.getInt("specimenCount");

                return new Pair<Integer, Integer>(specimenCount, personCount);
            }
        }
        return null;
    }

    protected static SQLFragment getFacetedSearchWhereClause(FacetedSearchForm form)
    {
        SQLFragment sqlFrag = new SQLFragment(" WHERE ");
        if(form.getFilters().size() > 0)
        {
            boolean first = true;
            for (String column : form.getFilters().keySet())
            {
                if(!first)
                {
                    sqlFrag.append(" AND ");
                }
                else
                {
                    first = false;
                }

                Object value = form.getFilters().get(column);

                if (column.equals("specimenCategory"))
                {
                    String delim = "";
                    sqlFrag.append(LIST_NAME).append(".").append(column).append(" IN(");
                    for (Object param : ((JSONArray)value).toArray())
                    {
                        sqlFrag.append(delim).append("'").append(param.toString()).append("'");
                        delim = ",";
                    }
                    sqlFrag.append(")");
                }
                else
                {
                    value = value == null ? " IS NULL" : "='" + form.getFilters().get(column) + "'";
                    sqlFrag.append(LIST_NAME).append(".").append(column).append(value);
                }
            }
        }
        else
        {
            sqlFrag.append(LIST_NAME + ".specimenCategory IN(SELECT DISTINCT " + LIST_NAME + ".specimenCategory FROM " + LIST_NAME + ") ");
        }
        return sqlFrag;
    }

    public static class FacetedSearchForm implements CustomApiForm
    {
        private Map<String, Object> filters = Collections.emptyMap();

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            filters = new HashMap<String, Object>();

            for (Map.Entry<String, Object> entry : props.entrySet())
            {
                filters.put(entry.getKey(), entry.getValue());
            }
        }

        public Map<String, Object> getFilters()
        {
            return filters;
        }
    }
}
