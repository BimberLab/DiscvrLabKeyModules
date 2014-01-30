ALTER TABLE onprc_billing.miscCharges ADD debitedaccount varchar(200);
ALTER TABLE onprc_billing.miscCharges rename creditaccount to creditedaccount;
