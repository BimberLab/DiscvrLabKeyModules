/*
 * Copyright (c) 2020 LabKey Corporation
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

package org.labkey.discvrcore;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.logging.LogHelper;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class DiscvrCoreManager
{
    private static final Logger _log = LogHelper.getLogger(DiscvrCoreManager.class, "Messages from DiscvrCoreManager");

    private static final DiscvrCoreManager _instance = new DiscvrCoreManager();

    private DiscvrCoreManager()
    {
        // prevent external construction with a private default constructor
    }

    public static DiscvrCoreManager get()
    {
        return _instance;
    }

    public boolean addCoreContainersIndexes()
    {
        try
        {
            TableInfo ti = CoreSchema.getInstance().getTableInfoContainers();
            addCustomIndex(ti, Arrays.asList("EntityId", "RowId", "Type", "Parent"));
            addCustomIndex(ti, Arrays.asList("Parent", "EntityId", "Type", "RowId"));

            return true;
        }
        catch (Exception e)
        {
            _log.error("Unable to create container indexes", e);
            return false;
        }
    }

    private void addCustomIndex(TableInfo ti, List<String> columnNames) throws Exception
    {
        String idxName = getIndexName(ti.getName(), columnNames);
        if (doesIndexExist(ti, idxName))
        {
            return;
        }

        createIndex(ti, idxName, columnNames);
    }

    private String getIndexName(String tableName, List<String> indexCols)
    {
        return "IDX_discvr_" + tableName + "_" + StringUtils.join(indexCols, "_");
    }

    private boolean doesIndexExist(TableInfo ti, String indexName) throws SQLException
    {
        Set<String> indexNames = new CaseInsensitiveHashSet();
        DatabaseMetaData meta = ti.getSchema().getScope().getConnection().getMetaData();
        try (ResultSet rs = meta.getIndexInfo(ti.getSchema().getScope().getDatabaseName(), ti.getSchema().getName(), ti.getName(), false, false))
        {
            while (rs.next())
            {
                indexNames.add(rs.getString("INDEX_NAME"));
            }
        }

        return indexNames.contains(indexName);
    }

    private void createIndex(TableInfo realTable, String indexName, List<String> columns)
    {
        _log.info("Creating index on column(s): " + StringUtils.join(columns, ", ") + " for table: " + realTable.getName());
        SQLFragment sql = new SQLFragment("CREATE NONCLUSTERED INDEX " + indexName + " ON " + realTable.getSelectName() + "(" + StringUtils.join(columns, ", ") + ")");
        new SqlExecutor(realTable.getSchema()).execute(sql);
    }
}