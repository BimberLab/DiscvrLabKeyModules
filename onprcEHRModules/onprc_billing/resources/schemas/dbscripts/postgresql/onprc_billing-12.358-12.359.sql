CREATE TABLE onprc_billing.bloodDrawFeeDefinition (
    rowid serial,
    chargeType int,
    chargeId int,

    active bool default true,
    objectid ENTITYID,
    createdBy int,
    created timestamp,
    modifiedBy int,
    modified timestamp,

    CONSTRAINT PK_bloodDrawFeeDefinition PRIMARY KEY (rowId)
);