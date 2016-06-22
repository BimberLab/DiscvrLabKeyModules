SELECT

c.analysis_id,
c.ref_nt_id,
c.ref_aa_id,
c.ref_aa,
c.ref_aa_position,
c.ref_aa_insert_index,
count(*) as totalRecords,

group_concat(c.q_aa, ',') as q_aas,
group_concat(CASE WHEN c.ref_aa = c.ref_aa THEN null ELSE c.q_aa END, ',') as q_non_ref_aas,
group_concat(c.codon, ',') as codons,
sum(c.readcount) as readcount,
max(c.adj_depth) as adj_depth,
100.0 * (CAST(sum(c.readcount) AS DOUBLE) / max(c.adj_depth)) as pct,
100.0 * (CAST(sum(CASE WHEN (c.q_aa = ':' OR c.q_aa = '?' OR c.q_aa = '+') THEN c.readcount ELSE 0 END) AS DOUBLE) / max(c.adj_depth)) as indel_pct,

FROM sequenceanalysis.aa_snps_by_codon c

GROUP BY c.analysis_id, c.ref_nt_id, c.ref_aa_id, c.ref_aa, c.ref_aa_position, c.ref_aa_insert_index
