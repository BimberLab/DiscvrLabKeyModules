/*
 * Copyright (c) 2012 LabKey Corporation
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
select
  'Drug Resistance Mutation' as category,
  d.reference_aa || cast(d.aa_position as varchar) as name,
  d.ref_nt_id,
  d.ref_aa_id,
  d.class,
  d.aa_position as aa_start,
  d.aa_position as aa_stop,
  d.aa_insert_index,
  d.reference_aa,
  d.description
from sequenceanalysis.drug_resistance_grouped d

union all

select
  a.category,
  a.name,
  a.ref_nt_id,
  a.ref_aa_id,
  null as class,
  a.aa_start,
  a.aa_stop,
  0 as aa_insert_index,
  null as reference_aa,
  comment as description
from sequenceanalysis.ref_aa_features a