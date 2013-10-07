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

FROM study.Demographics d

LEFT JOIN (
  SELECT
    pd.subjectId,
    count(*) as total
  FROM Parentage_Data.Data pd
  WHERE pd.run.method = 'UC Davis'
  GROUP BY pd.subjectId
) pd ON (d.Id = pd.subjectId)

--determine if we have actual genetic parentage calls
LEFT JOIN (
  SELECT
    pd.Id,
    count(distinct pd.relationship) as total
  FROM Study.Parentage pd
  WHERE pd.method = 'Genetic'
  GROUP BY pd.Id
) gp ON (d.Id = gp.Id)

LEFT JOIN (
  SELECT
  f.Id,
    max(f.date) as lastDate,
  count(*) as total
  FROM study."Animal Record Flags" f
  where f.isActive = true and f.category = 'Genetics' and f.value = 'Parentage Blood Draw Collected'
  GROUP BY f.Id
) f ON (f.Id = d.Id)

LEFT JOIN (
SELECT
  f.Id,
  max(f.date) as lastDate,
  count(*) as total
FROM study."Animal Record Flags" f
where f.isActive = true and f.category = 'Genetics' and f.value = 'Parentage Blood Draw Needed'
GROUP BY f.Id
) f2 ON (f2.Id = d.Id)

--join to freezer samples
LEFT JOIN (
  SELECT
    m.subjectId,
    count(*) as total
  FROM DNA_Bank.parentageSamples m
  WHERE m.sampletype IN ('gDNA', 'Whole Blood')
  GROUP BY m.subjectId
) freezer ON (freezer.subjectId = d.Id)

--U42
LEFT JOIN (
  SELECT
    a.Id,
    count(*) as total
  FROM study.assignment a
  WHERE a.isActive = true and a.project.name = '0492-02'
  GROUP BY a.Id
) a ON (a.Id = d.Id)
