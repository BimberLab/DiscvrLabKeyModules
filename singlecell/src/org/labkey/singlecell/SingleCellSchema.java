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

package org.labkey.singlecell;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;

public class SingleCellSchema
{
    private static final SingleCellSchema _instance = new SingleCellSchema();
    public static final String NAME = "singlecell";

    public static final String TABLE_SORTS = "sorts";
    public static final String TABLE_SAMPLES = "samples";
    public static final String TABLE_CDNAS = "cdna_libraries";
    public static final String TABLE_CITE_SEQ_ANTIBODIES = "citeseq_antibodies";
    public static final String TABLE_CITE_SEQ_PANELS = "citeseq_panels";
    public static final String TABLE_STIM_TYPES = "stim_types";
    public static final String TABLE_ASSAY_TYPES = "assay_types";

    public static final String SEQUENCE_SCHEMA_NAME = "sequenceanalysis";
    public static final String TABLE_READSETS = "sequence_readsets";
    public static final String TABLE_BARCODES = "barcodes";
    public static final String TABLE_QUALITY_METRICS = "quality_metrics";

    public static SingleCellSchema getInstance()
    {
        return _instance;
    }

    private SingleCellSchema()
    {

    }

    public DbSchema getSequenceAnalysisSchema()
    {
        return DbSchema.get(SEQUENCE_SCHEMA_NAME, DbSchemaType.Module);
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(NAME, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }
}
