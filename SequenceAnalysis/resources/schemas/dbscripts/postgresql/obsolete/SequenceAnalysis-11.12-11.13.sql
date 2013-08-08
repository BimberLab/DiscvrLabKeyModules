/*
 * Copyright (c) 2011-2012 LabKey Corporation
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


DROP INDEX IF EXISTS sequenceanalysis.nt_snps_ref_nt_position;
DROP INDEX IF EXISTS sequenceanalysis.aa_snps_ref_aa_position;


--we index container and also other FK relationships

DROP INDEX IF EXISTS sequenceanalysis.aa_snps_container;
CREATE INDEX aa_snps_container
ON sequenceanalysis.aa_snps USING btree (container);

DROP INDEX IF EXISTS sequenceanalysis.nt_snps_container;
CREATE INDEX nt_snps_container
ON sequenceanalysis.nt_snps USING btree (container);

DROP INDEX IF EXISTS sequenceanalysis.sequence_alignments_container;
CREATE INDEX sequence_alignments_container
ON sequenceanalysis.sequence_alignments USING btree (container);

DROP INDEX IF EXISTS sequenceanalysis.sequence_analyses_container;
CREATE INDEX sequence_analyses_container
ON sequenceanalysis.sequence_analyses USING btree (container);

DROP INDEX IF EXISTS sequenceanalysis.sequence_coverage_container;
CREATE INDEX sequence_coverage_container
ON sequenceanalysis.sequence_coverage USING btree (container);

DROP INDEX IF EXISTS sequenceanalysis.sequence_reads_container;
CREATE INDEX sequence_reads_container
ON sequenceanalysis.sequence_reads USING btree (container);

--analysis id
DROP INDEX IF EXISTS sequenceanalysis.aa_snps_analysis_id;
CREATE INDEX aa_snps_analysis_id
ON sequenceanalysis.aa_snps USING btree (analysis_id);

DROP INDEX IF EXISTS sequenceanalysis.nt_snps_analysis_id;
CREATE INDEX nt_snps_analysis_id
ON sequenceanalysis.nt_snps USING btree (analysis_id);

DROP INDEX IF EXISTS sequenceanalysis.sequence_alignments_analysis_id;
CREATE INDEX sequence_alignments_analysis_id
ON sequenceanalysis.sequence_alignments USING btree (analysis_id);

DROP INDEX IF EXISTS sequenceanalysis.sequence_coverage_analysis_id;
CREATE INDEX sequence_coverage_analysis_id
ON sequenceanalysis.sequence_coverage USING btree (analysis_id);

DROP INDEX IF EXISTS sequenceanalysis.sequence_reads_analysis_id;
CREATE INDEX sequence_reads_analysis_id
ON sequenceanalysis.sequence_reads USING btree (analysis_id);

--ref_nt_id
DROP INDEX IF EXISTS sequenceanalysis.aa_snps_ref_nt_id;
CREATE INDEX aa_snps_ref_nt_id
ON sequenceanalysis.aa_snps USING btree (ref_nt_id);

DROP INDEX IF EXISTS sequenceanalysis.nt_snps_ref_nt_id;
CREATE INDEX nt_snps_ref_nt_id
ON sequenceanalysis.nt_snps USING btree (ref_nt_id);

DROP INDEX IF EXISTS sequenceanalysis.sequence_alignments_ref_nt_id;
CREATE INDEX sequence_alignments_ref_nt_id
ON sequenceanalysis.sequence_alignments USING btree (ref_nt_id);

DROP INDEX IF EXISTS sequenceanalysis.sequence_coverage_ref_nt_id;
CREATE INDEX sequence_coverage_ref_nt_id
ON sequenceanalysis.sequence_coverage USING btree (ref_nt_id);

--alignment_id
DROP INDEX IF EXISTS sequenceanalysis.aa_snps_alignment_id;
CREATE INDEX aa_snps_alignment_id
ON sequenceanalysis.aa_snps USING btree (alignment_id);

DROP INDEX IF EXISTS sequenceanalysis.nt_snps_alignment_id;
CREATE INDEX nt_snps_alignment_id
ON sequenceanalysis.nt_snps USING btree (alignment_id);

--nt_snp_id
DROP INDEX IF EXISTS sequenceanalysis.aa_snps_nt_snp_id;
CREATE INDEX aa_snps_nt_snp_id
ON sequenceanalysis.aa_snps USING btree (nt_snp_id);

--ref_aa_id
DROP INDEX IF EXISTS sequenceanalysis.aa_snps_ref_aa_id;
CREATE INDEX aa_snps_ref_aa_id
ON sequenceanalysis.aa_snps USING btree (ref_aa_id);

