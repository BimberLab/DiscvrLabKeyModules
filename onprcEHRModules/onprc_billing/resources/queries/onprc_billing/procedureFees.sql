/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
--this query displays all animals co-housed with each housing record
--to be considered co-housed, they only need to overlap by any period of time
--added 2 additional queries
PARAMETERS(StartDate TIMESTAMP, EndDate TIMESTAMP)

SELECT
  e.Id,
  e.date,
  e.datefinalized as billingDate,
  e.project,
  e.chargetype,
  e.assistingstaff,
  e.procedureId,
  p.chargeId,
  e.objectid as sourceRecord,
  e.taskid

FROM study.encounters e
JOIN onprc_billing.procedureFeeDefinition p ON (
  p.procedureId = e.procedureId and
  --we want to either have the chargeType match between tables, or allow NULL to act like a wildcard
  (e.chargetype = p.chargetype OR (p.chargetype IS NULL AND (e.chargetype IS NULL OR e.chargetype NOT IN ('Not Billable', 'No Charge')))) AND
  (e.assistingstaff = p.assistingstaff OR (p.assistingstaff IS NULL AND e.assistingstaff IS NULL)) AND
  p.active = true
)

WHERE CAST(e.datefinalized as date) >= CAST(StartDate as date) AND CAST(e.datefinalized as date) <= CAST(EndDate as date)
--NOTE: datefinalized should account for this.
--AND e.qcstate.publicdata = true

UNION ALL

--Blood draws.  Note: group by task/date to create 1 charge per batch of draws
SELECT
  e.Id,
  e.dateOnly as date,
  CAST(e.datefinalized as date) as billingDate,
  e.project,
  e.chargetype,
  null as assistingstaff,
  null as procedureId,
  (select rowid from onprc_billing_public.chargeableItems ci where ci.name = 'Blood Draw' and ci.active = true) as chargeId,
  max(e.objectid) as sourceRecord,
  e.taskid

FROM study.blood e
WHERE CAST(e.datefinalized as date) >= CAST(StartDate as date) AND CAST(e.datefinalized as date) <= CAST(EndDate as date)
and e.chargetype != 'No Charge' and e.chargetype != 'Research Staff'
and (e.reason IS NULL or e.reason != 'Clinical')
and (e.sampletype IS NULL or e.sampletype != 'Bone Marrow')
AND e.qcstate.publicdata = true
GROUP BY e.Id, e.dateOnly, CAST(e.datefinalized as date), e.project, e.chargetype, e.taskid


UNION ALL

--Bottle Feeding Diet billing - charges for each time the feeding is completed based in enteries in Medication Fee Definition

SELECT
e.Id,
e.date as date,
CAST(e.dateFinalized as date) as billingDate,
e.project,
e.chargetype,
null as assistingstaff,
null as procedureId,
mfd.chargeId.rowId,
max(e.objectid) as sourceRecord,
e.taskid

FROM study.drug e  join Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.onprc_billing.medicationFeeDefinition mfd ON mfd.code = e.code .code
and  mfd.active = true
WHERE
cast(e.datefinalized as Date) >= StartDate  AND CAST(e.datefinalized as date) <= CAST(EndDate as date)
and e.billable = 'yes'
and e.chargetype IS NOT NULL and e.chargetype NOT IN ('Not Billable', 'No Charge') and e.code.meaning like 'bottle%'
GROUP BY e.Id, e.date, CAST(e.dateFinalized as date), e.project, e.chargetype, e.taskid, mfd.chargeId,mfd.code,e.code.code,mfd.code.code,e.code,mfd.chargeId.rowId

UNION ALL

-- Diet billing - charges for each time the diet is completed based in enteries in Medication Fee Definition

SELECT
e.Id,
e.date as date,
CAST(e.dateFinalized as date) as billingDate,
e.project,
e.chargetype,
null as assistingstaff,
null as procedureId,
mfd.chargeId.rowId,
max(e.objectid) as sourceRecord,
e.taskid

FROM study.drug e  join Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.onprc_billing.medicationFeeDefinition mfd ON mfd.code = e.code .code
and  mfd.active = true
WHERE
cast(e.datefinalized as Date) >= StartDate  AND CAST(e.datefinalized as date) <= CAST(EndDate as date)
and e.billable = 'yes'
and e.chargetype IS NOT NULL and e.chargetype NOT IN ('Not Billable', 'No Charge') and e.code.meaning like 'diet%'
GROUP BY e.Id, e.date, CAST(e.dateFinalized as date), e.project, e.chargetype, e.taskid, mfd.chargeId,mfd.code,e.code.code,mfd.code.code,e.code,mfd.chargeId.rowId


UNION ALL

--creates a procedure fee for each time a drug is administered and billable is set to yes

select
e.id,
e.date as date,
CAST(e.dateFinalized as date) as billingDate,
e.project,
e.chargetype,
null as assistingstaff,
null as procedureId,
(select rowid from onprc_billing_public.chargeableItems ci where ci.name = 'Drug Administration' and ci.active = true) as chargeId,
max(e.objectid) as sourceRecord,
e.taskid



FROM study.drug e  --join "/ONPRC/EHR".onprc_billing.medicationFeeDefinition mfd ON mfd.code = e.code .code
left outer join  Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.ehr_lookups.snomed_combo_list s on s.code = e.code.code

--and  mfd.active = true

WHERE
cast(e.datefinalized as Date) >= StartDate  AND CAST(e.datefinalized as date) <= CAST(EndDate as date)
and e.billable = 'yes'
--and e.chargetype IS NOT NULL and e.chargetype NOT IN ('Not Billable', 'No Charge')
and s.categories like '%drugs%'
GROUP BY e.Id, e.date, CAST(e.dateFinalized as date), e.project, e.chargetype, e.taskid, --mfd.chargeId,mfd.code,
e.code.code,e.billable,
--mfd.code.code,
e.code,
s.categories,s.code