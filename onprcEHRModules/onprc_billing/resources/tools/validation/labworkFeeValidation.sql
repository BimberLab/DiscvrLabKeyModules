--finds items we expected to bill, but not present in invoicedItems
SELECT
lf.*
FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.onprc_billing.labworkFeeRates lf

LEFT JOIN Site.{substitutePath moduleProperty('onprc_billing','BillingContainer')}.onprc_billing.invoicedItems i ON (
  lf.sourceRecord = i.sourceRecord
  AND lf.Id = i.Id
  AND lf.date = i.date
  and lf.chargeId = i.chargeId
)

WHERE i.objectid IS NULL