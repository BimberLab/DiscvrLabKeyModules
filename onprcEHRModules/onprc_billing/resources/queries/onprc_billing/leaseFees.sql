/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
PARAMETERS(StartDate TIMESTAMP, EndDate TIMESTAMP)

SELECT
a.id,
a.date,
a.project,
a.project.account,
a.date as assignmentStart,
a.enddate,
a.projectedReleaseCondition,
a.releaseCondition,
a.assignCondition,
a.ageAtTime.AgeAtTimeYearsRounded as ageAtTime,
'Lease Fees' as category,
CASE
  WHEN a.duration <= 1 THEN (SELECT rowid FROM onprc_billing_public.chargeableItems ci WHERE ci.active = true AND ci.name = 'One Day Lease')
  WHEN a2.id IS NOT NULL THEN (SELECT rowid FROM onprc_billing_public.chargeableItems ci WHERE ci.active = true AND ci.name = 'Animal Lease Fee - TMB')
  ELSE lf.chargeId
END as chargeId,
--special case one-day lease rates.
--TODO: figure out proper duration and use this value
CASE
  WHEN (a.duration = 0 AND a.enddate IS NULL) THEN 1
  WHEN (a.duration <= 1 AND a.enddate IS NULL) THEN a.duration
  ELSE 1
END as quantity,
cast(null as integer) as leaseCharge1,
cast(null as integer) as leaseCharge2,
a.objectid as sourceRecord,
null as chargeType,
null as isAdjustment
FROM study.assignment a

--find overlapping TMB at date of assignment
LEFT JOIN study.assignment a2 ON (
  a.id = a2.id AND a.project != a2.project
  AND a2.dateOnly <= a.dateOnly
  AND a2.endDateCoalesced >= a.dateOnly
  AND a2.project.name = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.TMB_PROJECT')
)

LEFT JOIN onprc_billing.leaseFeeDefinition lf ON (
  lf.assignCondition = a.assignCondition
  AND lf.releaseCondition = a.projectedReleaseCondition
  AND (a.ageAtTime.AgeAtTimeYearsRounded >= lf.minAge OR lf.minAge IS NULL)
  AND (a.ageAtTime.AgeAtTimeYearsRounded < lf.maxAge OR lf.maxAge IS NULL)
  AND lf.active = true
)

WHERE a.dateOnly >= CAST(STARTDATE as DATE) AND a.dateOnly <= CAST(ENDDATE as DATE)
AND a.qcstate.publicdata = true

--add setup fees for all starts
UNION ALL
SELECT
  a.id,
  a.date,
  a.project,
  a.project.account,
  a.date as assignmentStart,
  a.enddate,
  a.projectedReleaseCondition,
  a.releaseCondition,
  a.assignCondition,
  a.ageAtTime.AgeAtTimeYearsRounded as ageAtTime,
  'Lease Setup Fees' as category,
  (SELECT rowid FROM onprc_billing_public.chargeableItems ci WHERE ci.active = true AND ci.name = 'Lease Setup Fees') as chargeId,
  1 as quantity,
  cast(null as integer) as leaseCharge1,
  cast(null as integer) as leaseCharge2,
  a.objectid as sourceRecord,
  null as chargeType,
  null as isAdjustment

FROM study.assignment a

WHERE a.dateOnly >= CAST(STARTDATE as DATE) AND a.dateOnly <= CAST(ENDDATE as DATE)
AND a.qcstate.publicdata = true
--only charge setup fee for leases >24H
AND a.duration > 1

--add released animals that need adjustments
UNION ALL

SELECT
a.id,
a.enddate as date, --use enddate as the date for this charge
a.project,
a.project.account,
a.date as assignmentStart,
a.enddate,
a.projectedReleaseCondition,
a.releaseCondition,
a.assignCondition,
a.ageAtTime.AgeAtTimeYearsRounded as ageAtTime,
'Lease Fees' as category,
(SELECT max(rowid) as rowid FROM onprc_billing_public.chargeableItems ci WHERE ci.name = 'Lease Fee Adjustment' and ci.active = true) as chargeId,
1 as quantity,
lf2.chargeId as leaseCharge1,
lf.chargeId as leaseCharge2,
a.objectid as sourceRecord,
'Adjustment - Automatic' as chargeType,
'Y' as isAdjustment

FROM study.assignment a
LEFT JOIN onprc_billing.leaseFeeDefinition lf
  ON (lf.assignCondition = a.assignCondition
    AND lf.releaseCondition = a.releaseCondition
    AND (a.ageAtTime.AgeAtTimeYearsRounded >= lf.minAge OR lf.minAge IS NULL)
    AND (a.ageAtTime.AgeAtTimeYearsRounded < lf.maxAge OR lf.maxAge IS NULL)
  )

LEFT JOIN onprc_billing.leaseFeeDefinition lf2
  ON (lf2.assignCondition = a.assignCondition
    AND lf2.releaseCondition = a.projectedReleaseCondition
    AND (a.ageAtTime.AgeAtTimeYearsRounded >= lf2.minAge OR lf2.minAge IS NULL)
    AND (a.ageAtTime.AgeAtTimeYearsRounded < lf2.maxAge OR lf2.maxAge IS NULL)
  )

--find overlapping TMB at date of assignment
  LEFT JOIN study.assignment a2 ON (
    a.id = a2.id AND a.project != a2.project
    AND a2.dateOnly <= a.dateOnly
    AND a2.endDateCoalesced >= a.dateOnly
    AND a2.project.name = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.TMB_PROJECT')
  )

WHERE a.releaseCondition != a.projectedReleaseCondition
AND a.enddate is not null AND a.enddateCoalesced >= STARTDATE AND a.enddateCoalesced <= CAST(EndDate as DATE)
AND a.qcstate.publicdata = true AND lf.active = true
AND a2.id IS NULL
