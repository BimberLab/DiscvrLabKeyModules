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

import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: klum
 * Date: 10/23/13
 */
public class SummarySearchResults extends AbstractSearchResults
{
    /**
     * returns specimen and person totals grouped by specimen category and person category,
     * filtered by the specified form
     */
    public static ResultSet getResults(FacetedSearchForm form, UserSchema listSchema) throws SQLException
    {
        SQLFragment sqlFrag = new SQLFragment("SELECT ");
        sqlFrag.append(LIST_NAME + ".specimenCategory, ");
        sqlFrag.append(LIST_NAME + ".personCategory, ");
        sqlFrag.append("COUNT(DISTINCT " + LIST_NAME + ".personID) as personCount, ");
        sqlFrag.append("COUNT(*) as specimenCount ");
        sqlFrag.append("FROM " + LIST_NAME);
        sqlFrag.append(getFacetedSearchWhereClause(form));
        sqlFrag.append(" GROUP BY ");
        sqlFrag.append(LIST_NAME + ".specimenCategory, ");
        sqlFrag.append(LIST_NAME + ".personCategory");

        return QueryService.get().select(listSchema, sqlFrag.getSQL());
    }

    /**
     * returns specimen and person totals grouped by specimen category,
     * filtered by the specified form
     */
    public static ResultSet getSpecimenTotals(FacetedSearchForm form, UserSchema listSchema) throws SQLException
    {
        SQLFragment sqlFrag = new SQLFragment("SELECT ");
        sqlFrag.append("'total'").append(" AS personCategory, ");
        sqlFrag.append(LIST_NAME + ".specimenCategory, ");
        sqlFrag.append("COUNT(DISTINCT " + LIST_NAME + ".personID) as personCount, ");
        sqlFrag.append("COUNT(*) as specimenCount ");
        sqlFrag.append("FROM " + LIST_NAME);
        sqlFrag.append(getFacetedSearchWhereClause(form));
        sqlFrag.append(" GROUP BY ");
        sqlFrag.append(LIST_NAME + ".specimenCategory");

        return QueryService.get().select(listSchema, sqlFrag.getSQL());
    }

    /**
     * returns specimen and person totals grouped by person category,
     * filtered by the specified form
     */
    public static ResultSet getPersonCategoryTotals(FacetedSearchForm form, UserSchema listSchema) throws SQLException
    {
        SQLFragment sqlFrag = new SQLFragment("SELECT ");
        sqlFrag.append(LIST_NAME + ".personCategory, ");
        sqlFrag.append("COUNT(DISTINCT " + LIST_NAME + ".personID) as personCount, ");
        sqlFrag.append("COUNT(*) as specimenCount ");
        sqlFrag.append("FROM " + LIST_NAME);
        sqlFrag.append(getFacetedSearchWhereClause(form));
        sqlFrag.append(" GROUP BY ");
        sqlFrag.append(LIST_NAME + ".personCategory");

        return QueryService.get().select(listSchema, sqlFrag.getSQL());
    }
}
