/*
 * Copyright (c) 2014 LabKey Corporation
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

package org.labkey.blast;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.dialect.SqlDialect;

public class BLASTSchema
{
    private static final BLASTSchema _instance = new BLASTSchema();
    public static final String NAME = "blast";

    public static final String TABLE_DATABASES = "databases";
    public static final String TABLE_BLAST_JOBS = "blast_jobs";

    public static BLASTSchema getInstance()
    {
        return _instance;
    }

    private BLASTSchema()
    {

    }

    public DbSchema getSchema()
    {
        return DbSchema.get(NAME);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }
}
