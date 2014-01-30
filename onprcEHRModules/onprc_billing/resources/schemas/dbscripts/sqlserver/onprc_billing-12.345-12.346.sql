ALTER TABLE onprc_billing.chargeableItems ADD allowsCustomUnitCost bit DEFAULT 0;
GO
UPDATE onprc_billing.chargeableItems SET allowsCustomUnitCost = 0;
