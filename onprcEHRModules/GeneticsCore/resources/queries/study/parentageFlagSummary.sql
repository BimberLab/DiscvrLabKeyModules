-- NOTE: any changes to the logic the this query should also modify processingGeneticsBloodDraws.sql in ONPRC_Reports
SELECT
  d.Id,
  d.calculated_status,
  CASE
    WHEN f.Id is null THEN false
    ELSE true
  END as hasParentageDrawnFlag,
  f.lastDate as drawnFlagDateAdded,
  timestampdiff('SQL_TSI_DAY', curdate(), f.lastDate) as daysSinceDrawnFlagAdded,

  CASE
    WHEN f2.Id is null THEN false
    ELSE true
  END as hasParentageRedrawFlag,
  f2.lastDate as redrawFlagDateAdded,
  timestampdiff('SQL_TSI_DAY', curdate(), f2.lastDate) as daysSinceRedrawFlagAdded,

  --NOTE: if changing this logic, processingGeneticsBlooddraws.sql should also be updated
  true as isParentageRequired,

  CASE
    WHEN gp.Id is null THEN false
    ELSE true
  END as hasParentageCalls,

  CASE
    WHEN pd.subjectId is null THEN false
    ELSE true
  END as hasParentageData,

  CASE
    WHEN freezer.subjectId is null THEN false
    ELSE true
  END as hasFreezerSample,
  CASE
    WHEN (freezer.subjectId is null AND pd.subjectId IS NULL) THEN false
    ELSE true
  END as hasFreezerSampleOrData

FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.study.Demographics d

LEFT JOIN (
  SELECT
    pd.subjectId,
    count(*) as total
  FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.Parentage_Data.Data pd
  WHERE pd.run.method = 'UC Davis'
  GROUP BY pd.subjectId
) pd ON (d.Id = pd.subjectId)

--determine if we have actual genetic parentage calls
LEFT JOIN (
  SELECT
    pd.Id,
    count(distinct pd.relationship) as total
  FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.Study.Parentage pd
  WHERE pd.method = 'Genetic'
  GROUP BY pd.Id
) gp ON (d.Id = gp.Id)

LEFT JOIN (
  SELECT
  f.Id,
    max(f.date) as lastDate,
  count(*) as total
  FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.study."Animal Record Flags" f
  where f.isActive = true and f.category = 'Genetics' and f.value = javaConstant('org.labkey.GeneticsCore.GeneticsCoreManager.PARENTAGE_DRAW_COLLECTED')
  GROUP BY f.Id
) f ON (f.Id = d.Id)

LEFT JOIN (
SELECT
  f.Id,
  max(f.date) as lastDate,
  count(*) as total
FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.study."Animal Record Flags" f
where f.isActive = true and f.category = 'Genetics' and f.value = javaConstant('org.labkey.GeneticsCore.GeneticsCoreManager.PARENTAGE_DRAW_NEEDED')
GROUP BY f.Id
) f2 ON (f2.Id = d.Id)

--join to freezer samples
LEFT JOIN (
  SELECT
    m.subjectId,
    count(*) as total
  FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.DNA_Bank.parentageSamples m
  WHERE m.sampletype IN ('gDNA', 'Whole Blood')
  GROUP BY m.subjectId
) freezer ON (freezer.subjectId = d.Id)

--U42
LEFT JOIN (
  SELECT
    a.Id,
    count(*) as total
  FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.study.assignment a
  WHERE a.isActive = true and a.project.name = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.U42_PROJECT')
  GROUP BY a.Id
) a ON (a.Id = d.Id)
