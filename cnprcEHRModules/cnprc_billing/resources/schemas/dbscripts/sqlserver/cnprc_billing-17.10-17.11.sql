ALTER TABLE cnprc_billing.billing_fiscal DROP COLUMN Dafis_univ_fy_range_abbrev_1;
GO
ALTER TABLE cnprc_billing.billing_fiscal ADD Univ_fy_range_abbrev_1 nvarchar(7);
GO

ALTER TABLE cnprc_billing.eom_transaction_detail DROP COLUMN Credit_entry_prior_invoice_no;
GO
ALTER TABLE cnprc_billing.eom_transaction_detail ADD Credit_entry_prior_inv_no nvarchar(8);
GO