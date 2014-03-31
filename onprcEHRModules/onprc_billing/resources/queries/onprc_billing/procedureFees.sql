/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
--this query displays all animals co-housed with each housing record
--to be considered co-housed, they only need to overlap by any period of time

PARAMETERS(StartDate TIMESTAMP, EndDate TIMESTAMP)

SELECT
  e.Id,
  e.date,
  e.datefinalized as billingDate,
  e.project,
  e.project.account,
  e.procedureId,
  p.chargeId,
  e.objectid as sourceRecord,
  e.taskid

FROM study.encounters e
JOIN onprc_billing.procedureFeeDefinition p ON (
  p.procedureId = e.procedureId and
  --we want to either have the chargeType match between tables, or allow NULL to act like a wildcard
  (e.chargetype = p.chargetype OR (p.chargetype IS NULL AND e.chargetype != 'Not Billable')) AND
  p.active = true
)

WHERE CAST(e.datefinalized as date) >= CAST(StartDate as date) AND CAST(e.datefinalized as date) <= CAST(EndDate as date)

--AND e.qcstate.publicdata = true

UNION ALL

--Blood draws.  Note: group by task/date to create 1 charge per batch of draws
SELECT
  e.Id,
  e.dateOnly as date,
  e.datefinalized as billingDate,
  e.project,
  e.project.account,
  null as procedureId,
  (select rowid from onprc_billing_public.chargeableItems ci where ci.name = 'Blood Draw' and ci.active = true) as chargeId,
  max(e.objectid) as sourceRecord,
  e.taskid

FROM study.blood e
WHERE CAST(e.datefinalized as date) >= CAST(StartDate as date) AND CAST(e.datefinalized as date) <= CAST(EndDate as date)
and e.chargetype != 'No Charge' and e.chargetype != 'Research Staff'
and (e.reason IS NULL or e.reason != 'Clinical')
AND e.qcstate.publicdata = true
GROUP BY e.Id, e.dateOnly, e.dateFinalized, e.project, e.project.account, e.taskid