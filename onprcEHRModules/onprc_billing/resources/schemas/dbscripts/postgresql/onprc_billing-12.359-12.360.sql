ALTER TABLE onprc_billing.bloodDrawFeeDefinition DROP COLUMN chargetype;
ALTER TABLE onprc_billing.bloodDrawFeeDefinition ADD chargetype varchar(100);
ALTER TABLE onprc_billing.bloodDrawFeeDefinition ADD creditalias varchar(100);