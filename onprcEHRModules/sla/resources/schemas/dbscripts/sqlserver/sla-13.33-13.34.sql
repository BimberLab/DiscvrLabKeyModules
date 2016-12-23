
CREATE TABLE sla.purchaseDrafts (
    rowid INT IDENTITY(1,1) NOT NULL,
    owner USERID NOT NULL,
    content NVARCHAR(MAX) NOT NULL,

    container ENTITYID NOT NULL,
    createdby USERID,
    created DATETIME,
    modifiedby USERID,
    modified DATETIME,

    CONSTRAINT PK_purchaseDrafts PRIMARY KEY (rowid)
);