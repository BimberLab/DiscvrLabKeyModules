ALTER TABLE onprc_billing.aliases ADD budgetStartDate timestamp;
ALTER TABLE onprc_billing.aliases ADD budgetEndDate timestamp;

CREATE INDEX IDX_aliases ON onprc_billing.aliases (container, alias);