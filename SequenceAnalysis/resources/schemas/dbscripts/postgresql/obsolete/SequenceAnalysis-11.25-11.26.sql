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

alter table sequenceanalysis.sequence_alignments
  drop column haplotype
;

alter table sequenceanalysis.samples
  drop column workbook
;

alter table sequenceanalysis.sequence_analyses
  drop column sampleid
;

alter table sequenceanalysis.haplotype_types
  drop column container
;
alter table sequenceanalysis.haplotype_types
  drop column created
;
alter table sequenceanalysis.haplotype_types
  drop column createdby
;
alter table sequenceanalysis.haplotype_types
  drop column modified
;
alter table sequenceanalysis.haplotype_types
  drop column modifiedby
;

alter table sequenceanalysis.haplotype_sequences
  drop column container
;

drop table if exists sequenceanalysis.haplotype_mapping;
drop table if exists sequenceanalysis.haplotype_definitions;

drop table if exists sequenceanalysis.haplotypes;
CREATE TABLE sequenceanalysis.haplotypes (
  name varchar(200) NOT NULL,
  type varchar(200),
  comment text,

  CreatedBy USERID,
  Created timestamp,
  ModifiedBy USERID,
  Modified timestamp,

  CONSTRAINT PK_haplotypes PRIMARY KEY (name)

);

alter table sequenceanalysis.ref_nt_sequences
  rename column category4 to lineage;
alter table sequenceanalysis.ref_nt_sequences
  rename column category3 to locus;
alter table sequenceanalysis.ref_nt_sequences
  rename column category2 to subset;
alter table sequenceanalysis.ref_nt_sequences
  rename column category1 to category;