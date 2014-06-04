CREATE TABLE onprc_billing.aliasTypes (
    aliasType varchar(500),
    removeSubsidy bool,
    canRaiseFA bool,

    createdBy integer,
    created timestamp,
    modifiedBy integer,
    modified timestamp,

    CONSTRAINT PK_aliasTypes PRIMARY KEY (aliasType)
);