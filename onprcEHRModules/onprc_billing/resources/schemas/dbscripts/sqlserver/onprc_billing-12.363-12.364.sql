CREATE TABLE onprc_billing.projectMultipliers (
    rowid int identity(1,1) not null,
    project integer,
    multiplier double precision,

    startdate datetime,
    enddate datetime,
    comment varchar(4000),

    container entityid,
    createdBy integer,
    created datetime,
    modifiedBy integer,
    modified datetime,

    CONSTRAINT PK_projectMultipliers PRIMARY KEY (rowid)
);

ALTER TABLE onprc_billing.chargeableItems ADD canRaiseFA bit;