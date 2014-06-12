--finds items present in invoicedItems, but not expected to bill
PARAMETERS(StartDate TIMESTAMP, EndDate TIMESTAMP)

SELECT
i.*
FROM Site.{substitutePath moduleProperty('onprc_billing','BillingContainer')}.onprc_billing.invoicedItems i

LEFT JOIN Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.onprc_billing.miscChargesFeeRates lf ON (
  lf.sourceRecord = i.sourceRecord
  AND lf.Id = i.Id
  AND lf.date = i.date
  and lf.chargeId = i.chargeId
)

WHERE lf.sourceRecord IS NULL AND (i.category IS NULL OR i.category NOT IN (
  'Lease Fees', 'Lease Setup Fees', 'Animal Per Diem',
  'Surgical Procedure', 'Surgery', 'Clinical Procedure', 'Clinical Lab Test', 'Small Animal Per Diem'
))

AND CAST(i.date AS DATE) >= StartDate AND CAST(i.date as DATE) <= EndDate