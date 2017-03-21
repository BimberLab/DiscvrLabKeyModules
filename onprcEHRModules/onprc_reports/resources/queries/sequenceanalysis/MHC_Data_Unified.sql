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

UNION ALL

SELECT
  DISTINCT s.subjectId as Id,
  p.ref_nt_name as allele,
  null as shortName,
  1 as totalTests,
  cast('NEG' as varchar) as result,
  cast('SBT' as varchar) as type

--we want any IDs with SBT data, but lacking data for these special-cased markers
FROM genotypeassays.primer_pairs p
FULL JOIN (SELECT DISTINCT subjectId FROM assay.GenotypeAssay.Genotype.Data s WHERE s.run.assayType = 'SBT') s ON (1=1)
LEFT JOIN assay.GenotypeAssay.Genotype.Data a ON (a.run.assayType = 'SBT' AND p.ref_nt_name = a.marker AND a.subjectid = s.subjectid)
WHERE a.rowid IS NULL AND (p.ref_nt_name LIKE 'Mamu-A%' OR p.ref_nt_name LIKE 'Mamu-B%')

UNION ALL

SELECT

  t.subjectId as Id,
  t.marker as allele,
  null as shortName,
  count(*) as totalTests,
  t.result,
  GROUP_CONCAT(distinct t.assaytype) as type

FROM geneticscore.mhc_data t
WHERE t.datatype = 'Lineage'
GROUP BY t.subjectid, t.marker, t.result