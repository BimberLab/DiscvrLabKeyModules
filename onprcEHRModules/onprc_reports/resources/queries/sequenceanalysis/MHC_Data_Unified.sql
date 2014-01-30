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
  a.subjectId as Id,
  a.marker as allele,
  null as shortName,
  count(*) as totalTests,
  cast('POS' as varchar) as result,
  cast('SBT' as varchar) as type

FROM assay.GenotypeAssay.Genotype.Data a
WHERE a.run.assayType = 'SBT'
GROUP BY a.subjectid, a.marker