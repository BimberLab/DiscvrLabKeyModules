SELECT
  s.subjectId as Id,

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

FROM sequenceanalysis.sequence_readsets a
WHERE a.subjectId is not null