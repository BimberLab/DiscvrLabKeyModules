/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

package org.labkey.sequenceanalysis;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;

public class SequenceAnalysisSchema
{
    private static final SequenceAnalysisSchema _instance = new SequenceAnalysisSchema();
    public static final String SCHEMA_NAME = "sequenceanalysis";
    public static final String TABLE_ANALYSES = "sequence_analyses";
    public static final String TABLE_COVERAGE = "sequence_coverage";
    public static final String TABLE_READSETS = "sequence_readsets";
    public static final String TABLE_ALIGNMENTS = "alignments";
    public static final String TABLE_REF_NT_SEQUENCES = "ref_nt_sequences";
    public static final String TABLE_REF_AA_SEQUENCES = "ref_aa_sequences";
    public static final String TABLE_SEQUENCE_PLATFORMS = "sequence_platforms";
    public static final String TABLE_INSTRUMENT_RUNS = "instrument_runs";
    public static final String TABLE_QUALITY_METRICS = "quality_metrics";
    public static final String TABLE_NT_FEATURES = "ref_nt_features";
    public static final String TABLE_AA_FEATURES = "ref_aa_features";
    public static final String TABLE_DRUG_RESISTANCE = "drug_resistance";
    public static final String TABLE_ALIGNMENT_SUMMARY = "alignment_summary";
    public static final String TABLE_ALIGNMENT_SUMMARY_JUNCTION = "alignment_summary_junction";
    public static final String TABLE_NT_SNP_BY_POS = "nt_snps_by_pos";
    public static final String TABLE_AA_SNP_BY_CODON = "aa_snps_by_codon";
    public static final String TABLE_BARCODES = "barcodes";
    public static final String TABLE_REF_LIBRARIES = "reference_libraries";
    public static final String TABLE_OUTPUTFILES = "outputfiles";
    public static final String TABLE_REF_LIBRARY_MEMBERS = "reference_library_members";
    public static final String TABLE_SAVED_ANALYSES = "saved_analyses";
    public static final String TABLE_LIBRARY_TRACKS = "reference_library_tracks";
    public static final String TABLE_READSET_STATUS = "readset_status";
    public static final String TABLE_CHAIN_FILES = "chain_files";
    public static final String TABLE_LIBRARY_TYPES = "library_types";
    public static final String TABLE_ANALYSIS_SETS = "analysisSets";
    public static final String TABLE_ANALYSIS_SET_MEMBERS = "analysisSetMembers";
    public static final String TABLE_READ_DATA = "readData";

    public static SequenceAnalysisSchema getInstance()
    {
        return _instance;
    }

    public static TableInfo getTable(String name)
    {
        return _instance.getSchema().getTable(name);
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    private SequenceAnalysisSchema()
    {
        // private constructor to prevent instantiation from
        // outside this class: this singleton should only be
        // accessed via org.labkey.SequenceAnalysis.SequenceAnalysisScheme.getInstance()
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }
}
