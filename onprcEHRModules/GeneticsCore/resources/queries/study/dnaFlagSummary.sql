-- NOTE: and changes to the logic the this query should also modify processingGeneticsBloodDraws.sql in ONPRC_Reports
SELECT
  t.Id,
  t.calculated_status,
  CASE
    WHEN (t.activeBloodQuantity >= 5.0 OR t.activeBuffyCoatQuantity >= 5.0 OR t.activeGDNA > 0) THEN true
    ELSE false
  END as hasSample,
  t.activeBlood,
  t.activeBloodQuantity,
  t.activeBuffyCoat,
  t.activeBuffyCoatQuantity,
  t.activeGDNA,
  t.hasBloodDrawnFlag,
  t.flags,

  t.lastDate as drawnFlagDateAdded,
  timestampdiff('SQL_TSI_DAY', curdate(), t.lastDate) as daysSinceDrawnFlagAdded


FROM (

SELECT
  d.Id,
  d.calculated_status,
  f.flags,
  CASE
    WHEN dc.Id is null THEN false
    ELSE true
  END as hasBloodDrawnFlag,
  dc.lastDate,

  coalesce(s1.total, 0) as activeBlood,
  coalesce(s1.totalQuantity, 0) as activeBloodQuantity,
  coalesce(s2.total, 0) as activeBuffyCoat,
  coalesce(s2.totalQuantity, 0) as activeBuffyCoatQuantity,
  coalesce(s3.total, 0) as activeGDNA

FROM study.Demographics d

LEFT JOIN (
  SELECT
  f.Id,
  group_concat(f.value, chr(10)) as flags
  FROM study."Animal Record Flags" f
  where f.isActive = true and f.category = 'Genetics'
  GROUP BY f.Id
) f ON (f.Id = d.Id)

LEFT JOIN (
  SELECT
    f.Id,
    max(f.date) as lastDate,
    count(*) as total
  FROM study."Animal Record Flags" f
  where f.isActive = true and f.category = 'Genetics' and f.value = 'DNA Bank Blood Draw Collected'
  GROUP BY f.Id
) dc ON (dc.Id = d.Id)


LEFT JOIN (
SELECT
  s.subjectId,
  s.sampleType,
  count(*) as total,
  sum(coalesce(s.quantity, 0)) as totalQuantity
FROM DNA_Bank.samples s
WHERE s.dateremoved is null and sampleType = 'Whole Blood' and s.container.title = 'DNA Bank'
GROUP BY s.subjectId
) s1 ON (s1.subjectId = d.Id)

LEFT JOIN (
SELECT
  s.subjectId,
  s.sampleType,
  count(*) as total,
  sum(coalesce(s.quantity, 0)) as totalQuantity
FROM DNA_Bank.samples s
WHERE s.dateremoved is null and sampleType = 'Buffy Coat' and s.container.title = 'DNA Bank'
GROUP BY s.subjectId
) s2 ON (s2.subjectId = d.Id)

LEFT JOIN (
SELECT
  s.subjectId,
  s.sampleType,
  count(*) as total
FROM DNA_Bank.samples s
WHERE s.dateremoved is null and s.sampleType = 'gDNA' and s.container.title = 'DNA Bank'
GROUP BY s.subjectId
) s3 ON (s3.subjectId = d.Id)

) t