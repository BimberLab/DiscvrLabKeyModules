--cREATED 8/25/2016
--gjones
--NEW Data set to control Inflation factor for Rates for ONPRC
--
CREATE TABLE onprc_billing.AnnualInflationRate (
    billingYear varchar(10) not null,
    inflationRate decimal,
    startDate datetime,
    endDate datetime,

    createdBy integer,
    created datetime,
    modifiedBy integer,
    modified datetime,


);
