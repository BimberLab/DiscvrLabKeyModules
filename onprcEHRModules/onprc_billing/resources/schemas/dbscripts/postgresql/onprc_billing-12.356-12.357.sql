ALTER TABLE onprc_billing.creditAccount ADD COLUMN tempaccount varchar(100);
UPDATE onprc_billing.creditAccount SET tempaccount = cast(account as varchar(100));
ALTER TABLE onprc_billing.creditAccount DROP COLUMN account;
ALTER TABLE onprc_billing.creditAccount ADD COLUMN account varchar(100);
UPDATE onprc_billing.creditAccount SET account = tempaccount;
ALTER TABLE onprc_billing.creditAccount DROP COLUMN tempaccount;