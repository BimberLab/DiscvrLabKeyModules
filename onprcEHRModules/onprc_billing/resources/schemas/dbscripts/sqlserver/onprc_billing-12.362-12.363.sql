CREATE TABLE onprc_billing.aliasTypes (
    aliasType varchar(500) not null,
    removeSubsidy bit,
    canRaiseFA bit,

    createdBy integer,
    created datetime,
    modifiedBy integer,
    modified datetime,

    CONSTRAINT PK_aliasTypes PRIMARY KEY (aliasType)
);