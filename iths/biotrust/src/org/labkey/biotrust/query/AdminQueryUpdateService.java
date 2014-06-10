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
package org.labkey.biotrust.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;

import java.sql.SQLException;
import java.util.Map;

/**
 * User: cnathe
 * Date: 5/20/13
 */
public class AdminQueryUpdateService extends DefaultQueryUpdateService
{
    public AdminQueryUpdateService(TableInfo queryTable, TableInfo dbTable)
    {
        super(queryTable, dbTable);
    }

    @Override
    protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, AdminPermission.class))
            throw new QueryUpdateServiceException("Only admins are allowed to insert into this table.");

        return super.insertRow(user, container, row);
    }

    @Override
    protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, AdminPermission.class))
            throw new QueryUpdateServiceException("Only admins are allowed to update records in this table.");

        return super.updateRow(user, container, row, oldRow);
    }

    @Override
    protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        if (!hasPermission(user, AdminPermission.class))
            throw new QueryUpdateServiceException("Only admins are allowed to delete records from this table.");

        return super.deleteRow(user, container, oldRowMap);
    }
}
