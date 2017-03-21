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

  CASE
    WHEN f3.Id is null THEN false
    ELSE true
  END as hasDataNotNeededFlag,
  f3.lastDate as dateDataNotNeededFlagAdded,

  --NOTE: if changing this logic, processingGeneticsBlooddraws.sql should also be updated
  CASE
    WHEN f3.Id is not null THEN false
    WHEN (d.species = 'RHESUS MACAQUE' AND d.Id.age.ageInYears <= 5.0 AND d.geographic_origin = 'India' AND (a.Id IS NOT NULL OR d.gender = 'm')) THEN true
    else false
  END as isMHCRequired,
  CASE
    WHEN m.Id is null THEN false
    ELSE true
  END as hasMHCData,
  CASE
    WHEN m.hasSBTData IS NOT NULL THEN true
    ELSE false
  END as hasSBTData,
  CASE
    WHEN mhc.subjectId is null THEN false
    ELSE true
  END as hasFreezerSample,
  CASE
    WHEN (mhc.subjectId is null AND m.Id IS NULL) THEN false
    ELSE true
  END as hasFreezerSampleOrData

FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.study.Demographics d

LEFT JOIN (
  SELECT
  m.Id,
  count(m.hasSBTData) as hasSBTData
  --count(*) as total
  FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.MHC_Data.MHC_Data_Animals m
  GROUP BY m.Id
) m ON (m.Id = d.Id)

LEFT JOIN (
  SELECT
  f.Id,
    max(f.date) as lastDate,
  --count(*) as total
  FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.study."Animal Record Flags" f
  where f.isActive = true and f.flag.category = 'Genetics' and f.flag.value = javaConstant('org.labkey.GeneticsCore.GeneticsCoreManager.MHC_DRAW_COLLECTED')
  GROUP BY f.Id
) f ON (f.Id = d.Id)

LEFT JOIN (
SELECT
  f.Id,
  max(f.date) as lastDate,
  --count(*) as total
FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.study."Animal Record Flags" f
where f.isActive = true and f.flag.category = 'Genetics' and f.flag.value = javaConstant('org.labkey.GeneticsCore.GeneticsCoreManager.MHC_DRAW_NEEDED')
GROUP BY f.Id
) f2 ON (f2.Id = d.Id)

LEFT JOIN (
SELECT
  f.Id,
  max(f.date) as lastDate
FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.study."Animal Record Flags" f
where f.isActive = true and f.flag.category = 'Genetics' and f.flag.value = javaConstant('org.labkey.GeneticsCore.GeneticsCoreManager.MHC_NOT_NEEDED')
GROUP BY f.Id
) f3 ON (f3.Id = d.Id)

--join to freezer samples
LEFT JOIN (
  SELECT
    m.subjectId,
    --count(*) as total
  FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.MHC_Data.mhcSamples m
  WHERE m.sampletype IN ('RNA', 'Whole Blood')
  GROUP BY m.subjectId
) mhc ON (mhc.subjectId = d.Id)

--U42
LEFT JOIN (
  SELECT
    a.Id,
    --count(*) as total
  FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.study.assignment a
  WHERE a.isActive = true and a.project.name = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.U42_PROJECT')
  GROUP BY a.Id
) a ON (a.Id = d.Id)
