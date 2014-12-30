SELECT
  s.subjectId as Id,
  --NOTE: we initially cast a very wide net, allowing animals with either SSP data, or those with any reads imported.
  --The latter can be deceptive, so we also track whether we have published results
  null as hasSBTData
FROM assay.SSP_assay.SSP.Data s
WHERE s.subjectId is not null AND s.result != 'FAIL' and s.result != 'IND'

UNION ALL

SELECT
  a.subjectId as Id,
  null as hasSBTData

FROM sequenceanalysis.sequence_readsets a
WHERE a.subjectId is not null
AND (a.status IS NULL OR a.status != 'Fail')

UNION ALL

SELECT
  a.subjectId as Id,
  true as hasSBTData

FROM assay.GenotypeAssay.Genotype.Data a
WHERE a.run.assayType = 'SBT'