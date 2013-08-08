SELECT
aa.analysis_id,
aa.ref_aa_id,
aa.ref_nt_id,
aa.ref_aa_position,
aa.ref_aa_insert_index,
aa.ref_nt_positions,
aa.ref_aa,
aa.q_aa,
aa.readcount,
aa.pct,
aa.depth,
aa.adj_depth,
aa.container,

dr."class",
group_concat(DISTINCT dr.drug) as drug,

FROM sequenceanalysis.aa_snps_by_codon aa

JOIN sequenceanalysis.drug_resistance dr
  ON (
    dr.ref_aa_id=aa.ref_aa_id AND
    dr.aa_position=aa.ref_aa_position AND
    dr.aa_insert_index=aa.ref_aa_insert_index AND
    (dr.mutant_aa=aa.q_aa OR dr.mutant_aa='X')
  )

GROUP BY
  aa.analysis_id,
  aa.ref_aa_id,
  aa.ref_nt_id,
  aa.ref_aa_position,
  aa.ref_aa_insert_index,
  aa.ref_nt_positions,
  aa.ref_aa,
  aa.q_aa,
  aa.readcount,
  aa.pct,
  aa.depth,
  aa.adj_depth,
  aa.container,
  dr."class"
