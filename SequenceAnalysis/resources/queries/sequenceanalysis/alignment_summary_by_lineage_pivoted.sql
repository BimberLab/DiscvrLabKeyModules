SELECT
  a.analysis_id,
  a.lineages,
  sum(a.percent) as percent

FROM sequenceanalysis.alignment_summary_by_lineage a

GROUP BY a.analysis_id, a.lineages
PIVOT percent BY lineages