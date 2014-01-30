ALTER TABLE onprc_billing.chargeRateExemptions DROP COLUMN subsidy;
ALTER TABLE onprc_billing.chargeRates ADD subsidy double precision;
