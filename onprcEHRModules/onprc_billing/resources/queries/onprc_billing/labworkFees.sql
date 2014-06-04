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
  e.servicerequested,
  p.chargeId,
  e.objectid as sourceRecord,
  null as chargeCategory,
  e.taskid

FROM study.clinpathRuns e
JOIN onprc_billing.labworkFeeDefinition p ON (p.servicename = e.servicerequested AND p.active = true)

WHERE CAST(e.datefinalized as date) >= CAST(StartDate as date) AND CAST(e.datefinalized as date) <= CAST(EndDate as date)
AND (e.chargetype not in ('Not Billable', 'No Charge', 'Research Staff') or e.chargetype is null)
AND e.qcstate.publicdata = true

UNION ALL

--for any service sent to an outside lab, we have 1 processing charge per distinct sample
SELECT
  e.Id,
  e.dateOnly,
  e.datefinalized as billingDate,
  e.project,
  group_concat(e.servicerequested) as servicerequested,
  (SELECT c.rowid FROM onprc_billing_public.chargeableItems c WHERE c.name = 'Lab Processing Fee') as chargeId,
  null as sourceRecord,
  null as chargeCategory,
  e.taskid

FROM study.clinpathRuns e
WHERE CAST(e.datefinalized as date) >= CAST(StartDate as date) AND CAST(e.datefinalized as date) <= CAST(EndDate as date)
AND e.qcstate.publicdata = true
AND (e.chargetype not in ('Not Billable', 'No Charge', 'Research Staff') or e.chargetype is null)
AND e.servicerequested.outsidelab = true
GROUP BY e.Id, e.dateOnly, e.datefinalized, e.project, e.tissue, e.taskid