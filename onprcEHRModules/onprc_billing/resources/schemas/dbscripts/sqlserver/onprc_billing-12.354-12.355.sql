ALTER TABLE onprc_billing.aliases ADD budgetStartDate datetime;
ALTER TABLE onprc_billing.aliases ADD budgetEndDate datetime;

CREATE INDEX IDX_aliases ON onprc_billing.aliases (container, alias);

