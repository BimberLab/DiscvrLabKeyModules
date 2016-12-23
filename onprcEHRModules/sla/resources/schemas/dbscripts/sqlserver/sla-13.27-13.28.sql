-- Adding placeholder protocols table for use in the SLA prototype
-- I expect that this table will have additional columns related to the IACUC protocols.
CREATE TABLE sla.protocols (
    protocol varchar(4000),
    account varchar(255),
    "grant" varchar(255),
    --additional IACUC realted fields expected

    --TODO are the protocols container specific?
    --container entityid not null,

    createdby integer not null,
    created datetime not null,
    modifiedby integer not null,
    modified datetime not null,

    CONSTRAINT PK_protocols PRIMARY KEY (protocol)
);