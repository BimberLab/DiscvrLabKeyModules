ALTER TABLE onprc_billing.miscCharges ADD debitedaccount varchar(200);
EXEC sp_rename 'onprc_billing.miscCharges.creditaccount', 'creditedaccount', 'COLUMN';