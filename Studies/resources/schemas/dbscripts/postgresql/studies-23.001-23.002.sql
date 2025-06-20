CREATE TABLE studies.studies (
    rowid serial,
    studyName varchar(1000),
    label varchar(1000),
    category varchar(1000),
    description varchar(4000),

    lsid entityid,
    container entityid,
    created timestamp,
    createdby int,
    modified timestamp,
    modifiedby int,

    CONSTRAINT PK_studies PRIMARY KEY (rowid)
);

CREATE TABLE studies.studyCohorts (
    rowid serial,
    studyId int,
    cohortName varchar(4000),
    label varchar(4000),
    category varchar(4000),
    description varchar(4000),
    isControlGroup bool default false,
    sortOrder int,

    lsid entityid,
    container entityid,
    created timestamp,
    createdby int,
    modified timestamp,
    modifiedby int,

    CONSTRAINT PK_studyCohorts PRIMARY KEY (rowid)
);

CREATE TABLE studies.anchorEvents (
    rowid serial,
    studyId int,
    label varchar(4000),
    description varchar(4000),
    eventProviderName varchar(1000),

    container entityid,
    created timestamp,
    createdby int,
    modified timestamp,
    modifiedby int,

    CONSTRAINT PK_anchorEvents PRIMARY KEY (rowid)
);

CREATE TABLE studies.expectedTimepoints (
    rowid serial,
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
    created timestamp,
    createdby int,
    modified timestamp,
    modifiedby int,

    CONSTRAINT PK_expectedTimepoints PRIMARY KEY (rowid)
);

CREATE TABLE studies.timepointToDate (
    rowid serial,
    subjectId varchar(4000),
    timepointId int,
    dateMin timestamp,
    dateMax timestamp,
    isManualOverride bool default false,

    container entityid,
    created timestamp,
    createdby int,
    modified timestamp,
    modifiedby int,

    CONSTRAINT PK_timepointToDate PRIMARY KEY (rowid)
);