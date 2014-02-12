ALTER TABLE onprc_billing.creditAccount ADD tempaccount varchar(100);
GO
UPDATE onprc_billing.creditAccount SET tempaccount = cast(account as varchar(100));
ALTER TABLE onprc_billing.creditAccount DROP COLUMN account;
GO
ALTER TABLE onprc_billing.creditAccount ADD account varchar(100);
GO
UPDATE onprc_billing.creditAccount SET account = tempaccount;
ALTER TABLE onprc_billing.creditAccount DROP COLUMN tempaccount;