SELECT core.executeJavaUpgradeCode('migrateLibraryTracks');

CREATE TABLE sequenceanalysis.readData (
  rowid serial,
  readset int,
  platformUnit varchar(200),
  centerName varchar(200),
  description varchar(4000),
  date timestamp,
  fileid1 int,
  fileid2 int,

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  CONSTRAINT PK_readData PRIMARY KEY (rowid)
);

INSERT INTO sequenceanalysis.readData (readset,platformUnit,centerName,description,date,fileid1,fileid2,container,created,createdby,modified,modifiedby)
SELECT
  rowid as readset,
  CAST(rowid AS varchar) as platformUnit,
  null as centerName,
  null as description,
  null as date,
  fileid,
  fileid2,
  container,
  created,
  createdby,
  modified,
  modifiedby
FROM sequenceanalysis.sequence_readsets;

ALTER TABLE sequenceanalysis.sequence_readsets DROP COLUMN inputMaterial;
ALTER TABLE sequenceanalysis.sequence_readsets DROP COLUMN fileid;
ALTER TABLE sequenceanalysis.sequence_readsets DROP COLUMN fileid2;

DROP TABLE sequenceanalysis.input_material;

CREATE TABLE sequenceanalysis.analysisSets (
  rowid serial,
  name int,
  description varchar(4000),
  category varchar(100),

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  CONSTRAINT PK_analysisSets PRIMARY KEY (rowid)
);

CREATE TABLE sequenceanalysis.analysisSetMembers (
  rowid serial,
  analysisSet int,
  dataId int,
  groupName varchar(100),
  description varchar(4000),

  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  CONSTRAINT PK_analysisSetMembers PRIMARY KEY (rowid)
);

ALTER TABLE sequenceanalysis.outputfiles drop column intermediate;

INSERT INTO sequenceanalysis.sequence_applications (application) VALUES ('DNA Sequencing (Exome)');
INSERT INTO sequenceanalysis.sequence_applications (application) VALUES ('DNA Sequencing (GBS)');