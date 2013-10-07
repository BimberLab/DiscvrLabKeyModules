-- NOTE: any changes to the logic the this query should also modify processingGeneticsBloodDraws.sql in ONPRC_Reports
SELECT
  d.Id,
  d.calculated_status,
  CASE
    WHEN f.Id is null THEN false
    ELSE true
  END as hasMhcDrawnFlag,
  f.lastDate as drawnFlagDateAdded,
  timestampdiff('SQL_TSI_DAY', curdate(), f.lastDate) as daysSinceDrawnFlagAdded,

  CASE
    WHEN f2.Id is null THEN false
    ELSE true
  END as hasMhcRedrawFlag,
  f2.lastDate as redrawFlagDateAdded,
  timestampdiff('SQL_TSI_DAY', curdate(), f2.lastDate) as daysSinceRedrawFlagAdded,

  --NOTE: if changing this logic, processingGeneticsBlooddraws.sql should also be updated
  CASE
    WHEN (d.species = 'RHESUS MACAQUE' AND d.geographic_origin = 'India' AND (a.Id IS NOT NULL OR d.gender = 'm')) THEN true
    else false
  END as isMHCRequired,
  CASE
    WHEN m.Id is null THEN false
    ELSE true
  END as hasMHCData,
  CASE
    WHEN mhc.subjectId is null THEN false
    ELSE true
  END as hasFreezerSample,
  CASE
    WHEN (mhc.subjectId is null AND m.Id IS NULL) THEN false
    ELSE true
  END as hasFreezerSampleOrData

FROM study.Demographics d

LEFT JOIN (
  SELECT
  m.Id,
  count(*) as total
  FROM MHC_Data.MHC_Data_Raw m
  GROUP BY m.Id
) m ON (m.Id = d.Id)

LEFT JOIN (
  SELECT
  f.Id,
    max(f.date) as lastDate,
  count(*) as total
  FROM study."Animal Record Flags" f
  where f.isActive = true and f.category = 'Genetics' and f.value = 'MHC Blood Draw Collected'
  GROUP BY f.Id
) f ON (f.Id = d.Id)

LEFT JOIN (
SELECT
  f.Id,
  max(f.date) as lastDate,
  count(*) as total
FROM study."Animal Record Flags" f
where f.isActive = true and f.category = 'Genetics' and f.value = 'MHC Blood Draw Needed'
GROUP BY f.Id
) f2 ON (f2.Id = d.Id)

--join to freezer samples
LEFT JOIN (
  SELECT
    m.subjectId,
    count(*) as total
  FROM DNA_Bank.mhcSamples m
  WHERE m.sampletype IN ('RNA', 'Whole Blood')
  GROUP BY m.subjectId
) mhc ON (mhc.subjectId = d.Id)

--U42
LEFT JOIN (
  SELECT
    a.Id,
    count(*) as total
  FROM study.assignment a
  WHERE a.isActive = true and a.project.name = '0492-02'
  GROUP BY a.Id
) a ON (a.Id = d.Id)
