/*
 * Copyright (c) 2018 LabKey Corporation
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

package org.labkey.snprc_scheduler;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;

public class Snprc_schedulerSchemaZ
{

    public static final Snprc_schedulerSchemaZ _instance = new Snprc_schedulerSchemaZ();
    public static final String NAME = "snprc_scheduler";
    public static final String TABLE_NAME_TIMELINE = "TimelineTable";
    public static final String TABLE_NAME_TIMELINE_ITEM = "TimelineItem";

    public static Snprc_schedulerSchemaZ getInstance()
    {
        return _instance;
    }

    private Snprc_schedulerSchemaZ()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.snprc_scheduler.Snprc_schedulerSchemaZ.getInstance()
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(NAME, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoTimeline()
    {
        return getSchema().getTable(TABLE_NAME_TIMELINE);
    }
    public TableInfo getTableInfoTimelineItem()
    {
        return getSchema().getTable(TABLE_NAME_TIMELINE_ITEM);
    }
}
