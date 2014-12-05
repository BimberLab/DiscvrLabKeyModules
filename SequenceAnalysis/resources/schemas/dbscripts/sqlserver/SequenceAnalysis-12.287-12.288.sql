CREATE TABLE sequenceanalysis.chain_files (
  rowid int identity(1,1),
  genomeid1 int,
  genomeid2 int,
  chainfile int,
  version double precision,
  dateDisabled datetime,
  container entityid,
  created datetime,
  createdby int,
  modified datetime,
  modifiedby int,

  constraint PK_chain_files PRIMARY KEY (rowid)
);
