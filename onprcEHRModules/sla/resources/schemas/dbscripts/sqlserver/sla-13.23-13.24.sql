CREATE TABLE sla.allowableAnimals (
    protocol varchar(4000),
    species varchar (200),
    strain varchar (200),
    gender varchar(100),
    age varchar(100),
    allowed integer,

    startdate datetime,
    enddate datetime,

    objectid entityid not null,
    container entityid not null,
    createdby integer not null,
    created datetime not null,
    modifiedby integer not null,
    modified datetime not null,

    CONSTRAINT PK_allowableAnimals PRIMARY KEY (objectid)
);