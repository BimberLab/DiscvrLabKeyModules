SELECT
  t.*,
  CASE
    WHEN (t.max_pct = 0) THEN 'Non-coding'
    WHEN (t.max_s > 0 AND t.max_ns = 0) THEN 'Synonymous'
    WHEN (t.max_s > 0 AND t.pctDiff > 0.9) THEN 'Synonymous'
    --WHEN (t.max_s > 0 AND t.pctDiff2 = 0) THEN 'Synonymous'

    WHEN (t.max_ns > 0) THEN 'Non-Synonymous'
    ELSE 'ERROR'
  END as mutationClass

FROM (
SELECT
t.*,
CASE WHEN (t.max_s = 0) THEN 0 ELSE (t.max_s / (t.max_s + t.max_ns)) END as pctDiff,

FROM (
SELECT
n.rowid,
n.analysis_id,
n.ref_nt_id,
n.ref_nt_name,
n.ref_nt_position,
n.ref_nt_insert_index,
n.ref_nt,
n.q_nt,
n.readcount,
n.depth,
n.adj_depth,
n.pct,
n.pvalue,
n.workbook,
(select group_concat(distinct (a.ref_aa_id.name || ': ' || a.ref_aa || cast(a.ref_aa_position as varchar) || a.q_aa || '-' || cast(a.pct as varchar)), chr(10)) as expr from sequenceanalysis.aa_snps_by_codon a WHERE a.analysis_id = n.analysis_id AND a.ref_nt_id = n.ref_nt_id AND a.ref_nt_positions LIKE ('%' || n.ref_nt || cast(n.ref_nt_position as varchar) || n.q_nt || '%')) as aa_snps,
COALESCE((select max(pct) as expr from sequenceanalysis.aa_snps_by_codon a WHERE a.analysis_id = n.analysis_id AND a.ref_nt_id = n.ref_nt_id AND a.ref_nt_positions LIKE ('%' || n.ref_nt || cast(n.ref_nt_position as varchar) || n.q_nt || '%')), 0.0) as max_pct,

COALESCE((select max(pct) as expr from sequenceanalysis.aa_snps_by_codon a WHERE a.analysis_id = n.analysis_id AND a.ref_nt_id = n.ref_nt_id AND a.ref_nt_positions LIKE ('%' || n.ref_nt || cast(n.ref_nt_position as varchar) || n.q_nt || '%') AND  a.ref_aa = a.q_aa), 0.0) as max_s,
COALESCE((select max(pct) as expr from sequenceanalysis.aa_snps_by_codon a WHERE a.analysis_id = n.analysis_id AND a.ref_nt_id = n.ref_nt_id AND a.ref_nt_positions LIKE ('%' || n.ref_nt || cast(n.ref_nt_position as varchar) || n.q_nt || '%') AND  a.ref_aa != a.q_aa), 0.0) as max_ns,

FROM nt_snps_by_pos n
--where n.pct > 1 and readcount > 4

) t

) t