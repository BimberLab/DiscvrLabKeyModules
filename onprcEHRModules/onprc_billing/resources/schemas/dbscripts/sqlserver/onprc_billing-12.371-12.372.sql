--Updated 1/21/2016
--gjones
--added start and end dates to selected Finance datasets
--reset the tables


ALTER TABLE onprc_billing.procedureFeeDefinition ADD startDate DATETIME;
ALTER TABLE onprc_billing.procedureFeeDefinition ADD endDate DATETIME;

ALTER TABLE onprc_billing.labWorkFeeDefinition ADD startDate DATETIME;
ALTER TABLE onprc_billing.labWorkFeeDefinition ADD endDate DATETIME;


ALTER TABLE onprc_billing.slaPerDiemFeeDefinition ADD startDate DATETIME;
ALTER TABLE onprc_billing.slaPerDiemFeeDefinition ADD endDate DATETIME;

ALTER TABLE onprc_billing.leaseFeeDefinition ADD startDate DATETIME;
ALTER TABLE onprc_billing.leaseFeeDefinition ADD endDate DATETIME;
ALTER TABLE onprc_billing.chargeableItems ADD startDate DATETIME;
ALTER TABLE onprc_billing.chargeableItems ADD endDate DATETIME;


ALTER TABLE onprc_billing.perDiemFeeDefinition ADD startDate DATETIME;
ALTER TABLE onprc_billing.perDiemFeeDefinition ADD endDate DATETIME;

ALTER TABLE onprc_billing.medicationFeeDefinition ADD startDate DATETIME;
ALTER TABLE onprc_billing.medicationFeeDefinition ADD endDate DATETIME;