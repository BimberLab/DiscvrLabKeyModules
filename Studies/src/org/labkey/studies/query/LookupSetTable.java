/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
package org.labkey.studies.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.ldk.table.AbstractDataDefinedTable;
import org.labkey.api.query.QueryUpdateService;

import java.util.Map;

/**
 * User: bimber
 * Date: 1/31/13
 * Time: 4:33 PM
 */
public class LookupSetTable extends AbstractDataDefinedTable<StudiesUserSchema>
{
    private static final String CACHE_KEY = LookupSetTable.class.getName() + "||values";

    private static final String FILTER_COL = "setname";
    private static final String VALUE_COL = "value";

    private String _keyField;

    public static String getCacheKey(Container c)
    {
        return CACHE_KEY + "||" + c.getId();
    }

    public LookupSetTable(StudiesUserSchema schema, SchemaTableInfo table, ContainerFilter cf, String setName, Map<String, Object> map)
    {
        super(schema, table, cf, FILTER_COL, VALUE_COL, setName, setName);

        setTitleColumn(VALUE_COL);

        if (map.containsKey("label"))
            setTitle((String)map.get("label"));

        if (map.containsKey("description"))
            setDescription((String) map.get("description"));

        if (map.containsKey("keyField") && map.get("keyField") != null)
            _keyField = (String)map.get("keyField");

        if (map.containsKey("titleColumn") && map.get("titleColumn") != null)
            _titleColumn = (String)map.get("titleColumn");
        else
            _titleColumn = VALUE_COL;
    }

    @Override
    public LookupSetTable init()
    {
        super.init();

        if (_keyField != null)
        {
            var keyCol = getMutableColumn(_keyField);
            if (keyCol != null)
            {
                keyCol.setKeyField(true);
                getMutableColumnOrThrow("rowid").setKeyField(false);
            }
        }
        else
        {
            getMutableColumnOrThrow(VALUE_COL).setKeyField(false);
            getMutableColumnOrThrow("rowid").setKeyField(true);
        }

        if (_titleColumn != null)
        {
            ColumnInfo titleCol = getColumn(_titleColumn);
            if (titleCol != null)
            {
                setTitleColumn(titleCol.getName());
            }
        }
        LDKService.get().getDefaultTableCustomizer().customize(this);
        return this;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateService(this);
    }
}


