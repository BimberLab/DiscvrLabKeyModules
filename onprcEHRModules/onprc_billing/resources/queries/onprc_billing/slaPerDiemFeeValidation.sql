--finds items we expected to bill, but not present in invoicedItems
SELECT
lf.*
FROM Site.{substitutePath moduleProperty('EHR','EHRStudyContainer')}.onprc_billing.slaPerDiemRates lf

LEFT JOIN Site.{substitutePath moduleProperty('onprc_billing','BillingContainer')}.onprc_billing.invoicedItems i ON (
  lf.sourceRecord = i.sourceRecord
  AND lf.date = i.date
  and lf.chargeId = i.chargeId
)

WHERE i.objectid IS NULL