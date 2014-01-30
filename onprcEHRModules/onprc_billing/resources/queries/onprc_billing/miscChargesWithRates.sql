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
  coalesce(p.unitCost, e.unitCost, cr.unitCost) as unitCost,
  coalesce(p.quantity, 1) as quantity,
  coalesce(p.quantity, 1) * coalesce(p.unitCost, e.unitCost, cr.unitCost) as totalcost,
  coalesce(p.category, p.chargeId.category) as category,
  p.chargeType,
  p.invoicedItemId,
  p.objectid as sourceRecord,
  p.comment,
  p.objectid,
  p.created,
  p.createdby,
  p.taskId,

  coalesce(p.creditedaccount, cast(ce.account as varchar(200))) as creditAccount,
  CASE
    WHEN p.creditedaccount IS NULL THEN ce.rowid
    ELSE NULL
  END as creditAccountId,
  coalesce(p.project.account.investigatorId, p.project.investigatorId) as investigatorId,
  CASE
    WHEN (p.unitCost IS NOT NULL OR e.unitCost IS NOT NULL) THEN 'Y'
    ELSE null
  END as isExemption,
  CASE WHEN (p.chargeType = 'Reversal' OR p.chargeType = 'Adjustment') THEN 'Y' ELSE null END as isAdjustment,
  CASE
    WHEN (p.unitCost IS NULL AND e.unitCost IS NULL AND cr.unitCost IS NULL) THEN 'Y'
    ELSE null
  END as lacksRate,
  e.rowid as exemptionId,
  CASE WHEN e.rowid IS NULL THEN cr.rowid ELSE null END as rateId,

  --find assignment on this date
  CASE
    WHEN p.project IS NULL THEN 'N'
    WHEN p.project.alwaysavailable = true THEN null
    WHEN (SELECT count(*) as projects FROM study.assignment a WHERE
      p.Id = a.Id AND
      (p.project = a.project OR p.project.protocol = a.project.protocol) AND
      (cast(p.date AS DATE) < a.enddateCoalesced OR a.enddate IS NULL) AND
      p.date >= a.dateOnly
    ) > 0 THEN null
    ELSE 'N'
  END as matchesProject,
  (SELECT group_concat(distinct a.project.displayName, chr(10)) as projects FROM study.assignment a WHERE
    p.Id = a.Id AND
    (cast(p.date AS DATE) < a.enddateCoalesced OR a.enddate IS NULL) AND
    p.date >= a.dateOnly
  ) as assignmentAtTime,
  p.container,
  CASE WHEN p.project.account IS NULL THEN 'Y' ELSE null END as isMissingAccount,
  CASE WHEN ifdefined(p.project.account.fiscalAuthority.faid) IS NULL THEN 'Y' ELSE null END as isMissingFaid,
  CASE
    WHEN ifdefined(p.project.account.aliasEnabled) IS NULL THEN null
    WHEN (ifdefined(p.project.account.aliasEnabled) IS NULL OR ifdefined(p.project.account.aliasEnabled) != 'Y') THEN 'Y'
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

LEFT JOIN Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.onprc_billing.chargeRates cr ON (
  p.date >= cr.startDate AND
  (p.date <= cr.enddateTimeCoalesced OR cr.enddate IS NULL) AND
  p.chargeId = cr.chargeId
)

LEFT JOIN Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.onprc_billing.chargeRateExemptions e ON (
  p.date >= e.startDate AND
  (p.date <= e.enddateTimeCoalesced OR e.enddate IS NULL) AND
  p.chargeId = e.chargeId AND
  p.project = e.project
)

LEFT JOIN Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.onprc_billing.creditAccount ce ON (
  p.date >= ce.startDate AND
  (p.date <= ce.enddateTimeCoalesced OR ce.enddate IS NULL) AND
  p.chargeId = ce.chargeId
)