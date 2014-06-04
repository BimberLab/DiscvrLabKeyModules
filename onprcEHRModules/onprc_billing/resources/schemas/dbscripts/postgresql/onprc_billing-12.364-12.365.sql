ALTER TABLE onprc_billing.miscCharges ADD formSort integer;

CREATE TABLE onprc_billing.miscChargesType (
  category varchar(100) not null,

  CONSTRAINT PK_miscChargesType PRIMARY KEY (category)
);

INSERT INTO onprc_billing.miscChargesType (category) VALUES ('Adjustment');
INSERT INTO onprc_billing.miscChargesType (category) VALUES ('Reversal');

ALTER TABLE onprc_billing.miscCharges ADD chargeCategory VARCHAR(100);

UPDATE onprc_billing.miscCharges SET chargeCategory = chargetype;
UPDATE onprc_billing.miscCharges SET chargetype = null;

ALTER TABLE onprc_billing.invoicedItems RENAME COLUMN chargetype TO chargeCategory;