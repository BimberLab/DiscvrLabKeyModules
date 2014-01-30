ALTER TABLE onprc_billing.chargeableItems ADD allowsCustomUnitCost boolean DEFAULT false;

UPDATE onprc_billing.chargeableItems SET allowsCustomUnitCost = false;
