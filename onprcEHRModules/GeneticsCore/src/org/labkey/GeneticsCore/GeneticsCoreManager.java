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

package org.labkey.GeneticsCore;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeneticsCoreManager
{
    private static final GeneticsCoreManager _instance = new GeneticsCoreManager();

    public static final String DNA_DRAW_COLLECTED = "DNA Bank Blood Draw Collected";
    public static final String DNA_DRAW_NEEDED = "DNA Bank Blood Draw Needed";
    public static final String PARENTAGE_DRAW_COLLECTED = "Parentage Blood Draw Collected";
    public static final String PARENTAGE_DRAW_NEEDED = "Parentage Blood Draw Needed";
    public static final String MHC_DRAW_COLLECTED = "MHC Blood Draw Collected";
    public static final String MHC_DRAW_NEEDED = "MHC Blood Draw Needed";

    private GeneticsCoreManager()
    {
        // prevent external construction with a private default constructor
    }

    public static GeneticsCoreManager get()
    {
        return _instance;
    }

    public void ensureFlagActive(User u, Container c, String flag, Date date, String remark, String[] animalIds)
    {
        final List<String> toAdd = new ArrayList<String>(Arrays.asList(animalIds));
        TableSelector ts = getFlagsTableSelector(c, u, flag, animalIds);
        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                toAdd.remove(rs.getString("Id"));
            }
        });

        try
        {
            TableInfo ti = getFlagsTable(c, u);
            QueryUpdateService qus = ti.getUpdateService();
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();

            for (String animal : toAdd)
            {
                Map<String, Object> row = new CaseInsensitiveHashMap<Object>();
                row.put("Id", animal);
                row.put("date", date);
                row.put("remark", remark);
                row.put("category", "Genetics");
                row.put("value", flag);
                row.put("performedby", u.getDisplayName(u));

                rows.add(row);
            }

            BatchValidationException errors = new BatchValidationException();
            if (rows.size() > 0)
                qus.insertRows(u, ti.getUserSchema().getContainer(), rows, errors, new HashMap<String, Object>());
        }
        catch (QueryUpdateServiceException e)
        {
            throw new RuntimeException(e);
        }
        catch (BatchValidationException e)
        {
            throw new RuntimeException(e);
        }
        catch (DuplicateKeyException e)
        {
            throw new RuntimeException(e);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void terminateFlagsIfExists(User u, Container c, String flag, final Date enddate, String[] animalIds)
    {
        TableSelector ts = getFlagsTableSelector(c, u, flag, animalIds);

        TableInfo ti = getFlagsTable(c, u);
        QueryUpdateService qus = ti.getUpdateService();

        final List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        final List<Map<String, Object>> oldKeys = new ArrayList<Map<String, Object>>();
        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                Map<String, Object> row = new CaseInsensitiveHashMap<Object>();
                row.put("enddate", enddate);
                rows.add(row);

                Map<String, Object> keys = new CaseInsensitiveHashMap<Object>();
                keys.put("lsid", rs.getString("lsid"));
                oldKeys.add(keys);
            }
        });

        try
        {
            if (rows.size() > 0)
                qus.updateRows(u, ti.getUserSchema().getContainer(), rows, oldKeys, new HashMap<String, Object>());
        }
        catch (InvalidKeyException e)
        {
            throw new RuntimeException(e);
        }
        catch (BatchValidationException e)
        {
            throw new RuntimeException(e);
        }
        catch (QueryUpdateServiceException e)
        {
            throw new RuntimeException(e);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private Container getEHRContainer(User u, Container c)
    {
        Module ehrModule = ModuleLoader.getInstance().getModule("ehr");
        String path = ehrModule.getModuleProperties().get("EHRStudyContainer").getEffectiveValue(c);
        if (path == null)
        {
            throw new IllegalArgumentException("EHRStudyContainer property not set");
        }

        Container ehrContainer = ContainerManager.getForPath(path);
        if (ehrContainer == null)
        {
            throw new IllegalArgumentException("EHRStudyContainer property does not match a valid container: " + path);
        }

        return ehrContainer;
    }

    private TableInfo getFlagsTable(Container c, User u)
    {
        Container ehrContainer = getEHRContainer(u, c);

        UserSchema study = QueryService.get().getUserSchema(u, ehrContainer, "study");
        if (study == null)
        {
            throw new IllegalArgumentException("Study schema not found for container: " + ehrContainer.getPath());
        }

        TableInfo flagsTable = study.getTable("Animal Record Flags");
        if (flagsTable == null)
        {
            throw new IllegalArgumentException("Flags table not found for container: " + ehrContainer.getPath());
        }

        return flagsTable;
    }

    private TableSelector getFlagsTableSelector(Container c, User u, String flag, String[] animalIds)
    {
        TableInfo flagsTable = getFlagsTable(c, u);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("category"), "Genetics");
        filter.addCondition(FieldKey.fromString("value"), flag);
        filter.addCondition(FieldKey.fromString("isActive"), true);
        filter.addCondition(FieldKey.fromString("Id"), Arrays.asList(animalIds), CompareType.IN);

        return new TableSelector(flagsTable, PageFlowUtil.set("lsid", "Id", "date", "enddate", "remark"), filter, null);
    }
}
