CREATE TABLE onprc_billing.bloodDrawFeeDefinition (
    rowid int identity(1,1),
    chargeType int,
    chargeId int,

    active bit default 1,
    objectid ENTITYID,
    createdBy int,
    created datetime,
    modifiedBy int,
    modified datetime,

    CONSTRAINT PK_bloodDrawFeeDefinition PRIMARY KEY (rowId)
);