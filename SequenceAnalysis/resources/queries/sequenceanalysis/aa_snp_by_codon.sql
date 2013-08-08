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

select *,
  case
    when aa.depth > 0 THEN round((aa.total_reads / aa.depth)*100, 2)
    ELSE 0
  END as percent,
  case
    when aa.q_aa = ':' then null
    when aa.depth > 0 THEN round((aa.total_reads / (aa.depth-aa.incompletecodons))*100, 2)
    ELSE 0
  END as adj_percent,
  (aa.depth-aa.incompletecodons) as adj_depth
FROM (
SELECT
  aa_inner.analysis_id,
  aa_inner.ref_nt_id,
  aa_inner.ref_aa_id,
  aa_inner.ref_aa,
  aa_inner.ref_aa_position,
  aa_inner.ref_aa_insert_index,
  aa_inner.ref_nt_positions,
  aa_inner.q_aa,
  aa_inner.q_codon,
  aa_inner.total_reads,
  cast(case when aa_inner.q_aa = ':' then 0 else aa_inner.total_reads end as integer) as adj_total_reads,
  aa_inner.depth,
  (select
    count(distinct alignment_id)
    from sequenceanalysis.aa_snps
    WHERE
        aa_snps.analysis_id = aa_inner.analysis_id AND
        aa_snps.ref_nt_id = aa_inner.ref_nt_id AND
        aa_snps.ref_aa_id = aa_inner.ref_aa_id AND
        aa_snps.ref_aa_position = aa_inner.ref_aa_position AND
        aa_snps.ref_aa_insert_index = aa_inner.ref_aa_insert_index AND
        aa_snps.q_aa = ':'
  ) as incompletecodons

FROM (
  SELECT
    aa.analysis_id,
    aa.ref_aa_id,
    aa.ref_nt_id,
    group_concat(DISTINCT nt.ref_nt_position) AS ref_nt_positions,
    aa.ref_aa,
    aa.ref_aa_position,
    aa.ref_aa_insert_index,
    aa.q_aa,
    aa.q_codon,
    --NOTE: subquery not compatible w/ SQLServer, so we use a JOIN instead
--     avg((
--       select sc.depth
--       from sequenceanalysis.sequence_coverage sc
--       WHERE
--         sc.analysis_id=aa.analysis_id AND
--         sc.ref_nt_id=aa.ref_nt_id AND
--         sc.ref_nt_position = nt.ref_nt_position AND
--         --sc.ref_nt_insert_index = aa.nt_snp_id.ref_nt_insert_index
--         sc.ref_nt_insert_index = 0
--     )) as depth,
    avg(sc.depth) as depth,
    cast(count(distinct aa.alignment_id) as float) as total_reads

  FROM sequenceanalysis.aa_snps aa
  JOIN sequenceanalysis.nt_snps nt
    on (aa.nt_snp_id = nt.rowid)

  JOIN sequenceanalysis.sequence_coverage sc
    on (
      sc.analysis_id=aa.analysis_id AND
      sc.ref_nt_id=aa.ref_nt_id AND
      sc.ref_nt_position = nt.ref_nt_position AND
      --sc.ref_nt_insert_index = aa.nt_snp_id.ref_nt_insert_index
      sc.ref_nt_insert_index = 0
    )

  --WHERE aa.status=true

  GROUP BY
    aa.analysis_id,
    aa.ref_nt_id,
    aa.ref_aa_id,
    aa.ref_aa_position,
    aa.ref_aa_insert_index,
    aa.ref_aa,
    aa.q_aa,
    aa.q_codon

  ) aa_inner


) aa
