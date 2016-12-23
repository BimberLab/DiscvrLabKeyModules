/*
 * Copyright (c) 2010-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

SELECT
  t.*,
  CASE
    WHEN t.overlappingProjects IS NULL then 1
    WHEN t.tmbAssignments > 0 then 0  --note: when co-assigned to TMB, per diem is never charged.  if TMB is single-assigned, it needs to pay per diem, and will be caught above
    WHEN t.assignedProject IS NULL AND t.overlappingProjects IS NOT NULL THEN 0
    WHEN t.ProjectType != 'Research' and t.overlappingProjectsCategory LIKE '%Research%' Then 0
    WHEN t.ProjectType != 'Research' and t.overlappingProjectsCategory NOT LIKE '%Research%' Then (1.0 / (t.totalOverlappingProjects + 1))
    WHEN t.ProjectType = 'Research' and t.overlappingProjectsCategory NOT LIKE '%Research%' Then 1
    WHEN t.ProjectType = 'Research' and t.overlappingProjectsCategory LIKE '%Research%' Then (1.0 / (t.totalOverlappingResearchProjects + 1))
    ELSE 1
  END as effectiveDays,
  CASE
    WHEN (t.assignedProject IS NULL AND t.overlappingProjects IS NULL) THEN 'Base Grant'
    WHEN t.overlappingProjects IS NULL then 'Single Project'
    WHEN (t.tmbAssignments > 0) then 'Exempt By TMB'
    WHEN (t.isTMBProject = 1 AND t.overlappingProjects IS NOT NULL) then 'Exempt By TMB'
    WHEN t.assignedProject IS NULL AND t.overlappingProjects IS NOT NULL THEN 'Paid By Overlapping Project'
    WHEN t.ProjectType != 'Research' and t.overlappingProjectsCategory LIKE '%Research%' Then 'Paid By Overlapping Project'
    WHEN t.ProjectType != 'Research' and t.overlappingProjectsCategory NOT LIKE '%Research%' Then 'Multiple Resources'
    WHEN t.ProjectType = 'Research' and t.overlappingProjectsCategory NOT LIKE '%Research%' Then 'Single Project'
    WHEN t.ProjectType = 'Research' and t.overlappingProjectsCategory LIKE '%Research%' Then 'Multiple Research'
    ELSE 'Unknown'
  END as category,
  --find overlapping tier flags on that day
  coalesce((
     SELECT group_concat(DISTINCT f.flag.value) as tier
     FROM study.flags f
     --NOTE: allow flags that ended on this date
     WHERE f.Id = t.Id AND f.enddateCoalesced >= t.dateOnly AND f.dateOnly <= t.dateOnly AND f.flag.category = 'Housing Tier'
   ), 'Tier 2') as tier

FROM (

SELECT
    i2.Id,
    CAST(CAST(i2.dateOnly as date) as timestamp) as date,
    i2.dateOnly @hidden,
    coalesce(a.project, (SELECT p.project FROM ehr.project p WHERE p.name = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_GRANT_PROJECT'))) as project,
    a.project as assignedProject,
    max(a.duration) as duration,  --should only have 1 value, no so need to include in grouping
    max(timestampdiff('SQL_TSI_DAY', d.birth, i2.dateOnly)) as ageAtTime,
    a.project.use_Category as ProjectType,
    count(*) as totalAssignmentRecords,
    group_concat(DISTINCT a2.project.displayName) as overlappingProjects,
    count(DISTINCT a2.project) as totalOverlappingProjects,
    sum(CASE WHEN a2.project.use_Category = 'Research' THEN 1 ELSE 0 END) as totalOverlappingResearchProjects,
    group_concat(DISTINCT a2.project.use_category) as overlappingProjectsCategory,
    group_concat(DISTINCT a2.project.protocol) as overlappingProtocols,
    count(h3.room) as totalHousingRecords,
    group_concat(DISTINCT h3.room) as rooms,
    group_concat(DISTINCT h3.cage) as cages,
    group_concat(DISTINCT h3.objectid) as housingRecords,
    group_concat(DISTINCT a.objectid) as assignmentRecords,
    group_concat(DISTINCT h3.room.housingCondition.value) as housingConditions,
    group_concat(DISTINCT h3.room.housingType.value) as housingTypes,
    CASE
      WHEN (count(DISTINCT pdf.chargeId) > 1) THEN null  --this should catch duplicate chargeIds
      --determine if the animal has been bottle fed by checking the flag - bottle fed
      --When (count(*) from study.treatment_Order q1 where q1.id = i2.id and q1.code.value like '%bottle%' and q1.dateonly <=i2.dateOnly) then 'BottleFed' and Animal Is research assigned
     --use the treatmentOtrder to look for BottleFed
      WHEN (Select count(*) from study.treatment_Order t1 where t1.id = i2.id and t1.code.meaning like '%Bottle%' and t1.date <=i2.dateOnly) > 0
   				  and (Select count(*) from study.assignment a3 where a3.id = i2.id and a3.date <= i2.dateOnly and a3.endDateCoalesced > i2.dateOnly and a3.project.Use_Category like '%Research%') > 0
   			  THEN max(pdf.chargeId)
      --if this item supports infants, charge that

      WHEN (count(CASE WHEN pdf.canChargeInfants = true THEN 1 ELSE null END) > 0 AND max(pdf.chargeId) IS NOT NULL) THEN max(pdf.chargeId)
      --otherwise infants are a special rate
      WHEN (max(timestampdiff('SQL_TSI_DAY', d.birth, i2.dateOnly)) < CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.INFANT_PER_DIEM_AGE') AS INTEGER)) THEN (SELECT ci.rowid FROM onprc_billing_public.chargeableItems ci WHERE ci.name = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.INFANT_PER_DIEM'))
      --add quarantine flags, which trump housing type
      WHEN (SELECT count(*) FROM study.flags q WHERE q.Id = i2.Id AND q.flag.value LIKE '%Quarantine%' AND q.dateOnly <= i2.dateOnly AND q.enddateCoalesced >= i2.dateOnly) > 0 THEN (SELECT ci.rowid FROM onprc_billing_public.chargeableItems ci WHERE ci.name = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.QUARANTINE_PER_DIEM'))
      --finally defer to housing condition
      ELSE max(pdf.chargeId)
    END as chargeId,
    max(i2.startDate) as startDate @hidden,
    count(tmb.Id) as tmbAssignments,
    SUM(CASE WHEN a.projectName = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.TMB_PROJECT') THEN 1 ELSE 0 END) as isTMBProject

FROM (
  -- find all distinct animals housed here on each day.  this was moved to be the
  -- first join so we can be sure to include any animal housed here on that day,
  -- as opposed to only assigned animals
  SELECT
    h.Id,
    i.dateOnly,
    max(h.date) as lastHousingStart,
    min(i.startDate) as startDate @hidden
  FROM ldk.dateRange i
  JOIN study.housing h ON (h.dateOnly <= i.dateOnly AND h.enddateCoalesced >= i.dateOnly AND h.qcstate.publicdata = true)
  --WHERE i.dateOnly <= curdate()
  GROUP BY h.Id, i.dateOnly
) i2

JOIN study.demographics d ON (
  i2.Id = d.Id
)

-- housing is a little tricky.  using the query above, we want to find the max start date, on or before this day
-- the housingType from this room will be used
JOIN study.housing h3 ON (h3.Id = i2.Id AND i2.lastHousingStart = h3.date AND h3.qcstate.publicdata = true)

--then join to any assignment record overlapping each day
LEFT JOIN (
  SELECT
    a.lsid,
    a.id,
    a.project,
    a.project.name as projectName,
    a.date,
    a.assignCondition,
    a.releaseCondition,
    a.projectedReleaseCondition,
    a.duration,
    a.enddate,
    a.dateOnly,
    a.enddateCoalesced,
    a.objectid
  FROM study.assignment a

  WHERE a.qcstate.publicdata = true
    --NOTE: we might want to exclude 1-day assignments, or deal with them differently
    --AND a.duration > 0

) a ON (
    i2.Id = a.id AND
    a.dateOnly <= i2.dateOnly
    --assignments end at midnight, so an assignment doesnt count on the current date if it ends on it
    --NOTE: we do need to capture 1-day assignments, so these do count if the start and end are the same day
    AND (a.enddate IS NULL OR a.enddateCoalesced > i2.dateOnly OR (a.dateOnly = i2.dateOnly AND a.enddateCoalesced = i2.dateOnly))
  )

LEFT JOIN (
  --for each assignment, find co-assigned projects on that day
  SELECT
    a2.lsid,
    a2.date,
    a2.enddate,
    a2.id,
    a2.project,
    a2.dateOnly,
    a2.enddateCoalesced
  FROM study.assignment a2
  WHERE
    --NOTE: this has been reversed.  if one-day assignments are exempted from per diems, this should be restored
    --exclude 1-day assignments
    --a2.duration > 1 AND
    a2.qcstate.publicdata = true
) a2 ON (
  i2.id = a2.id
  AND a2.dateOnly <= i2.dateOnly
  AND a.project != a2.project
  --assignments end at midnight, so an assignment doesnt count on the current date if it ends on it
  --we also need to include 1-day assignments
  AND (a2.enddate IS NULL OR a2.enddateCoalesced > i2.dateOnly OR (a2.dateOnly = i2.dateOnly AND a2.enddateCoalesced = i2.dateOnly))
  AND a.lsid != a2.lsid
)

--find overlapping TMB on this date, which overrides the per diem
--note: also include them if the current project is TMB, as this is also exempt
LEFT JOIN study.assignment tmb ON (
  a.id = tmb.id
  AND tmb.dateOnly <= i2.dateOnly
  and tmb.project != a.project
  AND tmb.endDateCoalesced >= i2.dateOnly
  AND tmb.project.name = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.TMB_PROJECT')
)

LEFT JOIN onprc_billing.perDiemFeeDefinition pdf
ON (
  pdf.housingType = h3.room.housingType AND
  pdf.housingDefinition = h3.room.housingCondition AND

  --find overlapping tier flags on that day
  coalesce((
     SELECT group_concat(DISTINCT f.flag.value) as tier
     FROM study.flags f
     --NOTE: allow flags that ended on this date
     WHERE f.Id = i2.Id AND f.enddateCoalesced >= i2.dateOnly AND f.dateOnly <= i2.dateOnly AND f.flag.category = 'Housing Tier'
   ), 'Tier 2') = pdf.tier
)

GROUP BY i2.dateOnly, I2.Id, a.project, a.project.use_Category

) t