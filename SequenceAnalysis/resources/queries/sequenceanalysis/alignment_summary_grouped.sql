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
  max(a.lineages) as lineages,
  coalesce(max(a.totalLineages), 0) as totalLineages,
  a.loci,

  sum(a.total) as total_reads,
  sum(a.total_forward) as total_forward,
  sum(a.total_reverse) as total_reverse,
  sum(a.valid_pairs) as valid_pairs,
  max(cast(a.total_reads as integer)) as total_reads_in_analysis,
  --max(a.loci_total_reads) as total_reads_in_analysis_from_locus,
  CASE WHEN max(a.total_reads) = 0 THEN 0 ELSE round(100 * (cast(sum(a.total) as float) / cast(max(a.total_reads) as float)), 2) END as percent,
--   case
--     when (cast(sum(a.total) as float) / cast(max(a.total_reads) as float)) >= .04 THEN 'Major'
--     else 'Minor'
--   end as category,

  group_concat(a.rowid, ',') as rowids,
  group_concat(distinct a.haplotypesWithAllele) as haplotypesWithAllele,

  CAST((select sum(s.total) as total FROM sequenceanalysis.alignment_summary s WHERE s.analysis_id = a.analysis_id AND s.rowid IN (
      SELECT distinct asj.alignment_id from sequenceanalysis.alignment_summary_junction asj WHERE asj.ref_nt_id.locus = a.loci and asj.status = true
    )
  ) as INTEGER) as total_reads_from_locus,

  round(100 * (cast(sum(a.total) as float) / CASE WHEN count(a.lineages) = 0 THEN max(a.total_reads) ELSE cast((select sum(s.total) as total FROM sequenceanalysis.alignment_summary s WHERE s.analysis_id = a.analysis_id AND s.rowid IN (
      SELECT distinct asj.alignment_id from sequenceanalysis.alignment_summary_junction asj WHERE asj.ref_nt_id.locus = a.loci and asj.status = true
    )
  ) as float) END), 2) as percent_from_locus,
  max(lastModified) as lastModified,
  count(distinct a.rowid) as nAlignments,
  max(a.nloci) as nLoci

FROM (

  select
    a.analysis_id,
    a.rowid,

    group_concat(distinct j.ref_nt_id) as alleleIds,
    group_concat(distinct j.ref_nt_id.name, chr(10)) as alleles,
    group_concat(distinct j.ref_nt_id.lineage, chr(10)) as lineages,
    count(distinct j.ref_nt_id.lineage) as totalLineages,
    group_concat(distinct j.ref_nt_id.locus, chr(10)) as loci,
    count(distinct j.ref_nt_id.locus) as nloci,
    group_concat(distinct hs.haplotype, chr(10)) as haplotypesWithAllele,

    total,
    total_forward,
    total_reverse,
    valid_pairs,
    (select sum(total) as total FROM sequenceanalysis.alignment_summary s WHERE s.analysis_id = a.analysis_id) as total_reads,
    max(j.modified) as lastModified
  from sequenceanalysis.alignment_summary a
  left join sequenceanalysis.alignment_summary_junction j ON (j.alignment_id = a.rowid and j.status = true)
  left join sequenceanalysis.haplotype_sequences hs ON ((
    (hs.name = j.ref_nt_id.lineage AND hs.type = 'Lineage') OR
    (hs.name = j.ref_nt_id.name AND hs.type = 'Allele')
  ) AND hs.haplotype.datedisabled IS NULL)
  group by a.analysis_id, a.rowid, a.total, total_forward, total_reverse, valid_pairs

) a

GROUP BY a.analysis_id, a.alleles, a.alleleIds, a.loci
