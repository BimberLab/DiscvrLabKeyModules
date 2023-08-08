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
  (CAST(a.analysis_id as varchar) || '<>' || a.lineages) as key,
  a.analysis_id,
  a.lineages,
  max(a.totalLineages) as totalLineages,
  a.loci,

  sum(a.total) as total,
  max(a.total_reads) as total_reads,
  round(100 * (cast(sum(a.total) as float) / cast(max(a.total_reads) as float)), 2) as percent,
  group_concat(distinct a.haplotypesWithAllele) as haplotypesWithAllele,

  CAST((select sum(s.total) as total FROM sequenceanalysis.alignment_summary s WHERE s.analysis_id = a.analysis_id AND s.rowid IN (
      SELECT distinct asj.alignment_id from sequenceanalysis.alignment_summary_junction asj WHERE asj.ref_nt_id.locus = a.loci and asj.status = true
    )
  ) as integer) as total_reads_from_locus,

  round(100 * (cast(sum(a.total) as float) / cast((select sum(s.total) as total FROM sequenceanalysis.alignment_summary s WHERE s.analysis_id = a.analysis_id AND s.rowid IN (
      SELECT distinct asj.alignment_id from sequenceanalysis.alignment_summary_junction asj WHERE asj.ref_nt_id.locus = a.loci and asj.status = true
    )
  ) as float)), 2) as percent_from_locus,
  group_concat(distinct a.rowid, ',') as rowids

FROM (

  select
    a.analysis_id,
    a.rowid,

    group_concat(distinct coalesce(j.ref_nt_id.lineage, j.ref_nt_id.name), chr(10)) as lineages,
    count(distinct j.ref_nt_id.lineage) as totalLineages,
    group_concat(distinct coalesce(j.ref_nt_id.locus, j.ref_nt_id.name), chr(10)) as loci,

    total,
    cast((select sum(total) as total FROM sequenceanalysis.alignment_summary s WHERE s.analysis_id = a.analysis_id) as integer) as total_reads,
    group_concat(distinct hs.haplotype, chr(10)) as haplotypesWithAllele

  from sequenceanalysis.alignment_summary a
  join sequenceanalysis.alignment_summary_junction j ON (j.alignment_id = a.rowid and j.status = true)
  left join sequenceanalysis.haplotype_sequences hs ON ((
    (hs.name = j.ref_nt_id.lineage AND hs.type = 'Lineage') OR
    (hs.name = j.ref_nt_id.name AND hs.type = 'Allele')
  ) AND hs.haplotype.datedisabled IS NULL)
  group by a.analysis_id, a.rowid, a.total

) a

GROUP BY a.analysis_id, a.lineages, a.loci
