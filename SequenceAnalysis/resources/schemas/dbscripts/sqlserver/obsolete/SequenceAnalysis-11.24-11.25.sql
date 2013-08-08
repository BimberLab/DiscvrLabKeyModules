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

alter table sequenceanalysis.sequence_readsets
  drop column subjectid
alter table sequenceanalysis.sequence_readsets
  add subjectid varchar(200)
;


alter table sequenceanalysis.ref_nt_sequences
  add aliases varchar(1000)
  ;

alter table sequenceanalysis.aa_snps
  add raw_reads integer
alter table sequenceanalysis.aa_snps
  add adj_reads integer
alter table sequenceanalysis.aa_snps
  add raw_depth integer
alter table sequenceanalysis.aa_snps
  add adj_depth integer
alter table sequenceanalysis.aa_snps
  add raw_percent integer
alter table sequenceanalysis.aa_snps
  add adj_percent integer
alter table sequenceanalysis.aa_snps
  add nt_positions varchar(50)
;