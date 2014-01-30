ALTER TABLE onprc_billing.miscCharges ADD parentid entityid;

ALTER TABLE onprc_billing.perDiemFeeDefinition DROP COLUMN releaseCondition;
ALTER TABLE onprc_billing.perDiemFeeDefinition DROP COLUMN startDate;