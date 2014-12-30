ALTER TABLE sequenceanalysis.sequence_readsets ADD librarytype VARCHAR(200);

CREATE TABLE sequenceanalysis.library_types (
  rowid int identity(1,1),
  type varchar(100),
  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_library_types PRIMARY KEY (rowid)
);
