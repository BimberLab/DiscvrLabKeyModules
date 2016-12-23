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
  t.id,
  t.date,
  t.enddate,
  t.project,
  t.account,
  t.projectedReleaseCondition,
  t.releaseCondition,
  t.assignCondition,
  t.releaseType,
  t.ageAtTime,
  t.category,
  t.chargeId,
  t.serviceCenter,
  t.item,

  t.leaseCharge1,
  t.leaseCharge2,
  t.sourceRecord,
  t.chargeCategory,

  round(CAST(CASE
    WHEN t.displayName = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_GRANT_PROJECT') THEN 0
    --handle adjustments and non-adjustments separately
    WHEN (t.isAdjustment IS NULL) THEN t.unitCost1
    --note: we take the amount that should have been paid and subtract what was predicted to have been paid
    ELSE (t.unitCost3 - t.unitCost2)
  END AS DOUBLE), 2) as unitCost,
  CAST(CASE
    --handle adjustments and non-adjustments separately
    WHEN (t.isAdjustment IS NULL) THEN t.nihRate1
    --note: we take the amount that should have been paid and subtract what was predicted to have been paid
    ELSE (t.nihRate3 - t.nihRate2)
  END AS DOUBLE) as nihRate,

  t.unitCost1,
  t.nihRate1,
  t.unitCost2,
  t.nihRate2,
  t.unitCost3,
  t.nihRate3,

  t.quantity,
  t.creditAccount,
  t.creditAccountId,
  t.comment,
  t.investigatorId,
  t.isExemption,
  t.isNonStandardRate,
  t.lacksRate,
  t.rateId,
  t.exemptionId,
  t.isMiscCharge,
  t.isAdjustment,
  t.isMissingAccount,
  t.isMissingFaid,
  t.isAcceptingCharges,
  t.isExpiredAccount,
  t.isOldCharge,
  t.currentActiveAlias,
  t.datefinalized,
  t.enddatefinalized
  
 
