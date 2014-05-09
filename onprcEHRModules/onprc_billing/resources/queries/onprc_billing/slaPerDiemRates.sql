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
PARAMETERS(EndDate TIMESTAMP)

SELECT
  p.date,
  p.project,
  p.project.account,
  p.chargeId,
  p.sourceRecord,
  null as chargeType,
  p.cagecount,
  p.startDate,
  p.endDate,

  ci.name as item,
  ci.category as category,
  coalesce(e.unitCost, cr.unitCost) as unitCost,
  p.cagecount as quantity,
  ci.name as comment,
  (p.cagecount * coalesce(e.unitCost, cr.unitCost)) as totalcost,

  cast(ce.account as varchar(200)) as creditAccount,
  ce.rowid as creditAccountId,
  coalesce(p.project.account.investigatorId, p.project.investigatorId) as investigatorId,
  CASE
    WHEN e.unitCost IS NOT NULL THEN 'Y'
    ELSE null
  END as isExemption,
  CASE
    WHEN (e.unitCost IS NULL AND cr.unitCost IS NULL) THEN 'Y'
    ELSE null
  END as lacksRate,
  e.rowid as exemptionId,
  CASE WHEN e.rowid IS NULL THEN cr.rowid ELSE null END as rateId,
  null as isMiscCharge,
  null as isAdjustment,
  CASE WHEN p.project.account IS NULL THEN 'Y' ELSE null END as isMissingAccount,
  CASE WHEN ifdefined(p.project.account.fiscalAuthority.faid) IS NULL THEN 'Y' ELSE null END as isMissingFaid,
  CASE
    WHEN ifdefined(p.project.account.aliasEnabled) IS NULL THEN 'N'
    WHEN ifdefined(p.project.account.aliasEnabled) != 'Y' THEN 'N'
    ELSE null
  END as isAcceptingCharges,
  CASE
    WHEN (ifdefined(p.project.account.budgetStartDate) IS NOT NULL AND CAST(ifdefined(p.project.account.budgetStartDate) as date) > CAST(p.date as date)) THEN 'Prior To Budget Start'
    WHEN (ifdefined(p.project.account.budgetEndDate) IS NOT NULL AND CAST(ifdefined(p.project.account.budgetEndDate) as date) < CAST(p.date as date)) THEN 'After Budget End'
    WHEN (ifdefined(p.project.account.projectStatus) IS NOT NULL AND ifdefined(p.project.account.projectStatus) != 'ACTIVE' AND ifdefined(p.project.account.projectStatus) != 'No Cost Ext' AND ifdefined(p.project.account.projectStatus) != 'Partial Setup') THEN 'Grant Project Not Active'
    ELSE null
  END as isExpiredAccount,
  CASE WHEN (TIMESTAMPDIFF('SQL_TSI_DAY', p.date, curdate()) > 45) THEN 'Y' ELSE null END as isOldCharge

FROM onprc_billing.slaPerDiems p

LEFT JOIN onprc_billing_public.chargeableItems ci ON (
    p.chargeId = ci.rowid
)

LEFT JOIN onprc_billing_public.chargeRates cr ON (
    CAST(p.date AS DATE) >= CAST(cr.startDate AS DATE) AND
    (CAST(p.date AS DATE) <= cr.enddateCoalesced OR cr.enddate IS NULL) AND
    p.chargeId = cr.chargeId
)

LEFT JOIN onprc_billing_public.chargeRateExemptions e ON (
    CAST(p.date AS DATE) >= CAST(e.startDate AS DATE) AND
    (CAST(p.date AS DATE) <= e.enddateCoalesced OR e.enddate IS NULL) AND
    p.chargeId = e.chargeId AND
    p.project = e.project
)

LEFT JOIN onprc_billing_public.creditAccount ce ON (
    CAST(p.date AS DATE) >= CAST(ce.startDate AS DATE) AND
    (CAST(p.date AS DATE) <= ce.enddateCoalesced OR ce.enddate IS NULL) AND
    p.chargeId = ce.chargeId
)

UNION ALL

--add misc charges
SELECT
  mc.date,
  mc.project,
  mc.account,
  mc.chargeId,
  mc.sourceRecord,
  mc.chargeType,
  null as cagecount,
  null as startDate,
  null as endDate,
  mc.item,
  mc.category,
  mc.unitcost,
  mc.quantity,
  mc.comment,
  mc.totalcost,

  mc.creditAccount,
  mc.creditAccountId,
  mc.investigatorId,
  mc.isExemption,
  mc.lacksRate,
  mc.exemptionId,
  mc.rateId,
  'Y' as isMiscCharge,
  mc.isAdjustment,
  mc.isMissingAccount,
  mc.isMissingFaid,
  mc.isAcceptingCharges,
  mc.isExpiredAccount,
  mc.isOldCharge

FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.onprc_billing.miscChargesFeeRateData mc
WHERE cast(mc.billingDate as date) >= CAST(StartDate as date) AND cast(mc.billingDate as date) <= CAST(EndDate as date)
AND mc.category = 'Small Animal Per Diem'