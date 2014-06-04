ALTER TABLE onprc_billing.miscCharges ADD formSort integer;

CREATE TABLE onprc_billing.miscChargesType (
  category varchar(100) not null,

  CONSTRAINT PK_miscChargesType PRIMARY KEY (category)
);
GO
INSERT INTO onprc_billing.miscChargesType (category) VALUES ('Adjustment');
INSERT INTO onprc_billing.miscChargesType (category) VALUES ('Reversal');

ALTER TABLE onprc_billing.miscCharges ADD chargeCategory VARCHAR(100);
GO
UPDATE onprc_billing.miscCharges SET chargeCategory = chargetype;
UPDATE onprc_billing.miscCharges SET chargetype = null;

EXEC sp_rename 'onprc_billing.invoicedItems.chargetype', 'chargeCategory', 'COLUMN';