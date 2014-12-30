ALTER TABLE sequenceanalysis.sequence_readsets ADD librarytype VARCHAR(200);

CREATE TABLE sequenceanalysis.library_types (
  rowid serial,
  type varchar(100),
  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  constraint PK_library_types PRIMARY KEY (rowid)
);
