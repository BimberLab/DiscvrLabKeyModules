SELECT
asg.analysis_id,
asg.loci,
group_concat(DISTINCT lineages, ';') as majorAlleles,
0 as sortOrder

FROM sequenceanalysis.alignment_summary_grouped asg
WHERE (
   asg.loci = 'MHC-A' AND asg.percent_from_locus >= 9.0 OR
   asg.loci = 'MHC-B' AND asg.percent_from_locus >= 18.0
)
GROUP BY asg.analysis_id, asg.loci