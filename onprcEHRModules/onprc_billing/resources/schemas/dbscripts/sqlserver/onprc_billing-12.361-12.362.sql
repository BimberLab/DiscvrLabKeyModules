ALTER TABLE onprc_billing.aliases ADD aliasType VARCHAR(100);

DELETE FROM onprc_billing.aliasCategories WHERE category = 'Non-Syncing';
INSERT INTO onprc_billing.aliasCategories (category) VALUES ('Non-Syncing');