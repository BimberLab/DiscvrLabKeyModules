CREATE TABLE studies.subjectAnchorDates (
    rowid serial,
    subjectId varchar(4000),
    date int,
    eventLabel varchar(1000),
    anchorEventId int,

    container entityid,
    created timestamp,
    createdby int,
    modified timestamp,
    modifiedby int,

    CONSTRAINT PK_subjectAnchorDates PRIMARY KEY (rowid)
);