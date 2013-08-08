SELECT
  s.subjectId as Id,
  s.allele,
  --s.primerPairs,
  s.shortName,
  s.totalRecords as testsPerformed,
  s.status as result,
  cast('SSP' as varchar) as type

FROM assay.SSP_assay.SSP.SSP_Summary s
WHERE s.subjectId is not null

UNION ALL

SELECT
  a.analysis_id.readset.subjectId as Id,
  a.lineages as alleles,
  null as shortName,
  count(*) as totalTests,
  cast('POS' as varchar) as result,
  cast('SBT' as varchar) as type

FROM sequenceanalysis.alignment_summary_by_lineage a
WHERE a.analysis_id.makePublic = true
  and a.percent > 1 and a.totalLineages < 4
  and a.analysis_id.readset.subjectId is not null
  and a.lineages is not null
GROUP BY a.analysis_id.readset.subjectId, a.lineages