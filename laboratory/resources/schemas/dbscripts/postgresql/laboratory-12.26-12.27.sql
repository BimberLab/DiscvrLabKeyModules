INSERT into laboratory.samplecategory (category) VALUES ('Empty Well');

CREATE TABLE laboratory.plate_templates (
    rowid serial,
    name varchar(200),
    category varchar(100),
    json text,

    container entityid,
    createdby integer,
    created timestamp,
    modifiedby integer,
    modified timestamp,

    constraint PK_plate_templates PRIMARY KEY (rowid)
);