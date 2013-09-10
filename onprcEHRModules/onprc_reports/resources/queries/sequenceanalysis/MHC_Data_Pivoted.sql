SELECT
  m.Id,
  m.allele,
  GROUP_CONCAT(distinct m.result) as result
FROM (

SELECT
  m.Id,
  m.allele,
  m.result
FROM sequenceanalysis.MHC_Data_Unified m
WHERE m.totalLineages = 1
) m

GROUP BY m.Id, m.allele
PIVOT result by allele IN
(SELECT lineage FROM sequenceanalysis.ref_nt_sequences r WHERE r.subset = 'MHC' and r.locus IN ('MHC-A', 'MHC-B') and r.species = 'Rhesus macaque' and r.lineage is not null)
