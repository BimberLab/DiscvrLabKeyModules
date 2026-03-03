SELECT
    t.*,
    CASE WHEN t.total_reads_from_locus = 0 THEN 0 ELSE round(100 * (cast(t.total_reads as float) / cast(t.total_reads_from_locus as float)), 2) END as percent_from_locus,

FROM (
    SELECT
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
      max(a.total_reads_in_analysis) as total_reads_in_analysis,
      CASE WHEN max(a.total_reads_in_analysis) = 0 THEN 0 ELSE round(100 * (cast(sum(a.total) as float) / cast(max(a.total_reads_in_analysis) as float)), 2) END as percent,

      group_concat(a.rowid, ',') as rowids,
      group_concat(distinct a.haplotypesWithAllele) as haplotypesWithAllele,

      max(a.total_reads_from_locus) as total_reads_from_locus,
      max(a.lastModified) as lastModified,
      count(distinct a.rowid) as nAlignments,
      max(a.nloci) as nLoci

    FROM (

      select
        ac.analysis_id,
        ac.rowid,

        group_concat(distinct ac.ref_nt_id) as alleleIds,
        group_concat(distinct ac.ntName, chr(10)) as alleles,
        group_concat(distinct ac.lineage, chr(10)) as lineages,
        count(distinct ac.lineage) as totalLineages,
        group_concat(distinct ac.locus, chr(10)) as loci,
        count(distinct ac.locus) as nloci,
        group_concat(distinct haplotypesWithAllele, chr(10)) as haplotypesWithAllele,

        max(ac.total) as total,
        max(ac.total_forward) as total_forward,
        max(ac.total_reverse) as total_reverse,
        max(ac.valid_pairs) as valid_pairs,
        max(ac.total_reads_in_analysis) as total_reads_in_analysis,
        max(ac.total_reads_from_locus) as total_reads_from_locus,
        max(ac.modified) as lastModified
      from sequenceanalysis.alignment_summary_combined ac
      group by ac.analysis_id, ac.rowid
    ) a

    GROUP BY a.analysis_id, a.alleles, a.alleleIds, a.loci
) t