FROM ( 
SELECT
  p.id,
  p.date,
  p.enddate,
  p.project,
  alias.alias as account,
  p.project.displayName as displayName,
  p.projectedReleaseCondition,
  p.releaseCondition,
  p.assignCondition,
  p.releaseType,
  p.ageAtTime,
  p.category,
  p.chargeId,
  p.chargeId.departmentCode as serviceCenter,
  p.chargeId.name as item,

  p.leaseCharge1,
  p.leaseCharge2,
  p.sourceRecord,
  p.chargeCategory,

  --this is the cost of the original lease, based on projected release type
  CAST(CASE
    --order of priority for unit cost:
    --project-level exemption: pay this value
    WHEN (e.unitCost IS NOT NULL) THEN e.unitCost
    --project-level multiplier: multiply NIH rate by this value
    WHEN (pm.multiplier IS NOT NULL AND cr.unitCost IS NOT NULL) THEN (cr.unitCost * pm.multiplier)
    --if there is not a known rate, we dont know what do to
    WHEN (cr.unitCost IS NULL) THEN null
    --for non-OGA aliases, we always use the NIH rate
    WHEN (alias.category IS NOT NULL AND alias.category != 'OGA') THEN cr.unitCost
    --if we dont know the aliasType, we also dont know what do to
    WHEN (alias.aliasType.aliasType IS NULL) THEN null
    --remove both subsidy and raise F&A if needed
    WHEN (alias.aliasType.removeSubsidy = true AND (alias.aliasType.canRaiseFA = true AND p.chargeId.canRaiseFA = true)) THEN ((cr.unitCost / (1 - COALESCE(cr.subsidy, 0))) * (CASE WHEN (alias.faRate IS NOT NULL AND alias.faRate < CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE)) THEN (1 + (CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE) - alias.faRate)) ELSE 1 END))
    --remove subsidy only
    WHEN (alias.aliasType.removeSubsidy = true AND alias.aliasType.canRaiseFA = false) THEN (cr.unitCost / (1 - COALESCE(cr.subsidy, 0)))
    --raise F&A only
    WHEN (alias.aliasType.removeSubsidy = false AND (alias.aliasType.canRaiseFA = true AND p.chargeId.canRaiseFA = true)) THEN (cr.unitCost * (CASE WHEN (alias.faRate IS NOT NULL AND alias.faRate < CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE)) THEN (1 + (CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE) - alias.faRate)) ELSE 1 END))
    --the NIH rate
    ELSE cr.unitCost
  END AS DOUBLE) as unitCost1,
  cr.unitCost as nihRate1,

  --for adjustments, this is the first lease charge
  CAST(CASE
    --order of priority for unit cost:
    --project-level exemption: pay this value
    WHEN (e2.unitCost IS NOT NULL) THEN e2.unitCost
    --project-level multiplier: multiply NIH rate by this value
    WHEN (pm.multiplier IS NOT NULL AND cr2.unitCost IS NOT NULL) THEN (cr2.unitCost * pm.multiplier)
    --if there is not a known rate, we dont know what do to
    WHEN (cr2.unitCost IS NULL) THEN null
    --for non-OGA aliases, we always use the NIH rate
    WHEN (alias.category IS NOT NULL AND alias.category != 'OGA') THEN cr2.unitCost
    --if we dont know the aliasType, we also dont know what do to
    WHEN (alias.aliasType.aliasType IS NULL) THEN null
    --remove both subsidy and raise F&A if needed
    WHEN (alias.aliasType.removeSubsidy = true AND (alias.aliasType.canRaiseFA = true AND p.chargeId.canRaiseFA = true)) THEN ((cr2.unitCost / (1 - COALESCE(cr2.subsidy, 0))) * (CASE WHEN (alias.faRate IS NOT NULL AND alias.faRate < CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE)) THEN (1 + (CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE) - alias.faRate)) ELSE 1 END))
    --remove subsidy only
    WHEN (alias.aliasType.removeSubsidy = true AND alias.aliasType.canRaiseFA = false) THEN (cr2.unitCost / (1 - COALESCE(cr2.subsidy, 0)))
    --raise F&A only
    WHEN (alias.aliasType.removeSubsidy = false AND (alias.aliasType.canRaiseFA = true AND p.chargeId.canRaiseFA = true)) THEN (cr2.unitCost * (CASE WHEN (alias.faRate IS NOT NULL AND alias.faRate < CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE)) THEN (1 + (CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE) - alias.faRate)) ELSE 1 END))
    --the NIH rate
    ELSE cr2.unitCost
  END AS DOUBLE) as unitCost2,
  cr2.unitCost as nihRate2,

  --for adjustments, this is the second lease charge
  CAST(CASE
    --order of priority for unit cost:
    --project-level exemption: pay this value
    WHEN (e3.unitCost IS NOT NULL) THEN e3.unitCost
    --project-level multiplier: multiply NIH rate by this value
    WHEN (pm.multiplier IS NOT NULL AND cr3.unitCost IS NOT NULL) THEN (cr3.unitCost * pm.multiplier)
    --if there is not a known rate, we dont know what do to
    WHEN (cr3.unitCost IS NULL) THEN null
    --for non-OGA aliases, we always use the NIH rate
    WHEN (alias.category IS NOT NULL AND alias.category != 'OGA') THEN cr3.unitCost
    --if we dont know the aliasType, we also dont know what do to
    WHEN (alias.aliasType.aliasType IS NULL) THEN null
    --remove both subsidy and raise F&A if needed
    WHEN (alias.aliasType.removeSubsidy = true AND (alias.aliasType.canRaiseFA = true AND p.chargeId.canRaiseFA = true)) THEN ((cr3.unitCost / (1 - COALESCE(cr3.subsidy, 0))) * (CASE WHEN (alias.faRate IS NOT NULL AND alias.faRate < CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE)) THEN (1 + (CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE) - alias.faRate)) ELSE 1 END))
    --remove subsidy only
    WHEN (alias.aliasType.removeSubsidy = true AND alias.aliasType.canRaiseFA = false) THEN (cr3.unitCost / (1 - COALESCE(cr3.subsidy, 0)))
    --raise F&A only
    WHEN (alias.aliasType.removeSubsidy = false AND (alias.aliasType.canRaiseFA = true AND p.chargeId.canRaiseFA = true)) THEN (cr3.unitCost * (CASE WHEN (alias.faRate IS NOT NULL AND alias.faRate < CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE)) THEN (1 + (CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE) - alias.faRate)) ELSE 1 END))
    --the NIH rate
    ELSE cr3.unitCost
  END AS DOUBLE) as unitCost3,
  cr3.unitCost as nihRate3,
  
  p.quantity,
  cast(ce.account as varchar(200)) as creditAccount,
  ce.rowid as creditAccountId,
  null as comment,
  coalesce(alias.investigatorId, p.project.investigatorId) as investigatorId,
  CASE
    WHEN (e.rowid IS NOT NULL OR e2.rowid IS NOT NULL OR e3.rowid IS NOT NULL) THEN 'Y'
    WHEN (pm.multiplier IS NOT NULL) THEN ('Multiplier: ' || CAST(pm.multiplier AS varchar(100)))
    ELSE null
  END as isExemption,
  CASE
    WHEN (e.unitCost IS NOT NULL) THEN null  --ignore project-level exemptions
    WHEN (cr.unitCost IS NULL) THEN null --will be flagged for other reasons
    WHEN (pm.multiplier IS NOT NULL) THEN null --also ignore project-level multipliers
    WHEN (alias.aliasType.aliasType IS NULL) THEN null --unknown alias type, will be flagged elsewhere
    WHEN (alias.aliasType.removeSubsidy = true AND COALESCE(cr.subsidy, 0) > 0) THEN 'Removed NIH Subsidy'
    WHEN (alias.aliasType.canRaiseFA = true AND p.chargeId.canRaiseFA = true AND (alias.faRate IS NOT NULL AND alias.faRate < CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE))) THEN ('Reduced F&A: ' || CAST(alias.faRate as varchar(20)))
    ELSE null
  END as isNonStandardRate,
  CASE WHEN (alias.alias IS NOT NULL AND alias.aliasType.aliasType IS NULL) THEN ('Unknown Type: ' || alias.aliasType) ELSE null END as isUnknownAliasType,
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
  CASE WHEN alias.alias IS NULL THEN 'Y' ELSE null END as isMissingAccount,
  CASE WHEN alias.fiscalAuthority.faid IS NULL THEN 'Y' ELSE null END as isMissingFaid,
  CASE
    WHEN alias.aliasEnabled IS NULL THEN 'N'
    WHEN alias.aliasEnabled != 'Y' THEN 'N'
    ELSE null
  END as isAcceptingCharges,
  CASE
    WHEN (alias.budgetStartDate IS NOT NULL AND CAST(alias.budgetStartDate as date) > CAST(p.date as date)) THEN 'Prior To Budget Start'
    WHEN (alias.budgetEndDate IS NOT NULL AND CAST(alias.budgetEndDate as date) < CAST(p.date as date)) THEN 'After Budget End'
    WHEN (alias.projectStatus IS NOT NULL AND alias.projectStatus != 'ACTIVE' AND alias.projectStatus != 'No Cost Ext' AND alias.projectStatus != 'Partial Setup') THEN 'Grant Project Not Active'
    ELSE null
  END as isExpiredAccount,

  CASE WHEN (TIMESTAMPDIFF('SQL_TSI_DAY', p.date, curdate()) > 45) THEN 'Y' ELSE null END as isOldCharge,
  p.project.account as currentActiveAlias,
  p.datefinalized,
  p.enddatefinalized

