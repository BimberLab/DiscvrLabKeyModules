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

alter table sequenceAnalysis.nt_snps
  add column quality_score double precision,
  add column avg_qual double precision
  ;

alter table sequenceAnalysis.aa_snps
  add column avg_qual double precision
  ;

alter table sequenceAnalysis.virus_strains
  drop column workbook
  ;

alter table sequenceAnalysis.sequence_analyses
  add column alignmentFile integer,
  add column snpFile integer
;
