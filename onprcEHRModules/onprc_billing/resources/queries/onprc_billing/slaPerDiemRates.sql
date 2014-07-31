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
  alias.alias as account,
  p.chargeId,
  p.sourceRecord,
  null as chargeCategory,
  p.cagecount,
  p.startDate,
  p.endDate,

  ci.name as item,
  ci.category as category,
  round(CAST(CASE
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
  END AS DOUBLE), 2) as unitCost,
  cr.unitCost as nihRate,
  p.cagecount as quantity,
  ci.name as comment,

  cast(ce.account as varchar(200)) as creditAccount,
  ce.rowid as creditAccountId,
  coalesce(alias.investigatorId, p.project.investigatorId) as investigatorId,
  CASE
    WHEN e.unitCost IS NOT NULL THEN 'Y'
    WHEN pm.multiplier IS NOT NULL THEN ('Multiplier: ' || CAST(pm.multiplier AS varchar(100)))
    ELSE null
  END as isExemption,
  CASE
    WHEN (e.unitCost IS NOT NULL) THEN null  --ignore project-level exemptions
    WHEN (pm.multiplier IS NOT NULL) THEN null --also ignore project-level multipliers
    WHEN (cr.unitCost IS NULL) THEN null --will be flagged for other reasons
    WHEN (alias.aliasType.aliasType IS NULL) THEN null --unknown alias type, will be flagged elsewhere
    WHEN (alias.aliasType.removeSubsidy = true AND COALESCE(cr.subsidy, 0) > 0) THEN 'Removed NIH Subsidy'
    WHEN (alias.aliasType.canRaiseFA = true AND p.chargeId.canRaiseFA = true AND (alias.faRate IS NOT NULL AND alias.faRate < CAST(javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.BASE_SUBSIDY') AS DOUBLE))) THEN ('Reduced F&A: ' || CAST(alias.faRate as varchar(20)))
    ELSE null
  END as isNonStandardRate,
  CASE WHEN (alias.alias IS NOT NULL AND alias.aliasType.aliasType IS NULL) THEN ('Unknown Type: ' || alias.aliasType) ELSE null END as isUnknownAliasType,
  CASE
    WHEN (e.unitCost IS NULL AND cr.unitCost IS NULL) THEN 'Y'
    ELSE null
  END as lacksRate,
  e.rowid as exemptionId,
  CASE WHEN e.rowid IS NULL THEN cr.rowid ELSE null END as rateId,
  null as isMiscCharge,
  null as isAdjustment,
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
  p.project.account as currentActiveAlias

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

UNION ALL

--add misc charges
SELECT
  mc.date,
  mc.project,
  mc.account,
  mc.chargeId,
  mc.sourceRecord,
  mc.chargeCategory,
  null as cagecount,
  null as startDate,
  null as endDate,
  mc.item,
  mc.category,
  mc.unitcost,
  mc.nihRate,
  mc.quantity,
  mc.comment,

  mc.creditAccount,
  mc.creditAccountId,
  mc.investigatorId,
  mc.isExemption,
  mc.isNonStandardRate,
  mc.isUnknownAliasType,
  mc.lacksRate,
  mc.exemptionId,
  mc.rateId,
  'Y' as isMiscCharge,
  mc.isAdjustment,
  mc.isMissingAccount,
  mc.isMissingFaid,
  mc.isAcceptingCharges,
  mc.isExpiredAccount,
  mc.isOldCharge,
  mc.currentActiveAlias

FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.onprc_billing.miscChargesFeeRateData mc
WHERE cast(mc.billingDate as date) >= CAST(StartDate as date) AND cast(mc.billingDate as date) <= CAST(EndDate as date)
AND mc.category = 'Small Animal Per Diem'