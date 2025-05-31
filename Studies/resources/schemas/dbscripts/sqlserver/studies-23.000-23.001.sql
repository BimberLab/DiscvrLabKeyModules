CREATE TABLE studies.lookup_sets (
    rowid int identity(1,1),
    setname nvarchar(100),
    label nvarchar(500),
    description nvarchar(MAX),
    keyField nvarchar(MAX),
    titleColumn nvarchar(MAX),
    container entityid,
    created datetime,
    createdby int,
    modified datetime,
    modifiedby int,

    CONSTRAINT PK_lookup_sets PRIMARY KEY (rowid)
);

CREATE TABLE studies.lookups (
    rowid int identity(1,1),
    setname nvarchar(100),
    value nvarchar(MAX),
    title nvarchar(MAX),
    category nvarchar(MAX),
    description nvarchar(MAX),
    sort_order int,
    date_disabled datetime,
    objectid  nvarchar(100),
    container entityid,
    created datetime,
    createdby int,
    modified datetime,
    modifiedby int,

    CONSTRAINT PK_lookups PRIMARY KEY (rowid)
);