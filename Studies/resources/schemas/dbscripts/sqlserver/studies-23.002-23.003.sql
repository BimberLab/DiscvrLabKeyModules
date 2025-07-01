CREATE TABLE studies.subjectAnchorDates (
    rowid int identity(1,1),
    subjectId varchar(4000),
    date int,
    eventLabel varchar(1000),
    anchorEventId int,

    container entityid,
    created datetime,
    createdby int,
    modified datetime,
    modifiedby int,

    CONSTRAINT PK_subjectAnchorDates PRIMARY KEY (rowid)
);