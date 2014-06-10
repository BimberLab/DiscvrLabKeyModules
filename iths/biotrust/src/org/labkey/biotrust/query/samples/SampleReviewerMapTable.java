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
package org.labkey.biotrust.query.samples;

import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FilteredTable;
import org.labkey.biotrust.query.BioTrustQuerySchema;

/**
 * User: cnathe
 * Date: 8/12/13
 */
public class SampleReviewerMapTable extends FilteredTable<BioTrustQuerySchema>
{
    public SampleReviewerMapTable(TableInfo ti, BioTrustQuerySchema schema)
    {
        super(ti, schema);
        wrapAllColumns(true);
    }
}
