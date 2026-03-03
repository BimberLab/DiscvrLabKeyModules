PARAMETERS(AnalysisId INTEGER)

select
  (CAST(AnalysisId as varchar) || '<>' || a.lineages) as key,
  a.analysis_id,
  a.lineages,
  max(a.totalLineages) as totalLineages,
  a.loci,

  sum(a.total) as total,
  max(a.total_reads) as total_reads,
  round(100 * (cast(sum(a.total) as float) / cast(max(a.total_reads) as float)), 2) as percent,
  group_concat(distinct a.haplotypesWithAllele) as haplotypesWithAllele,

  CAST((select sum(s.total) as total FROM sequenceanalysis.alignment_summary s WHERE s.analysis_id = AnalysisId AND s.rowid IN (
      SELECT distinct asj.alignment_id from sequenceanalysis.alignment_summary_junction asj WHERE asj.analysis_id = AnalysisId AND asj.ref_nt_id.locus = a.loci and asj.status = true
    )
  ) as integer) as total_reads_from_locus,

  round(100 * (cast(sum(a.total) as float) / cast((select sum(s.total) as total FROM sequenceanalysis.alignment_summary s WHERE s.analysis_id = AnalysisId AND s.rowid IN (
      SELECT distinct asj.alignment_id from sequenceanalysis.alignment_summary_junction asj WHERE asj.analysis_id = AnalysisId AND asj.ref_nt_id.locus = a.loci and asj.status = true
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
    cast((select sum(total) as total FROM sequenceanalysis.alignment_summary s WHERE s.analysis_id = AnalysisId) as integer) as total_reads,
    group_concat(distinct hs.haplotype, chr(10)) as haplotypesWithAllele

  from sequenceanalysis.alignment_summary a
  join sequenceanalysis.alignment_summary_junction j ON (j.analysis_id = AnalysisId AND j.alignment_id = a.rowid and j.status = true)
  left join sequenceanalysis.haplotype_sequences hs ON ((
    (hs.name = j.ref_nt_id.lineage AND hs.type = 'Lineage') OR
    (hs.name = j.ref_nt_id.name AND hs.type = 'Allele')
  ) AND hs.haplotype.datedisabled IS NULL)
  WHERE a.analysis_id = AnalysisId
  group by a.analysis_id, a.rowid, a.total

) a

GROUP BY a.analysis_id, a.lineages, a.loci