FROM onprc_billing.leaseFees p

--the primary charge.  this will be based on transaction date
LEFT JOIN onprc_billing_public.chargeRates cr ON (
    CAST(p.assignmentStart AS DATE) >= CAST(cr.startDate AS DATE) AND
    (CAST(p.assignmentStart AS DATE) <= cr.enddateCoalesced OR cr.enddate IS NULL) AND
    p.chargeId = cr.chargeId
)

LEFT JOIN onprc_billing_public.chargeRateExemptions e ON (
    CAST(p.assignmentStart AS DATE) >= CAST(e.startDate AS DATE) AND
    (CAST(p.assignmentStart AS DATE) <= e.enddateCoalesced OR e.enddate IS NULL) AND
    p.chargeId = e.chargeId AND
    p.project = e.project
)

--the original charge (for adjustments)
--NOTE: the adjustment will use the lease end as the transaction date; however, we need to calculate this unit cost
--based on the original date of assignment
LEFT JOIN onprc_billing_public.chargeRates cr2 ON (
    CAST(p.assignmentStart AS DATE) >= CAST(cr2.startDate AS DATE) AND
    (CAST(p.assignmentStart AS DATE) <= cr2.enddateCoalesced OR cr2.enddate IS NULL) AND
    p.leaseCharge1 = cr2.chargeId
)

