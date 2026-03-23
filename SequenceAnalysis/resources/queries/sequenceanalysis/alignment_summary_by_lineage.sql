SELECT
    t.*,
    CASE WHEN t.total_reads_from_locus = 0 THEN 0 ELSE round(100 * (cast(t.total_reads as float) / cast(t.total_reads_from_locus as float)), 2) END as percent_from_locus,

FROM (
    SELECT
      (CAST(a.analysis_id as varchar) || '<>' || a.lineages) as key,
      a.analysis_id,
      max(a.lineages) as lineages,
      coalesce(max(a.totalLineages), 0) as totalLineages,
      a.loci,

      sum(a.total) as total_reads,
      max(a.total_reads_in_analysis) as total_reads_in_analysis,
      CASE WHEN max(a.total_reads_in_analysis) = 0 THEN 0 ELSE round(100 * (cast(sum(a.total) as float) / cast(max(a.total_reads_in_analysis) as float)), 2) END as percent,

      group_concat(a.rowid, ',') as rowids,
      group_concat(distinct a.haplotypesWithAllele) as haplotypesWithAllele,

      max(a.total_reads_from_locus) as total_reads_from_locus,
      max(a.lastModified) as lastModified,
      count(distinct a.rowid) as nAlignments

    FROM (

      select
        ac.analysis_id,
        ac.rowid,

        group_concat(distinct coalesce(ac.lineage, ac.ntName), chr(10)) as lineages,
        count(distinct ac.lineage) as totalLineages,
        group_concat(distinct coalesce(ac.locus, ac.ntName), chr(10)) as loci,

        group_concat(distinct haplotypesWithAllele, chr(10)) as haplotypesWithAllele,

        max(ac.total) as total,
        max(ac.total_reads_in_analysis) as total_reads_in_analysis,
        max(ac.total_reads_from_locus) as total_reads_from_locus,
        max(ac.modified) as lastModified
      from sequenceanalysis.alignment_summary_combined ac
      group by ac.analysis_id, ac.rowid, ac.total
    ) a

    GROUP BY a.analysis_id, a.lineages, a.loci
) t