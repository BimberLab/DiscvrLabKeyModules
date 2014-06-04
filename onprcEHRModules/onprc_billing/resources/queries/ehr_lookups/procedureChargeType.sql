SELECT
c.chargetype as value

FROM onprc_billing_public.chargeUnits c
WHERE c.shownInProcedures = true