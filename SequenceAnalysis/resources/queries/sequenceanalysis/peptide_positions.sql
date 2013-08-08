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
  p.sequence,
  a.name,
  a.ref_nt_id,
  a.rowid as ref_aa_id,
  locate(p.sequence, a.sequence) as start,
  (locate(p.sequence, a.sequence) +  length(p.sequence) - 1) as stop
from laboratory.reference_peptides p
left join sequenceanalysis.ref_aa_sequences a ON (a.sequence LIKE '%' || p.sequence || '%')