ALTER TABLE onprc_billing.chargeableItems ADD allowBlankId bit;
GO
UPDATE onprc_billing.chargeableItems SET allowBlankId = 0;
