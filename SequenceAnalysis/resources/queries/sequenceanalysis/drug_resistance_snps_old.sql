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
    when aa.depth > 0 THEN
      round((aa.total_reads / aa.depth)*100, 2)
    ELSE 0
  END as percent,
  case
    --when aa.q_aa = ':' then null
    when aa.depth > 0 THEN
      round((aa.total_reads / (aa.depth-aa.incompletecodons))*100, 2)
    ELSE 0
  END as adj_percent,
  (aa.depth-aa.incompletecodons) as adj_depth

FROM (

SELECT
  aa.analysis_id,
  aa.ref_nt_id,
  aa.ref_aa_id,
  aa.ref_aa,
  aa.q_aa,
  aa.ref_aa_position,
  aa.ref_aa_insert_index,
  aa.total_reads,
  cast(case when aa.q_aa = ':' then 0 else aa.total_reads end  as integer) as adj_total_reads,
  aa.nt_positions,

--   aa.nt_snp_ids,

  aa.depth,
  aa.distinct_depths,
  (select count(distinct alignment_id)
  from sequenceanalysis.aa_snps a
  WHERE
      a.analysis_id=aa.analysis_id AND
      a.ref_aa_id.ref_nt_id=aa.ref_nt_id AND
      a.ref_aa_id=aa.ref_aa_id AND
      a.ref_aa_position=aa.ref_aa_position AND
      a.ref_aa_insert_index = 0 AND
      a.q_aa = ':'
  ) as incompletecodons,
  aa."class",
  aa.drug

FROM (
SELECT
  aa.analysis_id,
  aa.ref_aa_id,
  aa.ref_nt_id,
  aa.ref_aa,
  aa.ref_aa_position,
  aa.ref_aa_insert_index,
  group_concat(DISTINCT aa.q_aa) as q_aa,
  cast(count(DISTINCT aa.alignment_id) as numeric) as total_reads,
  group_concat(DISTINCT aa.nt_snp_id.ref_nt_position) as nt_positions,

  dr."class",
  group_concat(DISTINCT dr.drug) as drug,
--   group_concat(DISTINCT aa.nt_snp_id) nt_snp_ids,
  group_concat(DISTINCT sc.depth) as distinct_depths,
  avg(sc.depth) as depth,
  avg(sc.adj_depth) as adj_depth

  FROM sequenceanalysis.aa_snps aa

  JOIN sequenceanalysis.drug_resistance dr
    ON (
      dr.ref_aa_id=aa.ref_aa_id AND
      dr.aa_position=aa.ref_aa_position AND
      dr.aa_insert_index=aa.ref_aa_insert_index AND
      (dr.mutant_aa=aa.q_aa OR dr.mutant_aa='X')
    )

  LEFT JOIN sequenceanalysis.sequence_coverage sc
    ON (
    sc.analysis_id=aa.analysis_id AND
    sc.ref_nt_id=aa.ref_nt_id AND
    sc.ref_nt_position = aa.nt_snp_id.ref_nt_position AND
    sc.ref_nt_insert_index = 0
    )

--   WHERE aa.status=true

  GROUP BY
  aa.analysis_id,
  aa.ref_nt_id,
  aa.ref_aa_id,
  aa.ref_aa,
  aa.ref_aa_position,
  aa.ref_aa_insert_index,
  dr."class"
  --dr.drug
  --aa.nt_snp_id

) aa


) aa

