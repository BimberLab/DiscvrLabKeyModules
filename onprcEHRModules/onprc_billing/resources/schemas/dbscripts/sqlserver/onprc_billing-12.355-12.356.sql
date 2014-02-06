ALTER TABLE onprc_billing.invoicedItems DROP CONSTRAINT PK_billedItems;
GO
ALTER TABLE onprc_billing.invoicedItems ALTER COLUMN objectid ENTITYID NOT NULL;
GO
ALTER TABLE onprc_billing.invoicedItems ADD CONSTRAINT PK_invoicedItems PRIMARY KEY (objectid);

CREATE TABLE onprc_billing.chargeableItemCategories (
  category varchar(100),

  CONSTRAINT PK_chargeableItemCategories PRIMARY KEY (category)
);
GO
INSERT INTO onprc_billing.chargeableItemCategories (category) VALUES ('Animal Per Diem');
INSERT INTO onprc_billing.chargeableItemCategories (category) VALUES ('Clinical Lab Test');
INSERT INTO onprc_billing.chargeableItemCategories (category) VALUES ('Clinical Procedure');
INSERT INTO onprc_billing.chargeableItemCategories (category) VALUES ('Lease Fees');
INSERT INTO onprc_billing.chargeableItemCategories (category) VALUES ('Lease Setup Fees');
INSERT INTO onprc_billing.chargeableItemCategories (category) VALUES ('Misc. Fees');
INSERT INTO onprc_billing.chargeableItemCategories (category) VALUES ('Small Animal Per Diem');
INSERT INTO onprc_billing.chargeableItemCategories (category) VALUES ('Surgery');
INSERT INTO onprc_billing.chargeableItemCategories (category) VALUES ('Time Mated Breeders');

CREATE TABLE onprc_billing.aliasCategories (
  category varchar(100),

  CONSTRAINT PK_aliasCategories PRIMARY KEY (category)
);
GO
INSERT INTO onprc_billing.aliasCategories (category) VALUES ('OGA');
INSERT INTO onprc_billing.aliasCategories (category) VALUES ('Other');
INSERT INTO onprc_billing.aliasCategories (category) VALUES ('GL');

