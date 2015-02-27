SELECT
  m.Id,
  m.allele,
  GROUP_CONCAT(distinct m.result) as result,
  --CASE WHEN ((SELECT count(a.subjectId) FROM assay.GenotypeAssay.Genotype.Data a WHERE a.run.assayType = 'SBT' AND a.subjectId = m.Id) > 0) THEN 'Y' ELSE 'N' END as hasSBTData

FROM (

SELECT
  m.Id,
  m.allele,
  m.result
FROM sequenceanalysis.MHC_Data_Unified m
WHERE m.allele NOT LIKE ('%' || chr(10) || '%')  --exclude multi-lineage hits
) m

GROUP BY m.Id, m.allele
PIVOT result by allele IN
(SELECT ref_nt_name FROM genotypeassays.primer_pairs WHERE (ref_nt_name LIKE '%-A%' OR ref_nt_name LIKE '%-B%'))
