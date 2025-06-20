CREATE TABLE studies.studies (
    rowid int identity(1,1),
    studyName varchar(1000),
    label varchar(1000),
    category varchar(1000),
    description varchar(4000),

    lsid entityid,
    container entityid,
    created datetime,
    createdby int,
    modified datetime,
    modifiedby int,

    CONSTRAINT PK_studies PRIMARY KEY (rowid)
);

CREATE TABLE studies.studyCohorts (
    rowid int identity(1,1),
    studyId int,
    cohortName varchar(4000),
    label varchar(4000),
    category varchar(4000),
    description varchar(4000),
    isControlGroup bit default 0,
    sortOrder int,

    lsid entityid,
    container entityid,
    created datetime,
    createdby int,
    modified datetime,
    modifiedby int,

    CONSTRAINT PK_studyCohorts PRIMARY KEY (rowid)
);

CREATE TABLE studies.anchorEvents (
    rowid int identity(1,1),
    studyId int,
    label varchar(4000),
    description varchar(4000),
    eventProviderName varchar(1000),

    container entityid,
    created datetime,
    createdby int,
    modified datetime,
    modifiedby int,

    CONSTRAINT PK_anchorEvents PRIMARY KEY (rowid)
);

CREATE TABLE studies.expectedTimepoints (
    rowid int identity(1,1),
    studyId int,
    cohortId int,
    label varchar(4000),
    labelShort varchar(100),
    description varchar(4000),
    numericLabel int,
    anchorEvent int,
    rangeMin int,
    rangeMax int,

    container entityid,
    created datetime,
    createdby int,
    modified datetime,
    modifiedby int,

    CONSTRAINT PK_expectedTimepoints PRIMARY KEY (rowid)
);

CREATE TABLE studies.timepointToDate (
    rowid int identity(1,1),
    subjectId varchar(4000),
    timepointId int,
    dateMin datetime,
    dateMax datetime,
    isManualOverride bit default 0,

    container entityid,
    created datetime,
    createdby int,
    modified datetime,
    modifiedby int,

    CONSTRAINT PK_timepointToDate PRIMARY KEY (rowid)
);