LEFT JOIN onprc_billing_public.chargeRateExemptions e2 ON (
    CAST(p.assignmentStart AS DATE) >= CAST(e2.startDate AS DATE) AND
    (CAST(p.assignmentStart AS DATE) <= e2.enddateCoalesced OR e2.enddate IS NULL) AND
    p.leaseCharge1 = e2.chargeId AND
    p.project = e2.project
)
--EO original charge

--the final charge (for adjustments)
--this is the what we should have charges, based on the true release condition.
--this is also based on the date of assignment, which will differ from transaction date
LEFT JOIN onprc_billing_public.chargeRates cr3 ON (
  CAST(p.assignmentStart AS DATE) >= CAST(cr3.startDate AS DATE) AND
  (CAST(p.assignmentStart AS DATE) <= cr3.enddateCoalesced OR cr3.enddate IS NULL) AND
  p.leaseCharge2 = cr3.chargeId
)

LEFT JOIN onprc_billing_public.chargeRateExemptions e3 ON (
  CAST(p.assignmentStart AS DATE) >= CAST(e3.startDate AS DATE) AND
  (CAST(p.assignmentStart AS DATE) <= e3.enddateCoalesced OR e3.enddate IS NULL) AND
  p.leaseCharge2 = e3.chargeId AND
  p.project = e3.project
)

--EO final charge

LEFT JOIN onprc_billing_public.creditAccount ce ON (
    CAST(p.date AS DATE) >= CAST(ce.startDate AS DATE) AND
    (CAST(p.date AS DATE) <= ce.enddateCoalesced OR ce.enddate IS NULL) AND
    p.chargeId = ce.chargeId
)

LEFT JOIN onprc_billing_public.projectAccountHistory aliasAtTime ON (
  aliasAtTime.project = p.project AND
  aliasAtTime.startDate <= cast(p.date as date) AND
  aliasAtTime.endDate >= cast(p.date as date)
)

LEFT JOIN onprc_billing_public.aliases alias ON (
  aliasAtTime.account = alias.alias
)

LEFT JOIN onprc_billing_public.projectMultipliers pm ON (
    CAST(p.date AS DATE) >= CASt(pm.startDate AS DATE) AND
    (CAST(p.date AS DATE) <= pm.enddateCoalesced OR pm.enddate IS NULL) AND
    alias.alias = pm.account
)

) t
where t.id.demographics.species Not IN ('Rabbit','Guinea Pig')
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
  mc.serviceCenter,
  mc.item,

  null as leaseCharge1,
  null as leaseCharge2,
  mc.sourceRecord,
  mc.chargeCategory,

  mc.unitcost,
  mc.nihRate,

  null as unitCost1,
  null as nihRate1,
  null as unitCost2,
  null as nihRate2,
  null as unitCost3,
  null as nihRate3,

  mc.quantity,

  mc.creditAccount,
  mc.creditAccountId,
  mc.comment,
  mc.investigatorId,
  mc.isExemption,
  mc.isNonStandardRate,
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
  mc.currentActiveAlias,
  null as datefinalized,
  null as enddatefinalized

FROM onprc_billing.miscChargesFeeRateData mc
WHERE cast(mc.billingDate as date) >= CAST(StartDate as date) AND cast(mc.billingDate as date) <= CAST(EndDate as date)
AND mc.category IN ('Lease Fees', 'Lease Setup Fee', 'Lease Setup Fees')
