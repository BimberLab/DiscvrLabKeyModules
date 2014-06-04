CREATE TABLE onprc_billing.projectMultipliers (
    rowid serial,
    project integer,
    multiplier double precision,

    startdate timestamp,
    enddate timestamp,
    comment varchar(4000),

    container entityid,
    createdBy integer,
    created timestamp,
    modifiedBy integer,
    modified timestamp,

    CONSTRAINT PK_projectMultipliers PRIMARY KEY (rowid)
);

ALTER TABLE onprc_billing.chargeableItems ADD canRaiseFA bool;