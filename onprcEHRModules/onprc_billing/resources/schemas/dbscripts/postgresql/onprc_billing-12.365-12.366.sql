DROP TABLE onprc_billing.bloodDrawFeeDefinition;
DROP TABLE onprc_billing.clinicalFeeDefinition;

ALTER TABLE onprc_billing.perDiemFeeDefinition ADD canChargeInfants bool default false;
ALTER TABLE onprc_billing.procedureFeeDefinition ADD assistingStaff VARCHAR(100);

CREATE TABLE onprc_billing.medicationFeeDefinition (
    rowid serial,
    route varchar(100),
    chargeId int,

    active bool default true,
    objectid ENTITYID,
    createdBy int,
    created timestamp,
    modifiedBy int,
    modified timestamp,

    CONSTRAINT PK_medicationFeeDefinition PRIMARY KEY (rowId)
);

CREATE TABLE onprc_billing.chargeUnits (
    chargetype varchar(100) NOT NULL,
    shownInBlood bool default false,
    shownInLabwork bool default false,
    shownInMedications bool default false,
    shownInProcedures bool default false,

    active bool default true,
    container entityid,
    createdBy int,
    created timestamp,
    modifiedBy int,
    modified timestamp,

    CONSTRAINT PK_chargeUnits PRIMARY KEY (chargetype)
);

CREATE TABLE onprc_billing.chargeUnitAccounts (
    rowid serial,
    chargetype varchar(100),
    account varchar(100),
    startdate timestamp,
    enddate timestamp,

    container entityid,
    createdBy int,
    created timestamp,
    modifiedBy int,
    modified timestamp,

    CONSTRAINT PK_chargeUnitAccounts PRIMARY KEY (rowid)
);