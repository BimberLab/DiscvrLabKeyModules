ALTER TABLE onprc_billing.chargeableItems ADD allowBlankId bool;
UPDATE onprc_billing.chargeableItems SET allowBlankId = false;
