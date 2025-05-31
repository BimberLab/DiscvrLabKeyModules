CREATE TABLE studies.lookup_sets (
    rowid serial,
    setname varchar(100),
    label varchar(500),
    description varchar(4000),
    keyField varchar(4000),
    titleColumn varchar(4000),
    container entityid,
    created timestamp,
    createdby int,
    modified timestamp,
    modifiedby int,

    CONSTRAINT PK_lookup_sets PRIMARY KEY (rowid)
);

CREATE TABLE studies.lookups (
    rowid serial,
    setname varchar(100),
    value varchar(4000),
    title varchar(4000),
    category varchar(4000),
    description varchar(4000),
    sort_order int,
    date_disabled timestamp,
    objectid  varchar(4000),
    container entityid,
    created timestamp,
    createdby int,
    modified timestamp,
    modifiedby int,

    CONSTRAINT PK_lookups PRIMARY KEY (rowid)
);