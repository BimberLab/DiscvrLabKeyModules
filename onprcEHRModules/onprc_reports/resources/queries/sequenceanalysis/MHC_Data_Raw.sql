SELECT
  s.subjectId as Id,
  s.allele,
  --s.primerPairs,
  s.shortName,
  s.status as result,
  cast('SSP' as varchar) as type,
  true as makePublic

FROM assay.SSP_assay.SSP.SSP_Summary s
WHERE s.subjectId is not null

UNION ALL

SELECT
  a.analysis_id.readset.subjectId as Id,
  a.ref_nt_id.name as alleles,
  null as shortName,
  cast('POS' as varchar) as result,
  cast('SBT' as varchar) as type,
  a.analysis_id.makePublic as makePublic

FROM sequenceanalysis.alignment_summary_junction a
WHERE a.analysis_id.readset.subjectId is not null
