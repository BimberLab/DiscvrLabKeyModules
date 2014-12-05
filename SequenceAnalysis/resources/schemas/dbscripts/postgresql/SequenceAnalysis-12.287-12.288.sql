CREATE TABLE sequenceanalysis.chain_files (
  rowid serial,
  genomeid1 int,
  genomeid2 int,
  chainfile int,
  version double precision,
  dateDisabled timestamp,
  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int,

  constraint PK_chain_files PRIMARY KEY (rowid)
);
