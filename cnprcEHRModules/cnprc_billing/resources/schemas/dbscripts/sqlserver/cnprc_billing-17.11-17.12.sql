ALTER TABLE cnprc_billing.billing_control DROP COLUMN Central_spply_status_closed_yn;
ALTER TABLE cnprc_billing.billing_control DROP COLUMN Central_spply_status_timestamp;
ALTER TABLE cnprc_billing.billing_control DROP COLUMN Work_orders_status_closed_yn;
ALTER TABLE cnprc_billing.billing_control DROP COLUMN Work_orders_status_timestamp;
ALTER TABLE cnprc_billing.billing_control DROP COLUMN Core_services_status_closed_yn;
ALTER TABLE cnprc_billing.billing_control DROP COLUMN Core_services_status_timestamp;

ALTER TABLE cnprc_billing.billing_control ADD Cs_status_closed_yn nvarchar(1);
ALTER TABLE cnprc_billing.billing_control ADD Cs_status_timestamp datetime;
ALTER TABLE cnprc_billing.billing_control ADD Wo_status_closed_yn nvarchar(1);
ALTER TABLE cnprc_billing.billing_control ADD Wo_status_timestamp datetime;
ALTER TABLE cnprc_billing.billing_control ADD Core_serv_status_closed_yn nvarchar(1);
ALTER TABLE cnprc_billing.billing_control ADD Core_serv_status_timestamp datetime;

