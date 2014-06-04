DROP TABLE onprc_billing.bloodDrawFeeDefinition;
DROP TABLE onprc_billing.clinicalFeeDefinition;

ALTER TABLE onprc_billing.perDiemFeeDefinition ADD canChargeInfants bit default 0;
ALTER TABLE onprc_billing.procedureFeeDefinition ADD assistingStaff VARCHAR(100);

CREATE TABLE onprc_billing.medicationFeeDefinition (
    rowid int identity(1,1),
    route varchar(100),
    chargeId int,

    active bit default 1,
    objectid ENTITYID,
    createdBy int,
    created datetime,
    modifiedBy int,
    modified datetime,

    CONSTRAINT PK_medicationFeeDefinition PRIMARY KEY (rowId)
);

CREATE TABLE onprc_billing.chargeUnits (
    chargetype varchar(100) NOT NULL,
    shownInBlood bit default 0,
    shownInLabwork bit default 0,
    shownInMedications bit default 0,
    shownInProcedures bit default 0,
    
    active bit default 1,
    container entityid,
    createdBy int,
    created datetime,
    modifiedBy int,
    modified datetime,

    CONSTRAINT PK_chargeUnits PRIMARY KEY (chargetype)
);

CREATE TABLE onprc_billing.chargeUnitAccounts (
    rowid int identity(1,1),
    chargetype varchar(100),
    account varchar(100),
    startdate datetime,
    enddate datetime,
    
    container entityid,
    createdBy int,
    created datetime,
    modifiedBy int,
    modified datetime,

    CONSTRAINT PK_chargeUnitAccounts PRIMARY KEY (rowid)
);