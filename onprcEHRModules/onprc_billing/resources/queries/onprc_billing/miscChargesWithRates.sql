/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

-- NOTE: this query provides the raw data used in most of billing *Rates.sql queries.  The goal is to have a single implementation of the
-- process to assign rates and accounts to the raw charges.
SELECT
  p.Id,
  p.date,
  p.billingDate,
  p.project,
  coalesce(p.debitedaccount, p.project.account) as account,
  p.chargeId,
  coalesce(p.chargeId.name, p.item) as item,
  CASE
    WHEN p.unitCost IS NOT NULL THEN p.unitCost
    ELSE coalesce(e.unitCost, cr.unitCost)
  END as unitCost,
  coalesce(p.quantity, 1) as quantity,
  CASE
    WHEN p.unitCost IS NOT NULL THEN (coalesce(p.quantity, 1) * p.unitCost)
    ELSE (coalesce(p.quantity, 1) * coalesce(e.unitCost, cr.unitCost))
  END as totalcost,
  coalesce(p.category, p.chargeId.category) as category,
  p.chargeType,
  p.invoicedItemId,
  p.objectid as sourceRecord,
  p.comment,
  p.objectid,
  p.created,
  p.createdby,
  p.taskId,

  coalesce(p.creditedaccount, ce.account) as creditAccount,
  CASE
    WHEN p.creditedaccount IS NULL THEN ce.rowid
    ELSE NULL
  END as creditAccountId,
  coalesce(alias.investigatorId, p.project.investigatorId) as investigatorId,
  CASE
    --dont flag adjustments/reversals as exceptions
    WHEN (p.chargeType = 'Reversal' OR p.chargeType LIKE 'Adjustment%') THEN null
    WHEN (p.unitCost IS NOT NULL OR e.unitCost IS NOT NULL) THEN 'Y'
    ELSE null
  END as isExemption,
  CASE WHEN (p.chargeType = 'Reversal' OR p.chargeType LIKE 'Adjustment%') THEN 'Y' ELSE null END as isAdjustment,
  CASE
    WHEN (p.unitCost IS NULL AND e.unitCost IS NULL AND cr.unitCost IS NULL) THEN 'Y'
    ELSE null
  END as lacksRate,
  e.rowid as exemptionId,
  CASE WHEN e.rowid IS NULL THEN cr.rowid ELSE null END as rateId,

  --find assignment on this date
  CASE
    WHEN (p.project IS NULL AND p.debitedaccount IS NOT NULL) THEN null  --note: allow charges entered by account only
    WHEN p.project IS NULL THEN 'N'
    WHEN p.project.alwaysavailable = true THEN null
    WHEN (SELECT count(*) as projects FROM study.assignment a WHERE
      p.Id = a.Id AND
      (p.project = a.project OR p.project.protocol = a.project.protocol) AND
      (cast(p.date AS DATE) <= a.enddateCoalesced OR a.enddate IS NULL) AND
      cast(p.date as date) >= a.dateOnly
    ) > 0 THEN null
    ELSE 'N'
  END as matchesProject,
  (SELECT group_concat(distinct a.project.displayName, chr(10)) as projects FROM study.assignment a WHERE
    p.Id = a.Id AND
    (cast(p.date AS DATE) <= a.enddateCoalesced OR a.enddate IS NULL) AND
    cast(p.date as date) >= a.dateOnly
  ) as assignmentAtTime,
  p.container,
  CASE WHEN coalesce(p.debitedaccount, p.project.account) IS NULL THEN 'Y' ELSE null END as isMissingAccount,
  CASE WHEN ifdefined(alias.fiscalAuthority.faid) IS NULL THEN 'Y' ELSE null END as isMissingFaid,
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
  p.sourceInvoicedItem,
  p.invoiceId,
  CASE
    WHEN (p.debitedaccount IS NULL OR p.debitedaccount = p.project.account) THEN null
    ELSE 'Y'
  END as accountDiffersFromProject,
  true as isMiscCharge

FROM onprc_billing.miscCharges p

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

LEFT JOIN onprc_billing_public.aliases alias ON (
  alias.alias = COALESCE(p.debitedaccount, p.project.account)
)