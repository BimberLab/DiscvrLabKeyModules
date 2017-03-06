PARAMETERS(StartDate TIMESTAMP, EndDate TIMESTAMP)

SELECT
a.id,
a.date,
a.project,
a.date as assignmentStart,
a.enddate,
a.projectedReleaseCondition,
a.releaseCondition,
a.assignCondition,
a.releaseType,
a.ageAtTime.AgeAtTimeYearsRounded as ageAtTime,
'Lease Fees' as category,
CASE
  WHEN (a.duration <= CAST(javaConstant('org.labkey.onprc_billing.ONPRC_BillingManager.DAY_LEASE_MAX_DURATION') as INTEGER) AND a.enddate IS NOT NULL AND a.assignCondition = a.releaseCondition) THEN (SELECT rowid FROM    onprc_billing_public.chargeableItems ci WHERE ci.active = true AND ci.name = javaConstant('org.labkey.onprc_billing.ONPRC_BillingManager.DAY_LEASE_NAME'))
  WHEN a2.id IS NOT NULL THEN (SELECT rowid FROM onprc_billing_public.chargeableItems ci WHERE (ci.startDate <= a.date and ci.endDate >= a.date) AND ci.name = javaConstant('org.labkey.onprc_billing.ONPRC_BillingManager.TMB_LEASE_NAME'))
  ELSE lf.chargeId
END as chargeId,
--special case one-day lease rates.  note: if enddate is null, these cannot be a one-day lease
CASE
	  WHEN (Select Count(*) from study.birth b
      left join Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.study.assignment a1 on b.id = a.id and a.date = b.dateOnly and a.project.use_category in ('Center Resource','U42','U24')
      left join Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.study.assignment a2 on b.dam = a2.id and a2.project.use_category in ('Center Resource','U42','U24') and (a2.date <= b.dateOnly and a2.endDate >=b.dateOnly or a2.enddate is Null)
        where b.id = a.id and a1.project.protocol = a2.project.protocol) > 0 THEN 0
  WHEN (a.duration = 0 AND a.enddate IS NOT NULL AND a.assignCondition = a.releaseCondition) THEN 1
  WHEN (fl.id Is Not Null) THEN 0
--This will check for infants born to resource moms and will not charge

  WHEN (a.duration <= CAST(javaConstant('org.labkey.onprc_billing.ONPRC_BillingManager.DAY_LEASE_MAX_DURATION') as INTEGER) AND a.enddate IS NOT NULL AND a.assignCondition = a.releaseCondition) THEN a.duration
  ELSE 1
END as quantity,
cast(null as integer) as leaseCharge1,
cast(null as integer) as leaseCharge2,
a.objectid as sourceRecord,
null as chargeCategory,
null as isAdjustment,
a.datefinalized,
a.enddatefinalized

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
--adds the reasearch owned animal exemption
Left JOIN study.flags fl on
	(a.id = fl.id
	and fl.flag.code = 4034
	and (a.date >= fl.date and a.date <=COALESCE(fl.enddate,Now()) ))

WHERE CAST(a.datefinalized AS DATE) >= CAST(STARTDATE as DATE) AND CAST(a.datefinalized AS DATE) <= CAST(ENDDATE as DATE)
AND a.qcstate.publicdata = true --and a.participantID.demographics.species.common not in ('Rabbit','Guinea Pigs')


--add setup fees for all starts, except day leases aznd sla
UNION ALL
SELECT
  a.id,
  a.date,
  a.project,
  a.date as assignmentStart,
  a.enddate,
  a.projectedReleaseCondition,
  a.releaseCondition,
  a.assignCondition,
  a.releaseType,
  a.ageAtTime.AgeAtTimeYearsRounded as ageAtTime,
  'Lease Setup Fees' as category,
  (SELECT rowid FROM onprc_billing_public.chargeableItems ci WHERE ci.active = true AND ci.name = javaConstant('org.labkey.onprc_billing.ONPRC_BillingManager.LEASE_SETUP_FEES')) as chargeId,
  1 as quantity,
  cast(null as integer) as leaseCharge1,
  cast(null as integer) as leaseCharge2,
  a.objectid as sourceRecord,
  null as chargeCategory,
  null as isAdjustment,
  a.datefinalized,
  a.enddatefinalized

FROM study.assignment a

WHERE CAST(a.datefinalized AS DATE) >= CAST(STARTDATE as DATE) AND CAST(a.datefinalized AS DATE) <= CAST(ENDDATE as DATE)
AND a.qcstate.publicdata = true
--only charge setup fee for leases >24H.  note: duration assumes today as end, so exclude null enddates
AND (a.duration > CAST(javaConstant('org.labkey.onprc_billing.ONPRC_BillingManager.DAY_LEASE_MAX_DURATION') as INTEGER) OR a.assignCondition != a.releaseCondition OR a.enddate IS NULL)
and  a.id.demographics.species Not IN ('Rabbit','Guinea Pigs')

--add released animals that need adjustments
UNION ALL

SELECT
a.id,
a.enddate as date, --use enddate as the transaction date for this charge
a.project,
a.date as assignmentStart,
a.enddate,
a.projectedReleaseCondition,
a.releaseCondition,
a.assignCondition,
a.releaseType,
a.ageAtTime.AgeAtTimeYearsRounded as ageAtTime,
'Lease Fees' as category,
(SELECT max(rowid) as rowid FROM onprc_billing_public.chargeableItems ci WHERE ci.name = javaConstant('org.labkey.onprc_billing.ONPRC_BillingManager.LEASE_FEE_ADJUSTMENT') and ci.active = true) as chargeId,
CASE
  when (fl.id Is Not Null) then 0
  else 1
  end as quantity,
lf2.chargeId as leaseCharge1,
lf.chargeId as leaseCharge2,
a.objectid as sourceRecord,
'Adjustment - Automatic' as chargeCategory,
'Y' as isAdjustment,
a.datefinalized,
a.enddatefinalized

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
    and (a.date >=lf2.startDate and a.date <= lf2.endDate)
  )

--find overlapping TMB at date of assignment
  LEFT JOIN study.assignment a2 ON (
    a.id = a2.id AND a.project != a2.project
    AND a2.dateOnly <= a.dateOnly
    AND a2.endDateCoalesced >= a.dateOnly
    AND a2.project.name = javaConstant('org.labkey.onprc_ehr.ONPRC_EHRManager.TMB_PROJECT')


  )
--adds the reasearch owned animal exemption
Left JOIN study.flags fl on
	(a.id = fl.id
	and fl.flag.code = 4034
	and (a.date >= fl.date and a.date <=COALESCE(fl.enddate,Now()) ))

WHERE a.releaseCondition != a.projectedReleaseCondition
AND a.enddatefinalized is not null AND CAST(a.enddatefinalized AS DATE) >= CAST(STARTDATE AS DATE) AND CAST(a.enddatefinalized AS DATE) <= CAST(EndDate as DATE)
AND a.qcstate.publicdata = true AND lf.active = true
AND a2.id IS NULL and a.participantID not like '[a-z]%'