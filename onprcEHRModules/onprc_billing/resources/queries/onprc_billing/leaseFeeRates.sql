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
SELECT
  p.id,
  p.date,
  p.enddate,
  p.project,
  p.project.account,
  p.projectedReleaseCondition,
  p.releaseCondition,
  p.assignCondition,
  p.releaseType,
  p.ageAtTime,
  p.category,
  p.chargeId,
  p.chargeId.name as item,

  p.leaseCharge1,
  p.leaseCharge2,
  p.sourceRecord,
  p.chargeType,
  CASE
    WHEN p.project.displayName = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_GRANT_PROJECT') THEN 0
    --handle adjustments and non-adjustments separately
    WHEN (p.isAdjustment IS NULL) THEN coalesce(e.unitCost, cr.unitCost)
    ELSE (CAST(coalesce(e3.unitCost, cr3.unitCost) as double) - cast(coalesce(e2.unitCost, cr2.unitCost) as double))
  END as unitCost,
  p.quantity,
  CASE
    WHEN p.project.displayName = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_GRANT_PROJECT') THEN 0
    WHEN (p.isAdjustment IS NULL) THEN (p.quantity * coalesce(e.unitCost, cr.unitCost))
    ELSE (CAST(coalesce(e3.unitCost, cr3.unitCost) as double) - cast(coalesce(e2.unitCost, cr2.unitCost) as double))
  END as totalcost,

  cast(ce.account as varchar(200)) as creditAccount,
  ce.rowid as creditAccountId,
  null as comment,
  coalesce(p.project.account.investigatorId, p.project.investigatorId) as investigatorId,
  CASE
    WHEN e.rowid IS NOT NULL THEN 'Y'
    ELSE null
  END as isExemption,
  CASE
    --handle adjustments and non-adjustments separately
    WHEN (p.isAdjustment IS NULL AND coalesce(e.unitCost, cr.unitCost) is null) THEN 'Y'
    WHEN (p.isAdjustment IS NOT NULL AND (coalesce(e3.unitCost, cr3.unitCost) IS NULL OR coalesce(e2.unitCost, cr2.unitCost) IS NULL)) THEN 'Y'
    ELSE null
  END as lacksRate,
  CASE
    WHEN (p.category = 'Lease Fees' or p.category = 'Lease Setup Fee' or p.category = 'Lease Setup Fees') AND e.rowid IS NULL THEN cr.rowId
    ELSE null
  END as rateId,
  CASE
    WHEN (p.category = 'Lease Fees' or p.category = 'Lease Setup Fee' or p.category = 'Lease Setup Fees') THEN e.rowid
    ELSE null
  END as exemptionId,
  null as isMiscCharge,
  p.isAdjustment,
  CASE WHEN p.project.account IS NULL THEN 'Y' ELSE null END as isMissingAccount,
  CASE WHEN ifdefined(p.project.account.fiscalAuthority.faid) IS NULL THEN 'Y' ELSE null END as isMissingFaid,
  CASE
    WHEN ifdefined(p.project.account.aliasEnabled) IS NULL THEN 'N'
    WHEN ifdefined(p.project.account.aliasEnabled) != 'Y' THEN 'N'
    ELSE null
  END as isAcceptingCharges,
  CASE
    --WHEN ifdefined(p.project.account.budgetStartDate) IS NULL THEN null
    WHEN (ifdefined(p.project.account.budgetStartDate) IS NOT NULL AND CAST(ifdefined(p.project.account.budgetStartDate) as date) > CAST(p.date as date)) THEN 'Prior To Budget Start'
    WHEN (ifdefined(p.project.account.budgetEndDate) IS NOT NULL AND CAST(ifdefined(p.project.account.budgetEndDate) as date) < CAST(p.date as date)) THEN 'After Budget End'
    WHEN (ifdefined(p.project.account.projectStatus) IS NOT NULL AND ifdefined(p.project.account.projectStatus) != 'ACTIVE' AND ifdefined(p.project.account.projectStatus) != 'No Cost Ext' AND ifdefined(p.project.account.projectStatus) != 'Partial Setup') THEN 'Grant Project Not Active'
    ELSE null
  END as isExpiredAccount,
  CASE WHEN (TIMESTAMPDIFF('SQL_TSI_DAY', p.date, curdate()) > 45) THEN 'Y' ELSE null END as isOldCharge,
  p.datefinalized,
  p.enddatefinalized

FROM onprc_billing.leaseFees p

--the first charge
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

--the original charge (for adjustments)
LEFT JOIN onprc_billing_public.chargeRates cr2 ON (
    CAST(p.date AS DATE) >= CAST(cr2.startDate AS DATE) AND
    (CAST(p.date AS DATE) <= cr2.enddateCoalesced OR cr2.enddate IS NULL) AND
    p.leaseCharge1 = cr2.chargeId
)

LEFT JOIN onprc_billing_public.chargeRateExemptions e2 ON (
    CAST(p.date AS DATE) >= CAST(e2.startDate AS DATE) AND
    (CAST(p.date AS DATE) <= e2.enddateCoalesced OR e2.enddate IS NULL) AND
    p.leaseCharge1 = e2.chargeId AND
    p.project = e2.project
)
--EO original charge

--the final charge (for adjustments)
LEFT JOIN onprc_billing_public.chargeRates cr3 ON (
  CAST(p.date AS DATE) >= CAST(cr3.startDate AS DATE) AND
  (CAST(p.date AS DATE) <= cr3.enddateCoalesced OR cr3.enddate IS NULL) AND
  p.leaseCharge2 = cr3.chargeId
)

LEFT JOIN onprc_billing_public.chargeRateExemptions e3 ON (
  CAST(p.date AS DATE) >= CAST(e3.startDate AS DATE) AND
  (CAST(p.date AS DATE) <= e3.enddateCoalesced OR e3.enddate IS NULL) AND
  p.leaseCharge2 = e3.chargeId AND
  p.project = e3.project
)
--EO final charge

LEFT JOIN onprc_billing_public.creditAccount ce ON (
    CAST(p.date AS DATE) >= CAST(ce.startDate AS DATE) AND
    (CAST(p.date AS DATE) <= ce.enddateCoalesced OR ce.enddate IS NULL) AND
    p.chargeId = ce.chargeId
)

UNION ALL

--add misc charges
SELECT
  mc.id,
  mc.date,
  null as enddate,
  mc.project,
  mc.account,
  null as projectedReleaseCondition,
  null as releaseCondition,
  null as assignCondition,
  null as releaseType,
  null as ageAtTime,
  mc.category,
  mc.chargeId,
  mc.item,

  null as leaseCharge1,
  null as leaseCharge2,
  mc.sourceRecord,
  mc.chargeType,

  mc.unitcost,
  mc.quantity,
  mc.totalcost,

  mc.creditAccount,
  mc.creditAccountId,
  mc.comment,
  mc.investigatorId,
  mc.isExemption,
  mc.lacksRate,
  mc.rateId,
  mc.exemptionId,
  'Y' as isMiscCharge,
  mc.isAdjustment,
  mc.isMissingAccount,
  mc.isMissingFaid,
  mc.isAcceptingCharges,
  mc.isExpiredAccount,
  mc.isOldCharge,
  null as datefinalized,
  null as enddatefinalized

FROM onprc_billing.miscChargesFeeRateData mc
WHERE cast(mc.billingDate as date) >= CAST(StartDate as date) AND cast(mc.billingDate as date) <= CAST(EndDate as date)
AND mc.category IN ('Lease Fees', 'Lease Setup Fee', 'Lease Setup Fees')
