SELECT
  s.subjectId as Id,
  --NOTE: we initially cast a very wide net, allowing animals with either SSP data, or those with any reads imported.
  --The latter can be deceptive, so we also track whether we have published results
  null as hasSBTData
FROM assay.SSP_assay.SSP.Data s
WHERE s.subjectId is not null AND s.result != 'FAIL' and s.result != 'IND'

--NOTE: redundant w/ the check for sequence readsets
-- UNION ALL
--
-- SELECT
--   a.subjectId as Id,
--
-- FROM assay.GenotypeAssay.Genotype.Data a
-- WHERE a.run.assayType = 'SBT'

UNION ALL

SELECT
  a.subjectId as Id,
  null as hasSBTData

FROM sequenceanalysis.sequence_readsets a
WHERE a.subjectId is not null

UNION ALL

SELECT
  a1.readset.subjectId as Id,
  true as hasSBTData
  --'Y' as hasSBTData

FROM sequenceanalysis.sequence_analyses a1
WHERE a1.readset.subjectId is not null AND a1.makePublic = true