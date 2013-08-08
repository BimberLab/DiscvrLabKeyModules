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

 ALTER TABLE sequenceAnalysis.sequence_coverage
  ADD COLUMN avgqual_a double precision,
  ADD COLUMN avgqual_t double precision,
  ADD COLUMN avgqual_g double precision,
  ADD COLUMN avgqual_c double precision,
  ADD COLUMN avgqual_n double precision,
  ADD COLUMN avgqual_del double precision
;

ALTER TABLE sequenceAnalysis.nt_snps
  ADD pvalue double precision
;

ALTER TABLE sequenceAnalysis.aa_snps
  ADD column min_pvalue double precision
;

ALTER TABLE sequenceAnalysis.sequence_analyses
  add column reference_library integer
;

ALTER TABLE sequenceAnalysis.sequence_readsets
  add column barcode5 varchar(100)
;

ALTER TABLE sequenceAnalysis.sequence_readsets
  add column barcode3 varchar(100)
;

CREATE INDEX aa_snps_ref_aa_position_codon
ON sequenceanalysis.aa_snps (analysis_id, ref_nt_id, ref_aa_id, ref_aa_position, ref_aa_insert_index, ref_aa, q_aa, q_codon);