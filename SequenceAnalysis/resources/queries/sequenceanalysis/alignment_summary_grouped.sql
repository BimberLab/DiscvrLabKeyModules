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
  a.analysis_id,
  a.alleles,
  a.alleleIds,
  a.lineages,

  sum(a.total) as total_reads,
  sum(a.total_forward) as total_forward,
  sum(a.total_reverse) as total_reverse,
  sum(a.valid_pairs) as valid_pairs,
  max(a.total_reads) as total_reads_in_analysis,
  round(100 * (cast(sum(a.total) as float) / cast(max(a.total_reads) as float)), 2) as percent,
--   case
--     when (cast(sum(a.total) as float) / cast(max(a.total_reads) as float)) >= .04 THEN 'Major'
--     else 'Minor'
--   end as category,

  group_concat(a.rowid) as rowids

FROM (

  select
    a.analysis_id,
    a.rowid,

    group_concat(j.ref_nt_id) as alleleIds,
    group_concat(j.ref_nt_id.name, chr(10)) as alleles,
    group_concat(distinct j.ref_nt_id.lineage, chr(10)) as lineages,

    total,
    total_forward,
    total_reverse,
    valid_pairs,
    (select sum(total) as total FROM sequenceanalysis.alignment_summary s WHERE s.analysis_id = a.analysis_id) as total_reads,

  from sequenceanalysis.alignment_summary a
  left join sequenceanalysis.alignment_summary_junction j ON (j.alignment_id = a.rowid and j.status = true)
  group by a.analysis_id, a.rowid, a.total, total_forward, total_reverse, valid_pairs

) a

GROUP BY a.analysis_id, a.alleles, a.alleleIds, a.lineages
