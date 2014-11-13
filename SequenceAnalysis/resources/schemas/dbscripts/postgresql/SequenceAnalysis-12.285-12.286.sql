CREATE TABLE sequenceanalysis.readset_status (
  rowid serial,
  status varchar(100),
  container entityid,
  created timestamp,
  createdby int,
  modified timestamp,
  modifiedby int
);