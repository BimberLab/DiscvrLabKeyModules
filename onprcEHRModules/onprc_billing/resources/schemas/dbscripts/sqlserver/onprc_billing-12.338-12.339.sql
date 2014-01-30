ALTER TABLE onprc_billing.miscCharges DROP COLUMN chargeType;
GO
ALTER TABLE onprc_billing.miscCharges ADD chargeType varchar(200);
ALTER TABLE onprc_billing.miscCharges ADD sourceInvoicedItem entityid;
