-- NOTE: and changes to the logic the this query should also modify mhcFlagSummary.sql, parentageFlagSummary.sql and dnaFlagSummary.sql
-- in the GeneticsCore module
SELECT
  t.Id,
  t.species,
  t.geographic_origin,
  t.gender,
  t.isU42,
  t.flags,
  t.parentageBloodDrawVol,
  t.mhcBloodDrawVol,
  t.dnaBloodDrawVol,
  (t.parentageBloodDrawVol + t.mhcBloodDrawVol + t.dnaBloodDrawVol) as totalBloodDrawVol,
  'EDTA' as tube_type

FROM (

SELECT
  d.Id,
  d.species,
  d.geographic_origin,
  d.gender,
  CASE
    WHEN (a.Id IS NULL) THEN 'N'
    ELSE 'Y'
  END as isU42,
  f.flags,
  CASE
    WHEN (f.flags LIKE '%Parentage Blood Draw Needed%') THEN 1
    WHEN (f.flags LIKE '%Parentage Not Needed%') THEN 0
    WHEN (f.flags LIKE '%Parentage Blood Draw Collected%') THEN 0
    WHEN (gp.Id IS NOT NULL) THEN 0
    WHEN (snpData.subjectId IS NOT NULL) THEN 0
    WHEN (pd.subjectId IS NOT NULL) THEN 0
    ELSE 1
  END as parentageBloodDrawVol,
  --Note: MHC draws are being taken on all U42 Animals (Males and females) and non-U42 males only
  --Note: if changing this logic, mhcFlagSummary.sql should also be updated
  CASE
    WHEN (f.flags LIKE '%MHC Blood Draw Needed%') THEN 1
    WHEN (f.flags LIKE '%MHC Typing Not Needed%') THEN 0
    WHEN (f.flags LIKE '%MHC Blood Draw Collected%') THEN 0
    WHEN (d.species = 'RHESUS MACAQUE' AND d.Id.age.ageInYears <= 5.0 AND d.geographic_origin = 'India' AND m.Id IS NULL AND (a.Id IS NOT NULL OR d.gender = 'm')) THEN 1
    ELSE 0
  END as mhcBloodDrawVol,
  CASE
    WHEN (f.flags LIKE '%DNA Bank Blood Draw Needed%') THEN 6
    WHEN (f.flags LIKE '%DNA Bank Not Needed%') THEN 0
    WHEN (f.flags LIKE '%DNA Bank Blood Draw Collected%') THEN 0
    WHEN (s.subjectId IS NULL) THEN 6  --timestampdiff('SQL_TSI_DAY', curdate(), d.birth) > 365 AND
    ELSE 0
  END as dnaBloodDrawVol
  
FROM study.Demographics d

--determine if animal has raw STR data performed by UC Davis
LEFT JOIN (
    SELECT
      pd.subjectId
      --count(pd.subjectId) as total
    FROM Parentage_Data.resultSummaryBySubjectAndMethod pd
    WHERE pd.method = 'UC Davis'
    GROUP BY pd.subjectId
) pd ON (d.Id = pd.subjectId)

--determine if animal has raw SNP data
LEFT JOIN (
    SELECT
      snpData.subjectId
      --count(pd.subjectId) as total
    FROM Parentage_SNP_Data.data snpData
    GROUP BY snpData.subjectId
) snpData ON (d.Id = snpData.subjectId)

--determine if we have actual genetic parentage calls
LEFT JOIN (
    SELECT
      pd.Id
      --count(distinct pd.relationship) as total
    FROM Study.Parentage pd
    WHERE (pd.method = 'Genetic' OR pd.method = 'Provisional Genetic')
    GROUP BY pd.Id
) gp ON (d.Id = gp.Id)

LEFT JOIN (
  SELECT
    m.Id
    --count(*) as total
  FROM MHC_Data.MHC_Data_Animals m
  GROUP BY m.Id
) m ON (m.Id = d.Id)

--we want to find any animals with archived gDNA, or blood/buffy coat samples with a total volume >= 5ml
LEFT JOIN (
  SELECT
    t.subjectId
  FROM (
    SELECT
      s.subjectId,
      s.sampleType,
      sum(coalesce(s.quantity, 0)) as quantity
      --count(*) as total
    FROM DNA_Bank.samples s
    WHERE s.dateremoved is null and s.sampleType IN ('gDNA', 'Buffy coat', 'Whole Blood')
    GROUP BY s.subjectId, s.sampleType
    HAVING (
      s.sampletype = 'gDNA' OR
      (s.sampletype = 'Buffy coat' AND sum(coalesce(s.quantity, 0)) >= 5.0) OR
      (s.sampletype = 'Whole Blood' AND sum(coalesce(s.quantity, 0)) >= 5.0)
    )
  ) t
  GROUP BY t.subjectId
) s ON (s.subjectId = d.Id)

--also exclude if this animal has been marked as sent for analysis, which might preceed results being present in the table above
LEFT JOIN (
  SELECT
    f.Id,
    group_concat(distinct f.flag.value, chr(10)) as flags
    --count(*) as total
  FROM study."Animal Record Flags" f
  WHERE f.flag.category = 'Genetics' And f.isActive = true
  GROUP BY f.Id
) f ON (f.Id = d.Id)

--U42
LEFT JOIN (
  SELECT
    a.Id
    --count(*) as total
  FROM study.assignment a
  WHERE a.isActive = true and a.project.name = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.U42_PROJECT')
  GROUP BY a.Id
) a ON (a.Id = d.Id)

WHERE d.calculated_status = 'Alive'

) t